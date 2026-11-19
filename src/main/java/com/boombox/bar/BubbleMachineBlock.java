package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Máquina de bolha de sabão: com música perto, solta bolhas que sobem balançando e estouram quando batem em algo. */
public class BubbleMachineBlock extends FacingBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 10, 14);
    /** Preenchido pelo cliente: anda com as bolhas. */
    public static BlockEntityTicker<BubbleMachineBlockEntity> clientTicker;

    public BubbleMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BubbleMachineBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() || type != BarBlocks.BUBBLE_BE || clientTicker == null) return null;
        return (BlockEntityTicker<T>) clientTicker;
    }
}
