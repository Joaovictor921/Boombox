package com.boombox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * O que uma boombox está tocando, a fila e as configurações. Fica no bloco e também no item
 * (componente "boombox:song"), então tudo continua quando você pega ou coloca a boombox.
 *
 * @param startMs     horário (relógio do servidor) em que a música está no ponto 0 — pode estar no futuro,
 *                    para todos terem tempo de carregar e começarem juntos do início
 * @param pausedPosMs posição em que a música foi pausada
 * @param volume      0–100
 * @param queue       próximas músicas
 * @param autoplay    fila automática: quando a fila acaba, adiciona músicas parecidas
 * @param history     últimas músicas tocadas (para a fila automática não repetir)
 */
public record SongData(String url, String title, long startMs, long durationSec,
                       boolean paused, long pausedPosMs, int volume,
                       List<QueueItem> queue, boolean autoplay, List<String> history) {

    public static final int MAX_QUEUE = 200;
    public static final int MAX_HISTORY = 30;

    public static final SongData EMPTY = new SongData("", "", 0, 0, false, 0, 100, List.of(), true, List.of());

    public static final Codec<SongData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("url", "").forGetter(SongData::url),
            Codec.STRING.optionalFieldOf("title", "").forGetter(SongData::title),
            Codec.LONG.optionalFieldOf("start_ms", 0L).forGetter(SongData::startMs),
            Codec.LONG.optionalFieldOf("duration_sec", 0L).forGetter(SongData::durationSec),
            Codec.BOOL.optionalFieldOf("paused", false).forGetter(SongData::paused),
            Codec.LONG.optionalFieldOf("paused_pos_ms", 0L).forGetter(SongData::pausedPosMs),
            Codec.INT.optionalFieldOf("volume", 100).forGetter(SongData::volume),
            QueueItem.CODEC.listOf().optionalFieldOf("queue", List.of()).forGetter(SongData::queue),
            Codec.BOOL.optionalFieldOf("autoplay", true).forGetter(SongData::autoplay),
            Codec.STRING.listOf().optionalFieldOf("history", List.of()).forGetter(SongData::history)
    ).apply(i, SongData::new));

    private static final StreamCodec<ByteBuf, List<String>> HISTORY_CODEC =
            ByteBufCodecs.stringUtf8(4096).apply(ByteBufCodecs.list(MAX_HISTORY));

    public static final StreamCodec<ByteBuf, SongData> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                ByteBufCodecs.stringUtf8(4096).encode(buf, s.url);
                ByteBufCodecs.stringUtf8(512).encode(buf, s.title);
                ByteBufCodecs.VAR_LONG.encode(buf, s.startMs);
                ByteBufCodecs.VAR_LONG.encode(buf, s.durationSec);
                ByteBufCodecs.BOOL.encode(buf, s.paused);
                ByteBufCodecs.VAR_LONG.encode(buf, s.pausedPosMs);
                ByteBufCodecs.VAR_INT.encode(buf, s.volume);
                QueueItem.LIST_STREAM_CODEC.encode(buf, s.queue);
                ByteBufCodecs.BOOL.encode(buf, s.autoplay);
                HISTORY_CODEC.encode(buf, s.history);
            },
            buf -> new SongData(
                    ByteBufCodecs.stringUtf8(4096).decode(buf),
                    ByteBufCodecs.stringUtf8(512).decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    QueueItem.LIST_STREAM_CODEC.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    HISTORY_CODEC.decode(buf)));

    public SongData {
        queue = List.copyOf(queue.size() > MAX_QUEUE ? queue.subList(0, MAX_QUEUE) : queue);
        history = List.copyOf(history.size() > MAX_HISTORY ? history.subList(history.size() - MAX_HISTORY, history.size()) : history);
        volume = Math.max(0, Math.min(100, volume));
    }

    public boolean hasSong() {
        return !url.isEmpty();
    }

    /** Posição atual da música em ms (negativa = ainda vai começar). */
    public long positionMs(long serverNow) {
        return paused ? pausedPosMs : serverNow - startMs;
    }

    public boolean isOver(long serverNow) {
        return hasSong() && !paused && durationSec > 0 && positionMs(serverNow) > durationSec * 1000L + 3000L;
    }

    /** Começa uma música; startMs pode estar no futuro (tempo para todos carregarem). */
    public SongData withSong(String url, String title, long durationSec, long startMs) {
        List<String> h = new ArrayList<>(history);
        h.remove(url);
        h.add(url);
        return new SongData(url, title, startMs, Math.max(0, durationSec), false, 0, volume, queue, autoplay, h);
    }

    public SongData stopped() {
        return new SongData("", "", 0, 0, false, 0, volume, queue, autoplay, history);
    }

    public SongData pausedAt(long now) {
        if (!hasSong() || paused) return this;
        return new SongData(url, title, startMs, durationSec, true, Math.max(0, now - startMs), volume, queue, autoplay, history);
    }

    public SongData resumedAt(long now) {
        if (!hasSong() || !paused) return this;
        return new SongData(url, title, now - pausedPosMs, durationSec, false, 0, volume, queue, autoplay, history);
    }

    public SongData withVolume(int v) {
        return new SongData(url, title, startMs, durationSec, paused, pausedPosMs, v, queue, autoplay, history);
    }

    public SongData withQueue(List<QueueItem> q) {
        return new SongData(url, title, startMs, durationSec, paused, pausedPosMs, volume, q, autoplay, history);
    }

    public SongData withAutoplay(boolean on) {
        return new SongData(url, title, startMs, durationSec, paused, pausedPosMs, volume, queue, on, history);
    }
}
