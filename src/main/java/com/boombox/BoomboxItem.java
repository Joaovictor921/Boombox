package com.boombox;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * Item da boombox e das caixas de festa. Clicar num bloco coloca a boombox no chão; clicar no ar abre o menu
 * e ela toca na sua mão (qualquer uma das mãos).
 */
public class BoomboxItem extends BlockItem {
    /** Preenchido pelo cliente: abre o menu para a boombox na mão. */
    public static Consumer<InteractionHand> openHeldScreenHook;

    public BoomboxItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() && openHeldScreenHook != null) {
            openHeldScreenHook.accept(hand);
        }
        return InteractionResult.SUCCESS;
    }

    /** true para qualquer caixa do mod (boombox ou caixa de festa). */
    public static boolean isBoombox(net.minecraft.world.item.ItemStack stack) {
        return stack != null && stack.getItem() instanceof BoomboxItem;
    }

    /** Tipo da caixa no item, ou null se não for uma caixa. */
    public static SpeakerType type(net.minecraft.world.item.ItemStack stack) {
        if (stack != null && stack.getItem() instanceof BoomboxItem item && item.getBlock() instanceof BoomboxBlock b) {
            return b.type();
        }
        return null;
    }
}
