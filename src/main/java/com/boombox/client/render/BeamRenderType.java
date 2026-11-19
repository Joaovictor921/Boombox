package com.boombox.client.render;

import com.boombox.BoomboxMod;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Luz que soma na cena (como o raio do jogo), mas que NÃO grava profundidade: o feixe nunca esconde nada
 * que é desenhado depois dele (telões, água, partículas, telas de outros mods). Antes as bordas
 * transparentes dos feixes "furavam" telas desenhadas depois, deixando manchas escuras.
 */
public final class BeamRenderType {
    public static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
                    .withLocation(BoomboxMod.id("pipeline/rave_beam"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)) // profundidade invertida: perto = maior
                    .withCull(false)
                    .build());

    public static final RenderType BEAM = RenderType.create("boombox_rave_beam",
            RenderSetup.builder(PIPELINE)
                    // com a transparência "OIT" ligada o jogo usa o mesmo caminho do raio (já não grava profundidade)
                    .setOitPipelines(RenderPipelines.OIT_LIGHTNING)
                    .sortOnUpload()
                    .createRenderSetup());

    private static Boolean irisPresent;
    private static java.lang.reflect.Method irisInUse;
    private static Object irisApi;

    /**
     * Tipo de desenho dos feixes agora. Com um shader pack do Iris ligado, usa o tipo do raio do próprio jogo
     * (que os shaders já sabem desenhar e fazer brilhar); o nosso tipo especial ficaria invisível nos shaders.
     */
    public static RenderType beam() {
        return shadersOn() ? net.minecraft.client.renderer.rendertype.RenderTypes.lightning() : BEAM;
    }

    private static boolean shadersOn() {
        if (irisPresent == null) {
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = c.getMethod("getInstance").invoke(null);
                irisInUse = c.getMethod("isShaderPackInUse");
                irisPresent = true;
            } catch (Throwable t) {
                irisPresent = false;
            }
        }
        if (!irisPresent) return false;
        try {
            return (boolean) irisInUse.invoke(irisApi);
        } catch (Throwable t) {
            return false;
        }
    }

    private BeamRenderType() {}
}
