package com.boombox.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 3.7: tela do chaveamento do torneio de sinuca: quartas, semifinais, final e campeão, com quem passou (branco), quem
 * caiu (cinza) e as pessoas de verdade em amarelo. Mostra também o jogo que está na mesa agora.
 */
public class SinucaBracketScreen extends Screen {
    private final String[] bracket = new String[15];
    private final Set<String> humans = new HashSet<>();
    private final int stage;
    private final boolean running;
    private final String table;

    public SinucaBracketScreen(String data) {
        super(Component.literal("Torneio de Sinuca do Seu Zé"));
        String[] lines = data.split("\n", -1);
        String[] b = lines[0].split("\t", -1);
        for (int i = 0; i < 15; i++) bracket[i] = i < b.length ? b[i] : "";
        if (lines.length > 1) for (String h : lines[1].split("\t")) if (!h.isEmpty()) humans.add(h);
        stage = lines.length > 2 && !lines[2].isEmpty() ? Integer.parseInt(lines[2]) : 0;
        running = lines.length > 3 && lines[3].equals("1");
        table = lines.length > 4 ? lines[4] : "";
    }

    private static String stageName(int s) {
        return s == 0 ? "Quartas de final" : s == 1 ? "Semifinal" : "Final";
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2, top = Math.max(4, height / 2 - 110);
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(cx - 50, top + 204, 100, 18).build());
    }

    private static String fit(Font font, String s, int max) {
        if (font.width(s) <= max) return s;
        while (s.length() > 2 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static final int BOX_W = 100, BOX_H = 12;

    private static int next(int slot) {
        return slot < 8 ? 8 + slot / 2 : slot < 12 ? 12 + (slot - 8) / 2 : 14;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2, top = Math.max(4, height / 2 - 110);
        g.fill(cx - 215, top - 4, cx + 215, top + 224, 0xE0102018);
        g.outline(cx - 215, top - 4, 430, 228, 0xFF2E7A40);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        String status = running ? "§f" + stageName(stage) : bracket[14].isEmpty() ? "§7Encerrado" : "§6Campeão: " + bracket[14];
        g.centeredText(font, "§a§l🎱 " + getTitle().getString() + " §r§7• " + status, cx, top, 0xFFFFFFFF);
        if (running && !table.isEmpty()) g.centeredText(font, "§7Na mesa agora: §e" + table, cx, top + 11, 0xFFFFFFFF);
        int y0 = top + 36;
        int[] colX = {cx - 210, cx - 105, cx, cx + 105};
        String[] heads = {"Quartas", "Semifinais", "Final", "Campeão"};
        for (int c = 0; c < 4; c++) g.text(font, "§7" + heads[c], colX[c], y0 - 10, 0xFFFFFFFF, false);
        int[] center = new int[7];
        for (int k = 0; k < 4; k++) center[k] = y0 + 8 + k * 40 + 12;
        center[4] = (center[0] + center[1]) / 2;
        center[5] = (center[2] + center[3]) / 2;
        center[6] = (center[4] + center[5]) / 2;
        for (int k = 0; k < 7; k++) {
            int col = k < 4 ? 0 : k < 6 ? 1 : 2;
            int slot = k < 4 ? 2 * k : k < 6 ? 8 + 2 * (k - 4) : 12;
            for (int s = 0; s < 2; s++) {
                int y = center[k] - BOX_H - 1 + s * (BOX_H + 2);
                box(g, colX[col], y, slot + s);
            }
            int x = colX[col], nx = colX[col + 1];
            g.fill(x + BOX_W, center[k], nx - 4, center[k] + 1, 0xFF4A7050);
            if (k < 6) {
                int nk = k < 4 ? 4 + k / 2 : 6;
                g.fill(nx - 5, Math.min(center[k], center[nk]), nx - 4, Math.max(center[k], center[nk]) + 1, 0xFF4A7050);
            }
        }
        box(g, colX[3], center[6] - BOX_H / 2, 14);
        if (!bracket[14].isEmpty()) g.text(font, "§6🏆 Campeão!", colX[3], center[6] + BOX_H / 2 + 3, 0xFFFFFFFF, false);
        g.text(font, "§7Em §eamarelo§7: pessoas. Para entrar na chave, fale com o Seu Zé.",
                cx - 210, y0 + 158, 0xFFFFFFFF, false);
    }

    private void box(GuiGraphicsExtractor g, int x, int y, int slot) {
        String n = bracket[slot];
        boolean human = humans.contains(n);
        g.fill(x, y, x + BOX_W, y + BOX_H, human ? 0xC0403810 : 0xC0000000);
        if (n.isEmpty()) {
            g.text(font, "§8—", x + 4, y + 2, 0xFFFFFFFF, false);
            return;
        }
        boolean lost = slot < 14 && !bracket[next(slot)].isEmpty() && !bracket[next(slot)].equals(n);
        String c = human ? (lost ? "§6" : "§e") : lost ? "§8" : "§f";
        g.text(font, c + fit(font, n, BOX_W - 8), x + 4, y + 2, 0xFFFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
