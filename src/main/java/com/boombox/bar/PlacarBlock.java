package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Placar do campo / da quadra (3.5): painel de 3 blocos de largura que mostra os times, o placar e o tempo do jogo
 * mais perto (futebol ou vôlei). Coloque numa parede ou num poste.
 */
public class PlacarBlock extends FacingBlock implements EntityBlock {
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 13, 16, 16, 16), Block.box(0, 0, 0, 3, 16, 16), Block.box(0, 0, 0, 16, 16, 3), Block.box(13, 0, 0, 16, 16, 16)};

    public PlacarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPES[0];
            case EAST -> SHAPES[1];
            case SOUTH -> SHAPES[2];
            default -> SHAPES[3];
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** Não guarda nada: o placar lê a bola mais perto (no cliente). */
    public static class Entity extends BlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(Blocks34.PLACAR_BE, pos, state);
        }
    }

    public static BlockEntityType<Entity> type() {
        return Blocks34.PLACAR_BE;
    }
}
