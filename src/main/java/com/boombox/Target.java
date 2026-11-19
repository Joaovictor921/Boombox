package com.boombox;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;

/**
 * Qual caixa o menu está controlando: um bloco no mundo, a que está na mão do jogador ou um paredão
 * andando num barco/carrinho (entidade).
 */
public record Target(BlockPos pos, InteractionHand hand, int entity) {

    public static final StreamCodec<ByteBuf, Target> STREAM_CODEC = StreamCodec.of(
            (buf, t) -> {
                int kind = t.isHeld() ? 1 : t.isEntity() ? 2 : 0;
                ByteBufCodecs.VAR_INT.encode(buf, kind);
                switch (kind) {
                    case 1 -> InteractionHand.STREAM_CODEC.encode(buf, t.hand);
                    case 2 -> ByteBufCodecs.VAR_INT.encode(buf, t.entity);
                    default -> BlockPos.STREAM_CODEC.encode(buf, t.pos);
                }
            },
            buf -> switch (ByteBufCodecs.VAR_INT.decode(buf)) {
                case 1 -> held(InteractionHand.STREAM_CODEC.decode(buf));
                case 2 -> entity(ByteBufCodecs.VAR_INT.decode(buf));
                default -> block(BlockPos.STREAM_CODEC.decode(buf));
            });

    public static Target block(BlockPos pos) {
        return new Target(pos.immutable(), null, -1);
    }

    public static Target held(InteractionHand hand) {
        return new Target(null, hand, -1);
    }

    public static Target entity(int id) {
        return new Target(null, null, id);
    }

    public boolean isHeld() {
        return hand != null;
    }

    public boolean isEntity() {
        return entity >= 0;
    }

    public boolean isBlock() {
        return !isHeld() && !isEntity();
    }
}
