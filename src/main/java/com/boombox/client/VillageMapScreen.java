package com.boombox.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 3.8: tela do mapa da vila. À esquerda, um desenho de cima da vila com cada lugar (você no meio); à direita, a lista
 * com distância e direção, e o botão "Ir" que liga a seta na tela até o lugar.
 */
public class VillageMapScreen extends Screen {
    private record Place(String name, int x, int z) {}

    private final List<Place> places = new ArrayList<>();
    private static final int[] COLORS = {0xFFFFFFFF, 0xFF3CB043, 0xFFE0A030, 0xFFE040E0, 0xFFF0C040, 0xFF40C0F0, 0xFFE06060,
            0xFFA0E060, 0xFFF08040, 0xFF60A0FF, 0xFFFF6040};

    public VillageMapScreen(String data) {
        super(Component.literal("Mapa da Vila"));
        for (String line : data.split("\n")) {
            String[] f = line.split("\t");
            if (f.length < 3) continue;
            try {
                places.add(new Place(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        // o centro da vila primeiro; o resto do mais perto para o mais longe
        if (minecraftPlayer() != null && places.size() > 1) {
            var rest = new ArrayList<>(places.subList(1, places.size()));
            rest.sort((a, b) -> Double.compare(dist(a), dist(b)));
            Place first = places.get(0);
            places.clear();
            places.add(first);
            places.addAll(rest);
        }
    }

    private static net.minecraft.world.entity.player.Player minecraftPlayer() {
        return net.minecraft.client.Minecraft.getInstance().player;
    }

    private double dist(Place p) {
        var me = minecraftPlayer();
        if (me == null) return 0;
        return Math.hypot(p.x + 0.5 - me.getX(), p.z + 0.5 - me.getZ());
    }

    private int rows() {
        return Math.min(places.size(), 11);
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = width / 2 - 200, top = height / 2 - 112;
        for (int i = 0; i < rows(); i++) {
            Place p = places.get(i);
            addRenderableWidget(Button.builder(Component.literal("Ir"), b -> {
                Client38.navigate(p.name, p.x, p.z);
                onClose();
            }).bounds(left + 370, top + 28 + i * 17, 30, 15).build());
        }
        int by = top + 216;
        if (Client38.navTarget() != null) {
            addRenderableWidget(Button.builder(Component.literal("§cTirar a seta"), b -> {
                Client38.stopNavigation();
                init();
            }).bounds(left + 196, by, 100, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(left + 300, by, 100, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = width / 2 - 200, top = height / 2 - 112;
        g.fill(left - 8, top - 8, left + 408, top + 240, 0xE0201810);
        g.outline(left - 8, top - 8, 416, 248, 0xFFC8A060);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, "§6§l🗺 Mapa da Vila", width / 2, top - 2, 0xFFFFFFFF);
        var me = minecraftPlayer();
        if (me == null) return;
        // desenho de cima: 180 x 180, você no centro, norte para cima
        int mx = left, my = top + 24, size = 176;
        g.fill(mx, my, mx + size, my + size, 0xFF3A5A2A);
        g.outline(mx, my, size, size, 0xFF8A6A3A);
        double far = 40;
        for (Place p : places) far = Math.max(far, Math.max(Math.abs(p.x - me.getX()), Math.abs(p.z - me.getZ())));
        double scale = (size / 2.0 - 8) / far;
        int cx = mx + size / 2, cz = my + size / 2;
        g.text(font, "§7N", cx - 2, my + 2, 0xFFFFFFFF, false);
        for (int i = 0; i < places.size() && i < 11; i++) {
            Place p = places.get(i);
            int px = cx + (int) Math.round((p.x + 0.5 - me.getX()) * scale), pz = cz + (int) Math.round((p.z + 0.5 - me.getZ()) * scale);
            int c = COLORS[i % COLORS.length];
            g.fill(px - 3, pz - 3, px + 3, pz + 3, 0xFF000000);
            g.fill(px - 2, pz - 2, px + 2, pz + 2, c);
            g.text(font, Integer.toString(i + 1), px + 4, pz - 4, c, true);
        }
        // você: triângulo branco no centro
        g.fill(cx - 2, cz - 2, cx + 3, cz + 3, 0xFFFFFFFF);
        g.outline(cx - 3, cz - 3, 7, 7, 0xFF000000);
        g.text(font, "§fvocê", cx - 10, cz + 5, 0xFFFFFFFF, true);
        // lista
        int lx = left + 186;
        if (places.size() <= 1) g.text(font, "§7Essa vila não tem lugares do Boombox.", lx, top + 30, 0xFFFFFFFF, false);
        for (int i = 0; i < rows(); i++) {
            Place p = places.get(i);
            double dx = p.x + 0.5 - me.getX(), dz = p.z + 0.5 - me.getZ();
            int d = (int) Math.hypot(dx, dz);
            int c = COLORS[i % COLORS.length];
            int y = top + 32 + i * 17;
            g.text(font, (i + 1) + ".", lx, y, c, false);
            String where = "§7" + d + " bl. " + Client38.compass(dx, dz);
            String name = p.name;
            int max = left + 362 - font.width(where) - (lx + 14);
            if (font.width(name) > max) {
                while (name.length() > 2 && font.width(name + "…") > max) name = name.substring(0, name.length() - 1);
                name += "…";
            }
            g.text(font, "§f" + name, lx + 14, y, 0xFFFFFFFF, false);
            g.text(font, where, left + 366 - font.width(where), y, 0xFFFFFFFF, false);
        }
        g.text(font, "§7\"Ir\" mostra uma seta na tela até o lugar.", left, top + 204, 0xFFFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
