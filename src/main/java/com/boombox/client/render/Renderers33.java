package com.boombox.client.render;

import com.boombox.BoomboxMod;
import com.boombox.bar.BarBlocks;
import com.boombox.bar.CupShelfBlockEntity;
import com.boombox.bar.FacingBlock;
import com.boombox.client.ClientBoomboxManager;
import com.boombox.entity.Ball;
import com.boombox.entity.Balloon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Desenho da 3.3: bola da várzea, balões de aniversário, prateleira de copos e pulseira de luz. */
public final class Renderers33 {
    private static final int WHITE = 15, LED = 9;

    private Renderers33() {}

    /** Cor da pulseira de luz agora: muda de cor e pisca com o grave da música mais perto. */
    public static int braceletColor(Vec3 at, float seed) {
        Minecraft mc = Minecraft.getInstance();
        float[] lv = new float[2];
        var st = ClientBoomboxManager.playingNear(mc, at, 24);
        float bass = 0;
        if (st != null) {
            ClientBoomboxManager.levelsOf(st, lv);
            bass = lv[0];
        }
        float t = (System.currentTimeMillis() % 100000) / 1000f;
        float hue = (t * (st != null ? 0.35f : 0.08f) + seed) % 1f;
        float v = st != null ? 0.7f + 0.3f * Math.min(1f, bass * 1.6f) : 0.85f;
        return PartyGeometry.hsv(hue, 0.9f, v);
    }

    /** Anel de luz em volta de um "braço" (em pixels do modelo). */
    static void band(Geo g, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        g.glow(color);
        g.box(x0, y0, z0, x1, y1, z1, LED);
        g.reset();
    }

