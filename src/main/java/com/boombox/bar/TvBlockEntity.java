package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TvBlockEntity extends BlockEntity {
    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(BarBlocks.TV_BE, pos, state);
    }
}
