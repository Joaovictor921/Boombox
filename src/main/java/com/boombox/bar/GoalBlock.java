package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gol de várzea: 3 blocos de largura e 2 de altura (6 partes), com traves brancas na frente e rede com fundo.
 * A frente (aberta) olha para FACING; a bola que entra no gol encosta na rede do fundo.
 * PART: coluna (0 esquerda, 1 meio, 2 direita, olhando de frente para o gol) + 3 * linha (0 embaixo, 1 em cima).
 */
public class GoalBlock extends FacingBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);

    public GoalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /** Direção da coluna 0 → 2 (para a direita de quem olha o gol de frente). */
    public static Direction across(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos partPos(BlockPos mid, Direction facing, int col, int row) {
        return mid.relative(across(facing), col - 1).above(row);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx).setValue(PART, 1);
        Direction f = s.getValue(FACING);
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 2; r++) {
                if (!ctx.getLevel().getBlockState(partPos(ctx.getClickedPos(), f, c, r)).canBeReplaced(ctx)) return null;
            }
        }
        return s;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        Direction f = state.getValue(FACING);
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 2; r++) {
                if (c == 1 && r == 0) continue;
                level.setBlock(partPos(pos, f, c, r), state.setValue(PART, c + 3 * r), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        int part = state.getValue(PART);
        Direction f = state.getValue(FACING);
        BlockPos mid = pos.relative(across(f), 1 - part % 3).below(part / 3);
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 2; r++) {
                BlockPos p = partPos(mid, f, c, r);
                if (!p.equals(pos) && level.getBlockState(p).is(this)) level.destroyBlock(p, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Caixa no espaço "gol virado para o norte" (frente em z=0, fundo em z=16) girada para FACING. */
    private static VoxelShape box(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (f) {
            case NORTH -> Block.box(x0, y0, z0, x1, y1, z1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Block.box(0, 0, 0, 16, 16, 16);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction f = state.getValue(FACING);
        int part = state.getValue(PART), col = part % 3, row = part / 3;
        // "esquerda" no modelo virado para o norte é x=16 (quem olha de frente vê o oeste à direita)
        VoxelShape s = box(f, 0, 0, 14.5, 16, 16, 16); // rede do fundo
        if (col == 0) s = Shapes.or(s, box(f, 14, 0, 0, 16, 16, 16));  // trave + rede do lado
        if (col == 2) s = Shapes.or(s, box(f, 0, 0, 0, 2, 16, 16));
        if (row == 1) s = Shapes.or(s, box(f, 0, 14, 0, 16, 16, 16));  // travessão + rede de cima
        return s;
    }
}
