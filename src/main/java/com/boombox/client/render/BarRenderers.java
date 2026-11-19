package com.boombox.client.render;

import com.boombox.BoomboxMod;
import com.boombox.bar.DanceFloorBlockEntity;
import com.boombox.bar.FacingBlock;
import com.boombox.bar.FootballMatch;
import com.boombox.bar.LousaBlockEntity;
import com.boombox.bar.PlasticTableBlockEntity;
import com.boombox.bar.TvBlockEntity;
import com.boombox.client.BarClient;
import com.boombox.client.ClientBoomboxManager;
import com.boombox.entity.Bouncer;
import com.boombox.entity.SeuZe;
import com.boombox.entity.Trio;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Desenho dos blocos e personagens do boteco e da festa (3.1). */
public final class BarRenderers {
    private static final int CAB = 0, TRIM = 2, LED = 9, RUBBER = 11, WHEEL = 12, METAL = 13, WHITE = 15;

    private BarRenderers() {}

    /** Gira o "para frente" (-Z) do desenho para a frente do bloco. */
    private static void face(PoseStack ps, Direction facing) {
        ps.translate(0.5f, 0f, 0.5f);
        ps.rotateDegrees(Axis.YP, 180f - facing.toYRot());
        ps.translate(-0.5f, 0f, -0.5f);
    }

    private static Direction facing(net.minecraft.world.level.block.state.BlockState s) {
        return s.hasProperty(FacingBlock.FACING) ? s.getValue(FacingBlock.FACING) : Direction.NORTH;
    }

    // ------------------------------------------------------------ mesa: o que está em cima
    public static class Table implements BlockEntityRenderer<PlasticTableBlockEntity, Table.State> {
        public static class State extends BlockEntityRenderState {
            public final List<ItemStackRenderState> items = new ArrayList<>();
            /** Item "em pé" (garrafa, bloco) ou deitado em cima da mesa (comida, papel). */
            public final List<Boolean> standing = new ArrayList<>();
        }

        private final ItemModelResolver items;

        public Table(BlockEntityRendererProvider.Context ctx) {
            this.items = ctx.itemModelResolver();
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(PlasticTableBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            s.items.clear();
            s.standing.clear();
            for (int i = 0; i < be.items.size(); i++) {
                ItemStackRenderState r = new ItemStackRenderState();
                var stack = be.items.get(i);
                if (!stack.isEmpty()) {
                    items.updateForTopItem(r, stack, ItemDisplayContext.FIXED, be.getLevel(), null, (int) be.getBlockPos().asLong() + i);
                }
                s.items.add(r);
                s.standing.add(stack.is(com.boombox.ModBlocks.BEER) || stack.getItem() instanceof net.minecraft.world.item.BlockItem);
            }
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            float[][] spots = {{0.3f, 0.3f}, {0.7f, 0.3f}, {0.3f, 0.7f}, {0.7f, 0.7f}};
            for (int i = 0; i < s.items.size(); i++) {
                ItemStackRenderState r = s.items.get(i);
                if (r.isEmpty()) continue;
                ps.pushPose();
                // tampo da mesa em 15/16: o fundo do item fica encostado nele (nada atravessando)
                if (s.standing.get(i)) {
                    float sc = 0.42f;
                    ps.translate(spots[i][0], 15f / 16f + sc * 0.5f, spots[i][1]);
                    ps.rotateDegrees(Axis.YP, i * 37f);
                    ps.scale(sc, sc, sc);
                } else {
                    float sc = 0.36f;
                    ps.translate(spots[i][0], 15f / 16f + 0.012f, spots[i][1]);
                    ps.rotateDegrees(Axis.YP, i * 37f);
                    ps.rotateDegrees(Axis.XP, 90f);
                    ps.scale(sc, sc, sc);
                }
                r.submit(ps, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                ps.popPose();
            }
        }
    }

    // ------------------------------------------------------------ lousa do fiado
    public static class Lousa implements BlockEntityRenderer<LousaBlockEntity, Lousa.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public final List<String> lines = new ArrayList<>();
        }

