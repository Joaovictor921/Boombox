package com.boombox.client;

import com.boombox.BoomboxMod;
import org.lwjgl.openal.AL10;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL11;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Uma música tocando numa boombox. Um thread decodifica com ffmpeg para PCM mono 48 kHz
 * (e mede o grave e o agudo de cada pedacinho), e a thread principal alimenta uma fonte OpenAL
 * posicionada na boombox (som 3D).
 */
public final class AudioStream {
    public static final int SAMPLE_RATE = 48_000;
    private static final int BYTES_PER_MS = SAMPLE_RATE * 2 / 1000; // mono, 16 bits
    private static final int CHUNK = SAMPLE_RATE * 2 / 10;          // 100 ms
    private static final int SUB = 5;                               // níveis a cada 20 ms
    private static final int SUB_SAMPLES = CHUNK / 2 / SUB;
    private static final int MAX_QUEUED_BUFFERS = 10;               // ~1 s na placa de som

    /** Pedaço de áudio + nível de grave/agudo de cada 20 ms dele. */
    private record Chunk(byte[] data, float[] bass, float[] treble) {}

    private record Queued(int buffer, Chunk chunk) {}

    private final String url;
    private final String name;
    private final LongSupplier elapsedMs;

    private final LinkedBlockingDeque<Chunk> decoded = new LinkedBlockingDeque<>(80);
    private final ArrayDeque<Queued> alBuffers = new ArrayDeque<>();
    private final ByteBuffer direct = ByteBuffer.allocateDirect(CHUNK).order(ByteOrder.nativeOrder());

    private volatile boolean closed;
    private volatile boolean decoderDone;
    private volatile String error;
    private volatile long seekMs = -1;
    private volatile Process process;
    private Thread thread;

    private int source;
    /** Bytes já tirados da fila de decodificação (pulados ou enviados à placa de som). */
    private long takenBytes;
    /** Bytes de buffers que a placa de som já terminou de tocar. */
    private long playedBytes;
    private boolean synced;
    private boolean finished;
    private int ticks;
    /** Posição sugerida pelo Sound Physics (som contornando paredes), ou null. */
    private double[] physicsPos;

    // filtros (thread de decodificação)
    private float lowBass, lowMid;

    // níveis suavizados (thread principal)
    private float bassSmooth, trebleSmooth;
    private long lastLevelNanos;

    /** Caixa estourada (DJ Leo): grave exagerado e som distorcido, saturando. */
    private final boolean blown;

    public AudioStream(String url, String name, LongSupplier elapsedMs) {
        this(url, name, elapsedMs, false);
    }

    public AudioStream(String url, String name, LongSupplier elapsedMs, boolean blown) {
        this.blown = blown;
        this.url = url;
        this.name = name;
        this.elapsedMs = elapsedMs;
    }

