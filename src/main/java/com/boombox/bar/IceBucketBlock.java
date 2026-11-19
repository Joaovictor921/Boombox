package com.boombox.bar;

import com.boombox.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Balde de gelo do camarote: quem tem a pulseira VIP pega uma gelada de graça (uma a cada 30 segundos). */
public class IceBucketBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 9, 12);
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public IceBucketBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        boolean vip = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) vip |= player.getInventory().getItem(i).is(BarBlocks.VIP);
        if (!vip) {
            player.sendOverlayMessage(Component.literal("§cEssa gelada é só para quem tem a pulseira VIP"));
            return InteractionResult.SUCCESS;
        }
        long now = sl.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < 600) {
            player.sendOverlayMessage(Component.literal("Calma, VIP! Espera um pouquinho para pegar outra."));
            return InteractionResult.SUCCESS;
        }
        LAST.put(player.getUUID(), now);
        ItemStack beer = new ItemStack(ModBlocks.BEER);
        if (!player.getInventory().add(beer)) player.spawnAtLocation(sl, beer);
        sl.playSound(null, pos, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 1f, 1.4f);
        player.sendOverlayMessage(Component.literal("§bGelada do camarote, cortesia do DJ Leo!"));
        return InteractionResult.SUCCESS;
    }
}
