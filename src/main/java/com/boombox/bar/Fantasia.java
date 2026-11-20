package com.boombox.bar;

import net.minecraft.world.level.Level;

import java.time.LocalDate;
import java.time.Month;

/**
 * Baile à fantasia (3.4): uma noite a cada 5 dias do jogo (e no Halloween de verdade, 31 de outubro) a balada do
 * DJ Leo vira baile à fantasia: os aldeões chegam fantasiados e até o DJ Leo desce do palco para dançar.
 * Funciona igual no servidor e no cliente (as fantasias são desenhadas no cliente).
 */
public final class Fantasia {
    public static final String[] NAMES = {"pirata", "bruxa", "máscara de baile", "cowboy", "coelhinho", "diabinho", "rei", "mágico"};
    public static final int COUNT = NAMES.length;

    private Fantasia() {}

    public static boolean tonight(Level level) {
        long clock = level.getOverworldClockTime();
        long tod = clock % 24000;
        boolean night = tod >= 12500 && tod < 23500;
        if (!night) return false;
        LocalDate today = LocalDate.now();
        boolean halloween = today.getMonth() == Month.OCTOBER && today.getDayOfMonth() == 31;
        return halloween || (clock / 24000) % 5 == 2;
    }

    /** Fantasia de cada um (sempre a mesma para o mesmo aldeão). */
    public static int costumeOf(java.util.UUID id) {
        return Math.floorMod(id.hashCode() * 31 + 7, COUNT);
    }
}
