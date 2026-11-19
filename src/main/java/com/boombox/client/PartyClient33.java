package com.boombox.client;

import com.boombox.BoomboxMod;
import com.boombox.bar.BarBlocks;
import com.boombox.client.render.Renderers33;
import com.boombox.entity.ModEntities;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;

/** Cliente da 3.3: tela do Drink Neon, desenhos novos (bola, balão, prateleira) e pulseira de luz. */
public final class PartyClient33 {
    private static int neonTicks, neonTotal;

    private PartyClient33() {}

    public static void register() {
        EntityRendererRegistry.register(ModEntities.BALL, Renderers33.BallRenderer::new);
        EntityRendererRegistry.register(ModEntities.BALLOON, Renderers33.BalloonRenderer::new);
        BlockEntityRendererRegistry.register(BarBlocks.CUP_SHELF_BE, Renderers33.Shelf::new);
        ClientPlayNetworking.registerGlobalReceiver(Payloads.NeonVision.TYPE, (p, ctx) -> {
            neonTicks = neonTotal = p.ticks();
        });
        // Drink Neon: a tela ganha uma cor que muda e pulsa com a música (mais forte no começo, sumindo no fim)
        HudElementRegistry.addFirst(BoomboxMod.id("neon_vision"), (g, delta) -> {
            if (neonTicks <= 0) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            float fade = Math.min(1f, neonTicks / 60f) * Math.min(1f, (neonTotal - neonTicks + 1) / 20f);
            int c = Renderers33.braceletColor(mc.player.position(), 0f);
            int alpha = (int) (fade * 70);
            int w = g.guiWidth(), h = g.guiHeight();
            g.fill(0, 0, w, h, (alpha << 24) | (c & 0xFFFFFF));
            // bordas mais fortes (efeito "neon")
            int edge = (int) (fade * 140) << 24 | (c & 0xFFFFFF);
            int c2 = Renderers33.braceletColor(mc.player.position(), 0.5f) & 0xFFFFFF | ((int) (fade * 140) << 24);
            g.fillGradient(0, 0, w, h / 5, edge, 0);
            g.fillGradient(0, h - h / 5, w, h, 0, c2);
        });
    }

    public static void tick(Minecraft mc) {
        if (neonTicks > 0) neonTicks--;
    }
}
