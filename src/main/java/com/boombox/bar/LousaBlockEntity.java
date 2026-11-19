package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LousaBlockEntity extends BlockEntity {
    public LousaBlockEntity(BlockPos pos, BlockState state) {
        super(BarBlocks.LOUSA_BE, pos, state);
    }
}
