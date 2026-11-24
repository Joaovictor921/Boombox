package com.boombox;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.Map;

/**
 * 3.7.3: vigia de travamento. Se o servidor (o mundo) ou a tela do jogo ficarem parados mais de 6 segundos, grava no
 * latest.log a pilha de onde cada um está parado. Serve para achar a causa de congelamentos que não deixam erro.
 * Não muda nada no jogo: só observa e escreve no log.
 */
public final class Watchdog {
    public static final long LIMIT_MS = 6000;
    private static volatile long serverTick, clientTick;
    private static volatile boolean serverRunning, clientPaused, clientWatched;
    private static volatile boolean started;

    private Watchdog() {}

    /** Lado do servidor (vale também no mundo de um jogador só). */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            serverTick = System.currentTimeMillis();
            serverRunning = true;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> serverRunning = false);
        ServerTickEvents.END_SERVER_TICK.register(s -> serverTick = System.currentTimeMillis());
        start();
    }

    /** Chamado a cada tick do cliente (tela do jogo). */
    public static void clientTick(boolean paused) {
        clientTick = System.currentTimeMillis();
        clientPaused = paused;
        clientWatched = true;
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        Thread t = new Thread(Watchdog::run, "Boombox-Vigia");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void run() {
        boolean serverReported = false, clientReported = false;
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.currentTimeMillis();
            // com o jogo pausado (menu) o mundo de um jogador só para de propósito: não conta
            long ss = serverRunning && !clientPaused ? now - serverTick : 0;
            long cs = clientWatched ? now - clientTick : 0;
            if (ss > LIMIT_MS && !serverReported) {
                serverReported = true;
                report("o mundo (Server thread)", ss, "Server thread");
            } else if (ss < 2000 && serverReported) {
                serverReported = false;
                BoomboxMod.LOGGER.warn("[Boombox vigia] o mundo voltou a andar.");
            }
            if (cs > LIMIT_MS && !clientReported) {
                clientReported = true;
                report("a tela do jogo (Render thread)", cs, "Render thread");
            } else if (cs < 2000 && clientReported) {
                clientReported = false;
                BoomboxMod.LOGGER.warn("[Boombox vigia] a tela do jogo voltou a andar.");
            }
        }
    }

    private static void report(String what, long ms, String thread) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Boombox vigia] ").append(what).append(" está parado há ").append(ms / 1000).append(" s. ")
                .append("Se foi carregando ou salvando o mundo, pode ignorar. Onde cada parte do jogo está agora:\n");
        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
            String n = e.getKey().getName();
            if (!n.equals("Server thread") && !n.equals("Render thread")) continue;
            sb.append("  ").append(n).append(n.equals(thread) ? " (parado)" : "").append(" [").append(e.getKey().getState()).append("]:\n");
            StackTraceElement[] st = e.getValue();
            for (int i = 0; i < Math.min(st.length, 60); i++) sb.append("      at ").append(st[i]).append('\n');
        }
        BoomboxMod.LOGGER.warn(sb.toString());
    }
}
