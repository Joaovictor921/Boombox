package com.boombox;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;

/** Qual boombox o menu está controlando: um bloco no mundo ou a que está na mão do jogador. */
public record Target(BlockPos pos, InteractionHand hand) {

    public static final StreamCodec<ByteBuf, Target> STREAM_CODEC = StreamCodec.of(
            (buf, t) -> {
                ByteBufCodecs.BOOL.encode(buf, t.isHeld());
                if (t.isHeld()) InteractionHand.STREAM_CODEC.encode(buf, t.hand);
                else BlockPos.STREAM_CODEC.encode(buf, t.pos);
            },
            buf -> ByteBufCodecs.BOOL.decode(buf)
                    ? held(InteractionHand.STREAM_CODEC.decode(buf))
                    : block(BlockPos.STREAM_CODEC.decode(buf)));

    public static Target block(BlockPos pos) {
        return new Target(pos.immutable(), null);
    }

    public static Target held(InteractionHand hand) {
        return new Target(null, hand);
    }

    public boolean isHeld() {
        return hand != null;
    }
}
