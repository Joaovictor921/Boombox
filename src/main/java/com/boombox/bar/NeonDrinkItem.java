package com.boombox.bar;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Drink Neon: depois de beber, a tela fica colorida (piscando com a música) por uns segundos. */
public class NeonDrinkItem extends Item {
    public static final int SECONDS = 20;

    public NeonDrinkItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer p) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.boombox.net.Payloads.NeonVision(SECONDS * 20));
            com.boombox.Achievements.award(p, "bebeu_no_bar");
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
