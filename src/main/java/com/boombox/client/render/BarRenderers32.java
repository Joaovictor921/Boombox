package com.boombox.client.render;

import com.boombox.bar.BubbleMachineBlockEntity;
import com.boombox.bar.FacingBlock;
import com.boombox.bar.GrillBlockEntity;
import com.boombox.bar.SinucaBlockEntity;
import com.boombox.client.BarClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Desenho da 3.2: sinuca 3D, churrasqueira com a carne na grelha e bolhas de sabão. */
public final class BarRenderers32 {
    private static final int WHITE = 15, LED = 9;

    private BarRenderers32() {}

    private static void face(PoseStack ps, Direction facing) {
        ps.translate(0.5f, 0f, 0.5f);
        ps.rotateDegrees(Axis.YP, 180f - facing.toYRot());
        ps.translate(-0.5f, 0f, -0.5f);
    }

    // ------------------------------------------------------------ sinuca 3x2
    public static class Sinuca implements BlockEntityRenderer<SinucaBlockEntity, Sinuca.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public final double[] x = new double[SinucaBlockEntity.N], z = new double[SinucaBlockEntity.N];
            public final boolean[] in = new boolean[SinucaBlockEntity.N];
            /** Mirando nesta mesa: ângulo (coordenadas da mesa) e força; NaN = ninguém mirando. */
            public double aim = Double.NaN;
            public float power;
            public final Quaternionf[] rot = new Quaternionf[SinucaBlockEntity.N];
            /** Bola fantasma: onde a branca vai encostar na primeira bola do caminho. */
            public boolean ghost;
            public double ghostT;
            public int ghostBall;
        }

        /** Giro de cada bola (só no cliente), por mesa. */
        private static final java.util.Map<Long, Quaternionf[]> ROT = new java.util.HashMap<>();
        private static final java.util.Map<Long, double[]> LAST = new java.util.HashMap<>();

        /** Cores das bolas: 1-7 lisas, 8 preta, 9-15 listradas (mesma cor da lisa de número n-8). */
        private static final int[] COLORS = {0xFFF4F4F0, 0xFFE8C020, 0xFF2050D0, 0xFFD02020, 0xFF7A2BB0, 0xFFE07020, 0xFF20A040, 0xFF8A2020, 0xFF101010};

        public Sinuca(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true;
        }

        @Override
        public void extractRenderState(SinucaBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            var bs = be.getBlockState();
            s.facing = bs.hasProperty(FacingBlock.FACING) ? bs.getValue(FacingBlock.FACING) : Direction.NORTH;
            int n = SinucaBlockEntity.N;
            for (int i = 0; i < n; i++) {
                s.x[i] = be.px[i] + (be.x[i] - be.px[i]) * pt;
                s.z[i] = be.pz[i] + (be.z[i] - be.pz[i]) * pt;
            }
            System.arraycopy(be.in, 0, s.in, 0, n);
            // rolamento: gira cada bola em volta do eixo perpendicular ao caminho (ângulo = distância / raio)
            long key = be.getBlockPos().asLong();
            Quaternionf[] rot = ROT.computeIfAbsent(key, k -> {
                Quaternionf[] a = new Quaternionf[n];
                java.util.Random rr = new java.util.Random(k);
                for (int i = 0; i < n; i++) a[i] = new Quaternionf().rotateXYZ(rr.nextFloat() * 6.3f, rr.nextFloat() * 6.3f, rr.nextFloat() * 6.3f);
                return a;
            });
            double[] last = LAST.computeIfAbsent(key, k -> {
                double[] a = new double[n * 2];
                for (int i = 0; i < n; i++) { a[i * 2] = s.x[i]; a[i * 2 + 1] = s.z[i]; }
                return a;
            });
            for (int i = 0; i < n; i++) {
                double mx = s.x[i] - last[i * 2], mz = s.z[i] - last[i * 2 + 1], d = Math.hypot(mx, mz);
                if (d > 1e-5 && d < 0.5) {
                    rot[i].premul(new Quaternionf().rotationAxis((float) (d / SinucaBlockEntity.R), (float) (mz / d), 0f, (float) (-mx / d)));
                }
                last[i * 2] = s.x[i];
                last[i * 2 + 1] = s.z[i];
                s.rot[i] = new Quaternionf(rot[i]);
            }
            s.aim = com.boombox.client.PoolClient.aimAngle(be.getBlockPos());
            s.power = com.boombox.client.PoolClient.aimPower();
            s.ghost = false;
            if (!Double.isNaN(s.aim)) {
                double dx = Math.cos(s.aim), dz = Math.sin(s.aim), best = Double.MAX_VALUE, R2 = 2 * SinucaBlockEntity.R;
                for (int i = 1; i < n; i++) {
                    if (s.in[i]) continue;
                    double ox = s.x[i] - s.x[0], oz = s.z[i] - s.z[0];
                    double t = ox * dx + oz * dz, perp2 = ox * ox + oz * oz - t * t;
                    if (t <= 0 || perp2 > R2 * R2) continue;
                    double hit = t - Math.sqrt(R2 * R2 - perp2);
                    if (hit < best) { best = hit; s.ghostBall = i; }
                }
                if (best < Double.MAX_VALUE) { s.ghost = true; s.ghostT = best; }
            }
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            face(ps, s.facing);
            int light = s.lightCoords;
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                float L = 48, W = 32, top = 13.2f;
                // pés torneados e saia de madeira escura
                g.color = 0xFF4A2A14;
                for (float[] p : new float[][]{{2, 2}, {L - 5, 2}, {2, W - 5}, {L - 5, W - 5}}) {
                    g.box(p[0], 0, p[1], p[0] + 3, 2, p[1] + 3, WHITE);
                    g.box(p[0] + 0.6f, 2, p[1] + 0.6f, p[0] + 2.4f, 9, p[1] + 2.4f, WHITE);
                }
                g.color = 0xFF5A3418;
                g.box(1, 9, 1, L - 1, 11.5f, W - 1, WHITE);
                // tabelas (madeira) com borracha verde por dentro
                g.color = 0xFF6A3E1E;
                g.box(0, 11.5f, 0, L, top + 1.4f, 2.4f, WHITE);
                g.box(0, 11.5f, W - 2.4f, L, top + 1.4f, W, WHITE);
                g.box(0, 11.5f, 2.4f, 2.4f, top + 1.4f, W - 2.4f, WHITE);
                g.box(L - 2.4f, 11.5f, 2.4f, L, top + 1.4f, W - 2.4f, WHITE);
                g.color = 0xFF14602C;
                g.box(2.4f, top, 2.4f, L - 2.4f, top + 0.7f, 2.9f, WHITE);
                g.box(2.4f, top, W - 2.9f, L - 2.4f, top + 0.7f, W - 2.4f, WHITE);
                g.box(2.4f, top, 2.9f, 2.9f, top + 0.7f, W - 2.9f, WHITE);
                g.box(L - 2.9f, top, 2.9f, L - 2.4f, top + 0.7f, W - 2.9f, WHITE);
                // feltro
                g.color = 0xFF1A7A3A;
                g.box(2.4f, 11.5f, 2.4f, L - 2.4f, top, W - 2.4f, WHITE);
                // losangos (marcas) nas tabelas
                g.color = 0xFFE8E0C8;
                for (int i = 1; i < 8; i++) {
                    if (i == 4) continue;
                    float mx = i * L / 8f;
                    g.box(mx - 0.25f, top + 1.41f, 0.9f, mx + 0.25f, top + 1.45f, 1.4f, WHITE);
                    g.box(mx - 0.25f, top + 1.41f, W - 1.4f, mx + 0.25f, top + 1.45f, W - 0.9f, WHITE);
                }
                // caçapas
                g.color = 0xFF050505;
                for (double[] p : SinucaBlockEntity.POCKETS) {
                    float px = (float) (p[0] * 16), pz = (float) (p[1] * 16), r = (float) (SinucaBlockEntity.POCKET * 16);
                    g.box(px - r, top - 0.05f, pz - r, px + r, top + 0.02f, pz + r, WHITE);
                }
                // sombrinha embaixo de cada bola
                float r = (float) (SinucaBlockEntity.R * 16);
                g.color = 0xFF0C3A1A;
                for (int i = 0; i < SinucaBlockEntity.N; i++) {
                    if (s.in[i]) continue;
                    float bx = (float) (s.x[i] * 16), bz = (float) (s.z[i] * 16);
                    g.box(bx - r * 0.8f, top + 0.01f, bz - r * 0.8f, bx + r * 0.8f, top + 0.03f, bz + r * 0.8f, WHITE);
                }
                // taco e mira quando é a sua vez nesta mesa
                if (!Double.isNaN(s.aim) && !s.in[0]) {
                    float cx = (float) (s.x[0] * 16), cz = (float) (s.z[0] * 16);
                    float dx = (float) Math.cos(s.aim), dz = (float) Math.sin(s.aim);
                    float back = r + 0.4f + s.power * 6f;
                    // taco: ponteira azul, madeira clara, cabo escuro, subindo para trás
                    for (int k = 0; k < 64; k++) {
                        float d = back + k * 0.5f;
                        float hx = cx - dx * d, hz = cz - dz * d, hy = top + r + 0.25f + k * 0.045f;
                        float w = 0.16f + k * 0.0035f;
                        g.color = k < 1 ? 0xFF2050D0 : k < 3 ? 0xFFF0EAD8 : k > 44 ? 0xFF2A1A10 : 0xFFD8B070;
                        g.box(hx - w, hy - w, hz - w, hx + w, hy + w, hz + w, WHITE);
                    }
                    // linha até a primeira bola e bola fantasma onde a branca vai encostar
                    g.glow(0xFFFFFFFF);
                    float end = s.ghost ? (float) s.ghostT * 16 : 40;
                    for (float k = r + 0.6f; k < end; k += 1.1f) {
                        float hx = cx + dx * k, hz = cz + dz * k;
                        g.box(hx - 0.07f, top + 0.05f, hz - 0.07f, hx + 0.07f, top + 0.1f, hz + 0.07f, LED);
                    }
                    if (s.ghost) {
                        float gx = cx + dx * end, gz = cz + dz * end;
                        for (int k = 0; k < 16; k++) {
                            double t = k * Math.PI / 8;
                            float ox = (float) Math.cos(t) * r, oz = (float) Math.sin(t) * r;
                            g.box(gx + ox - 0.08f, top + r - 0.06f, gz + oz - 0.08f, gx + ox + 0.08f, top + r + 0.06f, gz + oz + 0.08f, LED);
                        }
                        // para onde a bola atingida vai
                        g.glow(0xFFFFE070);
                        float ox = (float) (s.x[s.ghostBall] * 16), oz = (float) (s.z[s.ghostBall] * 16);
                        float ex = ox - gx, ez = oz - gz, el = (float) Math.sqrt(ex * ex + ez * ez);
                        if (el > 1e-3) {
                            ex /= el; ez /= el;
                            for (float k = r + 0.4f; k < r + 7f; k += 0.9f) {
                                g.box(ox + ex * k - 0.06f, top + 0.05f, oz + ez * k - 0.06f, ox + ex * k + 0.06f, top + 0.1f, oz + ez * k + 0.06f, LED);
                            }
                        }
                    }
                }
                g.reset();
            });
            // bolas: esferas com a textura de cada uma (número, faixa), girando conforme rolam
            float rr = (float) SinucaBlockEntity.R, topB = 13.2f / 16f;
            collector.submitCustomGeometry(ps, BALLS, (pose, vc) -> {
                for (int i = 0; i < SinucaBlockEntity.N; i++) {
                    if (s.in[i]) continue;
                    sphere(pose, vc, (float) s.x[i], topB + rr, (float) s.z[i], rr, s.rot[i], i, light);
                }
            });
            ps.popPose();
        }

        private static final net.minecraft.client.renderer.rendertype.RenderType BALLS =
                net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(com.boombox.BoomboxMod.id("textures/entity/pool_balls.png"));

        /** Esfera UV: 8 faixas x 16 gomos; textura 4x4 bolas, cada uma em projeção "mapa-múndi". */
        private static void sphere(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float r, Quaternionf q, int ball, int light) {
            int LAT = 8, LON = 16;
            float u0 = (ball % 4) * 0.25f, v0 = (ball / 4) * 0.25f;
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
                    float ua = u0 + 0.25f * b / LON, ub = u0 + 0.25f * (b + 1) / LON;
                    float va = v0 + 0.25f * (1f - (float) a / LAT), vb = v0 + 0.25f * (1f - (float) (a + 1) / LAT);
                    ballVertex(pose, vc, cx, cy, cz, r, pts[a][b], ua, va, light);
                    ballVertex(pose, vc, cx, cy, cz, r, pts[a + 1][b], ua, vb, light);
                    ballVertex(pose, vc, cx, cy, cz, r, pts[a + 1][b + 1], ub, vb, light);
                    ballVertex(pose, vc, cx, cy, cz, r, pts[a][b + 1], ub, va, light);
                }
            }
        }

        private static void ballVertex(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float r, Vector3f n, float u, float v, int light) {
            vc.addVertex(pose, cx + n.x * r, cy + n.y * r, cz + n.z * r).setColor(-1).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n.x, n.y, n.z);
        }
    }

    // ------------------------------------------------------------ churrasqueira com a carne na grelha
    public static class Grill implements BlockEntityRenderer<GrillBlockEntity, Grill.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public final List<ItemStackRenderState> items = new ArrayList<>();
            public boolean cooking;
        }

        private final ItemModelResolver items;

        public Grill(BlockEntityRendererProvider.Context ctx) {
            this.items = ctx.itemModelResolver();
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(GrillBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            var bs = be.getBlockState();
            s.facing = bs.hasProperty(FacingBlock.FACING) ? bs.getValue(FacingBlock.FACING) : Direction.NORTH;
            s.cooking = be.isCooking();
            s.items.clear();
            for (int i = 0; i < 4; i++) {
                ItemStackRenderState r = new ItemStackRenderState();
                var stack = be.items.get(i);
                if (!stack.isEmpty()) {
                    // pronto: mostra o prato assado; cru: mostra a carne crua
                    var show = be.progress[i] >= GrillBlockEntity.COOK_TICKS && GrillBlockEntity.cooked(stack) != null
                            ? new net.minecraft.world.item.ItemStack(GrillBlockEntity.cooked(stack)) : stack;
                    items.updateForTopItem(r, show, ItemDisplayContext.GROUND, be.getLevel(), null, i);
                }
                s.items.add(r);
            }
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            face(ps, s.facing);
            int light = s.lightCoords;
            double t = System.nanoTime() / 1e9;
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                g.color = 0xFF2A2A2E; // caixa de tijolo/ferro
                g.box(1, 7, 3, 15, 11, 13, WHITE);
                g.color = 0xFF3A3A40;
                for (float[] p : new float[][]{{1.5f, 3.5f}, {13.5f, 3.5f}, {1.5f, 11.5f}, {13.5f, 11.5f}}) g.box(p[0], 0, p[1], p[0] + 1, 7, p[1] + 1, WHITE);
                // carvão em brasa
                for (int i = 0; i < 12; i++) {
                    float bx = 2.5f + (i % 6) * 2, bz = 4.5f + (i / 6) * 4;
                    float hot = (float) (0.6 + 0.4 * Math.sin(t * 3 + i * 1.7));
                    g.glow(s.cooking ? PartyGeometry.hsv(0.04f, 0.95f, hot) : 0xFF202020);
                    g.box(bx, 10.6f, bz, bx + 1.6f, 11.2f, bz + 1.6f, WHITE);
                    g.reset();
                }
                // grelha
                g.color = 0xFF9A9AA0;
                for (int i = 0; i < 7; i++) g.box(1.5f + i * 2, 11.8f, 3.2f, 1.9f + i * 2, 12f, 12.8f, WHITE);
                g.reset();
            });
            float[][] spots = {{0.3f, 0.35f}, {0.7f, 0.35f}, {0.3f, 0.65f}, {0.7f, 0.65f}};
            for (int i = 0; i < s.items.size(); i++) {
                var r = s.items.get(i);
                if (r.isEmpty()) continue;
                ps.pushPose();
                ps.translate(spots[i][0], 12.2f / 16f, spots[i][1]);
                ps.scale(0.6f, 0.6f, 0.6f);
                r.submit(ps, collector, light, OverlayTexture.NO_OVERLAY, 0);
                ps.popPose();
            }
            ps.popPose();
        }
    }

    // ------------------------------------------------------------ máquina de bolhas e as bolhas no ar
    public static class Bubbles implements BlockEntityRenderer<BubbleMachineBlockEntity, Bubbles.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public final List<float[]> bubbles = new ArrayList<>();
        }

        public Bubbles(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true;
        }

        @Override
        public void extractRenderState(BubbleMachineBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            var bs = be.getBlockState();
            s.facing = bs.hasProperty(FacingBlock.FACING) ? bs.getValue(FacingBlock.FACING) : Direction.NORTH;
            s.bubbles.clear();
            for (var b : be.bubbles) {
                s.bubbles.add(new float[]{(float) (b.x + b.vx * pt), (float) (b.y + b.vy * pt), (float) (b.z + b.vz * pt), b.size, b.hue});
            }
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            face(ps, s.facing);
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, s.lightCoords, OverlayTexture.NO_OVERLAY, 8);
                g.color = 0xFF3080E0; // corpo azul da máquina
                g.box(2, 0, 2, 14, 8, 14, WHITE);
                g.color = 0xFFE8E8F0;
                g.box(3, 8, 3, 13, 10, 13, WHITE);
                // roda de varetas na frente
                g.color = 0xFFF0C030;
                g.box(5, 3, 1.5f, 11, 9, 2, WHITE);
                g.reset();
            });
            ps.popPose();
            // bolhas: anel transparente furta-cor, virado para a câmera (coordenadas do bloco, sem girar)
            Quaternionf cam = camera.orientation == null ? new Quaternionf() : new Quaternionf(camera.orientation);
            Vector3f right = cam.transform(new Vector3f(1, 0, 0)), up = cam.transform(new Vector3f(0, 1, 0));
            collector.order(1000).submitCustomGeometry(ps, BeamRenderType.beam(), (pose, vc) -> {
                for (float[] b : s.bubbles) bubble(vc, pose, b[0], b[1], b[2], b[3], b[4], right, up);
            });
        }

        private static void bubble(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz, float r, float hue, Vector3f right, Vector3f up) {
            int seg = 14;
            for (int k = 0; k < seg; k++) {
                double t0 = k * Math.PI * 2 / seg, t1 = (k + 1) * Math.PI * 2 / seg;
                int rgb = PartyGeometry.hsv(hue + k / (float) seg * 0.6f, 0.5f, 1f) & 0xFFFFFF;
                float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
                float ri = r * 0.82f;
                // anel: borda mais forte (como bolha de sabão), meio quase transparente
                v(vc, pose, cx + (right.x * c0 + up.x * s0) * ri, cy + (right.y * c0 + up.y * s0) * ri, cz + (right.z * c0 + up.z * s0) * ri, rgb, 0.05f);
                v(vc, pose, cx + (right.x * c0 + up.x * s0) * r, cy + (right.y * c0 + up.y * s0) * r, cz + (right.z * c0 + up.z * s0) * r, rgb, 0.45f);
                v(vc, pose, cx + (right.x * c1 + up.x * s1) * r, cy + (right.y * c1 + up.y * s1) * r, cz + (right.z * c1 + up.z * s1) * r, rgb, 0.45f);
                v(vc, pose, cx + (right.x * c1 + up.x * s1) * ri, cy + (right.y * c1 + up.y * s1) * ri, cz + (right.z * c1 + up.z * s1) * ri, rgb, 0.05f);
            }
            // brilhinho
            float hx = cx + (right.x * -0.4f + up.x * 0.4f) * r, hy = cy + (right.y * -0.4f + up.y * 0.4f) * r, hz = cz + (right.z * -0.4f + up.z * 0.4f) * r;
            float q = r * 0.15f;
            v(vc, pose, hx - right.x * q, hy - right.y * q, hz - right.z * q, 0xFFFFFF, 0.6f);
            v(vc, pose, hx + up.x * q, hy + up.y * q, hz + up.z * q, 0xFFFFFF, 0.6f);
            v(vc, pose, hx + right.x * q, hy + right.y * q, hz + right.z * q, 0xFFFFFF, 0.6f);
            v(vc, pose, hx - up.x * q, hy - up.y * q, hz - up.z * q, 0xFFFFFF, 0.6f);
        }

        private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int rgb, float a) {
            vc.addVertex(pose, x, y, z).setColor(Math.round(a * 255) << 24 | rgb);
        }
    }
}
