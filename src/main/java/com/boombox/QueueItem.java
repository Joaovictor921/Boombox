package com.boombox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** Uma música na fila da boombox. */
public record QueueItem(String url, String title, long durationSec) {
    public static final Codec<QueueItem> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("url").forGetter(QueueItem::url),
            Codec.STRING.optionalFieldOf("title", "").forGetter(QueueItem::title),
            Codec.LONG.optionalFieldOf("duration_sec", 0L).forGetter(QueueItem::durationSec)
    ).apply(i, QueueItem::new));

    public static final StreamCodec<ByteBuf, QueueItem> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(4096), QueueItem::url,
            ByteBufCodecs.stringUtf8(512), QueueItem::title,
            ByteBufCodecs.VAR_LONG, QueueItem::durationSec,
            QueueItem::new);

    public static final StreamCodec<ByteBuf, List<QueueItem>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list(SongData.MAX_QUEUE));
}
