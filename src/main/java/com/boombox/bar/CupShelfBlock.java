package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Prateleira de copos: guarda até 5 copos de lembrança à mostra. Clique com um copo para pôr, mão vazia para tirar. */
public class CupShelfBlock extends FacingBlock implements EntityBlock {
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 8, 16, 16, 16), Block.box(0, 0, 0, 8, 16, 16), Block.box(0, 0, 0, 16, 16, 8), Block.box(8, 0, 0, 16, 16, 16)};

    public CupShelfBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPES[0];
            case EAST -> SHAPES[1];
            case SOUTH -> SHAPES[2];
            default -> SHAPES[3];
        };
    }

    public static boolean isCup(ItemStack s) {
        for (var c : BarBlocks.CUPS) if (s.is(c)) return true;
        return false;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isCup(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CupShelfBlockEntity be && be.put(stack.copyWithCount(1))) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CupShelfBlockEntity be) {
            ItemStack s = be.take();
            if (!s.isEmpty() && !player.getInventory().add(s)) net.minecraft.world.Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), s);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CupShelfBlockEntity(pos, state);
    }
}
