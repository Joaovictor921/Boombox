package com.boombox.client.render;

import com.boombox.BoomboxBlock;
import com.boombox.BoomboxBlockEntity;
import com.boombox.SpeakerSettings;
import com.boombox.SpeakerType;
import com.boombox.client.ClientBoomboxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Desenha a boombox / caixa de festa no chão, com os alto-falantes acompanhando a música. */
public class BoomboxBlockRenderer implements BlockEntityRenderer<BoomboxBlockEntity, BoomboxBlockRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public SpeakerType type = SpeakerType.BOOMBOX;
        public float bass;
        public float treble;
        public SpeakerSettings settings = SpeakerSettings.DEFAULT;
    }

    private final float[] levels = new float[2];

    public BoomboxBlockRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    /** As caixas grandes passam da altura de um bloco: não some quando só a parte de baixo sai da tela. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void extractRenderState(BoomboxBlockEntity be, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumbling);
        var bs = be.getBlockState();
        state.facing = bs.hasProperty(BoomboxBlock.FACING) ? bs.getValue(BoomboxBlock.FACING) : Direction.NORTH;
        state.type = bs.getBlock() instanceof BoomboxBlock b ? b.type() : SpeakerType.BOOMBOX;
        state.settings = be.settings();
        if (be.getLevel() != null) {
            ClientBoomboxManager.levelsForBlock(be.getLevel().dimension().identifier().toString(), be.getBlockPos(), levels);
            state.bass = levels[0];
            state.treble = levels[1];
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0f, 0.5f);
        poseStack.rotateDegrees(Axis.YP, 180f - state.facing.toYRot());
        poseStack.translate(-0.5f, 0f, -0.5f);
        SpeakerRendering.submit(state.type, poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.bass, state.treble, state.settings);
        poseStack.popPose();
    }
}
