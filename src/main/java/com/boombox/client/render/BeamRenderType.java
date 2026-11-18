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

    private BeamRenderType() {}
}
