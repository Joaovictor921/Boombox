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
    private static final int BODY = 0, TRIM = 1, CHROME = 2, RIM = 4, SURROUND = 5, CONE = 6, CAP = 7,
            LCD = 8, CASSETTE = 9, RED = 10, STRIPE = 11, BUTTON = 12, GRILLE = 13, EQ = 14, PEAK = 15;

    private static final float[][] WOOFERS = {{3.6f, 4.0f}, {12.4f, 4.0f}};
    private static final float[][] TWEETERS = {{3.6f, 8.05f}, {12.4f, 8.05f}};

    /** Frente do aro dos woofers e quanto o cone pode andar (pixels): o cone fica sempre atrás do aro. */
    private static final float WOOFER_RIM_FRONT = 3.70f;
    private static final float WOOFER_MAX_TRAVEL = 0.5f;

    private BoomboxGeometry() {}

    /** Desenha a boombox inteira. bass/treble de 0 a 1. */
    public static void draw(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float bass, float treble) {
        Geo c = new Geo(pose, vc, light, overlay);
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

        // woofers: o cone vai para frente e para trás com o grave, mas sempre por dentro do aro
        // (o aro tem espessura e o cone nunca passa da frente dele, mesmo com grave forte)
        float wobble = (float) (0.8 + 0.2 * Math.sin(t * Math.PI * 2 * 9));
        float e = WOOFER_MAX_TRAVEL * (1f - (float) Math.exp(-2.4f * bass)) * wobble;
        float grow = 1f + Math.min(1f, bass) * 0.03f;
        for (float[] w : WOOFERS) {
            float cx = w[0], cy = w[1];
            c.ring(cx, cy, 2.6f, WOOFER_RIM_FRONT, 4.46f, RIM, 5);
            c.face(cx, cy, 2.25f, 4.47f, SURROUND);
            c.face(cx, cy, 1.85f * grow, 4.40f - e * 0.8f, CONE);
            c.face(cx, cy, 0.7f * grow, 4.32f - e, CAP);
        }

        // tweeters: vibram com o agudo (rápido e curtinho)
        float jitter = (float) (0.7 + 0.3 * Math.sin(t * Math.PI * 2 * 23));
        float et = 0.18f * Math.min(1f, treble) * jitter;
        for (float[] w : TWEETERS) {
            float cx = w[0], cy = w[1];
            c.ring(cx, cy, 1.15f, 3.98f, 4.46f, RIM, 3);
            c.face(cx, cy, 0.85f, 4.40f - et * 0.6f, CONE);
            c.face(cx, cy, 0.34f, 4.32f - et, CAP);
        }
    }

    /** Extensão (cantos) do modelo, em blocos, para o item. */
    public static final float[][] EXTENTS = {
            {0.5f / 16, 0, 3.8f / 16}, {15.6f / 16, 16f / 16, 11.6f / 16}
    };
}
