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

    /** Marca um critério de uma conquista com vários (ex.: colecionador de copos). Devolve true se era novo. */
    public static boolean awardCriterion(ServerPlayer player, String name, String criterion) {
        var server = player.level().getServer();
        if (server == null) return false;
        var adv = server.getAdvancements().get(BoomboxMod.id(name));
        if (adv == null) return false;
        var progress = player.getAdvancements().getOrStartProgress(adv);
        var c = progress.getCriterion(criterion);
        if (c == null || c.isDone()) return false;
        player.getAdvancements().award(adv, criterion);
        return true;
    }

    /** Contadores (estatísticas do jogo, salvas no jogador). */
    public static final net.minecraft.resources.Identifier ESPETINHOS = BoomboxMod.id("espetinhos_assados");

    public static void registerStats() {
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.CUSTOM_STAT, ESPETINHOS, ESPETINHOS);
        net.minecraft.stats.Stats.CUSTOM.get(ESPETINHOS, net.minecraft.stats.StatFormatter.DEFAULT);
    }

    public static int stat(ServerPlayer p, net.minecraft.resources.Identifier id) {
        return p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(id));
    }
}
