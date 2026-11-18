package com.boombox.client;

import com.boombox.SpeakerType;

import com.boombox.SongData;
import com.boombox.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.boombox.QueueItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Lista de boomboxes tocando (recebida do servidor), no chão ou na mão de jogadores.
 * Inicia/para o áudio conforme o jogador se aproxima ou se afasta, sempre no ponto certo
 * da música usando o relógio do servidor.
 */
public final class ClientBoomboxManager {
    public static final class State {
        public final String key;
        public final String dim;
        public final BlockPos pos;
        /** Tipo da caixa (define alcance e volume). */
        public final SpeakerType type;
        /** Quantas caixas estão sincronizadas com esta (0 = nenhuma). */
        public final int group;
        public final UUID holder;
        public final SongData song;
        boolean finished;
        String error;
        boolean reportedEnd;
        boolean suggested;
        String prefetched;

        State(Payloads.BoomboxState p) {
            this.key = p.key();
            this.dim = p.dim();
            this.pos = p.pos();
            this.holder = p.holder();
            this.song = p.song();
            this.type = SpeakerType.byOrdinal(p.speakerType());
            this.group = p.group();
        }

        public String error() { return error; }
        public boolean finished() { return finished; }

        boolean sameStream(SongData other) {
            return song.url().equals(other.url()) && song.startMs() == other.startMs() && song.paused() == other.paused();
        }
    }

    /** Distância (blocos) em que o som começa a tocar / some completamente. */
    public static final double HEAR_DISTANCE = 48;
    private static final double FULL_VOLUME_DISTANCE = 6;

    private static final Map<String, State> STATES = new HashMap<>();
    private static final Map<String, AudioStream> STREAMS = new HashMap<>();
    /** Boomboxes paradas com configurações (só para o menu). */
    private static final Map<String, SongData> IDLE = new HashMap<>();
    /** id da entidade do jogador → chave da boombox que ele segura */
    private static final Map<Integer, String> HELD_BY_ENTITY = new HashMap<>();
    /** relógio do servidor - relógio local */
    private static long clockOffset;

    private ClientBoomboxManager() {}

    public static long serverNow() {
        return System.currentTimeMillis() + clockOffset;
    }

    public static void onState(Payloads.BoomboxState p) {
        clockOffset = p.serverNowMs() - System.currentTimeMillis();
        String k = p.key();
        State old = STATES.get(k);
        if (!p.song().hasSong()) {
            STATES.remove(k);
            closeStream(k);
            // boombox parada, mas com fila/volume/fila automática configurados: guarda para o menu
            if (p.song().equals(SongData.EMPTY.withVolume(p.song().volume())) && p.song().volume() == 100) IDLE.remove(k);
            else IDLE.put(k, p.song());
            return;
        }
        IDLE.remove(k);
        State st = new State(p);
        if (old != null && old.song.url().equals(p.song().url()) && old.song.startMs() == p.song().startMs()) {
            st.suggested = old.suggested;
            st.reportedEnd = old.reportedEnd;
            st.prefetched = old.prefetched;
        }
        if (old != null && old.sameStream(p.song())) {
            st.finished = old.finished;
            st.error = old.error;
        } else {
            closeStream(k);
        }
        STATES.put(k, st);
    }

    // ------------------------------------------------------------ consultas (menu e renderização)

    public static State getBlock(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return null;
        return STATES.get(Payloads.blockKey(mc.level.dimension().identifier().toString(), pos));
    }

    /** Dados da boombox no chão para o menu (tocando ou parada). */
    public static SongData blockSong(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return null;
        String k = Payloads.blockKey(mc.level.dimension().identifier().toString(), pos);
        State st = STATES.get(k);
        return st != null ? st.song : IDLE.get(k);
    }

    public static State getHeld(UUID player) {
        return STATES.get(Payloads.playerKey(player));
    }

    public static boolean isLoading(State st) {
        if (st == null) return false;
        AudioStream s = STREAMS.get(st.key);
        return s != null && s.isLoading();
    }

