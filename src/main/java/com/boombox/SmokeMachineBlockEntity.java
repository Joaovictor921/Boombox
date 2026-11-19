package com.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SmokeMachineBlockEntity extends BlockEntity {
    public SmokeMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SMOKE_MACHINE_BE, pos, state);
    }
}
