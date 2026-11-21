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
 * Gol grande (3.5.3): 5 blocos de largura e 3 de altura (15 partes), traves brancas e rede com fundo.
 * PART: coluna (0 esquerda ... 4 direita, olhando de frente) + 5 * linha (0 embaixo ... 2 em cima).
 */
public class GoalLargeBlock extends FacingBlock {
    public static final int W = 5, H = 3;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, W * H - 1);

    public GoalLargeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, W / 2));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    public static BlockPos partPos(BlockPos mid, Direction facing, int col, int row) {
        return mid.relative(GoalBlock.across(facing), col - W / 2).above(row);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx).setValue(PART, W / 2);
        Direction f = s.getValue(FACING);
        for (int c = 0; c < W; c++) {
            for (int r = 0; r < H; r++) {
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
        for (int c = 0; c < W; c++) {
            for (int r = 0; r < H; r++) {
                if (c == W / 2 && r == 0) continue;
                level.setBlock(partPos(pos, f, c, r), state.setValue(PART, c + W * r), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        int part = state.getValue(PART);
        Direction f = state.getValue(FACING);
        BlockPos mid = pos.relative(GoalBlock.across(f), W / 2 - part % W).below(part / W);
        for (int c = 0; c < W; c++) {
            for (int r = 0; r < H; r++) {
                BlockPos p = partPos(mid, f, c, r);
                if (!p.equals(pos) && level.getBlockState(p).is(this)) level.destroyBlock(p, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

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
        int part = state.getValue(PART), col = part % W, row = part / W;
        VoxelShape s = box(f, 0, 0, 14.5, 16, 16, 16);
        if (col == 0) s = Shapes.or(s, box(f, 14, 0, 0, 16, 16, 16));
        if (col == W - 1) s = Shapes.or(s, box(f, 0, 0, 0, 2, 16, 16));
        if (row == H - 1) s = Shapes.or(s, box(f, 0, 14, 0, 16, 16, 16));
        return s;
    }
}