    private static void levels(String key, float[] out) {
        out[0] = out[1] = 0;
        State st = key == null ? null : STATES.get(key);
        AudioStream s = key == null ? null : STREAMS.get(key);
        if (st == null || s == null) {
            return;
        }
        s.levels(out);
        float vol = 0.35f + 0.65f * st.song.volume() / 100f;
        out[0] *= vol;
        out[1] *= vol;
    }

    /** Grave/agudo da boombox no chão (0–1). */
    public static void levelsForBlock(String dim, BlockPos pos, float[] out) {
        levels(Payloads.blockKey(dim, pos), out);
    }

    /** Grave/agudo da boombox na mão de um jogador (pelo id da entidade). */
    public static void levelsForEntity(int entityId, float[] out) {
        levels(HELD_BY_ENTITY.get(entityId), out);
    }

    /** Grave/agudo para um item (procura a boombox tocando essa mesma música). */
    public static void levelsForSong(SongData song, float[] out) {
        out[0] = out[1] = 0;
        if (song == null || !song.hasSong() || song.paused()) return;
        for (State st : STATES.values()) {
            if (st.holder != null && st.song.url().equals(song.url()) && st.song.startMs() == song.startMs()) {
                levels(st.key, out);
                return;
            }
        }
    }

    // ------------------------------------------------------------ tick

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!STREAMS.isEmpty()) stopAll();
            return;
        }
        String dim = mc.level.dimension().identifier().toString();
        Vec3 ear = mc.gameRenderer.mainCamera().position();
        float master = mc.options.getFinalSoundSourceVolume(SoundSource.RECORDS);
        HELD_BY_ENTITY.clear();

        for (State st : STATES.values()) {
            Vec3 src = sourcePosition(mc, st);
            if (st.holder != null) {
                Player holder = mc.level.getPlayerByUUID(st.holder);
                if (holder != null) HELD_BY_ENTITY.put(holder.getId(), st.key);
            }
            double dist = src == null ? Double.MAX_VALUE : Math.sqrt(ear.distanceToSqr(src));
            AudioStream stream = STREAMS.get(st.key);
            double hear = hearDistance(mc, st);
            boolean inRange = src != null && st.dim.equals(dim)
                    && dist < (stream != null ? hear + 16 : hear);
            boolean shouldPlay = inRange && !st.song.paused() && !st.finished;

            if (shouldPlay && stream == null) {
                SongData song = st.song;
                stream = new AudioStream(song.url(), st.key, () -> song.positionMs(serverNow()));
                stream.start();
                STREAMS.put(st.key, stream);
            } else if (!shouldPlay && stream != null) {
                closeStream(st.key);
                stream = null;
            }

            if (stream != null) {
                double full = FULL_VOLUME_DISTANCE * hear / HEAR_DISTANCE; // caixas maiores enchem mais o ambiente
                double att = dist <= full ? 1.0 : Math.max(0.0, 1.0 - (dist - full) / (hear - full));
                // caixas maiores tocam mais alto que a boombox
                float loud = st.holder != null ? 1f : st.type.loudness;
                float gain = (float) (master * (st.song.volume() / 100.0) * att * att * loud);
                stream.tick(src.x, src.y, src.z, gain);
                if (!stream.isLoading()) maybeAutoQueue(mc, st);
                if (!st.song.queue().isEmpty() && !st.song.queue().get(0).url().equals(st.prefetched)) {
                    st.prefetched = st.song.queue().get(0).url();
                    YouTube.prefetch(st.prefetched); // a próxima já fica pronta
                }
                if (stream.isFinished()) {
                    st.finished = true;
                    st.error = stream.error();
                    if (st.error == null && !st.reportedEnd) {
                        st.reportedEnd = true;
                        ClientPlayNetworking.send(new Payloads.SongEnded(st.key, st.song.url(), st.song.startMs()));
                    }
                    if (st.error != null && dist < 16) {
                        mc.player.sendOverlayMessage(Component.literal("§cBoombox: " + shorten(st.error)));
                    }
                    closeStream(st.key);
                }
            }
        }

        // streams sem estado (segurança)
        Iterator<Map.Entry<String, AudioStream>> it = STREAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AudioStream> e = it.next();
            if (!STATES.containsKey(e.getKey())) {
                e.getValue().close();
                it.remove();
            }
        }
    }

    /** Até onde o som chega: quanto maior a caixa, mais longe (o servidor informa o tipo). */
    private static double hearDistance(Minecraft mc, State st) {
        return st.holder != null ? HEAR_DISTANCE : st.type.hearDistance;
    }

    /**
     * Fila automática: quando a fila está vazia, busca músicas parecidas (Mix do YouTube) e sugere
     * ao servidor. Vários ouvintes podem tentar; o servidor aceita só a primeira sugestão.
     */
    private static void maybeAutoQueue(Minecraft mc, State st) {
        if (st.suggested || !st.song.autoplay() || !st.song.queue().isEmpty() || st.song.paused()) return;
        if (YouTube.videoId(st.song.url()) == null) return;
        st.suggested = true;
        String key = st.key, url = st.song.url();
        long delay = (long) (Math.random() * 2500);
        Tools.EXEC.submit(() -> {
            try {
                Thread.sleep(delay);
                if (!stillNeedsSuggestions(mc, key, url)) return;
                List<YouTube.Result> related = YouTube.related(url, 12);
                List<QueueItem> items = related.stream()
                        .map(r -> new QueueItem(r.url(), r.title(), r.durationSec()))
                        .toList();
                if (items.isEmpty()) return;
                mc.execute(() -> {
                    State now = STATES.get(key);
                    if (now == null || !now.song.url().equals(url) || !now.song.queue().isEmpty()) return;
                    List<QueueItem> fresh = items.stream().filter(i -> !now.song.history().contains(i.url())).limit(8).toList();
                    if (!fresh.isEmpty()) ClientPlayNetworking.send(new Payloads.AutoSuggest(key, url, fresh));
                });
            } catch (Exception e) {
                com.boombox.BoomboxMod.LOGGER.info("Boombox: fila automática falhou: {}", e.getMessage());
            }
        });
    }

    private static boolean stillNeedsSuggestions(Minecraft mc, String key, String url) {
        java.util.concurrent.CompletableFuture<Boolean> f = new java.util.concurrent.CompletableFuture<>();
        mc.execute(() -> {
            State now = STATES.get(key);
            f.complete(now != null && now.song.url().equals(url) && now.song.queue().isEmpty() && now.song.autoplay());
        });
        try {
            return f.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /** Quantos ms faltam para a música começar (0 se já começou). */
    public static long startsInMs(State st) {
        if (st == null || st.song.paused()) return 0;
        return Math.max(0, -st.song.positionMs(serverNow()));
    }

    /** De onde o som sai: centro do bloco, ou ao lado da cabeça de quem segura a boombox. */
    private static Vec3 sourcePosition(Minecraft mc, State st) {
        if (st.holder == null) {
            return new Vec3(st.pos.getX() + 0.5, st.pos.getY() + 0.4, st.pos.getZ() + 0.5);
        }
        Player p = mc.level.getPlayerByUUID(st.holder);
        if (p == null) return null;
        return p.getEyePosition();
    }

    private static String shorten(String s) {
        return s.length() > 90 ? s.substring(0, 90) + "..." : s;
    }

    private static void closeStream(String k) {
        AudioStream s = STREAMS.remove(k);
        if (s != null) s.close();
    }

    public static void stopAll() {
        STREAMS.values().forEach(AudioStream::close);
        STREAMS.clear();
    }

    public static void clear() {
        stopAll();
        STATES.clear();
        IDLE.clear();
        HELD_BY_ENTITY.clear();
    }
}
