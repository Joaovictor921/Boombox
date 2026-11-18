package com.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sem dados: existe só para as luzes serem desenhadas (animadas) pelo cliente. */
public class RaveLedBlockEntity extends BlockEntity {
    public RaveLedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RAVE_LED_BE, pos, state);
    }
}
