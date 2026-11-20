package com.boombox.client.render;

import com.boombox.BoomboxMod;
import com.boombox.bar.FacingBlock;
import com.boombox.bar.NeonSignBlockEntity;
import com.boombox.entity.PoolFloat;
import com.boombox.entity.Volleyball;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/** Desenho da 3.4: neon personalizado, bola de vôlei, boia de flamingo e as fantasias do baile. */
public final class Renderers34 {
    private static final int WHITE = 15, LED = 9;

    private Renderers34() {}

    // ------------------------------------------------------------ neon personalizado
    public static class Neon implements BlockEntityRenderer<NeonSignBlockEntity, Neon.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public String text = "";
            public int color;
            public boolean on = true;
        }

        public Neon(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true;
        }

        @Override
        public void extractRenderState(NeonSignBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            var bs = be.getBlockState();
            s.facing = bs.hasProperty(FacingBlock.FACING) ? bs.getValue(FacingBlock.FACING) : Direction.NORTH;
            s.text = be.text;
            long ms = System.currentTimeMillis();
            if (be.color >= NeonSignBlockEntity.RAINBOW) {
                s.color = PartyGeometry.hsv((ms % 6000) / 6000f, 0.85f, 1f) & 0xFFFFFF;
            } else {
                s.color = NeonSignBlockEntity.COLORS[be.color];
            }
            // piscando: apaga de vez em quando, como letreiro de bar com mau contato
            s.on = !be.blink || ((ms / 120) % 23 != 0 && (ms / 700) % 2 == 0 || (ms / 700) % 7 == 3);
            if (be.blink && (ms / 700) % 2 == 1 && (ms / 700) % 7 != 3) s.on = (ms / 90) % 9 != 0;
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            if (s.text.isEmpty()) return;
            Font font = Minecraft.getInstance().font;
            int w = Math.max(1, font.width(s.text));
            // largura do letreiro: até 3 blocos (letras maiores para textos curtos)
            float scale = Math.min(1f / 28f, 2.8f / w);
            ps.pushPose();
            ps.translate(0.5f, 0f, 0.5f);
            ps.rotateDegrees(Axis.YP, 180f - s.facing.toYRot());
            ps.translate(-0.5f, 0f, -0.5f);
            // tubo apagado (cinza) e aceso (cor forte): o contorno brilhante faz o efeito de neon
            int core = s.on ? 0xFF000000 | mix(s.color, 0xFFFFFF, 0.55f) : 0xFF3A3A44;
            int glow = s.on ? 0xFF000000 | s.color : 0xFF22222A;
            int light = s.on ? 0xF000F0 : s.lightCoords;
            ps.translate(0.5f, 0.5f + 4f * scale, 13.6f / 16f);
            ps.rotateDegrees(Axis.YP, 180f);
            ps.scale(scale, -scale, scale);
            var seq = Component.literal(s.text).getVisualOrderText();
            collector.submitText(ps, -w / 2f, -4f, seq, false, Font.DisplayMode.POLYGON_OFFSET, light, core, 0, glow);
            ps.popPose();
        }

        private static int mix(int a, int b, float t) {
            int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
            int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
            int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
            return r << 16 | g << 8 | bl;
        }
    }

    // ------------------------------------------------------------ bola de vôlei
    public static class VolleyballRenderer extends EntityRenderer<Volleyball, VolleyballRenderer.State> {
        public static class State extends EntityRenderState {
            public Quaternionf rot = new Quaternionf();
        }

        private static final RenderType TYPE = RenderTypes.entityCutout(BoomboxMod.id("textures/entity/volleyball.png"));
        private static final Map<Integer, Quaternionf> ROT = new HashMap<>();
        private static final Map<Integer, Vec3> LAST = new HashMap<>();

        public VolleyballRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            shadowRadius = 0.25f;
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(Volleyball e, State s, float pt) {
            super.extractRenderState(e, s, pt);
            Vec3 now = e.getPosition(pt);
            Quaternionf q = ROT.computeIfAbsent(e.getId(), k -> new Quaternionf());
            Vec3 last = LAST.put(e.getId(), now);
            if (last != null) {
                Vec3 d = now.subtract(last);
                double len = d.length();
                if (len > 1e-4 && len < 3) q.premul(new Quaternionf().rotationAxis((float) (len / 0.25), (float) (d.z / len), 0.2f, (float) (-d.x / len)));
            }
            s.rot = new Quaternionf(q);
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            int light = s.lightCoords;
            collector.submitCustomGeometry(ps, TYPE, (pose, vc) -> Renderers33.sphere(pose, vc, 0, 0.25f, 0, 0.25f, s.rot, 0, 0, 1, 1, light));
            super.submit(s, ps, collector, camera);
        }
    }

    // ------------------------------------------------------------ boia de flamingo
    public static class FloatRenderer extends EntityRenderer<PoolFloat, FloatRenderer.State> {
        public static class State extends EntityRenderState {
            public float yaw;
        }

        public FloatRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            shadowRadius = 0.6f;
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(PoolFloat e, State s, float pt) {
            super.extractRenderState(e, s, pt);
            s.yaw = e.getYRot(pt);
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            int light = s.lightCoords;
            ps.pushPose();
            ps.rotateDegrees(Axis.YP, -s.yaw);
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                // anel (boia) feito de gomos
                int n = 16;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    float cx = (float) (Math.cos(a) * 8.5), cz = (float) (Math.sin(a) * 8.5);
                    g.color = i % 2 == 0 ? 0xFFFF7BAC : 0xFFFF94BE;
                    g.box(cx - 2.4f, 0, cz - 2.4f, cx + 2.4f, 4.6f, cz + 2.4f, WHITE);
                }
                // pescoço em "S" e cabeça (para a frente, -Z)
                g.color = 0xFFFF7BAC;
                g.box(-1.5f, 3, -12, 1.5f, 10, -9, WHITE);
                g.box(-1.4f, 9, -13.5f, 1.4f, 15, -10.5f, WHITE);
                g.box(-1.3f, 14, -11.5f, 1.3f, 18, -8.5f, WHITE);
                g.box(-1.8f, 17, -12.5f, 1.8f, 20.5f, -8.8f, WHITE);
                // bico (branco com ponta preta) e olhinhos
                g.color = 0xFFF2F2F2;
                g.box(-0.8f, 17.6f, -14.6f, 0.8f, 19.2f, -12.5f, WHITE);
                g.color = 0xFF151515;
                g.box(-0.7f, 17.0f, -15.4f, 0.7f, 18.4f, -14.4f, WHITE);
                g.box(-1.85f, 19.0f, -11.2f, -1.75f, 19.8f, -10.4f, WHITE);
                g.box(1.75f, 19.0f, -11.2f, 1.85f, 19.8f, -10.4f, WHITE);
                // rabinho
                g.color = 0xFFFF6FA0;
                g.box(-2.5f, 3.5f, 9, 2.5f, 7.5f, 12.5f, WHITE);
                g.reset();
            });
            ps.popPose();
            super.submit(s, ps, collector, camera);
        }
    }

    // ------------------------------------------------------------ fantasias do baile
    /**
     * Desenha a fantasia na cabeça (já na posição da cabeça do modelo). top = altura (em pixels, para cima é
     * negativo) do topo da cabeça: -10 no aldeão, -8 no DJ Leo.
     */
    public static void costume(PoseStack ps, SubmitNodeCollector collector, int light, int kind, float top) {
        collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
            Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
            float t = top;
            switch (kind) {
                case 0 -> { // pirata: chapéu de três pontas, caveirinha e tapa-olho
                    g.color = 0xFF1A1A1A;
                    g.box(-6, t - 0.6f, -6, 6, t, 6, WHITE);
                    g.box(-4.5f, t - 3.4f, -4.5f, 4.5f, t - 0.6f, 4.5f, WHITE);
                    g.box(-6.5f, t - 2f, -1, -5.5f, t - 0.6f, 1, WHITE);
                    g.box(5.5f, t - 2f, -1, 6.5f, t - 0.6f, 1, WHITE);
                    g.color = 0xFFF0F0F0;
                    g.box(-1, t - 2.8f, -4.62f, 1, t - 1.2f, -4.52f, WHITE);
                    g.color = 0xFF101010;
                    g.box(-4.25f, t + 3.3f, -4.25f, 4.25f, t + 3.8f, 4.25f, WHITE);
                    g.box(0.8f, t + 3.0f, -4.35f, 3.0f, t + 5.2f, -4.25f, WHITE);
                }
                case 1 -> { // bruxa: chapéu pontudo roxo de aba larga
                    g.color = 0xFF3A1A5A;
                    g.box(-7, t - 0.5f, -7, 7, t, 7, WHITE);
                    g.box(-4, t - 3f, -4, 4, t - 0.5f, 4, WHITE);
                    g.box(-3, t - 5.5f, -2.5f, 3, t - 3f, 3.5f, WHITE);
                    g.box(-2, t - 8f, -0.5f, 2, t - 5.5f, 4f, WHITE);
                    g.box(-1, t - 10f, 1.5f, 1, t - 8f, 4.5f, WHITE);
                    g.color = 0xFFE0B020;
                    g.box(-4.1f, t - 1.4f, -4.1f, 4.1f, t - 0.6f, 4.1f, WHITE);
                }
                case 2 -> { // máscara de baile dourada, com pena rosa
                    g.glow(0xFFE8C040);
                    g.box(-4.35f, t + 2.8f, -4.35f, 4.35f, t + 4.6f, -4.2f, WHITE);
                    g.color = 0xFF8A2BB0;
                    g.box(-3.2f, t + 3.3f, -4.4f, -0.8f, t + 4.1f, -4.3f, WHITE);
                    g.box(0.8f, t + 3.3f, -4.4f, 3.2f, t + 4.1f, -4.3f, WHITE);
                    g.reset();
                    g.color = 0xFFFF5CA8;
                    g.box(4.0f, t - 3f, -1.2f, 4.8f, t + 3.4f, 0.2f, WHITE);
                    g.box(4.2f, t - 5f, -0.8f, 4.6f, t - 3f, 0.6f, WHITE);
                }
                case 3 -> { // cowboy
                    g.color = 0xFF7A4A22;
                    g.box(-7, t - 0.4f, -7, 7, t, 7, WHITE);
                    g.box(-4, t - 3.2f, -4, 4, t - 0.4f, 4, WHITE);
                    g.box(-7.4f, t - 1.2f, -2, -6.6f, t - 0.4f, 2, WHITE);
                    g.box(6.6f, t - 1.2f, -2, 7.4f, t - 0.4f, 2, WHITE);
                    g.color = 0xFF3A2010;
                    g.box(-4.1f, t - 1.2f, -4.1f, 4.1f, t - 0.5f, 4.1f, WHITE);
                }
                case 4 -> { // orelhas de coelhinho
                    g.color = 0xFFF4F4F4;
                    g.box(-3, t - 7, -0.6f, -1, t, 0.6f, WHITE);
                    g.box(1, t - 7, -0.6f, 3, t, 0.6f, WHITE);
                    g.color = 0xFFFFA8C8;
                    g.box(-2.6f, t - 6.3f, -0.7f, -1.4f, t - 0.8f, -0.6f, WHITE);
                    g.box(1.4f, t - 6.3f, -0.7f, 2.6f, t - 0.8f, -0.6f, WHITE);
                }
                case 5 -> { // chifrinhos de diabinho
                    g.glow(0xFFE02020);
                    g.box(-3.6f, t - 2f, -2f, -2f, t, -0.5f, WHITE);
                    g.box(-3.3f, t - 3.4f, -1.7f, -2.5f, t - 2f, -0.9f, WHITE);
                    g.box(2f, t - 2f, -2f, 3.6f, t, -0.5f, WHITE);
                    g.box(2.5f, t - 3.4f, -1.7f, 3.3f, t - 2f, -0.9f, WHITE);
                    g.reset();
                }
                case 6 -> { // coroa de rei
                    g.color = 0xFFE8C030;
                    g.box(-4.3f, t - 2f, -4.3f, 4.3f, t, 4.3f, WHITE);
                    for (float[] c : new float[][]{{-4.3f, -4.3f}, {3.3f, -4.3f}, {-4.3f, 3.3f}, {3.3f, 3.3f}, {-0.5f, -4.3f}, {-0.5f, 3.3f}}) {
                        g.box(c[0], t - 3.4f, c[1], c[0] + 1, t - 2f, c[1] + 1, WHITE);
                    }
                    g.glow(0xFFE01030);
                    g.box(-0.7f, t - 1.6f, -4.45f, 0.7f, t - 0.4f, -4.3f, WHITE);
                    g.reset();
                }
                default -> { // mágico: cartola
                    g.color = 0xFF151515;
                    g.box(-6, t - 0.5f, -6, 6, t, 6, WHITE);
                    g.box(-3.8f, t - 7f, -3.8f, 3.8f, t - 0.5f, 3.8f, WHITE);
                    g.color = 0xFFB01020;
                    g.box(-3.9f, t - 2f, -3.9f, 3.9f, t - 1f, 3.9f, WHITE);
                }
            }
            g.reset();
        });
    }
}
