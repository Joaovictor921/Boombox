package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Corda VIP: pedestal dourado com corda de veludo vermelho, que se liga às cordas vizinhas. Quem está na lista VIP
 * (pulseira com o próprio nome) passa direto, como se a corda abrisse; o resto esbarra.
 */
public class VelvetRopeBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST,
            SOUTH = BlockStateProperties.SOUTH, WEST = BlockStateProperties.WEST;
    private static final VoxelShape POST = Block.box(6, 0, 6, 10, 15, 10);
    private static final VoxelShape WALL_POST = Block.box(5, 0, 5, 11, 24, 11);

    public VelvetRopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    private static BooleanProperty prop(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    private BlockState connect(BlockState s, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) s = s.setValue(prop(d), level.getBlockState(pos.relative(d)).is(this));
        return s;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connect(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (dir.getAxis().isHorizontal()) return state.setValue(prop(dir), neighbor.is(this));
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape s = POST;
        if (state.getValue(NORTH)) s = Shapes.or(s, Block.box(7, 9, 0, 9, 12, 8));
        if (state.getValue(SOUTH)) s = Shapes.or(s, Block.box(7, 9, 8, 9, 12, 16));
        if (state.getValue(WEST)) s = Shapes.or(s, Block.box(0, 9, 7, 8, 12, 9));
        if (state.getValue(EAST)) s = Shapes.or(s, Block.box(8, 9, 7, 16, 12, 9));
        return s;
    }

    /** Barreira alta (não dá para pular) para quem não está na lista; nada para quem está. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (ctx instanceof EntityCollisionContext ec && ec.getEntity() instanceof Player p && Vip.onList(p)) return Shapes.empty();
        VoxelShape s = WALL_POST;
        if (state.getValue(NORTH)) s = Shapes.or(s, Block.box(5, 0, 0, 11, 24, 8));
        if (state.getValue(SOUTH)) s = Shapes.or(s, Block.box(5, 0, 8, 11, 24, 16));
        if (state.getValue(WEST)) s = Shapes.or(s, Block.box(0, 0, 5, 8, 24, 11));
        if (state.getValue(EAST)) s = Shapes.or(s, Block.box(8, 0, 5, 16, 24, 11));
        return s;
    }
}
