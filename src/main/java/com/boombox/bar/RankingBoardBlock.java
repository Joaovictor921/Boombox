package com.boombox.bar;

import com.boombox.WorldStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 3.8: quadro do ranking do bar: artilheiros (gols em jogos marcados), quem mais ganhou copas (futebol e vôlei) e
 * campeões do torneio de sinuca. Fica na parede (3 x 2 blocos, desenhado em volta desse bloco) e se atualiza sozinho;
 * clicando abre a tela com o top 10 de cada um.
 */
public class RankingBoardBlock extends FacingBlock implements EntityBlock {
    /** Clique: abre a tela do ranking (preenchido pelo servidor). */
    public static java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> openHook;

    public RankingBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(0, 0, 0, 16, 16, 1);
            case WEST -> Block.box(15, 0, 0, 16, 16, 16);
            case EAST -> Block.box(0, 0, 0, 1, 16, 16);
            default -> Block.box(0, 0, 15, 16, 16, 16);
        };
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                  net.minecraft.world.phys.BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && openHook != null) openHook.accept(sp);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != Blocks34.RANKING_BOARD_BE) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) (l, p, s, be) -> be.serverTick((ServerLevel) l);
    }

    /** Guarda o texto do ranking (top 5 de cada categoria), atualizado pelo servidor a cada 5 segundos. */
    public static class Entity extends BlockEntity {
        public String data = "";

        public Entity(BlockPos pos, BlockState state) {
            super(Blocks34.RANKING_BOARD_BE, pos, state);
        }

        void serverTick(ServerLevel sl) {
            if (sl.getGameTime() % 100 != (worldPosition.asLong() & 63)) return;
            String now = WorldStats.get(sl.getServer()).rankingData(5);
            if (now.equals(data)) return;
            data = now;
            setChanged();
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }

        @Override
        protected void loadAdditional(ValueInput in) {
            super.loadAdditional(in);
            data = in.getStringOr("Data", "");
        }

        @Override
        protected void saveAdditional(ValueOutput out) {
            super.saveAdditional(out);
            out.putString("Data", data);
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return saveCustomOnly(registries);
        }
    }

    /**
     * Pendura o quadro numa parede perto desse ponto (se ainda não tem um num raio de 12 blocos): 3x3 blocos livres na
     * frente de uma parede, na altura dos olhos. Usado pelo Seu Zé no bar.
     */
    public static boolean hangNear(ServerLevel sl, BlockPos center) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-12, -3, -12), center.offset(12, 4, 12))) {
            if (sl.getBlockState(p).is(Blocks34.RANKING_BOARD)) return false;
        }
        BlockPos best = null;
        Direction bestD = null;
        double bd = 1e9;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, 1, -8), center.offset(8, 2, 8))) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!fits(sl, p, d)) continue;
                double dist = p.distSqr(center);
                if (dist < bd) { bd = dist; best = p.immutable(); bestD = d; }
            }
        }
        if (best == null) return false;
        sl.setBlock(best, Blocks34.RANKING_BOARD.defaultBlockState().setValue(FACING, bestD), Block.UPDATE_ALL);
        return true;
    }

    private static boolean fits(ServerLevel sl, BlockPos p, Direction d) {
        Direction across = d.getClockWise();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos q = p.relative(across, i).above(j);
                if (!sl.getBlockState(q).isAir()) return false;
                BlockPos wall = q.relative(d.getOpposite());
                if (!sl.getBlockState(wall).isFaceSturdy(sl, wall, d)) return false;
            }
        }
        // não cobre o quadro do chaveamento (que é desenhado 3 blocos para os lados)
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-3, -2, -3), p.offset(3, 2, 3))) {
            if (sl.getBlockState(q).is(Blocks34.BRACKET_BOARD)) return false;
        }
        return true;
    }
}
