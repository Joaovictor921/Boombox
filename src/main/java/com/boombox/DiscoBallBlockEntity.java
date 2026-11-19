package com.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DiscoBallBlockEntity extends BlockEntity {
    public DiscoBallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISCO_BALL_BE, pos, state);
    }
}
