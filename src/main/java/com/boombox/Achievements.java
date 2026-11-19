package com.boombox;

import net.minecraft.server.level.ServerPlayer;

/** Conquistas do mod (aba "Boombox" nas conquistas). São dadas pelo código, não por critérios do jogo. */
public final class Achievements {
    private Achievements() {}

    public static void award(ServerPlayer player, String name) {
        var server = player.level().getServer();
        if (server == null) return;
        var adv = server.getAdvancements().get(BoomboxMod.id(name));
        if (adv != null) player.getAdvancements().award(adv, "feito");
    }
}
