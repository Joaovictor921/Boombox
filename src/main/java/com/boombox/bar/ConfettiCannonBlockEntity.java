package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ConfettiCannonBlockEntity extends BlockEntity {
    /** Grave do último tick e quantas rajadas já soltou (usado no cliente). */
    public float lastBass;
    public int bursts;
    public long lastBurst;

    public ConfettiCannonBlockEntity(BlockPos pos, BlockState state) {
        super(BarBlocks.CONFETTI_BE, pos, state);
    }
}
