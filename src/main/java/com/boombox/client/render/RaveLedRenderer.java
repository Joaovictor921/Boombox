package com.boombox.client.render;

import com.boombox.RaveLedBlock;
import com.boombox.RaveLedBlockEntity;
import com.boombox.SpeakerSettings;
import com.boombox.client.ClientBoomboxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Canhão de luz de rave: feixes de laser macios (mais fortes no meio, sumindo nas bordas) que varrem o
 * ambiente, param nas paredes, acendem um ponto onde batem e deixam uma "fumaça" colorida no ar,
 * no estilo dos feixes de luz com shader. As luzes somam (como luz de verdade), então brilham no escuro.
 * <p>
 * Perto de uma caixa tocando, o movimento acelera e pulsa no grave, e as cores seguem o modo de luz
 * da caixa (arco-íris, cor fixa, na batida ou desligada).
 */
public class RaveLedRenderer implements BlockEntityRenderer<RaveLedBlockEntity, RaveLedRenderer.State> {
    /** Luz que soma na cena e não grava profundidade (não mancha telões desenhados depois). */
    private static final RenderType BEAM = BeamRenderType.BEAM;
    private static final int BEAMS = 5;
    /** Ordem de desenho dos feixes (maior = depois). */
    private static final int BEAM_ORDER = 1000;
    private static final double MAX_LENGTH = 22;

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.DOWN;
        public boolean playing;
        public float bass;
        public SpeakerSettings settings = SpeakerSettings.DEFAULT;
        public float phase;
        public double time;
        /** Origem dos feixes (relativa ao canto do bloco) e, para cada feixe: direção xyz + comprimento. */
        public final float[] origin = new float[3];
        public final float[][] beams = new float[BEAMS][4];
        public final int[] colors = new int[BEAMS];
        public float intensity;
        public float lensColorMix;
    }

    private static final int CAB = 0, LED = 9, METAL = 13;
    private final float[] levels = new float[2];

    public RaveLedRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true; // os feixes vão longe do bloco
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void extractRenderState(RaveLedBlockEntity be, State s, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, cameraPos, crumbling);
        var bs = be.getBlockState();
        s.facing = bs.hasProperty(RaveLedBlock.FACING) ? bs.getValue(RaveLedBlock.FACING) : Direction.DOWN;
        var pos = be.getBlockPos();
        s.phase = Math.floorMod(pos.getX() * 7 + pos.getY() * 13 + pos.getZ() * 5, 17) / 17f;
        s.time = System.nanoTime() / 1e9;
        ClientBoomboxManager.State near = ClientBoomboxManager.playingNear(Minecraft.getInstance(), Vec3.atCenterOf(pos), 28);
        s.playing = near != null;
        s.settings = near != null ? near.settings : SpeakerSettings.DEFAULT;
        if (near != null) {
            ClientBoomboxManager.levelsOf(near, levels);
            s.bass = levels[0];
        } else {
            s.bass = 0;
        }

        // ---- força da luz
        float kick = Math.min(1f, s.bass * 1.3f);
        if (s.settings.light() == SpeakerSettings.LIGHT_OFF) s.intensity = 0f;
        else if (!s.playing) s.intensity = 0.28f;
        else if (s.settings.light() == SpeakerSettings.LIGHT_BEAT) s.intensity = Math.max(0f, Math.min(1f, (s.bass - 0.38f) / 0.3f));
        else s.intensity = 0.45f + 0.55f * kick;

        // ---- direções: padrões que trocam a cada 6 s (leque varrendo, cruzado, cone girando)
        double t = s.time + s.phase * 10;
        double speed = s.playing ? 0.9 + 2.2 * kick : 0.35;
        int pattern = s.playing ? (int) ((s.time / 6.0 + s.phase * 3) % 3) : 0;
        Quaternionf toWorld = facingRotation(s.facing);
        Vector3f o = new Vector3f(0, 0, 0.25f); // lente um pouco à frente da parede
        toWorld.transform(o);
        s.origin[0] = 0.5f + o.x;
        s.origin[1] = 0.5f + o.y;
        s.origin[2] = 0.5f + o.z;
        Vec3 worldOrigin = new Vec3(pos.getX() + s.origin[0], pos.getY() + s.origin[1], pos.getZ() + s.origin[2]);
        var level = be.getLevel();
        for (int i = 0; i < BEAMS; i++) {
            double f = i / (double) (BEAMS - 1) - 0.5; // -0.5 .. 0.5
            double yaw, pitch;
            switch (pattern) {
                case 1 -> { // cruzado: feixes pares e ímpares varrem em sentidos opostos
                    double sw = Math.sin(t * speed) * 0.7;
                    yaw = f * 1.1 + (i % 2 == 0 ? sw : -sw);
                    pitch = 0.45 + 0.2 * Math.sin(t * speed * 0.7 + i);
                }
                case 2 -> { // cone girando em volta do eixo
                    yaw = t * speed * 0.8 + i * (Math.PI * 2 / BEAMS);
                    pitch = 0.55 + 0.15 * Math.sin(t * speed * 1.3);
                }
                default -> { // leque varrendo de um lado para o outro
                    yaw = f * 1.0 + Math.sin(t * speed * 0.8) * 0.6;
                    pitch = 0.35 + 0.25 * Math.sin(t * speed * 0.5 + i * 0.9);
                }
            }
            Vector3f d;
            if (pattern == 2) {
                // inclina pitch para fora do eixo e gira yaw em volta dele
                d = new Vector3f((float) (Math.sin(pitch) * Math.cos(yaw)), (float) (Math.sin(pitch) * Math.sin(yaw)), (float) -Math.cos(pitch));
            } else {
                d = new Vector3f((float) (Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch), (float) (-Math.cos(yaw) * Math.cos(pitch)));
            }
            d.normalize();
            toWorld.transform(d);
            double len = MAX_LENGTH;
            if (level != null) {
                Vec3 end = worldOrigin.add(d.x * MAX_LENGTH, d.y * MAX_LENGTH, d.z * MAX_LENGTH);
                BlockHitResult hit = level.clip(new ClipContext(worldOrigin.add(d.x * 0.3, d.y * 0.3, d.z * 0.3), end,
                        ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (hit.getType() == HitResult.Type.BLOCK) len = hit.getLocation().distanceTo(worldOrigin);
            }
            s.beams[i][0] = d.x;
            s.beams[i][1] = d.y;
            s.beams[i][2] = d.z;
            s.beams[i][3] = (float) len;
            s.colors[i] = beamColor(s, i, t);
        }
    }

    private static int beamColor(State s, int i, double t) {
        return switch (s.settings.light()) {
            case SpeakerSettings.LIGHT_FIXED -> s.settings.colorRgb();
            default -> PartyGeometry.hsv((float) (t * 0.07 + s.phase + i * 0.11), 0.85f, 1f) & 0xFFFFFF;
        };
    }

    /** Gira o "para frente" do canhão (-Z) para a direção em que ele aponta. */
    private static Quaternionf facingRotation(Direction facing) {
        return switch (facing) {
            case UP -> Axis.XP.rotationDegrees(90f);
            case DOWN -> Axis.XP.rotationDegrees(-90f);
            default -> Axis.YP.rotationDegrees(180f - facing.toYRot());
        };
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        // ---- o aparelho (caixinha preta com lente), virado para onde aponta
        ps.pushPose();
        ps.translate(0.5f, 0.5f, 0.5f);
        ps.rotate(facingRotation(s.facing));
        ps.translate(-0.5f, -0.5f, -0.5f);
        int light = s.lightCoords;
        int lens = s.intensity <= 0.01f ? 0xFF202024 : 0xFF000000 | scale(s.colors[0], 0.4f + 0.6f * s.intensity);
        collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
            Geo c = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
            c.box(4.5f, 4.5f, 15f, 11.5f, 11.5f, 16f, METAL);     // suporte
            c.box(5.5f, 5.5f, 11.5f, 10.5f, 10.5f, 15f, CAB);     // corpo
            if (s.intensity > 0.01f) c.glow(lens);
            else c.color = lens;
            c.plate(6.5f, 6.5f, 9.5f, 9.5f, 11.3f, 11.5f, LED, LED); // lente
            c.reset();
        });
        ps.popPose();

        if (s.intensity <= 0.01f) return;
        // ---- feixes e fumaça (em coordenadas do mundo, a partir do canto do bloco)
        Quaternionf camRot = camera.orientation == null ? new Quaternionf() : new Quaternionf(camera.orientation);
        // camada de desenho depois de tudo (telões, caixas, entidades): o laser na frente aparece na frente
        collector.order(BEAM_ORDER).submitCustomGeometry(ps, BEAM, (pose, vc) -> {
            Light l = new Light(pose, vc);
            float ox = s.origin[0], oy = s.origin[1], oz = s.origin[2];
            Vector3f right = camRot.transform(new Vector3f(1, 0, 0));
            Vector3f up = camRot.transform(new Vector3f(0, 1, 0));
            for (int i = 0; i < BEAMS; i++) {
                float[] b = s.beams[i];
                float len = b[3];
                int rgb = s.colors[i];
                float a0 = 0.7f * s.intensity, a1 = 0.08f * s.intensity;
                float w0 = 0.025f, w1 = 0.04f + len * 0.013f; // feixe fino que abre pouco
                l.beam(ox, oy, oz, b[0], b[1], b[2], len, w0, w1, rgb, a0, a1);
                // fumaça iluminada ao longo do feixe (manchas suaves que respiram)
                for (int k = 1; k <= 2; k++) {
                    float f = k / 3f;
                    double breathe = 0.75 + 0.25 * Math.sin(s.time * 0.9 + i * 1.7 + k);
                    float r = (float) ((0.5 + len * f * 0.09) * breathe);
                    l.blob(ox + b[0] * len * f, oy + b[1] * len * f, oz + b[2] * len * f, r, right, up, rgb, 0.09f * s.intensity);
                }
                // ponto de luz onde o feixe bate
                if (len < MAX_LENGTH - 0.5) {
                    l.blob(ox + b[0] * len, oy + b[1] * len, oz + b[2] * len, 0.35f, right, up, rgb, 0.55f * s.intensity);
                }
            }
            // névoa geral em volta do canhão (fumaça da rave)
            l.blob(ox, oy, oz, 3.2f, right, up, s.colors[BEAMS / 2], 0.07f * s.intensity);
            l.blob(ox, oy, oz, 0.45f, right, up, s.colors[0], 0.5f * s.intensity); // brilho da lente
        });
    }

    private static int scale(int rgb, float v) {
        v = Math.max(0f, Math.min(1f, v));
        int r = Math.round(((rgb >> 16) & 255) * v), g = Math.round(((rgb >> 8) & 255) * v), b = Math.round((rgb & 255) * v);
        return r << 16 | g << 8 | b;
    }

    /** Desenha luz que soma (cor x alfa): feixes macios e manchas redondas. */
    private record Light(PoseStack.Pose pose, VertexConsumer vc) {
        void v(float x, float y, float z, int rgb, float a) {
            int alpha = Math.round(Math.max(0f, Math.min(1f, a)) * 255);
            vc.addVertex(pose, x, y, z).setColor(alpha << 24 | (rgb & 0xFFFFFF));
        }

        /** Quadrilátero (o pipeline não descarta o lado de trás). */
        void quad(float[] p, int rgb, float[] a) {
            v(p[0], p[1], p[2], rgb, a[0]); v(p[3], p[4], p[5], rgb, a[1]); v(p[6], p[7], p[8], rgb, a[2]); v(p[9], p[10], p[11], rgb, a[3]);
        }

        /**
         * Feixe: dois planos cruzados ao longo do eixo; cada plano é mais forte no meio e some nas bordas,
         * e o feixe vai ficando mais largo e mais fraco com a distância.
         */
        void beam(float ox, float oy, float oz, float dx, float dy, float dz, float len, float w0, float w1, int rgb, float a0, float a1) {
            Vector3f d = new Vector3f(dx, dy, dz);
            Vector3f ref = Math.abs(dy) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
            Vector3f u = new Vector3f(d).cross(ref).normalize();
            Vector3f w = new Vector3f(d).cross(u).normalize();
            float ex = ox + dx * len, ey = oy + dy * len, ez = oz + dz * len;
            for (Vector3f side : new Vector3f[]{u, w}) {
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    float[] p = {
                            ox, oy, oz,
                            ox + side.x * w0 * sgn, oy + side.y * w0 * sgn, oz + side.z * w0 * sgn,
                            ex + side.x * w1 * sgn, ey + side.y * w1 * sgn, ez + side.z * w1 * sgn,
                            ex, ey, ez};
                    quad(p, rgb, new float[]{a0, 0f, 0f, a1});
                }
            }
        }

        /** Mancha redonda virada para a câmera: forte no centro, some na borda. */
        void blob(float cx, float cy, float cz, float r, Vector3f right, Vector3f up, int rgb, float a) {
            int seg = 12;
            for (int k = 0; k < seg; k++) {
                double t0 = k * Math.PI * 2 / seg, t1 = (k + 1) * Math.PI * 2 / seg;
                float c0 = (float) Math.cos(t0) * r, s0 = (float) Math.sin(t0) * r, c1 = (float) Math.cos(t1) * r, s1 = (float) Math.sin(t1) * r;
                float[] p = {
                        cx, cy, cz,
                        cx + right.x * c0 + up.x * s0, cy + right.y * c0 + up.y * s0, cz + right.z * c0 + up.z * s0,
                        cx + right.x * c1 + up.x * s1, cy + right.y * c1 + up.y * s1, cz + right.z * c1 + up.z * s1,
                        cx, cy, cz};
                quad(p, rgb, new float[]{a, 0f, 0f, a});
            }
        }
    }
}