        public Lousa(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(LousaBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            s.facing = facing(be.getBlockState());
            s.lines.clear();
            s.lines.add("FIADO");
            if (be.getLevel() == null) return;
            List<SeuZe> zes = be.getLevel().getEntitiesOfClass(SeuZe.class, new AABB(be.getBlockPos()).inflate(16));
            if (zes.isEmpty()) return;
            int n = 0;
            for (Map.Entry<String, Integer> e : zes.get(0).debtsForDisplay().entrySet()) {
                if (n++ >= 5) break;
                s.lines.add(e.getKey() + " ...... " + e.getValue());
            }
            if (n == 0) s.lines.add("ninguém deve :)");
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            face(ps, s.facing);
            // quadro preto com moldura de madeira, encostado na parede (z = 16)
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, s.lightCoords, OverlayTexture.NO_OVERLAY, 8);
                g.color = 0xFF6A4424;
                g.box(0, 2, 15, 16, 14, 16, WHITE);
                g.color = 0xFF1C2620;
                g.box(1, 3, 14.9f, 15, 13, 15, WHITE);
                g.reset();
            });
            // texto de giz
            Font font = Minecraft.getInstance().font;
            ps.translate(0.5f, 12.4f / 16f, 14.85f / 16f);
            ps.rotateDegrees(Axis.YP, 180f);
            float scale = 1f / 110f;
            ps.scale(scale, -scale, scale);
            for (int i = 0; i < s.lines.size(); i++) {
                String line = s.lines.get(i);
                int w = font.width(line);
                collector.submitText(ps, -w / 2f, i * 10f, Component.literal(line).getVisualOrderText(), false,
                        Font.DisplayMode.POLYGON_OFFSET, s.lightCoords, i == 0 ? 0xFFF0E8A0 : 0xFFE8E8E0, 0, 0);
            }
            ps.popPose();
        }
    }

    // ------------------------------------------------------------ TV de tubo com futebol
    public static class Tv implements BlockEntityRenderer<TvBlockEntity, Tv.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public long time;
            public float partial;
        }

        public Tv(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(TvBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            s.facing = facing(be.getBlockState());
            s.time = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
            s.partial = pt;
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            face(ps, s.facing);
            int light = s.lightCoords;
            float[] ball = FootballMatch.ball(s.time, s.partial);
            boolean goal = FootballMatch.sinceGoal(s.time) < 60;
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                // caixa da TV (bege, antiga) e tela
                g.color = 0xFFB8A888;
                g.box(2, 0, 4, 14, 11, 14, WHITE);
                g.color = 0xFF3A3A3A;
                g.box(5, 11, 8, 6, 14, 9, WHITE);   // antena
                g.box(10, 11, 8, 11, 14, 9, WHITE);
                float x0 = 3, x1 = 13, y0 = 1.5f, y1 = 9.5f, z = 3.9f;
                g.glow(goal && (s.time / 4) % 2 == 0 ? 0xFF3AA84A : 0xFF2A8A3A); // gramado (pisca no gol)
                g.plate(x0, y0, x1, y1, z, 4f, WHITE, WHITE);
                g.glow(0xFFE8E8E8);
                g.plate(7.9f, y0, 8.1f, y1, z - 0.01f, z, WHITE, WHITE);       // meio de campo
                g.plate(x0, 4.5f, x0 + 0.3f, 6.5f, z - 0.01f, z, WHITE, WHITE); // traves
                g.plate(x1 - 0.3f, 4.5f, x1, 6.5f, z - 0.01f, z, WHITE, WHITE);
                // jogadores (5 de cada time) correndo atrás da bola
                double t = (s.time + s.partial) / 20.0;
                for (int i = 0; i < 10; i++) {
                    boolean home = i < 5;
                    double px = 0.5 + (ball[0] - 0.5) * 0.7 + 0.18 * Math.sin(t * 0.9 + i * 1.7) + (home ? -0.08 : 0.08);
                    double py = 0.5 + (ball[1] - 0.5) * 0.6 + 0.3 * Math.sin(t * 0.6 + i * 2.3);
                    float bx = (float) (x1 - (x1 - x0) * Math.max(0.03, Math.min(0.97, px)));
                    float by = (float) (y0 + (y1 - y0) * Math.max(0.05, Math.min(0.95, py)));
                    g.glow(home ? 0xFFE8D020 : 0xFFD02828);
                    g.plate(bx - 0.25f, by - 0.25f, bx + 0.25f, by + 0.25f, z - 0.02f, z, WHITE, WHITE);
                }
                float bx = x1 - (x1 - x0) * ball[0], by = y0 + (y1 - y0) * ball[1];
                g.glow(0xFFFFFFFF);
                g.plate(bx - 0.18f, by - 0.18f, bx + 0.18f, by + 0.18f, z - 0.03f, z, WHITE, WHITE);
                g.reset();
            });
            // placar
            int[] sc = FootballMatch.score(s.time);
            String text = goal ? "GOOOL!" : FootballMatch.HOME + " " + sc[0] + " x " + sc[1] + " " + FootballMatch.AWAY + "  " + FootballMatch.minute(s.time) + "'";
            Font font = Minecraft.getInstance().font;
            ps.translate(0.5f, 9.4f / 16f, 3.85f / 16f);
            ps.rotateDegrees(Axis.YP, 180f);
            float scale = 1f / 200f;
            ps.scale(scale, -scale, scale);
            collector.submitText(ps, -font.width(text) / 2f, 0, Component.literal(text).getVisualOrderText(), false,
                    Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, goal ? 0xFFFFE040 : 0xFFFFFFFF, 0x80000000, 0);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------ pista de dança que acende
    public static class DanceFloor implements BlockEntityRenderer<DanceFloorBlockEntity, DanceFloor.State> {
        public static class State extends BlockEntityRenderState {
            public int color;
        }

        private final float[] levels = new float[2];

        public DanceFloor(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(DanceFloorBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            var pos = be.getBlockPos();
            double t = System.nanoTime() / 1e9;
            ClientBoomboxManager.State near = ClientBoomboxManager.playingNear(Minecraft.getInstance(), Vec3.atCenterOf(pos), 24);
            ClientBoomboxManager.levelsOf(near, levels);
            boolean stepped = BarClient.stepped(pos);
            // cada bloco com uma cor, mudando em ondas; pisca com o grave; pisou: fica branco e brilhante
            float hue = (float) (((pos.getX() * 3 + pos.getZ() * 5) % 7) / 7.0 + t * (near != null ? 0.18 : 0.05));
            float v = near != null ? 0.35f + 0.65f * Math.min(1f, levels[0] * 1.3f) : 0.3f;
            s.color = stepped ? 0xFFF8F8FF : PartyGeometry.hsv(hue, 0.9f, v);
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, s.lightCoords, OverlayTexture.NO_OVERLAY, 8);
                g.glow(s.color);
                g.box(0.6f, 15.9f, 0.6f, 15.4f, 16.03f, 15.4f, LED);
                g.reset();
            });
        }
    }

    // ------------------------------------------------------------ segurança
    public static class BouncerRenderer extends HumanoidMobRenderer<Bouncer, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
        private static final Identifier TEXTURE = BoomboxMod.id("textures/entity/bouncer.png");

        public BouncerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.6f);
        }

        @Override
        public HumanoidRenderState createRenderState() {
            return new HumanoidRenderState();
        }

        @Override
        public Identifier getTextureLocation(HumanoidRenderState state) {
            return TEXTURE;
        }
    }

    // ------------------------------------------------------------ trio elétrico (o paredão vai em cima, montado)
    public static class TrioRenderer extends EntityRenderer<Trio, TrioRenderer.State> {
        public static class State extends EntityRenderState {
            public float yaw, walk;
        }

        public TrioRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(Trio e, State s, float pt) {
            super.extractRenderState(e, s, pt);
            s.yaw = e.getYRot(pt);
            s.walk = e.walkAnimation.position(pt);
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            ps.rotateDegrees(Axis.YP, 180f - s.yaw);
            ps.translate(-1.2f, 0f, -1.6f);
            int light = s.lightCoords;
            double t = System.nanoTime() / 1e9;
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                g.color = 0xFFE8C020; // carroceria amarela
                g.box(2, 4, 2, 36, 24, 48, WHITE);
                g.color = 0xFF2050D0; // cabine azul na frente
                g.box(4, 4, -6, 34, 20, 2, WHITE);
                g.color = 0xFF202830;
                g.box(6, 13, -6.2f, 32, 19, -6, WHITE); // para-brisa
                g.reset();
                for (float wz : new float[]{0, 38}) { // rodas
                    for (float off = 0.2f; off < 1.2f; off += 0.25f) {
                        g.sideDisc(2 - off, 4, wz + 4, 4, WHEEL, false, -s.walk);
                        g.sideDisc(36 + off, 4, wz + 4, 4, WHEEL, true, -s.walk);
                    }
                }
                // faixa de LED em volta
                for (int i = 0; i < 12; i++) {
                    g.glow(PartyGeometry.hsv((float) (t * 0.3 + i / 12.0), 1f, 1f));
                    g.plate(2 + i * 2.8f, 21, 4.6f + i * 2.8f, 22.5f, 1.9f, 2f, LED, LED);
                    g.reset();
                }
            });
            ps.popPose();
            super.submit(s, ps, collector, camera);
        }
    }
}
