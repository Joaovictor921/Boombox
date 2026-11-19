package com.boombox.bar;

import com.boombox.entity.Seat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bloco de sentar (sofá do camarote, cadeira de praia): clique para sentar. */
public class SittableBlock extends FacingBlock implements SeatMarker {
    private final VoxelShape shape;
    private final double seatHeight;

    public SittableBlock(Properties properties, VoxelShape shape, double seatHeight) {
        super(properties);
        this.shape = shape;
        this.seatHeight = seatHeight;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && !player.isPassenger()) Seat.sit(level, pos, player, seatHeight, state.getValue(FACING));
        return InteractionResult.SUCCESS;
    }
}
