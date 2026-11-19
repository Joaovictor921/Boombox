package com.boombox;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Cerveja do Seu Zé: bebe como poção, deixa a tela tonta (náusea) por uns segundos e sobra a garrafa. */
public class BeerItem extends Item {
    public BeerItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer p) Achievements.award(p, "bebeu_no_bar");
        return super.finishUsingItem(stack, level, entity);
    }
}
