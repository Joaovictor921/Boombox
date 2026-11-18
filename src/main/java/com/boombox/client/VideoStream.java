package com.boombox.client;

import com.boombox.BoomboxMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Vídeo de uma música (YouTube) para o telão: o ffmpeg decodifica em 384x216, 20 quadros por segundo,
 * a partir do ponto atual da música, e cada quadro aparece quando a música chega no tempo dele
 * (mesmo relógio do áudio, então imagem e som ficam juntos).
 */
public final class VideoStream {
    public static final int W = 384, H = 216, FPS = 20;
    private static final int FRAME = W * H * 4;
    private static final AtomicInteger IDS = new AtomicInteger();

    private record Frame(long ptsMs, byte[] rgba) {}

    private final String url;
    private final LongSupplier elapsedMs;
    private final Identifier textureId = BoomboxMod.id("dynamic/telao_" + IDS.incrementAndGet());
    private final ArrayBlockingQueue<Frame> frames = new ArrayBlockingQueue<>(30);
    private DynamicTexture texture;
    private boolean hasFrame;
    private volatile boolean closed;
    private volatile boolean failed;
    private volatile Process process;
    private Thread thread;

    public VideoStream(String url, LongSupplier elapsedMs) {
        this.url = url;
        this.elapsedMs = elapsedMs;
    }

    public void start() {
        thread = new Thread(this::decodeLoop, "Boombox-Video");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean hasFrame() {
        return hasFrame;
    }

    public boolean failed() {
        return failed;
    }

    private void decodeLoop() {
        try {
            Tools.ensureReady().get();
            YouTube.StreamInfo info = YouTube.videoStreamUrl(url);
            if (closed) return;
            long seek = Math.max(0, elapsedMs.getAsLong());
            List<String> cmd = new ArrayList<>(List.of(Tools.ffmpeg(), "-hide_banner", "-loglevel", "error", "-nostdin",
                    "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5"));
            if (info.userAgent() != null) cmd.addAll(List.of("-user_agent", info.userAgent()));
            if (seek > 0) cmd.addAll(List.of("-ss", String.format(java.util.Locale.ROOT, "%.3f", seek / 1000.0)));
            cmd.addAll(List.of("-i", info.url(), "-an",
                    "-vf", "fps=" + FPS + ",scale=" + W + ":" + H + ":force_original_aspect_ratio=decrease,"
                            + "pad=" + W + ":" + H + ":(ow-iw)/2:(oh-ih)/2:black",
                    "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1"));
            Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process = p;
            long index = 0;
            try (InputStream in = p.getInputStream()) {
                while (!closed) {
                    byte[] buf = in.readNBytes(FRAME);
                    if (buf.length < FRAME) break;
                    long pts = seek + index++ * 1000L / FPS;
                    // muito atrasado (internet lenta)? pula o quadro em vez de mostrar fora de hora
                    if (pts < elapsedMs.getAsLong() - 400) continue;
                    while (!closed && !frames.offer(new Frame(pts, buf), 200, TimeUnit.MILLISECONDS)) {
                        // fila cheia: espera a música andar
                    }
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            failed = true;
            BoomboxMod.LOGGER.info("Boombox: telão sem vídeo para {} ({})", url, e.getMessage());
        } finally {
            Process p = process;
            if (p != null) p.destroy();
        }
    }

    /**
     * Mostra o quadro do momento atual (chamar na thread de desenho). Devolve a textura do vídeo,
     * ou null se ainda não tem nenhum quadro.
     */
    public Identifier update() {
        if (closed) return null;
        long pos = elapsedMs.getAsLong();
        Frame show = null;
        Frame f;
        while ((f = frames.peek()) != null && f.ptsMs <= pos) {
            show = frames.poll();
        }
        if (show != null) {
            if (texture == null) {
                texture = new DynamicTexture(() -> "Boombox telão", W, H, true);
                Minecraft.getInstance().getTextureManager().register(textureId, texture);
            }
            MemoryUtil.memByteBuffer(texture.getPixels().getPointer(), FRAME).put(0, show.rgba);
            texture.upload();
            hasFrame = true;
        }
        return hasFrame ? textureId : null;
    }

    public void close() {
        closed = true;
        Process p = process;
        if (p != null) p.destroyForcibly();
        if (thread != null) thread.interrupt();
        frames.clear();
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
            texture = null;
        }
    }
}
