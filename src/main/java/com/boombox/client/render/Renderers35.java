package com.boombox.client.render;

import com.boombox.bar.FacingBlock;
import com.boombox.bar.PlacarBlock;
import com.boombox.entity.Ball;
import com.boombox.entity.Volleyball;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Desenho da 3.5: o placar eletrônico do campo e da quadra. */
public final class Renderers35 {
    private static final int WHITE = 15;

    private Renderers35() {}

    public static class Placar implements BlockEntityRenderer<PlacarBlock.Entity, Placar.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public String nameA = "", nameB = "", info = "", score = "";
            public int colorA, colorB;
            public boolean game;
        }

        public Placar(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true;
        }

        @Override
        public void extractRenderState(PlacarBlock.Entity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            var bs = be.getBlockState();
            s.facing = bs.hasProperty(FacingBlock.FACING) ? bs.getValue(FacingBlock.FACING) : Direction.NORTH;
            s.game = false;
            var level = be.getLevel();
            if (level == null) return;
            Vec3 at = Vec3.atCenterOf(be.getBlockPos());
            AABB box = new AABB(at, at).inflate(48);
            Ball fb = null;
            for (Ball b : level.getEntitiesOfClass(Ball.class, box)) if (fb == null || b.distanceToSqr(at) < fb.distanceToSqr(at)) fb = b;
            Volleyball vb = null;
            for (Volleyball v : level.getEntitiesOfClass(Volleyball.class, box)) if (vb == null || v.distanceToSqr(at) < vb.distanceToSqr(at)) vb = v;
            if (fb != null && (vb == null || fb.distanceToSqr(at) <= vb.distanceToSqr(at))) {
                s.game = true;
                s.nameA = fb.teamName(0);
                s.nameB = fb.teamName(1);
                s.colorA = fb.teamColor(0);
                s.colorB = fb.teamColor(1);
                s.score = fb.scoreA() + "  x  " + fb.scoreB();
                s.info = fb.info();
            } else if (vb != null) {
                s.game = true;
                s.nameA = vb.teamName(0);
                s.nameB = vb.teamName(1);
                s.colorA = vb.teamColor(0);
                s.colorB = vb.teamColor(1);
                s.score = vb.score(0) + "  x  " + vb.score(1);
                s.info = vb.info();
            }
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            ps.translate(0.5f, 0f, 0.5f);
            ps.rotateDegrees(Axis.YP, 180f - s.facing.toYRot());
            ps.translate(-0.5f, 0f, -0.5f);
            int light = s.lightCoords;
            // painel preto de 3 x 1,5 blocos com moldura e pés
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                g.color = 0xFF2A2A2E;
                g.box(-17, -1, 13, 33, 23, 16, WHITE);
                g.color = 0xFF08080A;
                g.box(-16, 0, 12.8f, 32, 22, 13, WHITE);
                if (s.game) {
                    // faixas com as cores dos times
                    // (de frente, o lado de x grande fica à esquerda de quem olha: time A à esquerda)
                    g.glow(0xFF000000 | s.colorA);
                    g.box(17, 14.5f, 12.7f, 31, 15.5f, 12.8f, WHITE);
                    g.glow(0xFF000000 | s.colorB);
                    g.box(-15, 14.5f, 12.7f, -1, 15.5f, 12.8f, WHITE);
                    g.reset();
                }
            });
            Font font = Minecraft.getInstance().font;
            if (s.game) {
                text(ps, collector, font, s.info, 0.5f, 1.22f, 1f / 55f, 0xFFFFD040);
                text(ps, collector, font, shorten(font, s.nameA, 46), 1.05f, 0.8f, 1f / 42f, 0xFF000000 | brighten(s.colorA));
                text(ps, collector, font, shorten(font, s.nameB, 46), -0.05f, 0.8f, 1f / 42f, 0xFF000000 | brighten(s.colorB));
                text(ps, collector, font, s.score, 0.5f, 0.12f, 1f / 18f, 0xFFFFFFFF);
            } else {
                text(ps, collector, font, "PLACAR", 0.5f, 0.75f, 1f / 30f, 0xFFFFD040);
                text(ps, collector, font, "Fale com o organizador", 0.5f, 0.35f, 1f / 70f, 0xFFB0B0B0);
            }
            ps.popPose();
        }

        private static int brighten(int c) {
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            if (r + g + b < 160) { r += 90; g += 90; b += 90; }
            return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
        }

        private static String shorten(Font font, String s, int max) {
            if (font.width(s) <= max) return s;
            while (s.length() > 3 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
            return s + "…";
        }

        /** Texto centrado em (x, y) blocos, na frente do painel, com luz própria. */
        private static void text(PoseStack ps, SubmitNodeCollector collector, Font font, String t, float x, float y, float scale, int color) {
            ps.pushPose();
            ps.translate(x, y, 12.6f / 16f);
            ps.rotateDegrees(Axis.YP, 180f);
            ps.scale(scale, -scale, scale);
            collector.submitText(ps, -font.width(t) / 2f, -8f, Component.literal(t).getVisualOrderText(), false,
                    Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, color, 0, 0);
            ps.popPose();
        }
    }
}
