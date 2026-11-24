package com.boombox.client.render;

import com.boombox.bar.FacingBlock;
import com.boombox.bar.RankingBoardBlock;
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
import net.minecraft.world.phys.Vec3;

/** Desenho da 3.8: o quadro do ranking do bar (lousa com moldura de madeira, 3 x 2 blocos). */
public final class Renderers38 {
    private static final int WHITE = 15;
    private static final float TEXT_Z = 14.8f / 16f;

    private Renderers38() {}

    public static class Ranking implements BlockEntityRenderer<RankingBoardBlock.Entity, Ranking.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public String data = "";
        }

        public Ranking(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true;
        }

        @Override
        public void extractRenderState(RankingBoardBlock.Entity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            var bs = be.getBlockState();
            s.facing = bs.hasProperty(FacingBlock.FACING) ? bs.getValue(FacingBlock.FACING) : Direction.NORTH;
            s.data = be.data == null ? "" : be.data;
        }

        private static final String[] HEADS = {"ARTILHEIROS", "COPAS", "SINUCA"};
        private static final int[] HEAD_COLORS = {0xFF8CE070, 0xFFF0D060, 0xFF80C8F0};
        /** Centro de cada coluna (x em blocos; maior fica à esquerda de quem olha). */
        private static final float[] COL_X = {1.5f, 0.5f, -0.5f};

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            ps.translate(0.5f, 0f, 0.5f);
            ps.rotateDegrees(Axis.YP, 180f - s.facing.toYRot());
            ps.translate(-0.5f, 0f, -0.5f);
            int light = s.lightCoords;
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                g.color = 0xFF6A4424; // moldura de madeira
                g.box(-16, -8, 15, 32, 24, 16, WHITE);
                g.color = 0xFF22302A; // lousa
                g.box(-15, -7, 14.9f, 31, 23, 15, WHITE);
                g.color = 0xFF9A8A60; // divisórias de giz
                g.box(15.85f, -5, 14.85f, 16.15f, 17.5f, 14.9f, WHITE);
                g.box(-0.15f, -5, 14.85f, 0.15f, 17.5f, 14.9f, WHITE);
                g.box(-14, 14.6f, 14.85f, 30, 14.9f, 14.9f, WHITE);
                g.reset();
            });
            Font font = Minecraft.getInstance().font;
            text(ps, collector, font, "RANKING DO BAR DO SEU ZÉ", 0.5f, 1.3f, 1f / 95f, 0xFFF0E8A0);
            String[] lines = s.data.split("\n", -1);
            for (int c = 0; c < 3; c++) {
                text(ps, collector, font, HEADS[c], COL_X[c], 1.0f, 1f / 105f, HEAD_COLORS[c]);
                String[][] rows = parse(c < lines.length ? lines[c] : "");
                if (rows.length == 0) text(ps, collector, font, "ninguém ainda", COL_X[c], 0.8f, 1f / 150f, 0xFF8A9A8A);
                for (int i = 0; i < rows.length && i < 5; i++) {
                    String name = shorten(font, rows[i][0], 64);
                    int color = i == 0 ? 0xFFFFE060 : 0xFFF2F2EA;
                    text(ps, collector, font, (i + 1) + ". " + name + "  " + rows[i][1], COL_X[c], 0.8f - i * 0.17f, 1f / 125f, color);
                }
            }
            text(ps, collector, font, "clique para ver o top 10", 0.5f, -0.38f, 1f / 150f, 0xFFB8B8A8);
            ps.popPose();
        }

        private static String[][] parse(String line) {
            if (line.isEmpty()) return new String[0][];
            String[] items = line.split("\t");
            String[][] out = new String[items.length][];
            for (int i = 0; i < items.length; i++) {
                int eq = items[i].lastIndexOf('=');
                out[i] = eq < 0 ? new String[]{items[i], ""} : new String[]{items[i].substring(0, eq), items[i].substring(eq + 1)};
            }
            return out;
        }

        private static String shorten(Font font, String s, int max) {
            if (font.width(s) <= max) return s;
            while (s.length() > 3 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
            return s + "…";
        }

        private static void text(PoseStack ps, SubmitNodeCollector collector, Font font, String t, float x, float y, float scale, int color) {
            ps.pushPose();
            ps.translate(x, y, TEXT_Z);
            ps.rotateDegrees(Axis.YP, 180f);
            ps.scale(scale, -scale, scale);
            collector.submitText(ps, -font.width(t) / 2f, -8f, Component.literal(t).getVisualOrderText(), false,
                    Font.DisplayMode.POLYGON_OFFSET, 0xF000F0, color, 0, 0);
            ps.popPose();
        }
    }
}
