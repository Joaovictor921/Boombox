package com.boombox.client.render;

import com.boombox.BoomboxMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * O modelo 3D da boombox, desenhado em código para que os alto-falantes possam se mexer
 * com a música. Coordenadas em "pixels" (0–16 = 1 bloco), frente virada para -Z (norte), Y para cima.
 * Os woofers (grandes) seguem o grave; os tweeters (pequenos) e o equalizador seguem o agudo.
 */
public final class BoomboxGeometry {
    public static final Identifier TEXTURE = BoomboxMod.id("textures/entity/boombox.png");
    public static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);

    // regiões da textura (grade 4x4 de 16x16 numa textura 64x64)
    private static final int BODY = 0, TRIM = 1, CHROME = 2, SIDE_DARK = 3, RIM = 4, SURROUND = 5, CONE = 6, CAP = 7,
            LCD = 8, CASSETTE = 9, RED = 10, STRIPE = 11, BUTTON = 12, GRILLE = 13, EQ = 14, PEAK = 15;

    private static final float[][] WOOFERS = {{3.6f, 4.0f}, {12.4f, 4.0f}};
    private static final float[][] TWEETERS = {{3.6f, 8.05f}, {12.4f, 8.05f}};

    private BoomboxGeometry() {}

    /** Desenha a boombox inteira. bass/treble de 0 a 1. */
    public static void draw(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float bass, float treble) {
        Ctx c = new Ctx(pose, vc, light, overlay);
        double t = System.nanoTime() / 1e9;

        // pés
        c.box(1.5f, 0, 5, 3, 0.6f, 6.5f, TRIM);
        c.box(13, 0, 5, 14.5f, 0.6f, 6.5f, TRIM);
        c.box(1.5f, 0, 9.5f, 3, 0.6f, 11, TRIM);
        c.box(13, 0, 9.5f, 14.5f, 0.6f, 11, TRIM);

        // corpo: frente lisa, traseira com grade
        c.box(0.5f, 0.6f, 4.5f, 15.5f, 9.6f, 11.5f, BODY, BODY, BODY, GRILLE);
        // cantos arredondados (frisos pretos nas arestas da frente)
        c.box(0.4f, 0.6f, 4.4f, 0.9f, 9.6f, 4.9f, TRIM);
        c.box(15.1f, 0.6f, 4.4f, 15.6f, 9.6f, 4.9f, TRIM);
        // faixa laranja embaixo
        c.box(0.9f, 0.85f, 4.38f, 15.1f, 1.25f, 4.5f, STRIPE);
        // tampa de cima
        c.box(1, 9.6f, 5, 15, 9.9f, 11, TRIM);

        // botões do toca-fitas (o vermelho é o REC/PLAY)
        for (int i = 0; i < 6; i++) {
            float x0 = 5.2f + i * 1.0f;
            float h = i == 1 ? 10.1f : 10.3f; // um botão "apertado"
            c.box(x0, 9.9f, 5.4f, x0 + 0.8f, h, 6.6f, i == 0 ? RED : BUTTON);
        }
        // botões giratórios de volume/sintonia
        c.box(2.0f, 9.9f, 9.2f, 3.0f, 10.4f, 10.2f, CHROME);
        c.box(3.6f, 9.9f, 9.2f, 4.6f, 10.4f, 10.2f, CHROME);

        // alça
        c.box(2, 9.9f, 7.6f, 2.8f, 13, 8.4f, CHROME);
        c.box(13.2f, 9.9f, 7.6f, 14, 13, 8.4f, CHROME);
        c.box(2, 12.4f, 7.4f, 14, 13.3f, 8.6f, TRIM);
        // antena
        c.box(14.7f, 9.6f, 10.6f, 15.1f, 15.6f, 11.0f, CHROME);
        c.box(14.6f, 15.6f, 10.5f, 15.2f, 16.0f, 11.1f, CHROME);

        // centro: fita cassete e display com equalizador
        c.plate(6.6f, 1.6f, 9.4f, 5.0f, 4.36f, 4.5f, CASSETTE, TRIM);
        c.plate(6.6f, 5.6f, 9.4f, 8.4f, 4.36f, 4.5f, LCD, TRIM);
        float[] eq = {bass, bass * 0.6f + treble * 0.4f, bass * 0.3f + treble * 0.7f, treble};
        for (int i = 0; i < 4; i++) {
            float x0 = 6.85f + i * 0.65f;
            float h = 0.15f + Math.min(1f, eq[i]) * 2.2f;
            c.plate(x0, 5.85f, x0 + 0.45f, 5.85f + h, 4.3f, 4.36f, eq[i] > 0.85f ? PEAK : EQ, EQ);
        }

        // woofers: o cone vai para frente e para trás com o grave
        float wobble = (float) (0.78 + 0.22 * Math.sin(t * Math.PI * 2 * 9));
        float e = bass * 0.95f * wobble;               // deslocamento do cone (pixels)
        float grow = 1f + bass * 0.05f;
        for (float[] w : WOOFERS) {
            float cx = w[0], cy = w[1];
            c.ring(cx, cy, 2.6f, 3.85f, 4.5f, RIM, CHROME);
            c.round(cx, cy, 2.25f, 4.42f, 4.48f, SURROUND);
            c.round(cx, cy, 1.85f * grow, 4.34f - e, 4.40f - e * 0.6f, CONE);
            c.round(cx, cy, 0.7f * grow, 4.26f - e * 1.15f, 4.34f - e, CAP);
        }

        // tweeters: vibram com o agudo (rápido e curtinho)
        float jitter = (float) (0.7 + 0.3 * Math.sin(t * Math.PI * 2 * 23));
        float et = treble * 0.4f * jitter;
        for (float[] w : TWEETERS) {
            float cx = w[0], cy = w[1];
            c.ring(cx, cy, 1.15f, 4.05f, 4.5f, RIM, CHROME);
            c.round(cx, cy, 0.85f, 4.36f - et, 4.42f - et * 0.5f, CONE);
            c.round(cx, cy, 0.34f, 4.28f - et * 1.2f, 4.36f - et, CAP);
        }
    }

    /** Extensão (cantos) do modelo, em blocos, para o item. */
    public static final float[][] EXTENTS = {
            {0.5f / 16, 0, 3.8f / 16}, {15.6f / 16, 16f / 16, 11.6f / 16}
    };

    // ------------------------------------------------------------------ desenho de caixas

    private static final class Ctx {
        final PoseStack.Pose pose;
        final VertexConsumer vc;
        final int light, overlay;

        Ctx(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay) {
            this.pose = pose;
            this.vc = vc;
            this.light = light;
            this.overlay = overlay;
        }

        void box(float x0, float y0, float z0, float x1, float y1, float z1, int region) {
            box(x0, y0, z0, x1, y1, z1, region, region, region, region);
        }

        /** Caixa com regiões separadas para frente (-Z), lados, topo/baixo e traseira (+Z). */
        void box(float x0, float y0, float z0, float x1, float y1, float z1, int front, int sides, int topBottom, int back) {
            x0 /= 16; y0 /= 16; z0 /= 16; x1 /= 16; y1 /= 16; z1 /= 16;
            // norte (-Z)
            quad(front, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, true);
            // sul (+Z)
            quad(back, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, false);
            // oeste (-X)
            quad(sides, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, false);
            // leste (+X)
            quad(sides, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, false);
            // cima (+Y)
            quad(topBottom, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, false);
            // baixo (-Y)
            quad(topBottom, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, false);
        }

        /** Placa fina com a frente desenhada (display, fita...). */
        void plate(float x0, float y0, float x1, float y1, float z0, float z1, int front, int edge) {
            box(x0, y0, z0, x1, y1, z1, front, edge, edge, edge);
        }

        /** Disco redondo (textura recortada) centrado em cx,cy com raio r. */
        void round(float cx, float cy, float r, float z0, float z1, int region) {
            box(cx - r, cy - r, z0, cx + r, cy + r, z1, region, SIDE_DARK, SIDE_DARK, SIDE_DARK);
        }

        /** Anel redondo (borda cromada do alto-falante). */
        void ring(float cx, float cy, float r, float z0, float z1, int region, int edge) {
            // só a frente: o anel tem o centro transparente para mostrar o cone
            float x0 = (cx - r) / 16, y0 = (cy - r) / 16, x1 = (cx + r) / 16, y1 = (cy + r) / 16, zf = z0 / 16;
            quad(region, 0, 0, -1, x0, y0, zf, x0, y1, zf, x1, y1, zf, x1, y0, zf, true);
            // borda do anel vista de lado: a mesma textura redonda, um pouco recuada, para dar profundidade
            float zm = (z0 + (z1 - z0) * 0.5f) / 16;
            quad(region, 0, 0, -1, x0, y0, zm, x0, y1, zm, x1, y1, zm, x1, y0, zm, true);
        }

        /**
         * Um quadrilátero no sentido anti-horário visto de fora.
         * frontFace=true usa o mapeamento "em pé" da textura para quem olha a frente.
         */
        void quad(int region, float nx, float ny, float nz,
                  float ax, float ay, float az, float bx, float by, float bz,
                  float cx, float cy, float cz, float dx, float dy, float dz, boolean frontFace) {
            float u0 = (region % 4) * 0.25f + 0.25f / 32, v0 = (region / 4) * 0.25f + 0.25f / 32;
            float u1 = u0 + 0.25f - 0.25f / 16, v1 = v0 + 0.25f - 0.25f / 16;
            if (frontFace) {
                // a=(x0,y0) b=(x0,y1) c=(x1,y1) d=(x1,y0); quem olha a frente vê x0 à direita
                vertex(ax, ay, az, u1, v1, nx, ny, nz);
                vertex(bx, by, bz, u1, v0, nx, ny, nz);
                vertex(cx, cy, cz, u0, v0, nx, ny, nz);
                vertex(dx, dy, dz, u0, v1, nx, ny, nz);
            } else {
                vertex(ax, ay, az, u0, v1, nx, ny, nz);
                vertex(bx, by, bz, u1, v1, nx, ny, nz);
                vertex(cx, cy, cz, u1, v0, nx, ny, nz);
                vertex(dx, dy, dz, u0, v0, nx, ny, nz);
            }
        }

        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            vc.addVertex(pose, x, y, z)
                    .setColor(-1)
                    .setUv(u, v)
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}
