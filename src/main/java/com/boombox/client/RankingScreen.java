package com.boombox.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 3.8: ranking do bar: top 10 de artilheiros, copas ganhas (futebol e vôlei) e torneios de sinuca. */
public class RankingScreen extends Screen {
    private final String[][][] cols = new String[3][][];
    private static final String[] HEADS = {"§a⚽ Artilheiros", "§6🏆 Copas", "§b🎱 Sinuca"};
    private static final String[] UNITS = {"gols", "copas", "títulos"};

    public RankingScreen(String data) {
        super(Component.literal("Ranking do Bar"));
        cols[0] = cols[1] = cols[2] = new String[0][];
        String[] lines = data.split("\n", -1);
        for (int c = 0; c < 3; c++) cols[c] = parse(c < lines.length ? lines[c] : "");
    }

    /** "nome=número" separados por tab. */
    static String[][] parse(String line) {
        if (line.isEmpty()) return new String[0][];
        String[] items = line.split("\t");
        String[][] out = new String[items.length][];
        for (int i = 0; i < items.length; i++) {
            int eq = items[i].lastIndexOf('=');
            out[i] = eq < 0 ? new String[]{items[i], "0"} : new String[]{items[i].substring(0, eq), items[i].substring(eq + 1)};
        }
        return out;
    }

    @Override
    protected void init() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(width / 2 - 50, height / 2 + 92, 100, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = width / 2 - 195, top = height / 2 - 100;
        g.fill(left - 8, top - 8, left + 398, top + 222, 0xE01A1008);
        g.outline(left - 8, top - 8, 406, 230, 0xFF8A6A3A);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, "§6§l★ Ranking do Bar do Seu Zé", width / 2, top - 2, 0xFFFFFFFF);
        String me = minecraft == null || minecraft.player == null ? "" : minecraft.player.getName().getString();
        for (int c = 0; c < 3; c++) {
            int x = left + c * 132;
            g.text(font, "§l" + HEADS[c], x, top + 16, 0xFFFFFFFF, false);
            g.fill(x, top + 27, x + 124, top + 28, 0xFF8A6A3A);
            if (cols[c].length == 0) g.text(font, "§7Ninguém ainda.", x, top + 34, 0xFFFFFFFF, false);
            for (int i = 0; i < cols[c].length && i < 10; i++) {
                String[] e = cols[c][i];
                int y = top + 34 + i * 15;
                String medal = i == 0 ? "§6" : i == 1 ? "§7" : i == 2 ? "§c" : "§8";
                String col = e[0].equals(me) ? "§e" : "§f";
                String name = e[0];
                while (name.length() > 2 && font.width(name) > 74) name = name.substring(0, name.length() - 1);
                g.text(font, medal + (i + 1) + ". " + col + name, x, y, 0xFFFFFFFF, false);
                String n = "§f" + e[1];
                g.text(font, n, x + 120 - font.width(n), y, 0xFFFFFFFF, false);
            }
        }
        g.centeredText(font, "§7Gols em jogos marcados • copas de futebol e vôlei • torneios de sinuca", width / 2, top + 178, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
