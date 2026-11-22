package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 3.6: quadro do chaveamento do torneio de sinuca. Fica pendurado na parede (3 blocos de largura por 2 de altura,
 * desenhado em volta desse bloco) e mostra a chave da mesa de sinuca mais perto: quartas, semifinais, final e campeão.
 */
public class BracketBoardBlock extends FacingBlock implements EntityBlock {
    public BracketBoardBlock(Properties properties) {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** Só guarda (no cliente) onde está a mesa de sinuca mais perto. */
    public static class Entity extends BlockEntity {
        public BlockPos table;
        public long lastScan = -1000;

        public Entity(BlockPos pos, BlockState state) {
            super(Blocks34.BRACKET_BOARD_BE, pos, state);
        }

        /** Mesa de sinuca mais perto (procura de novo a cada 2 segundos). */
        public SinucaBlockEntity findTable(Level level) {
            long now = level.getGameTime();
            if (table != null && level.getBlockEntity(table) instanceof SinucaBlockEntity t) return t;
            if (now - lastScan < 40) return null;
            lastScan = now;
            table = null;
            double best = 1e9;
            for (BlockPos p : BlockPos.betweenClosed(getBlockPos().offset(-10, -3, -10), getBlockPos().offset(10, 3, 10))) {
                if (level.getBlockEntity(p) instanceof SinucaBlockEntity && p.distSqr(getBlockPos()) < best) {
                    best = p.distSqr(getBlockPos());
                    table = p.immutable();
                }
            }
            return table == null ? null : (SinucaBlockEntity) level.getBlockEntity(table);
        }
    }
}
