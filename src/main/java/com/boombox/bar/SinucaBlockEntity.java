package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SinucaBlockEntity extends BlockEntity {
    public SinucaBlockEntity(BlockPos pos, BlockState state) {
        super(BarBlocks.SINUCA_BE, pos, state);
    }
}
