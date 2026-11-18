package com.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sem dados: existe para o telão ser desenhado (com o vídeo) pelo cliente. */
public class RaveScreenBlockEntity extends BlockEntity {
    public RaveScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RAVE_SCREEN_BE, pos, state);
    }
}
