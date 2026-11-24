package com.boombox.client;

import com.boombox.entity.Sports;
import com.boombox.server.Features38;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 3.8: Liga dos Amigos. Em cima, o seu time (nome e cor, para criar ou mudar); embaixo, a tabela de todos os times do
 * mundo. Jogo contra um amigo que também tem time ("Jogar contra o amigo" no organizador) vale pontos.
 */
public class LeagueScreen extends Screen {
    private String myName = "";
    private int myColor;
    private boolean hasTeam;
    private final List<String[]> table = new ArrayList<>();
    private EditBox name;
    private int color;

    public LeagueScreen(String data) {
        super(Component.literal("Liga dos Amigos"));
        update(data);
        color = Math.max(0, indexOf(myColor));
    }

    private static int indexOf(int c) {
        for (int i = 0; i < Sports.COLORS.length; i++) if (Sports.COLORS[i] == c) return i;
        return -1;
    }

    public void update(String data) {
        String[] lines = data.split("\n", -1);
        String[] me = lines[0].split("\t");
        hasTeam = me.length >= 2 && !me[0].isEmpty();
        if (hasTeam) {
            myName = me[0];
            try { myColor = Integer.parseInt(me[1]); } catch (NumberFormatException e) { myColor = Sports.COLORS[0]; }
        }
        table.clear();
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split("\t", -1);
            if (f.length >= 10) table.add(f);
        }
        if (minecraft != null) init();
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = width / 2 - 170, top = height / 2 - 115;
        String old = name == null ? myName : name.getValue();
        name = new EditBox(font, left, top + 26, 150, 18, Component.literal("Nome do time"));
        name.setMaxLength(24);
        name.setValue(old);
        name.setHint(Component.literal("§7Nome do seu time"));
        addRenderableWidget(name);
        for (int i = 0; i < Sports.COLORS.length; i++) {
            int idx = i;
            addRenderableWidget(Button.builder(Component.literal(""), b -> color = idx)
                    .bounds(left + 156 + (i % 6) * 22, top + 22 + (i / 6) * 14, 20, 12).build());
        }
        addRenderableWidget(Button.builder(Component.literal(hasTeam ? "§aSalvar" : "§aCriar time"), b ->
                Client38.send(Features38.A_LEAGUE_SAVE, name.getValue().replace('\t', ' ') + "\t" + color)).bounds(left + 292, top + 20, 48, 26).build());
        int by = top + 216;
        if (hasTeam) {
            addRenderableWidget(Button.builder(Component.literal("§cSair da liga"), b -> Client38.send(Features38.A_LEAGUE_DELETE, ""))
                    .bounds(left, by, 90, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(left + 250, by, 90, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = width / 2 - 170, top = height / 2 - 115;
        g.fill(left - 8, top - 8, left + 348, top + 242, 0xE0101828);
        g.outline(left - 8, top - 8, 356, 250, 0xFF40A0F0);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, "§b§l★ Liga dos Amigos", width / 2, top - 2, 0xFFFFFFFF);
        g.text(font, hasTeam ? "§fSeu time:" : "§7Crie seu time (nome e cor):", left, top + 12, 0xFFFFFFFF, false);
        for (int i = 0; i < Sports.COLORS.length; i++) {
            int x = left + 156 + (i % 6) * 22 + 3, y = top + 22 + (i / 6) * 14 + 2;
            g.fill(x, y, x + 14, y + 8, 0xFF000000 | Sports.COLORS[i]);
            if (i == color) g.outline(x - 2, y - 2, 18, 12, 0xFFFFFFFF);
        }
        // tabela
        int y = top + 56;
        String[] heads = {"#", "Time", "Dono", "P", "J", "V", "E", "D", "SG"};
        int[] xs = {0, 14, 130, 214, 236, 256, 276, 296, 318};
        for (int i = 0; i < heads.length; i++) g.text(font, "§7" + heads[i], left + xs[i], y, 0xFFFFFFFF, false);
        g.fill(left, y + 10, left + 340, y + 11, 0xFF40607A);
        if (table.isEmpty()) {
            g.text(font, "§7Ninguém montou time ainda. Seja o primeiro!", left, y + 18, 0xFFFFFFFF, false);
        }
        String me = minecraft == null || minecraft.player == null ? "" : minecraft.player.getName().getString();
        for (int r = 0; r < table.size() && r < 12; r++) {
            String[] f = table.get(r);
            int ry = y + 16 + r * 12;
            int c;
            try { c = Integer.parseInt(f[1]); } catch (NumberFormatException e) { c = 0xFFFFFF; }
            boolean mine = f[2].equals(me);
            String col = mine ? "§e" : "§f";
            int sg = Integer.parseInt(f[8]) - Integer.parseInt(f[9]);
            g.text(font, col + (r + 1), left + xs[0], ry, 0xFFFFFFFF, false);
            g.fill(left + xs[1], ry, left + xs[1] + 6, ry + 7, 0xFF000000 | c);
            g.text(font, col + fit(f[0], 104), left + xs[1] + 9, ry, 0xFFFFFFFF, false);
            g.text(font, "§7" + fit(f[2], 80), left + xs[2], ry, 0xFFFFFFFF, false);
            g.text(font, col + "§l" + f[3], left + xs[3], ry, 0xFFFFFFFF, false);
            for (int k = 4; k <= 7; k++) g.text(font, col + f[k], left + xs[k], ry, 0xFFFFFFFF, false);
            g.text(font, col + (sg > 0 ? "+" : "") + sg, left + xs[8], ry, 0xFFFFFFFF, false);
        }
        g.text(font, "§7Jogo contra um amigo que tem time vale pontos: organizador →", left, top + 192, 0xFFFFFFFF, false);
        g.text(font, "§7\"Jogar contra o amigo\". Vitória 3, empate 1.", left, top + 202, 0xFFFFFFFF, false);
    }

    private String fit(String s, int max) {
        if (font.width(s) <= max) return s;
        while (s.length() > 2 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
