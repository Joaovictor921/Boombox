package com.boombox.client;

import com.boombox.server.Features38;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 3.8: apostas de brincadeira nos jogos da copa, entre jogadores: escolha o time e quantas esmeraldas. Quem acertar
 * divide tudo o que foi apostado (do tamanho da aposta de cada um). Fecha no fim do primeiro tempo.
 */
public class BetScreen extends Screen {
    private static final int[] AMOUNTS = {1, 3, 5, 10};
    private int organizer;
    private String teamA = "", teamB = "";
    private int colorA, colorB, poolA, poolB, myTeam = -1, myAmount, playingFor = -1;
    private boolean open, cup;
    private int pick = 0, amount = 1;

    public BetScreen(String data) {
        super(Component.literal("Apostas da Copa"));
        update(data);
    }

    public void update(String data) {
        String[] f = data.split("\t", -1);
        try {
            organizer = Integer.parseInt(f[0]);
            teamA = f[1];
            teamB = f[2];
            open = f[3].equals("1");
            poolA = Integer.parseInt(f[4]);
            poolB = Integer.parseInt(f[5]);
            myTeam = Integer.parseInt(f[6]);
            myAmount = Integer.parseInt(f[7]);
            playingFor = Integer.parseInt(f[8]);
            cup = f[9].equals("1");
            colorA = Integer.parseInt(f[10]);
            colorB = Integer.parseInt(f[11]);
        } catch (RuntimeException ignored) {
        }
        if (myTeam >= 0) pick = myTeam;
        else if (playingFor >= 0) pick = playingFor;
        if (minecraft != null) init();
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = width / 2 - 130, top = height / 2 - 90;
        if (open) {
            boolean lockA = (myTeam == 1) || playingFor == 1, lockB = (myTeam == 0) || playingFor == 0;
            Button a = Button.builder(Component.literal((pick == 0 ? "§e▶ " : "") + teamA), b -> { pick = 0; init(); }).bounds(left, top + 50, 126, 20).build();
            Button bb = Button.builder(Component.literal((pick == 1 ? "§e▶ " : "") + teamB), b -> { pick = 1; init(); }).bounds(left + 134, top + 50, 126, 20).build();
            a.active = !lockA;
            bb.active = !lockB;
            addRenderableWidget(a);
            addRenderableWidget(bb);
            for (int i = 0; i < AMOUNTS.length; i++) {
                int v = AMOUNTS[i];
                addRenderableWidget(Button.builder(Component.literal((amount == v ? "§e" : "") + v + " esm."), b -> { amount = v; init(); })
                        .bounds(left + i * 66, top + 96, 62, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("§a§lApostar"), b ->
                    Client38.send(Features38.A_BET_PLACE, organizer + "\t" + pick + "\t" + amount)).bounds(left, top + 126, 126, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(left + 134, top + 126, 126, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = width / 2 - 130, top = height / 2 - 90;
        g.fill(left - 8, top - 8, left + 268, top + 176, 0xE0181808);
        g.outline(left - 8, top - 8, 276, 184, 0xFFE0C040);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, "§e§l$ Apostas da Copa", width / 2, top - 2, 0xFFFFFFFF);
        if (!cup) {
            g.centeredText(font, "§7Só dá para apostar nos jogos da copa (campeonato).", width / 2, top + 20, 0xFFFFFFFF);
            g.centeredText(font, "§7Volte quando alguém estiver jogando uma copa.", width / 2, top + 32, 0xFFFFFFFF);
            return;
        }
        g.centeredText(font, "§f" + teamA + " §7x §f" + teamB, width / 2, top + 14, 0xFFFFFFFF);
        g.centeredText(font, open ? "§aApostas abertas até o fim do 1º tempo" : "§cApostas fechadas (só no 1º tempo)", width / 2, top + 26, 0xFFFFFFFF);
        g.fill(left, top + 40, left + 126, top + 44, 0xFF000000 | colorA);
        g.fill(left + 134, top + 40, left + 260, top + 44, 0xFF000000 | colorB);
        g.centeredText(font, "§7Na mesa: §e" + poolA + " esm.", left + 63, top + 74, 0xFFFFFFFF);
        g.centeredText(font, "§7Na mesa: §e" + poolB + " esm.", left + 197, top + 74, 0xFFFFFFFF);
        if (open) g.text(font, "§fQuanto?", left, top + 86, 0xFFFFFFFF, false);
        String mine = myTeam < 0 ? "§7Você ainda não apostou." : "§fSua aposta: §e" + myAmount + " esm. §fno §e" + (myTeam == 0 ? teamA : teamB);
        g.centeredText(font, mine, width / 2, top + 152, 0xFFFFFFFF);
        g.centeredText(font, "§7Quem acertar divide tudo. Empate: a aposta volta.", width / 2, top + 164, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
