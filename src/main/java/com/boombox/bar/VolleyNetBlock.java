package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rede de vôlei de praia: um pedaço de 1 bloco (coloque vários em fila, com cercas de 2 de altura nas pontas).
 * A faixa de cima fica a 1,75 bloco do chão; a rede de verdade vai de 0,8 até lá.
 */
public class VolleyNetBlock extends FacingBlock {
    /** Altura do topo da rede (em blocos). */
    public static final double TOP = 1.75;
    private static final VoxelShape X = Block.box(0, 0, 7, 16, 28, 9), Z = Block.box(7, 0, 0, 9, 28, 16);
    private static final VoxelShape X_OUT = Block.box(0, 12, 7, 16, 28, 9), Z_OUT = Block.box(7, 12, 0, 9, 28, 16);

    public VolleyNetBlock(Properties properties) {
        super(properties);
    }

    /** A rede corre ao longo do eixo X quando está virada para norte/sul. */
    public static boolean alongX(BlockState s) {
        Direction f = s.getValue(FACING);
        return f == Direction.NORTH || f == Direction.SOUTH;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return alongX(state) ? X_OUT : Z_OUT;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return alongX(state) ? X : Z;
    }
}
