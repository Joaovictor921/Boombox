package com.boombox.client;

import com.boombox.BoomboxMod;
import com.boombox.bar.Blocks34;
import com.boombox.net.Payloads;
import com.boombox.server.Features38;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.util.Mth;

/** 3.8 no cliente: telas do mapa da vila, da Liga dos Amigos, das apostas e do ranking, e a seta de navegação. */
public final class Client38 {
    /** Seta de navegação: para onde (nome, x, z), ou null. */
    private static String navName;
    private static int navX, navZ;
    private static Object navLevel;

    private Client38() {}

    public static void register() {
        BlockEntityRendererRegistry.register(Blocks34.RANKING_BOARD_BE, com.boombox.client.render.Renderers38.Ranking::new);
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Ui.TYPE, (p, ctx) -> {
            Minecraft mc = ctx.client();
            switch (p.kind()) {
                case Features38.UI_MAP -> mc.execute(() -> mc.gui.setScreen(new VillageMapScreen(p.data())));
                case Features38.UI_NAV -> {
                    String[] f = p.data().split("\t");
                    if (f.length >= 3) navigate(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                }
                case Features38.UI_LEAGUE -> mc.execute(() -> {
                    if (mc.gui.screen() instanceof LeagueScreen ls) ls.update(p.data());
                    else mc.gui.setScreen(new LeagueScreen(p.data()));
                });
                case Features38.UI_BET -> mc.execute(() -> {
                    if (mc.gui.screen() instanceof BetScreen bs) bs.update(p.data());
                    else mc.gui.setScreen(new BetScreen(p.data()));
                });
                case Features38.UI_RANKING -> mc.execute(() -> mc.gui.setScreen(new RankingScreen(p.data())));
                default -> { }
            }
        });
        HudElementRegistry.addLast(BoomboxMod.id("village_nav"), (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            String line = navLine(mc);
            if (line == null || mc.gui.hud.isHidden()) return;
            int w = g.guiWidth(), y = g.guiHeight() - 74;
            int lw = mc.font.width(line);
            g.fill(w / 2 - lw / 2 - 4, y - 3, w / 2 + lw / 2 + 4, y + 10, 0xA0000000);
            g.centeredText(mc.font, line, w / 2, y, 0xFFFFFFFF);
        });
    }

    public static void send(int kind, String data) {
        ClientPlayNetworking.send(new Payloads.UiAction(kind, data));
    }

    // ------------------------------------------------------------ seta de navegação
    public static void navigate(String name, int x, int z) {
        navName = name;
        navX = x;
        navZ = z;
        navLevel = Minecraft.getInstance().level;
    }

    public static void stopNavigation() {
        navName = null;
    }

    public static String navTarget() {
        return navName;
    }

    private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    /** Texto da seta ("➡ Campo de futebol: 85 blocos"), ou null se não tem seta. */
    public static String navLine(Minecraft mc) {
        if (navName == null || mc.player == null) return null;
        if (mc.level != navLevel) {
            navName = null; // mudou de mundo / dimensão
            return null;
        }
        double dx = navX + 0.5 - mc.player.getX(), dz = navZ + 0.5 - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 7) {
            mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§aVocê chegou: §f" + navName));
            navName = null;
            return null;
        }
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float rel = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        int i = Math.floorMod(Math.round(rel / 45f), 8);
        return "§e" + ARROWS[i] + " §f" + navName + "§7: §e" + (int) dist + " blocos";
    }

    /** Direção (norte, nordeste...) de um ponto em relação a outro, para a tela do mapa. */
    public static String compass(double dx, double dz) {
        String[] names = {"sul", "sudoeste", "oeste", "noroeste", "norte", "nordeste", "leste", "sudeste"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz)); // 0 = sul (z+), 90 = oeste
        int i = Math.floorMod((int) Math.round(ang / 45.0), 8);
        return names[i];
    }
}
