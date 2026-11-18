package com.boombox.client.render;

import com.boombox.RaveLedBlock;
import com.boombox.RaveLedBlockEntity;
import com.boombox.SpeakerSettings;
import com.boombox.client.ClientBoomboxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * LED de rave: painel 4x4 de luzes. Sozinho, as cores passam devagar como arco-íris; perto de uma
 * caixa tocando, pulsa no grave e segue o modo de luz da caixa (cor fixa, na batida, desligada).
 */
public class RaveLedRenderer implements BlockEntityRenderer<RaveLedBlockEntity, RaveLedRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float bass;
        public boolean playing;
        public SpeakerSettings settings = SpeakerSettings.DEFAULT;
        public float phase;
    }

    private static final int CAB = 0, LED = 9, WHITE = 15;
    private final float[] levels = new float[2];

    public RaveLedRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RaveLedBlockEntity be, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumbling);
        var bs = be.getBlockState();
        state.facing = bs.hasProperty(RaveLedBlock.FACING) ? bs.getValue(RaveLedBlock.FACING) : Direction.NORTH;
        var pos = be.getBlockPos();
        state.phase = (pos.getX() * 0.13f + pos.getY() * 0.21f + pos.getZ() * 0.17f) % 1f; // cada LED um pouco diferente
        ClientBoomboxManager.State near = ClientBoomboxManager.playingNear(Minecraft.getInstance(), Vec3.atCenterOf(pos), 24);
        state.playing = near != null;
        state.settings = near != null ? near.settings : SpeakerSettings.DEFAULT;
        if (near != null) {
            ClientBoomboxManager.levelsOf(near, levels);
            state.bass = levels[0];
        } else {
            state.bass = 0;
        }
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        ps.pushPose();
        ps.translate(0.5f, 0.5f, 0.5f);
        switch (s.facing) {
            case UP -> ps.rotateDegrees(Axis.XP, 90f);
            case DOWN -> ps.rotateDegrees(Axis.XP, -90f);
            default -> ps.rotateDegrees(Axis.YP, 180f - s.facing.toYRot());
        }
        ps.translate(-0.5f, -0.5f, -0.5f);
        int light = s.lightCoords;
        collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> draw(s, new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8)));
        ps.popPose();
    }

    /** Painel encostado em z=16 (a parede), com as luzes viradas para -Z. */
    private static void draw(State s, Geo c) {
        c.box(1, 1, 15, 15, 15, 16, CAB);
        double t = System.nanoTime() / 1e9;
        float kick = s.playing ? Math.min(1f, s.bass * 1.3f) : 0f;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                float hue = (float) (t * (s.playing ? 0.25 : 0.08)) + s.phase + (i + j) * 0.07f;
                float v = s.playing ? 0.35f + 0.65f * kick : 0.55f;
                switch (s.settings.light()) {
                    case SpeakerSettings.LIGHT_FIXED -> c.glow(scale(s.settings.colorRgb(), v));
                    case SpeakerSettings.LIGHT_BEAT -> c.glow(PartyGeometry.hsv(hue, 1f,
                            s.playing ? 0.05f + 0.95f * Math.max(0f, Math.min(1f, (s.bass - 0.42f) / 0.3f)) : 0.55f));
                    case SpeakerSettings.LIGHT_OFF -> c.color = 0xFF2A2A2E;
                    default -> c.glow(PartyGeometry.hsv(hue, 1f, v));
                }
                float x = 2.2f + i * 3f, y = 2.2f + j * 3f;
                c.plate(x, y, x + 2.6f, y + 2.6f, 14.6f, 15f, LED, LED);
                c.reset();
            }
        }
        // flash branco no centro nas batidas fortes
        if (s.playing && s.bass > 0.75f && s.settings.light() != SpeakerSettings.LIGHT_OFF) {
            c.glow(0xFFFFFFFF);
            c.plate(6.5f, 6.5f, 9.5f, 9.5f, 14.5f, 14.6f, WHITE, WHITE);
            c.reset();
        }
    }

    private static int scale(int rgb, float v) {
        int r = Math.round(((rgb >> 16) & 255) * v), g = Math.round(((rgb >> 8) & 255) * v), b = Math.round((rgb & 255) * v);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
