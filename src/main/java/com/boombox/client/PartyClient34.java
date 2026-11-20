package com.boombox.client;

import com.boombox.bar.Blocks34;
import com.boombox.bar.NeonSignBlock;
import com.boombox.client.render.Renderers34;
import com.boombox.entity.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;

/** Cliente da 3.4: neon personalizado, bola de vôlei e boia de flamingo. */
public final class PartyClient34 {
    private PartyClient34() {}

    public static void register() {
        BlockEntityRendererRegistry.register(Blocks34.NEON_SIGN_BE, Renderers34.Neon::new);
        BlockEntityRendererRegistry.register(Blocks34.PLACAR_BE, com.boombox.client.render.Renderers35.Placar::new);
        EntityRendererRegistry.register(ModEntities.VOLLEYBALL, Renderers34.VolleyballRenderer::new);
        EntityRendererRegistry.register(ModEntities.POOL_FLOAT, Renderers34.FloatRenderer::new);
        NeonSignBlock.openHook = pos -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new NeonScreen(pos)));
    }
}