    // ------------------------------------------------------------ pulseira no braço do jogador
    public static class BraceletLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
        public BraceletLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack ps, SubmitNodeCollector collector, int light, AvatarRenderState s, float yRot, float xRot) {
            boolean right = s.rightHandItemStack != null && s.rightHandItemStack.is(BarBlocks.GLOW_BRACELET);
            boolean left = s.leftHandItemStack != null && s.leftHandItemStack.is(BarBlocks.GLOW_BRACELET);
            if (!right && !left) return;
            int color = braceletColor(new Vec3(s.x, s.y, s.z), (s.id * 0.37f) % 1f);
            for (int side = 0; side < 2; side++) {
                if (side == 0 ? !right : !left) continue;
                ModelPart arm = side == 0 ? getParentModel().rightArm : getParentModel().leftArm;
                ps.pushPose();
                arm.translateAndRotate(ps);
                final float cx = side == 0 ? -1f : 1f; // centro do braço (pixels do modelo)
                collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                    Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                    band(g, cx - 2.45f, 7.2f, -2.45f, cx + 2.45f, 9.4f, 2.45f, color);
                });
                ps.popPose();
            }
        }
    }

    // ------------------------------------------------------------ bola de futebol
    public static class BallRenderer extends EntityRenderer<Ball, BallRenderer.State> {
        public static class State extends EntityRenderState {
            public Quaternionf rot = new Quaternionf();
        }

        private static final RenderType TYPE = RenderTypes.entityCutout(BoomboxMod.id("textures/entity/soccer_ball.png"));
        private static final Map<Integer, Quaternionf> ROT = new HashMap<>();
        private static final Map<Integer, Vec3> LAST = new HashMap<>();

        public BallRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            shadowRadius = 0.25f;
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(Ball e, State s, float pt) {
            super.extractRenderState(e, s, pt);
            Vec3 now = e.getPosition(pt);
            Quaternionf q = ROT.computeIfAbsent(e.getId(), k -> new Quaternionf());
            Vec3 last = LAST.put(e.getId(), now);
            if (last != null) {
                double mx = now.x - last.x, mz = now.z - last.z, d = Math.hypot(mx, mz);
                if (d > 1e-4 && d < 2) q.premul(new Quaternionf().rotationAxis((float) (d / 0.25), (float) (mz / d), 0f, (float) (-mx / d)));
            }
            s.rot = new Quaternionf(q);
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            int light = s.lightCoords;
            collector.submitCustomGeometry(ps, TYPE, (pose, vc) -> sphere(pose, vc, 0, 0.25f, 0, 0.25f, s.rot, 0, 0, 1, 1, light));
            super.submit(s, ps, collector, camera);
        }
    }

    /** Esfera UV com a textura inteira (u0..u1, v0..v1) em projeção "mapa-múndi". */
    static void sphere(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float r, Quaternionf q,
                       float u0, float v0, float u1, float v1, int light) {
        int LAT = 10, LON = 18;
        Vector3f[][] pts = new Vector3f[LAT + 1][LON + 1];
        for (int a = 0; a <= LAT; a++) {
            double phi = -Math.PI / 2 + Math.PI * a / LAT;
            for (int b = 0; b <= LON; b++) {
                double th = 2 * Math.PI * b / LON;
                pts[a][b] = q.transform(new Vector3f((float) (Math.cos(phi) * Math.cos(th)), (float) Math.sin(phi), (float) (Math.cos(phi) * Math.sin(th))));
            }
        }
        for (int a = 0; a < LAT; a++) {
            for (int b = 0; b < LON; b++) {
                float ua = u0 + (u1 - u0) * b / LON, ub = u0 + (u1 - u0) * (b + 1) / LON;
                float va = v0 + (v1 - v0) * (1f - (float) a / LAT), vb = v0 + (v1 - v0) * (1f - (float) (a + 1) / LAT);
                vtx(pose, vc, cx, cy, cz, r, pts[a][b], ua, va, light);
                vtx(pose, vc, cx, cy, cz, r, pts[a + 1][b], ua, vb, light);
                vtx(pose, vc, cx, cy, cz, r, pts[a + 1][b + 1], ub, vb, light);
                vtx(pose, vc, cx, cy, cz, r, pts[a][b + 1], ub, va, light);
            }
        }
    }

    private static void vtx(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float r, Vector3f n, float u, float v, int light) {
        vc.addVertex(pose, cx + n.x * r, cy + n.y * r, cz + n.z * r).setColor(-1).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
    }

    // ------------------------------------------------------------ balão de festa
    public static class BalloonRenderer extends EntityRenderer<Balloon, BalloonRenderer.State> {
        public static class State extends EntityRenderState {
            public int color;
            public float bob, sway;
        }

        public BalloonRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(Balloon e, State s, float pt) {
            super.extractRenderState(e, s, pt);
            s.color = e.color();
            float t = e.tickCount + pt + e.getId() * 7;
            s.bob = (float) Math.sin(t * 0.08f) * 1.2f;
            s.sway = (float) Math.sin(t * 0.05f) * 6f;
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            int light = s.lightCoords;
            ps.pushPose();
            ps.rotateDegrees(Axis.ZP, s.sway);
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                // barbante
                g.color = 0xFFEEEEEE;
                g.box(-0.15f, 0, -0.15f, 0.15f, 24 + s.bob, 0.15f, WHITE);
                // balão: "esfera" de camadas, mais largo em cima (forma de gota)
                g.color = s.color;
                float base = 24 + s.bob;
                float[] rs = {1.2f, 2.6f, 3.6f, 4.2f, 4.4f, 4.3f, 4.0f, 3.4f, 2.4f};
                for (int i = 0; i < rs.length; i++) {
                    float r = rs[i];
                    g.box(-r, base + i * 1.05f, -r, r, base + (i + 1) * 1.05f, r, WHITE);
                }
                g.box(-0.6f, base - 0.6f, -0.6f, 0.6f, base, 0.6f, WHITE); // nozinho
                // brilho
                g.glow(0xFFFFFFFF);
                g.box(-2.6f, base + 6.2f, -4.45f, -1.6f, base + 7.6f, -4.35f, WHITE);
                g.reset();
            });
            ps.popPose();
            super.submit(s, ps, collector, camera);
        }
    }

    // ------------------------------------------------------------ prateleira de copos
    public static class Shelf implements BlockEntityRenderer<CupShelfBlockEntity, Shelf.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public final List<ItemStackRenderState> cups = new ArrayList<>();
        }

        private final ItemModelResolver items;

        public Shelf(BlockEntityRendererProvider.Context ctx) {
            this.items = ctx.itemModelResolver();
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(CupShelfBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            var bs = be.getBlockState();
            s.facing = bs.hasProperty(FacingBlock.FACING) ? bs.getValue(FacingBlock.FACING) : Direction.NORTH;
            s.cups.clear();
            for (int i = 0; i < be.items.size(); i++) {
                ItemStack st = be.items.get(i);
                ItemStackRenderState r = new ItemStackRenderState();
                if (!st.isEmpty()) items.updateForTopItem(r, st, ItemDisplayContext.FIXED, be.getLevel(), null, i);
                s.cups.add(r);
            }
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            ps.translate(0.5f, 0f, 0.5f);
            ps.rotateDegrees(Axis.YP, 180f - s.facing.toYRot());
            ps.translate(-0.5f, 0f, -0.5f);
            // 3 em cima, 2 embaixo, de frente para quem olha
            float[][] spots = {{0.22f, 0.56f}, {0.5f, 0.56f}, {0.78f, 0.56f}, {0.33f, 0.07f}, {0.67f, 0.07f}};
            for (int i = 0; i < s.cups.size() && i < spots.length; i++) {
                ItemStackRenderState r = s.cups.get(i);
                if (r.isEmpty()) continue;
                ps.pushPose();
                float sc = 0.42f;
                ps.translate(spots[i][0], spots[i][1] + sc * 0.5f, 12f / 16f);
                ps.scale(sc, sc, sc);
                r.submit(ps, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                ps.popPose();
            }
            ps.popPose();
        }
    }
}
