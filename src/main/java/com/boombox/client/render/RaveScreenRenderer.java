package com.boombox.client.render;

import com.boombox.RaveScreenBlock;
import com.boombox.RaveScreenBlockEntity;
import com.boombox.SpeakerSettings;
import com.boombox.client.ClientBoomboxManager;
import com.boombox.client.VideoManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Telão: moldura preta com o clipe da música (textura que o vídeo atualiza a cada quadro), com brilho
 * próprio (aparece igual no escuro). Sem vídeo: equalizador colorido que pula com a música.
 */
public class RaveScreenRenderer implements BlockEntityRenderer<RaveScreenBlockEntity, RaveScreenRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float width = 8, height = 4.5f;
        public Identifier video;
        public boolean playing;
        public float bass, treble;
        public SpeakerSettings settings = SpeakerSettings.DEFAULT;
    }

    private static final int CAB = 0, LED = 9, WHITE = 15;
    /** Até onde o telão procura uma caixa tocando, e até onde você vê o vídeo. */
    private static final double FIND_RADIUS = 48, WATCH_DISTANCE = 96;
    private final float[] levels = new float[2];

    public RaveScreenRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true; // a tela é bem maior que o bloco
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void extractRenderState(RaveScreenBlockEntity be, State s, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, crumbling);
        var bs = be.getBlockState();
        s.facing = bs.hasProperty(RaveScreenBlock.FACING) ? bs.getValue(RaveScreenBlock.FACING) : Direction.NORTH;
        int size = bs.hasProperty(RaveScreenBlock.SIZE) ? bs.getValue(RaveScreenBlock.SIZE) : 2;
        s.width = RaveScreenBlock.width(size);
        s.height = s.width * 9f / 16f;
        Vec3 center = Vec3.atBottomCenterOf(be.getBlockPos()).add(0, s.height / 2, 0);
        ClientBoomboxManager.State near = ClientBoomboxManager.playingNear(Minecraft.getInstance(), center, FIND_RADIUS);
        s.playing = near != null;
        s.settings = near != null ? near.settings : SpeakerSettings.DEFAULT;
        ClientBoomboxManager.levelsOf(near, levels);
        s.bass = levels[0];
        s.treble = levels[1];
        boolean watching = cameraPos.distanceToSqr(center) < WATCH_DISTANCE * WATCH_DISTANCE;
        s.video = near != null && watching ? VideoManager.frameFor(near) : null;
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        ps.pushPose();
        ps.translate(0.5f, 0f, 0.5f);
        ps.rotateDegrees(Axis.YP, 180f - s.facing.toYRot());
        ps.translate(-0.5f, 0f, -0.5f);
        // em pixels (16 = 1 bloco); a tela fica encostada na parede de trás (z = 16), virada para -Z
        float hw = s.width * 8f, h = s.height * 16f;
        float x0 = 8 - hw, x1 = 8 + hw;
        int light = s.lightCoords;
        collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
            Geo c = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
            c.box(x0 - 1.2f, -0.6f, 15f, x1 + 1.2f, h + 0.6f, 16f, CAB);   // moldura
            c.color = 0xFF050507;
            c.light = Geo.FULL_BRIGHT;
            c.plate(x0, 0, x1, h, 14.85f, 15f, WHITE, WHITE);              // tela apagada (preta)
            c.reset();
            if (s.video == null && s.playing) equalizer(c, s, x0, x1, h);
        });
        if (s.video != null) {
            Identifier tex = s.video;
            collector.submitCustomGeometry(ps, RenderTypes.eyes(tex), (pose, vc) -> {
                float z = 14.8f / 16f, a = x0 / 16f, b = x1 / 16f, top = h / 16f;
                // quem olha a tela de frente vê +X à esquerda: u=0 em +X
                // ordem anti-horária vista de frente (senão o jogo descarta a face)
                vertex(vc, pose, a, 0, z, 1f, 1f);
                vertex(vc, pose, a, top, z, 1f, 0f);
                vertex(vc, pose, b, top, z, 0f, 0f);
                vertex(vc, pose, b, 0, z, 0f, 1f);
            });
        }
        ps.popPose();
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(Geo.FULL_BRIGHT).setNormal(pose, 0, 0, -1);
    }

    /** Barras que pulam com o grave (esquerda) e o agudo (direita), nas cores do modo de luz da caixa. */
    private static void equalizer(Geo c, State s, float x0, float x1, float h) {
        int bars = 24;
        double t = System.nanoTime() / 1e9;
        float gap = (x1 - x0) / bars;
        for (int i = 0; i < bars; i++) {
            float f = i / (float) (bars - 1);
            float level = (1 - f) * s.bass + f * s.treble;
            float wobble = (float) (0.55 + 0.45 * Math.sin(t * (3 + i * 0.37) + i * 1.7));
            float bh = Math.max(0.6f, h * 0.85f * Math.min(1f, level * (0.6f + 0.6f * wobble)));
            int color = switch (s.settings.light()) {
                case SpeakerSettings.LIGHT_FIXED -> 0xFF000000 | s.settings.colorRgb();
                case SpeakerSettings.LIGHT_OFF -> 0xFF404048;
                default -> PartyGeometry.hsv((float) (t * 0.05) + f * 0.8f, 0.9f, 1f);
            };
            c.glow(color);
            float bx = x1 - (i + 1) * gap; // da esquerda para a direita de quem olha
            c.plate(bx + gap * 0.15f, h * 0.06f, bx + gap * 0.85f, h * 0.06f + bh, 14.7f, 14.8f, LED, LED);
            c.reset();
        }
    }
}
