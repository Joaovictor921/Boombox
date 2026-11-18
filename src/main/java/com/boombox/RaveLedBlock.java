package com.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * LED de rave: um canhão de luz pequeno (parede, teto ou chão) que solta feixes de laser coloridos
 * com fumaça, varrendo o ambiente. Perto de uma caixa tocando, acompanha a música e o modo de luz dela.
 */
public class RaveLedBlock extends Block implements EntityBlock {
    /** Para onde as luzes apontam (o painel fica grudado no lado oposto). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // canhão de luz pequeno grudado na face de apoio
        SHAPES.put(Direction.NORTH, Block.box(4.5, 4.5, 11.5, 11.5, 11.5, 16));
        SHAPES.put(Direction.SOUTH, Block.box(4.5, 4.5, 0, 11.5, 11.5, 4.5));
        SHAPES.put(Direction.WEST, Block.box(11.5, 4.5, 4.5, 16, 11.5, 11.5));
        SHAPES.put(Direction.EAST, Block.box(0, 4.5, 4.5, 4.5, 11.5, 11.5));
        SHAPES.put(Direction.UP, Block.box(4.5, 0, 4.5, 11.5, 4.5, 11.5));
        SHAPES.put(Direction.DOWN, Block.box(4.5, 11.5, 4.5, 11.5, 16, 11.5));
    }

    public RaveLedBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RaveLedBlockEntity(pos, state);
    }
}
