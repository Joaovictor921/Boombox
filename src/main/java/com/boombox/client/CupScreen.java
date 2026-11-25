package com.boombox.client;

import com.boombox.entity.Cup;
import com.boombox.entity.Sports;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 3.6: tela da copa (futebol ou vôlei). Duas abas que dá para escolher: os 4 grupos (com a tabela de cada um) e o
 * chaveamento do mata-mata (quartas, semifinais, final e campeão). O seu time aparece em amarelo.
 */
public class CupScreen extends Screen {
    private final Cup cup;
    private final int sport;
    private boolean bracket;

    public CupScreen(Cup cup, int sport) {
        super(Component.literal(cup.vilas ? "Copa das Vilas" : sport == Sports.VOLLEY ? "Copa de Vôlei de Praia" : "Copa da Várzea"));
        this.cup = cup;
        this.sport = sport;
        this.bracket = cup.stage >= Cup.QUARTER || cup.vilas;
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2, top = Math.max(4, height / 2 - 114);
        // 3.9: a Copa das Vilas não tem grupos (só o chaveamento)
        if (!cup.vilas) addRenderableWidget(Button.builder(Component.literal((bracket ? "" : "§e▶ ") + "Grupos"), b -> { bracket = false; init(); })
                .bounds(cx - 155, top + 14, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal((bracket ? "§e▶ " : "") + "Chaveamento"), b -> { bracket = true; init(); })
                .bounds(cx - 50, top + 14, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(cx + 55, top + 14, 100, 18).build());
    }

    private static String fit(Font font, String s, int max) {
        if (font.width(s) <= max) return s;
        while (s.length() > 2 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2, top = Math.max(4, height / 2 - 114);
        g.fill(cx - 215, top - 4, cx + 215, top + 232, 0xE0101820);
        g.outline(cx - 215, top - 4, 430, 236, sport == Sports.VOLLEY ? 0xFFF0C040 : 0xFF3CB043);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        String status = cup.out ? "§c" + cup.names[0] + " foi eliminado" : cup.stage >= Cup.DONE && cup.champion() == 0 ? "§6Campeão!" : "§f" + cup.stageName();
        g.centeredText(font, "§6§l🏆 " + getTitle().getString() + " §r§7• " + status, cx, top, 0xFFFFFFFF);
        if (bracket) drawBracket(g, cx, top + 40);
        else drawGroups(g, cx, top + 40);
    }

    // ------------------------------------------------------------ grupos
    private void drawGroups(GuiGraphicsExtractor g, int cx, int y0) {
        boolean groupsOver = cup.stage >= Cup.QUARTER;
        for (int gr = 0; gr < 4; gr++) {
            int x = cx - 205 + (gr % 2) * 208, y = y0 + (gr / 2) * 74;
            g.fill(x, y, x + 200, y + 70, 0x80000000);
            g.text(font, "§e§lGrupo " + Cup.groupLetter(gr), x + 4, y + 3, 0xFFFFFFFF, false);
            g.text(font, "§7J   P   SG", x + 140, y + 3, 0xFFFFFFFF, false);
            int pos = 0;
            for (int t : cup.groupOrder(gr)) {
                int ry = y + 16 + pos * 13;
                if (groupsOver && pos < 2) g.fill(x + 1, ry - 2, x + 3, ry + 9, 0xFF3CB043);
                g.fill(x + 6, ry, x + 12, ry + 7, 0xFF000000 | cup.colors[t]);
                String color = t == 0 ? "§e" : "§f";
                g.text(font, color + (pos + 1) + ". " + fit(font, cup.names[t], 112), x + 15, ry, 0xFFFFFFFF, false);
                int sg = cup.gf[t] - cup.ga[t];
                g.text(font, color + cup.played[t], x + 141, ry, 0xFFFFFFFF, false);
                g.text(font, color + cup.pts[t], x + 157, ry, 0xFFFFFFFF, false);
                g.text(font, color + (sg > 0 ? "+" : "") + sg, x + 176, ry, 0xFFFFFFFF, false);
                pos++;
            }
        }
        int y = y0 + 151;
        g.text(font, "§7Seus jogos: " + (cup.myGroupGames[0].isEmpty() ? "ainda nenhum" : ""), cx - 205, y, 0xFFFFFFFF, false);
        String legend = "§a▌§7 = 2 primeiros: vão para as quartas";
        g.text(font, legend, cx + 205 - font.width(legend), y, 0xFFFFFFFF, false);
        for (int i = 0; i < 3; i++) if (!cup.myGroupGames[i].isEmpty()) g.text(font, "§f" + (i + 1) + "ª rodada: " + cup.myGroupGames[i], cx - 205, y + 11 + i * 10, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------ mata-mata
    private static final int BOX_W = 100, BOX_H = 12;

    private void drawBracket(GuiGraphicsExtractor g, int cx, int y0) {
        int[] colX = {cx - 210, cx - 105, cx, cx + 105};
        String[] heads = {"Quartas", "Semifinais", "Final", "Campeão"};
        for (int c = 0; c < 4; c++) g.text(font, "§7" + heads[c], colX[c], y0 - 10, 0xFFFFFFFF, false);
        // centro de cada jogo: quartas 0-3, semis 4-5, final 6
        int[] center = new int[7];
        for (int k = 0; k < 4; k++) center[k] = y0 + 8 + k * 40 + 12;
        center[4] = (center[0] + center[1]) / 2;
        center[5] = (center[2] + center[3]) / 2;
        center[6] = (center[4] + center[5]) / 2;
        for (int k = 0; k < 7; k++) {
            int col = k < 4 ? 0 : k < 6 ? 1 : 2;
            int slot = k < 4 ? 2 * k : k < 6 ? 8 + 2 * (k - 4) : 12;
            int win = k < 4 ? 8 + k : k < 6 ? 12 + (k - 4) : 14;
            String[] sc = cup.koScore[k].isEmpty() ? null : cup.koScore[k].replace(" (pên.)", "").split(" x ");
            boolean pen = cup.koScore[k].contains("pên");
            int x = colX[col];
            for (int s = 0; s < 2; s++) {
                int t = cup.ko[slot + s];
                int y = center[k] - BOX_H - 1 + s * (BOX_H + 2);
                boolean lost = cup.ko[win] >= 0 && t >= 0 && cup.ko[win] != t;
                box(g, x, y, t, lost, sc == null ? "" : sc[s] + (pen && cup.ko[win] == t ? "*" : ""));
            }
            // linhas da chave
            int nx = colX[col + 1];
            g.fill(x + BOX_W, center[k], nx - 4, center[k] + 1, 0xFF606870);
            if (k < 6) {
                int next = k < 4 ? 4 + k / 2 : 6;
                g.fill(nx - 5, Math.min(center[k], center[next]), nx - 4, Math.max(center[k], center[next]) + 1, 0xFF606870);
            }
        }
        int champ = cup.ko[14];
        box(g, colX[3], center[6] - BOX_H / 2, champ, false, "");
        if (champ >= 0) g.text(font, "§6🏆 Campeão!", colX[3], center[6] + BOX_H / 2 + 3, 0xFFFFFFFF, false);
        g.text(font, "§7* passou nos pênaltis  •  o seu time está em §eamarelo", cx - 205, y0 + 172, 0xFFFFFFFF, false);
        if (cup.stage < Cup.QUARTER) g.centeredText(font, "§7O chaveamento sai no fim da fase de grupos.", cx, y0 + 80, 0xFFFFFFFF);
    }

    private void box(GuiGraphicsExtractor g, int x, int y, int t, boolean lost, String score) {
        g.fill(x, y, x + BOX_W, y + BOX_H, t == 0 ? 0xC0403810 : 0xC0000000);
        if (t < 0) {
            g.text(font, "§8—", x + 4, y + 2, 0xFFFFFFFF, false);
            return;
        }
        g.fill(x + 2, y + 3, x + 6, y + 9, 0xFF000000 | cup.colors[t]);
        String c = t == 0 ? "§e" : lost ? "§8" : "§f";
        g.text(font, c + fit(font, cup.names[t], BOX_W - 10 - (score.isEmpty() ? 0 : font.width(score) + 3)), x + 9, y + 2, 0xFFFFFFFF, false);
        if (!score.isEmpty()) g.text(font, (lost ? "§8" : "§f") + score, x + BOX_W - 3 - font.width(score), y + 2, 0xFFFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
