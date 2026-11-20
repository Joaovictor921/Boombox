package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * Neon personalizado: placa de acrílico presa na parede com um letreiro que acende. Clique para escrever o texto e
 * escolher a cor (ou arco-íris, ou piscando). O letreiro pode passar das bordas do bloco (até uns 3 blocos de largura).
 */
public class NeonSignBlock extends FacingBlock implements EntityBlock {
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 2, 14, 16, 14, 16), Block.box(0, 2, 0, 2, 14, 16), Block.box(0, 2, 0, 16, 14, 2), Block.box(14, 2, 0, 16, 14, 16)};

    /** Preenchido pelo cliente: abre a tela do neon (posição do bloco). */
    public static java.util.function.Consumer<BlockPos> openHook;

    public NeonSignBlock(Properties properties) {
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

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide() && by instanceof Player && openHook != null) openHook.accept(pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() && openHook != null) openHook.accept(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NeonSignBlockEntity(pos, state);
    }
}
