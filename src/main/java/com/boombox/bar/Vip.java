package com.boombox.bar;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Lista VIP (3.4): a pulseira VIP vem com o nome de quem comprou. Só passa da corda VIP quem tem a pulseira com o
 * próprio nome (a do amigo não vale). Pulseiras antigas, sem nome, ganham o nome de quem estiver com ela.
 */
public final class Vip {
    private Vip() {}

    public static String owner(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? "" : d.copyTag().getStringOr("VipName", "");
    }

    /** Escreve o nome na pulseira (e no nome do item). */
    public static void bind(ItemStack s, String name) {
        CompoundTag t = new CompoundTag();
        t.putString("VipName", name);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        s.set(DataComponents.CUSTOM_NAME, Component.literal("§6Pulseira VIP §f— " + name));
    }

    /** Pulseira VIP já com o nome dessa pessoa. */
    public static ItemStack braceletFor(String name) {
        ItemStack s = new ItemStack(BarBlocks.VIP);
        bind(s, name);
        return s;
    }

    /** Está na lista (pulseira com o próprio nome, ou sem nome)? */
    public static boolean onList(Player p) {
        if (p.isCreative() || p.isSpectator()) return true;
        String me = p.getName().getString();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.is(BarBlocks.VIP)) continue;
            String o = owner(s);
            if (o.isEmpty() || o.equals(me)) return true;
        }
        return false;
    }

    /** No servidor: pulseiras sem nome ganham o nome de quem está com elas. */
    public static void bindUnnamed(Player p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(BarBlocks.VIP) && owner(s).isEmpty()) bind(s, p.getName().getString());
        }
    }

    /** Tem pulseira, mas de outra pessoa? */
    public static boolean hasSomeoneElses(Player p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(BarBlocks.VIP) && !owner(s).isEmpty() && !owner(s).equals(p.getName().getString())) return true;
        }
        return false;
    }
}
