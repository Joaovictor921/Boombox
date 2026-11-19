package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DanceFloorBlockEntity extends BlockEntity {
    public DanceFloorBlockEntity(BlockPos pos, BlockState state) {
        super(BarBlocks.DANCE_FLOOR_BE, pos, state);
    }
}
