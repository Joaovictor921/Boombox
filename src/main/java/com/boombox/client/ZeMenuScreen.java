package com.boombox.client;

import com.boombox.entity.SeuZe;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Cardápio do bar do Seu Zé: pagar com esmeralda, pendurar no fiado ou pagar o que deve. */
public class ZeMenuScreen extends Screen {
    private static final int W = 260;
    private final int zeId;

    public ZeMenuScreen(int zeId) {
        super(Component.literal("Bar do Seu Zé"));
        this.zeId = zeId;
    }

    private int myDebt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.level.getEntity(zeId) instanceof SeuZe ze)) return 0;
        return ze.debtsForDisplay().getOrDefault(mc.player.getName().getString(), 0);
    }

    @Override
    protected void init() {
        int left = width / 2 - W / 2, top = height / 2 - 110;
        for (int i = 0; i < SeuZe.MENU.length; i++) {
            int item = i, y = top + 30 + i * 22;
            addRenderableWidget(Button.builder(Component.literal("Pagar (" + SeuZe.PRICES[i] + " esm.)"), b -> order(item, 0))
                    .bounds(left + 110, y, 76, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Pendurar"), b -> order(item, 1))
                    .bounds(left + 190, y, 70, 20).build());
        }
        int by = top + 30 + SeuZe.MENU.length * 22 + 6;
        addRenderableWidget(Button.builder(Component.literal("§aPagar o fiado"), b -> order(0, 2))
                .bounds(left, by, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose()).bounds(left + W - 70, by, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§6🎱 Torneio de sinuca (" + SeuZe.TOURNAMENT_PRICE + " esm.)"), b -> { order(0, 3); onClose(); })
                .bounds(left, by + 24, W, 20).build());
        // 3.8: desafio de pênaltis valendo esmeraldas (no campo da vila)
        for (int i = 0; i < SeuZe.PENALTY_BETS.length; i++) {
            int bet = SeuZe.PENALTY_BETS[i];
            addRenderableWidget(Button.builder(Component.literal("§a" + bet + " esm."), b -> { order(bet, 4); onClose(); })
                    .bounds(left + 122 + i * 46, by + 48, 44, 20).build());
        }
    }

    private void order(int item, int mode) {
        ClientPlayNetworking.send(new Payloads.ZeOrder(zeId, item, mode));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = width / 2 - W / 2, top = height / 2 - 110;
        int h = 30 + SeuZe.MENU.length * 22 + 6 + 72 + 8;
        g.fill(left - 8, top - 8, left + W + 8, top + h, 0xE0241A10);
        g.outline(left - 8, top - 8, W + 16, h + 8, 0xFF8A6A3A);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, "§6§l♣ Bar do Seu Zé ♣", width / 2, top - 2, 0xFFFFFFFF);
        int debt = myDebt();
        g.centeredText(font, debt == 0 ? "§7Você não deve nada" : "§cNo fiado: " + debt + " de " + SeuZe.MAX_DEBT + " esmeraldas",
                width / 2, top + 12, 0xFFFFFFFF);
        String[] desc = {"§fGelada do Zé", "§fTorresmo §7(velocidade)", "§fCoxinha §7(pressa)", "§fQuentão §7(esquenta)",
                "§fFritas §7(porção)", "§fCalabresa §7(porção)", "§fFrango à passarinho"};
        for (int i = 0; i < desc.length; i++) g.text(font, desc[i], left, top + 36 + i * 22, 0xFFFFFFFF, false);
        int by = top + 30 + SeuZe.MENU.length * 22 + 6;
        g.text(font, "§a⚽ Desafio de pênaltis:", left, by + 54, 0xFFFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
