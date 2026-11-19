package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Churrasqueira: ponha carne de vaca (vira espetinho), costeleta de porco (vira linguiça) ou pão (vira pão de alho),
 * até 4 de uma vez. Em ~20 segundos fica pronto; clique com a mão vazia para pegar. Enquanto assa sai fumaça e os
 * aldeões perto vêm atrás do cheiro. No domingo (relógio do computador) a churrasqueira do bar assa sozinha.
 */
public class GrillBlock extends FacingBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 3, 15, 13, 13);

    public GrillBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GrillBlockEntity be) || GrillBlockEntity.cooked(stack) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && be.put(stack.copyWithCount(1)) && !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GrillBlockEntity be)) return InteractionResult.PASS;
        if (level instanceof ServerLevel sl) {
            ItemStack food = be.takeReady();
            if (!food.isEmpty() && !player.getInventory().add(food)) player.spawnAtLocation(sl, food);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GrillBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BarBlocks.GRILL_BE) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<GrillBlockEntity>) GrillBlockEntity::serverTick;
    }
}
