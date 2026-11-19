package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Pista de dança: cada bloco acende numa cor, pisca com o grave e brilha forte quando alguém pisa. */
public class DanceFloorBlock extends Block implements EntityBlock {
    public DanceFloorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DanceFloorBlockEntity(pos, state);
    }
}
