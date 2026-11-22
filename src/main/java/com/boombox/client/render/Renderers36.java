package com.boombox.client.render;

import com.boombox.bar.BracketBoardBlock;
import com.boombox.bar.FacingBlock;
import com.boombox.bar.SinucaBlockEntity;
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

/** Desenho da 3.6: o quadro do chaveamento do torneio de sinuca. */
public final class Renderers36 {
    private static final int WHITE = 15;
    private static final float TEXT_Z = 14.8f / 16f;

    private Renderers36() {}

    public static class Bracket implements BlockEntityRenderer<BracketBoardBlock.Entity, Bracket.State> {
        public static class State extends BlockEntityRenderState {
            public Direction facing = Direction.NORTH;
            public final String[] slots = new String[15];
            public final java.util.Set<String> humans = new java.util.HashSet<>();
            public String status = "";
            public boolean any;
        }

        public Bracket(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true;
        }

        @Override
        public void extractRenderState(BracketBoardBlock.Entity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
            BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
            var bs = be.getBlockState();
            s.facing = bs.hasProperty(FacingBlock.FACING) ? bs.getValue(FacingBlock.FACING) : Direction.NORTH;
            java.util.Arrays.fill(s.slots, "");
            s.any = false;
            s.humans.clear();
            s.status = "";
            if (be.getLevel() == null) return;
            SinucaBlockEntity t = be.findTable(be.getLevel());
            if (t == null) {
                s.status = "Sem mesa de sinuca por perto";
                return;
            }
            System.arraycopy(t.bracket, 0, s.slots, 0, 15);
            s.any = !t.bracket[0].isEmpty();
            s.humans.addAll(t.tourHumans);
            if (t.tournamentRunning()) s.status = SinucaBlockEntity.stageName(t.tourStage) + " • na mesa: " + t.tableLabel();
            else if (s.any) s.status = t.bracket[14].isEmpty() ? "Torneio encerrado" : "Campeão: " + t.bracket[14];
            else s.status = "Fale com o Seu Zé para entrar (3 esmeraldas)";
        }

        // colunas (x em blocos; x maior fica à esquerda de quem olha) e alturas (y em blocos) de cada lugar da chave
        private static final float[] SLOT_X = new float[15], SLOT_Y = new float[15];

        static {
            float qL = 1.62f, sL = 1.04f, fX = 0.5f, sR = -0.04f, qR = -0.62f;
            float[] qy = {1.1f, 0.93f, 0.5f, 0.33f};
            for (int i = 0; i < 4; i++) { SLOT_X[i] = qL; SLOT_Y[i] = qy[i]; SLOT_X[4 + i] = qR; SLOT_Y[4 + i] = qy[i]; }
            SLOT_X[8] = sL; SLOT_Y[8] = 1.015f;
            SLOT_X[9] = sL; SLOT_Y[9] = 0.415f;
            SLOT_X[10] = sR; SLOT_Y[10] = 1.015f;
            SLOT_X[11] = sR; SLOT_Y[11] = 0.415f;
            SLOT_X[12] = fX; SLOT_Y[12] = 0.86f;
            SLOT_X[13] = fX; SLOT_Y[13] = 0.62f;
            SLOT_X[14] = fX; SLOT_Y[14] = 0.2f;
        }

        private static int next(int slot) {
            return slot < 8 ? 8 + slot / 2 : slot < 12 ? 12 + (slot - 8) / 2 : 14;
        }

        @Override
        public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
            ps.pushPose();
            ps.translate(0.5f, 0f, 0.5f);
            ps.rotateDegrees(Axis.YP, 180f - s.facing.toYRot());
            ps.translate(-0.5f, 0f, -0.5f);
            int light = s.lightCoords;
            collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                Geo g = new Geo(pose, vc, light, OverlayTexture.NO_OVERLAY, 8);
                // moldura de madeira e feltro verde de mesa de sinuca, 3 x 2 blocos
                g.color = 0xFF6A4424;
                g.box(-16, -8, 15, 32, 24, 16, WHITE);
                g.color = 0xFF285434;
                g.box(-15, -7, 14.9f, 31, 23, 15, WHITE);
                // linhas de giz da chave
                g.color = 0xFFD8D8CC;
                for (int i = 0; i < 12; i++) {
                    float x = SLOT_X[i], y = SLOT_Y[i] + 0.03f;
                    int n = next(i);
                    boolean left = x > 0.5f;
                    float edge = left ? x - 0.29f : x + 0.29f;
                    float nx = SLOT_X[n] + (left ? 0.29f : -0.29f);
                    float mid = (edge + nx) / 2;
                    float ny = SLOT_Y[n] + 0.03f;
                    hline(g, edge, mid, y);
                    vline(g, mid, y, ny);
                    hline(g, mid, nx, ny);
                }
                g.reset();
            });
            Font font = Minecraft.getInstance().font;
            text(ps, collector, font, "TORNEIO DE SINUCA DO SEU ZÉ", 0.5f, 1.3f, 1f / 95f, 0xFFF0E8A0);
            String[] heads = {"Quartas", "Semi", "Final", "Semi", "Quartas"};
            float[] hx = {1.62f, 1.04f, 0.5f, -0.04f, -0.62f};
            for (int i = 0; i < 5; i++) text(ps, collector, font, heads[i], hx[i], 1.19f, 1f / 170f, 0xFFA8C8A8);
            for (int i = 0; i < 15; i++) {
                String n = s.slots[i];
                boolean lost = !n.isEmpty() && i < 14 && !s.slots[next(i)].isEmpty() && !s.slots[next(i)].equals(n);
                int color = n.isEmpty() ? 0xFF8AA08A : s.humans.contains(n) ? 0xFFFFE060 : lost ? 0xFF9AA89A : 0xFFF2F2EA;
                String label = n.isEmpty() ? "?" : shorten(font, n, 74);
                if (i == 14 && !n.isEmpty()) label = "🏆 " + label;
                text(ps, collector, font, label, SLOT_X[i], SLOT_Y[i], i == 14 ? 1f / 105f : 1f / 132f, color);
            }
            text(ps, collector, font, "CAMPEÃO", 0.5f, 0.33f, 1f / 170f, 0xFFF0E8A0);
            text(ps, collector, font, s.status, 0.5f, -0.38f, 1f / 130f, 0xFFD8D8CC);
            ps.popPose();
        }

        private static void hline(Geo g, float x0, float x1, float y) {
            g.box(Math.min(x0, x1) * 16, y * 16 - 0.15f, 14.85f, Math.max(x0, x1) * 16, y * 16 + 0.15f, 14.9f, WHITE);
        }

        private static void vline(Geo g, float x, float y0, float y1) {
            g.box(x * 16 - 0.15f, Math.min(y0, y1) * 16, 14.85f, x * 16 + 0.15f, Math.max(y0, y1) * 16, 14.9f, WHITE);
        }

        private static String shorten(Font font, String s, int max) {
            if (font.width(s) <= max) return s;
            while (s.length() > 3 && font.width(s + "…") > max) s = s.substring(0, s.length() - 1);
            return s + "…";
        }

        /** Texto centrado em x (blocos), com a base em y, na frente do feltro. */
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
