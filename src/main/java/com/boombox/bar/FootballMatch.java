package com.boombox.bar;

import java.util.Random;

/**
 * O jogo de futebol que passa na TV do bar. É calculado a partir do tempo do mundo, então todo mundo (e o
 * servidor, que faz os fregueses comemorarem) vê o mesmo jogo: cada partida dura 5 minutos e tem de 0 a 5 gols.
 */
public final class FootballMatch {
    public static final int LENGTH = 20 * 60 * 5;
    public static final String HOME = "ZÉ FC", AWAY = "VILA";

    private FootballMatch() {}

    /** Momentos dos gols (ticks desde o começo da partida) e de quem foi cada um (true = time da casa). */
    private record Goals(int[] ticks, boolean[] home) {}

    private static Goals goals(long match) {
        Random r = new Random(match * 7919L + 13);
        int n = r.nextInt(6);
        int[] t = new int[n];
        boolean[] h = new boolean[n];
        for (int i = 0; i < n; i++) {
            t[i] = 400 + r.nextInt(LENGTH - 800);
            h[i] = r.nextFloat() < 0.6f;
        }
        java.util.Arrays.sort(t);
        return new Goals(t, h);
    }

    /** Placar agora: [gols da casa, gols de fora]. */
    public static int[] score(long gameTime) {
        long match = gameTime / LENGTH;
        int at = (int) (gameTime % LENGTH);
        Goals g = goals(match);
        int a = 0, b = 0;
        for (int i = 0; i < g.ticks.length; i++) {
            if (g.ticks[i] <= at) {
                if (g.home[i]) a++;
                else b++;
            }
        }
        return new int[]{a, b};
    }

    /** Ticks desde o último gol (grande se ainda não saiu nenhum nesta partida). */
    public static int sinceGoal(long gameTime) {
        int at = (int) (gameTime % LENGTH);
        int best = Integer.MAX_VALUE;
        for (int t : goals(gameTime / LENGTH).ticks) if (t <= at) best = at - t;
        return best;
    }

    /** Minuto do jogo (0 a 90) para o placar. */
    public static int minute(long gameTime) {
        return (int) ((gameTime % LENGTH) * 90 / LENGTH);
    }

    /** Posição da bola no campo (0..1, 0..1): vai e vem, e entra no gol quando sai um. */
    public static float[] ball(long gameTime, float partial) {
        double t = (gameTime % LENGTH + partial) / 20.0;
        int since = sinceGoal(gameTime);
        double x = 0.5 + 0.38 * Math.sin(t * 0.7) * Math.cos(t * 0.23);
        double y = 0.5 + 0.35 * Math.sin(t * 1.1 + 1.3);
        if (since < 60) { // replay do gol: bola no fundo da rede
            boolean homeGoal = score(gameTime)[0] > score(gameTime - since - 1)[0];
            x = homeGoal ? 0.97 : 0.03;
            y = 0.5;
        }
        return new float[]{(float) x, (float) y};
    }
}
