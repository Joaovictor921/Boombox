package com.boombox.bar;

import com.boombox.Achievements;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Copos de lembrança: cada evento dá o seu (uma vez por jogador). Junte todos para a conquista "Colecionador". */
public final class Cups {
    public static final int CARNAVAL = 0, ARRAIA = 1, REVEILLON = 2, SHOW = 3, ANIVERSARIO = 4, QUERMESSE = 5, FEIRA = 6, VARZEA = 7, CHURRASCO = 8;
    public static final String[] IDS = {"carnaval", "arraia", "reveillon", "show", "aniversario", "quermesse", "feira", "varzea", "churrasco"};
    public static final String[] NAMES = {"Carnaval", "Arraiá", "Réveillon", "Show do DJ Leo", "Aniversário", "Quermesse", "Feira de Domingo",
            "Várzea", "Churrasco de Domingo"};

    private Cups() {}

    public static Item item(int i) {
        return BarBlocks.CUPS[i];
    }

    /** Dá o copo do evento se o jogador ainda não ganhou esse. */
    public static void give(ServerPlayer p, int i) {
        if (!Achievements.awardCriterion(p, "colecionador", IDS[i])) return;
        ItemStack cup = new ItemStack(item(i));
        if (!p.getInventory().add(cup)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), cup);
        p.sendSystemMessage(Component.literal("§b🥤 Você ganhou o copo de lembrança: §f" + NAMES[i] + "§b! Ponha na prateleira de copos."));
        p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 0.8f);
    }
}
