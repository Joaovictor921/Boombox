package com.boombox.bar;

import com.boombox.entity.Seat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Cadeira de plástico de boteco: clique para sentar. */
public class PlasticChairBlock extends FacingBlock implements SeatMarker {
    private static final VoxelShape SEAT = Block.box(2, 0, 2, 14, 8, 14);

    public PlasticChairBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape back = switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(2, 8, 2, 14, 18, 4);
            case WEST -> Block.box(12, 8, 2, 14, 18, 14);
            case EAST -> Block.box(2, 8, 2, 4, 18, 14);
            default -> Block.box(2, 8, 12, 14, 18, 14);
        };
        return Shapes.or(SEAT, back);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && !player.isPassenger()) Seat.sit(level, pos, player, 0.45, state.getValue(FACING));
        return InteractionResult.SUCCESS;
    }
}
