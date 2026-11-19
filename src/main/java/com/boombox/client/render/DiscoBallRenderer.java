package com.boombox.client.render;

import com.boombox.DiscoBallBlockEntity;
import com.boombox.SpeakerSettings;
import com.boombox.client.ClientBoomboxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Globo espelhado: bola de espelhinhos girando, pendurada por uma corrente. Cada espelhinho joga um ponto
 * de luz onde o raio bate (parede, chão, teto), e os pontos andam junto com o giro.
 */
public class DiscoBallRenderer implements BlockEntityRenderer<DiscoBallBlockEntity, DiscoBallRenderer.State> {
    private static final int DOTS = 60;
    private static final double REACH = 20;
    /** Direções dos espelhinhos (espalhadas na esfera, mais para baixo e para os lados). */
    private static final double[][] DIRS = new double[DOTS][3];

    static {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < DOTS; i++) {
            double y = 0.3 - 1.3 * (i + 0.5) / DOTS;
            double r = Math.sqrt(Math.max(0, 1 - y * y));
            DIRS[i][0] = Math.cos(golden * i) * r;
            DIRS[i][1] = y;
            DIRS[i][2] = Math.sin(golden * i) * r;
        }
    }

    public static class State extends BlockEntityRenderState {
        public float angle;
        public float brightness;
        public int color = 0xFFFFFF;
        /** Pontos de luz: posição (relativa ao bloco) + normal da superfície. */
        public final float[][] dots = new float[DOTS][6];
        public final boolean[] hit = new boolean[DOTS];
    }

    private static final int METAL = 13, TRIM = 2;

    public DiscoBallRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void extractRenderState(DiscoBallBlockEntity be, State s, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, crumbling);
        var pos = be.getBlockPos();
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.42, pos.getZ() + 0.5);
        ClientBoomboxManager.State near = ClientBoomboxManager.playingNear(Minecraft.getInstance(), center, 28);
        double t = System.nanoTime() / 1e9;
        float[] lv = new float[2];
        ClientBoomboxManager.levelsOf(near, lv);
        s.angle = (float) ((t * (near != null ? 0.9 : 0.35)) % (Math.PI * 2));
        SpeakerSettings st = near != null ? near.settings : SpeakerSettings.DEFAULT;
        s.brightness = st.light() == SpeakerSettings.LIGHT_OFF ? 0.15f
                : near != null ? 0.55f + 0.45f * Math.min(1f, lv[0] * 1.2f) : 0.35f;
        s.color = st.light() == SpeakerSettings.LIGHT_FIXED ? st.colorRgb() : 0xFFF4E0;
        var level = be.getLevel();
        double c = Math.cos(s.angle), sn = Math.sin(s.angle);
        for (int i = 0; i < DOTS; i++) {
            double dx = DIRS[i][0] * c - DIRS[i][2] * sn, dy = DIRS[i][1], dz = DIRS[i][0] * sn + DIRS[i][2] * c;
            Vec3 from = center.add(dx * 0.3, dy * 0.3, dz * 0.3);
            Vec3 to = center.add(dx * REACH, dy * REACH, dz * REACH);
            s.hit[i] = false;
            if (level == null) continue;
            BlockHitResult h = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (h.getType() != HitResult.Type.BLOCK) continue;
            Direction n = h.getDirection();
            Vec3 p = h.getLocation().add(n.getStepX() * 0.01, n.getStepY() * 0.01, n.getStepZ() * 0.01);
            s.dots[i][0] = (float) (p.x - pos.getX());
            s.dots[i][1] = (float) (p.y - pos.getY());
            s.dots[i][2] = (float) (p.z - pos.getZ());
            s.dots[i][3] = n.getStepX();
            s.dots[i][4] = n.getStepY();
            s.dots[i][5] = n.getStepZ();
            s.hit[i] = true;
        }
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = s.lightCoords;
        ps.pushPose();
        ps.translate(0.5f, 0f, 0.5f);
        ps.rotate(com.mojang.math.Axis.YP, s.angle);
        ps.translate(-0.5f, 0f, -0.5f);
        collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
            Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
            g.box(7.6f, 11f, 7.6f, 8.4f, 16f, 8.4f, TRIM); // corrente
            // bola: anéis de "espelhinhos", mais largos no meio, brilhando sozinhos
            for (int ring = 0; ring < 7; ring++) {
                float y0 = 2.5f + ring * 1.2f, y1 = y0 + 1.2f;
                float mid = (y0 + y1) / 2 - 6.7f;
                float r = (float) Math.sqrt(Math.max(0.5, 17.6 - mid * mid));
                g.color = (ring % 2 == 0) ? 0xFFE8E8F0 : 0xFFB8BCC8;
                g.light = Geo.FULL_BRIGHT;
                g.box(8 - r, y0, 8 - r, 8 + r, y1, 8 + r, METAL);
            }
            g.reset();
        });
        ps.popPose();
        if (s.brightness <= 0.02f) return;
        // pontinhos de luz (somam luz, desenhados depois de tudo)
        collector.order(1000).submitCustomGeometry(ps, BeamRenderType.BEAM, (pose, vc) -> {
            for (int i = 0; i < DOTS; i++) {
                if (s.hit[i]) dot(vc, pose, s.dots[i], 0.13f, s.color, 0.9f * s.brightness);
            }
        });
    }

    /** Pontinho de luz achatado na superfície (normal d[3..5]), mais forte no meio. */
    private static void dot(VertexConsumer vc, PoseStack.Pose pose, float[] d, float r, int rgb, float a) {
        float ux, uy, uz, wx, wy, wz;
        if (Math.abs(d[4]) > 0.5f) { ux = 1; uy = 0; uz = 0; wx = 0; wy = 0; wz = 1; }
        else if (Math.abs(d[3]) > 0.5f) { ux = 0; uy = 1; uz = 0; wx = 0; wy = 0; wz = 1; }
        else { ux = 1; uy = 0; uz = 0; wx = 0; wy = 1; wz = 0; }
        int seg = 8;
        for (int k = 0; k < seg; k++) {
            double t0 = k * Math.PI * 2 / seg, t1 = (k + 1) * Math.PI * 2 / seg;
            float c0 = (float) Math.cos(t0) * r, s0 = (float) Math.sin(t0) * r, c1 = (float) Math.cos(t1) * r, s1 = (float) Math.sin(t1) * r;
            v(vc, pose, d[0], d[1], d[2], rgb, a);
            v(vc, pose, d[0] + ux * c0 + wx * s0, d[1] + uy * c0 + wy * s0, d[2] + uz * c0 + wz * s0, rgb, 0f);
            v(vc, pose, d[0] + ux * c1 + wx * s1, d[1] + uy * c1 + wy * s1, d[2] + uz * c1 + wz * s1, rgb, 0f);
            v(vc, pose, d[0], d[1], d[2], rgb, a);
        }
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int rgb, float a) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, a)) * 255);
        vc.addVertex(pose, x, y, z).setColor(alpha << 24 | (rgb & 0xFFFFFF));
    }
}