    public void start() {
        thread = new Thread(this::decodeLoop, "Boombox-Audio " + name);
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isFinished() { return finished; }
    public boolean blown() { return blown; }
    public String error() { return error; }
    public boolean isLoading() { return source == 0 && !finished; }
    /** Já decodificou e está só esperando o horário de começar. */
    public boolean isReady() { return seekMs >= 0 && !decoded.isEmpty(); }

    // ------------------------------------------------------------ decodificação (thread própria)

    private void decodeLoop() {
        try {
            if (url.startsWith("local:")) {
                decode(ClientFiles.get(url.substring(6)).get(10, TimeUnit.MINUTES).toString(), null);
                return;
            }
            Tools.ensureReady().get();
            if (YouTube.isDirectAudio(url)) {
                decode(url, null);
                return;
            }
            // 1) Streaming: toca direto do YouTube, sem baixar a música
            boolean streamed = false;
            try {
                YouTube.StreamInfo s = YouTube.streamUrl(url);
                if (closed) return;
                streamed = decode(s.url(), s.userAgent());
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                BoomboxMod.LOGGER.info("Boombox: streaming falhou ({}), baixando a música", e.getMessage());
            }
            // 2) Se o YouTube bloqueou o streaming, baixa o áudio e toca do arquivo
            if (!streamed && !closed) {
                decode(YouTube.downloadAudio(url).toString(), null);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            error = c.getMessage() != null ? c.getMessage() : c.toString();
            BoomboxMod.LOGGER.warn("Boombox: não foi possível tocar {}", url, e);
        } finally {
            decoderDone = true;
            Process p = process;
            if (p != null) p.destroy();
        }
    }

    /**
     * Roda o ffmpeg a partir do ponto atual da música e coloca o PCM na fila.
     * Devolve false se o ffmpeg falhou sem gerar áudio (ex.: YouTube bloqueou o stream).
     */
    private boolean decode(String input, String userAgent) throws Exception {
        if (closed) return true;
        long seek = Math.max(0, elapsedMs.getAsLong());
        List<String> cmd = new ArrayList<>(List.of(Tools.ffmpeg(), "-hide_banner", "-loglevel", "error", "-nostdin"));
        if (input.startsWith("http://") || input.startsWith("https://")) {
            cmd.addAll(List.of("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_on_network_error", "1",
                    "-reconnect_delay_max", "5"));
            if (userAgent != null) cmd.addAll(List.of("-user_agent", userAgent));
        }
        if (seek > 0) cmd.addAll(List.of("-ss", String.format(java.util.Locale.ROOT, "%.3f", seek / 1000.0)));
        cmd.addAll(List.of("-i", input, "-vn"));
        if (blown) {
            // grave lá em cima, agudo abafado e volume passando do limite: o som "estoura" (clipa)
            cmd.addAll(List.of("-af", "bass=g=20:f=60:w=0.7,bass=g=9:f=120:w=1,treble=g=-6,volume=3.6,"
                    + "asoftclip=type=tanh:threshold=0.8,volume=1.5"));
        }
        cmd.addAll(List.of("-ac", "1", "-ar", String.valueOf(SAMPLE_RATE), "-f", "s16le", "pipe:1"));

        seekMs = seek;
        Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        process = p;
        long produced = 0;
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[CHUNK];
            while (!closed) {
                int n = in.readNBytes(buf, 0, CHUNK);
                if (n <= 0) break;
                n &= ~1; // amostras de 16 bits inteiras
                Chunk chunk = analyze(Arrays.copyOf(buf, n));
                produced += n;
                while (!closed && !decoded.offer(chunk, 200, TimeUnit.MILLISECONDS)) {
                    // fila cheia: espera a placa de som consumir
                }
            }
        }
        boolean exitedOk = p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
        p.destroy();
        // sem áudio mas saída OK = a música já tinha acabado nesse ponto
        return produced > 0 || exitedOk || closed;
    }

    /** Mede o grave (passa-baixa ~150 Hz) e o agudo (acima de ~2,5 kHz) a cada 20 ms. */
    private Chunk analyze(byte[] data) {
        float[] bass = new float[SUB];
        float[] treble = new float[SUB];
        final float aBass = 0.0194f; // 150 Hz
        final float aMid = 0.279f;   // 2,5 kHz
        int samples = data.length / 2;
        for (int s = 0; s < SUB; s++) {
            int from = s * SUB_SAMPLES, to = Math.min(samples, from + SUB_SAMPLES);
            double sb = 0, st = 0;
            int count = 0;
            for (int i = from; i < to; i++) {
                float x = (short) ((data[2 * i] & 0xFF) | (data[2 * i + 1] << 8)) / 32768f;
                lowBass += aBass * (x - lowBass);
                lowMid += aMid * (x - lowMid);
                float hi = x - lowMid;
                sb += lowBass * lowBass;
                st += hi * hi;
                count++;
            }
            if (count > 0) {
                bass[s] = Math.min(1f, (float) Math.sqrt(sb / count) * 4.5f);
                treble[s] = Math.min(1f, (float) Math.sqrt(st / count) * 9f);
            }
        }
        return new Chunk(data, bass, treble);
    }

    // ------------------------------------------------------------ reprodução (thread principal)

    /** Filtro passa-baixa próprio (abafa os agudos atrás de paredes) quando o Sound Physics não está instalado. */
    private int lowpass;
    private static boolean efxBroken;
    /** Onde o Sound Physics calculou o ambiente pela última vez. */
    private double[] physicsAt;

    public void tick(double x, double y, double z, float gain) {
        tick(x, y, z, gain, 1f);
    }

    /** Chamado a cada tick do cliente. highs = quanto dos agudos passa (1 = som aberto). */
    public void tick(double x, double y, double z, float gain, float highs) {
        if (closed || finished) return;
        try {
            if (!synced) {
                if (seekMs < 0) return; // ainda resolvendo/baixando
                // a música ainda não começou para ninguém: espera o horário combinado (começa do zero)
                if (elapsedMs.getAsLong() < 0) return;
                // Pula o áudio correspondente ao tempo gasto preparando, para ficar em sincronia com os outros
                long behindBytes = (elapsedMs.getAsLong() - seekMs) * BYTES_PER_MS - takenBytes;
                behindBytes &= ~1L;
                while (behindBytes > 0) {
                    Chunk c = decoded.peek();
                    if (c == null) break;
                    decoded.poll();
                    if (c.data.length <= behindBytes) {
                        takenBytes += c.data.length;
                        behindBytes -= c.data.length;
                    } else {
                        decoded.offerFirst(new Chunk(Arrays.copyOfRange(c.data, (int) behindBytes, c.data.length), c.bass, c.treble));
                        takenBytes += behindBytes;
                        behindBytes = 0;
                    }
                }
                if (behindBytes > 0 || decoded.isEmpty()) {
                    if (decoderDone) finished = true;
                    return;
                }
                synced = true;
            }

            if (source == 0) {
                source = AL10.alGenSources();
                AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f); // a atenuação é feita aqui no código
                AL10.alSourcef(source, AL10.AL_MAX_GAIN, 12f); // caixas grandes passam do volume "normal"
                AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            }
            // eco/abafamento do Sound Physics Remastered (se instalado): logo que a fonte nasce, a cada 4 ticks
            // e na hora em que o som muda de caixa (antes ficava até meio segundo sem abafar: o som "vazava")
            if (SoundPhysicsCompat.available()) {
                boolean moved = physicsAt == null || Math.abs(physicsAt[0] - x) + Math.abs(physicsAt[1] - y) + Math.abs(physicsAt[2] - z) > 1.5;
                if (ticks++ % 4 == 0 || moved) {
                    Vec3 p = SoundPhysicsCompat.process(source, x, y, z);
                    physicsPos = p == null ? null : new double[]{p.x, p.y, p.z};
                    physicsAt = new double[]{x, y, z};
                }
            } else {
                applyLowpass(highs);
            }
            if (physicsPos != null) AL10.alSource3f(source, AL10.AL_POSITION, (float) physicsPos[0], (float) physicsPos[1], (float) physicsPos[2]);
            else AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
            AL10.alSourcef(source, AL10.AL_GAIN, gain);

            int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
            while (processed-- > 0 && !alBuffers.isEmpty()) {
                int b = AL10.alSourceUnqueueBuffers(source);
                Queued done = null;
                for (Queued q : alBuffers) if (q.buffer == b) { done = q; break; }
                if (done != null) {
                    alBuffers.remove(done);
                    playedBytes += done.chunk.data.length;
                }
                AL10.alDeleteBuffers(b);
            }

            // Correção de atraso: se a internet travou e o som ficou para trás, pula para o ponto certo
            if (ticks % 20 == 0 && driftMs() > 120) {
                resync();
                return;
            }

            while (alBuffers.size() < MAX_QUEUED_BUFFERS) {
                Chunk c = decoded.poll();
                if (c == null) break;
                takenBytes += c.data.length;
                direct.clear();
                direct.put(c.data).flip();
                int b = AL10.alGenBuffers();
                AL10.alBufferData(b, AL10.AL_FORMAT_MONO16, direct, SAMPLE_RATE);
                AL10.alSourceQueueBuffers(source, b);
                alBuffers.add(new Queued(b, c));
            }

            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && !alBuffers.isEmpty()) {
                AL10.alSourcePlay(source);
            }
            if (decoderDone && decoded.isEmpty() && alBuffers.isEmpty()) {
                finished = true;
            }
        } catch (Throwable t) {
            error = "erro de áudio: " + t.getMessage();
            finished = true;
            BoomboxMod.LOGGER.warn("Boombox: erro OpenAL", t);
        }
    }

    /** Quanto o som que está saindo agora está atrasado em relação ao relógio do servidor (ms). */
    private long driftMs() {
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (state != AL10.AL_PLAYING) return 0;
        int offset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
        // bytes já pulados + tocados até agora (o offset conta dentro dos buffers ainda na fila)
        long queuedBytes = 0;
        for (Queued q : alBuffers) queuedBytes += q.chunk.data.length;
        long firstQueuedAt = takenBytes - queuedBytes;
        long playingByte = firstQueuedAt + offset * 2L;
        long playingMs = seekMs + playingByte / BYTES_PER_MS;
        return elapsedMs.getAsLong() - playingMs;
    }

    /** Joga fora o que está na placa de som e volta a sincronizar pelo relógio. */
    private void resync() {
        AL10.alSourceStop(source);
        AL10.alSourcei(source, AL10.AL_BUFFER, 0);
        for (Queued q : alBuffers) AL10.alDeleteBuffers(q.buffer);
        alBuffers.clear();
        synced = false;
    }

    /**
     * Nível de grave e agudo do trecho que está saindo da caixa agora (0–1), suavizado como
     * um cone de alto-falante de verdade: sobe rápido, desce mais devagar.
     * Escreve em out[0] (grave) e out[1] (agudo).
     */
    public void levels(float[] out) {
        float bassTarget = 0, trebleTarget = 0;
        try {
            if (source != 0 && !alBuffers.isEmpty() && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
                // o offset conta desde o começo da fila (inclusive buffers já tocados e ainda não removidos)
                int offset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
                for (Queued q : alBuffers) {
                    int len = q.chunk.data.length / 2;
                    if (offset < len) {
                        int idx = Math.max(0, Math.min(SUB - 1, offset / SUB_SAMPLES));
                        bassTarget = q.chunk.bass[idx];
                        trebleTarget = q.chunk.treble[idx];
                        break;
                    }
                    offset -= len;
                }
            }
        } catch (Throwable ignored) {}

        long now = System.nanoTime();
        float dt = lastLevelNanos == 0 ? 0.016f : Math.min(0.2f, (now - lastLevelNanos) / 1e9f);
        lastLevelNanos = now;
        bassSmooth += (bassTarget - bassSmooth) * (1f - (float) Math.exp(-dt / (bassTarget > bassSmooth ? 0.012f : 0.09f)));
        trebleSmooth += (trebleTarget - trebleSmooth) * (1f - (float) Math.exp(-dt / (trebleTarget > trebleSmooth ? 0.006f : 0.04f)));
        out[0] = bassSmooth;
        out[1] = trebleSmooth;
    }

    /** Abafa os agudos com o filtro passa-baixa do OpenAL (EFX). Se a placa de som não suportar, só o volume abaixa. */
    private void applyLowpass(float highs) {
        if (efxBroken || source == 0) return;
        try {
            if (lowpass == 0) {
                if (highs >= 0.99f) return; // som aberto: nem precisa do filtro
                lowpass = org.lwjgl.openal.EXTEfx.alGenFilters();
                org.lwjgl.openal.EXTEfx.alFilteri(lowpass, org.lwjgl.openal.EXTEfx.AL_FILTER_TYPE, org.lwjgl.openal.EXTEfx.AL_FILTER_LOWPASS);
            }
            org.lwjgl.openal.EXTEfx.alFilterf(lowpass, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAIN, 1f);
            org.lwjgl.openal.EXTEfx.alFilterf(lowpass, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAINHF, Math.max(0.01f, Math.min(1f, highs)));
            AL10.alSourcei(source, org.lwjgl.openal.EXTEfx.AL_DIRECT_FILTER, lowpass);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) throw new IllegalStateException("EFX");
        } catch (Throwable t) {
            efxBroken = true;
            BoomboxMod.LOGGER.info("Boombox: filtro de abafamento indisponível ({}), usando só o volume", t.toString());
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (thread != null) thread.interrupt();
        Process p = process;
        if (p != null) p.destroyForcibly();
        decoded.clear();
        try {
            if (source != 0) {
                AL10.alSourceStop(source);
                AL10.alSourcei(source, AL10.AL_BUFFER, 0);
                AL10.alDeleteSources(source);
                source = 0;
            }
            for (Queued q : alBuffers) AL10.alDeleteBuffers(q.buffer);
            if (lowpass != 0) {
                org.lwjgl.openal.EXTEfx.alDeleteFilters(lowpass);
                lowpass = 0;
            }
        } catch (Throwable ignored) {}
        alBuffers.clear();
    }
}
