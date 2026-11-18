package com.boombox.client.render;

import com.boombox.BoomboxMod;
import com.boombox.SpeakerType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Caixas de festa (estilo "party speaker"): gabinete preto em pé, woofers com anel de LED
 * que muda de cor com a música, tweeters, barra de luz estroboscópica e alça.
 * Três tamanhos: pequena (1 woofer), média (2 woofers) e grande (2 woofers grandes, rodinhas,
 * alça de puxar e faixas de LED nas laterais).
 */
public final class PartyGeometry {
    public static final Identifier TEXTURE = BoomboxMod.id("textures/entity/party_speaker.png");
    public static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);

    private static final int CAB = 0, GRILLE = 1, TRIM = 2, RIM = 4, SURROUND = 5, CONE = 6, CAP = 7,
            LED_RING = 8, LED = 9, PANEL = 10, RUBBER = 11, WHEEL = 12, METAL = 13, TWEETER = 14, WHITE = 15;

    /** Arrumação de cada tamanho (pixels). */
    private record Layout(float w, float d, float bottom, float top, float wooferR, float[][] woofers,
                          float tweeterR, float[][] tweeters, float[] strobe, boolean wheels, boolean sideStrips,
                          boolean telescopic) {}

    private static final Layout SMALL = new Layout(9, 7, 0.5f, 12f, 3.0f,
            new float[][]{{8, 4.7f}},
            0.75f, new float[][]{{5.7f, 9.55f}, {10.3f, 9.55f}},
            new float[]{6.9f, 10.95f, 9.1f, 11.45f}, false, false, false);
    private static final Layout MEDIUM = new Layout(12, 10, 0.6f, 20f, 3.6f,
            new float[][]{{8, 5.35f}, {8, 15.0f}},
            0.75f, new float[][]{{5.3f, 10.15f}, {10.7f, 10.15f}},
            new float[]{6.6f, 9.8f, 9.4f, 10.5f}, false, false, false);
    private static final Layout LARGE = new Layout(15, 12, 0.8f, 22.5f, 4.2f,
            new float[][]{{8, 6.5f}, {8, 16.7f}},
            0.85f, new float[][]{{2.5f, 11.6f}, {13.5f, 11.6f}},
            new float[]{3.5f, 21.9f, 12.5f, 22.3f}, true, true, true);

    private PartyGeometry() {}

    private static Layout layout(SpeakerType type) {
        return switch (type) {
            case PARTY_SMALL -> SMALL;
            case PARTY_LARGE -> LARGE;
            default -> MEDIUM;
        };
    }

    /** Desenha a caixa. bass/treble de 0 a 1 (0 e 0 = parada). */
    public static void draw(SpeakerType type, PoseStack.Pose pose, VertexConsumer vc, int light, int overlay,
                            float bass, float treble) {
        Layout L = layout(type);
        Geo c = new Geo(pose, vc, light, overlay);
        double t = System.nanoTime() / 1e9;
        boolean playing = bass > 0.001f || treble > 0.001f;
        float x0 = 8 - L.w / 2, x1 = 8 + L.w / 2, z0 = 8 - L.d / 2, z1 = 8 + L.d / 2;
        float zf = z0; // frente do gabinete

        // ---- pés / rodas
        if (L.wheels) {
            float wr = 1.6f, wz = z1 - 1.9f;
            for (float off = 0.12f; off < 0.6f; off += 0.14f) { // camadas dão espessura à roda
                c.sideDisc(x0 - off, wr, wz, wr, WHEEL, false);
                c.sideDisc(x1 + off, wr, wz, wr, WHEEL, true);
            }
            c.box(x0 + 1, 0, z0 + 1, x0 + 3, L.bottom, z0 + 3, RUBBER);
            c.box(x1 - 3, 0, z0 + 1, x1 - 1, L.bottom, z0 + 3, RUBBER);
        } else {
            for (float[] f : new float[][]{{x0 + 0.8f, z0 + 0.8f}, {x1 - 2.3f, z0 + 0.8f}, {x0 + 0.8f, z1 - 2.3f}, {x1 - 2.3f, z1 - 2.3f}}) {
                c.box(f[0], 0, f[1], f[0] + 1.5f, L.bottom, f[1] + 1.5f, RUBBER);
            }
        }

        // ---- gabinete
        c.box(x0, L.bottom, z0, x1, L.top, z1, CAB, CAB, CAB, GRILLE);
        // quinas arredondadas
        c.box(x0 - 0.1f, L.bottom, z0 - 0.1f, x0 + 0.45f, L.top, z0 + 0.45f, TRIM);
        c.box(x1 - 0.45f, L.bottom, z0 - 0.1f, x1 + 0.1f, L.top, z0 + 0.45f, TRIM);
        c.box(x0 - 0.1f, L.bottom, z1 - 0.45f, x0 + 0.45f, L.top, z1 + 0.1f, TRIM);
        c.box(x1 - 0.45f, L.bottom, z1 - 0.45f, x1 + 0.1f, L.top, z1 + 0.1f, TRIM);
        // painel de controle em cima
        c.box(x0 + 0.6f, L.top, z0 + 0.6f, x1 - 0.6f, L.top + 0.15f, z1 - 0.6f, TRIM, TRIM, PANEL, TRIM);

        // ---- alça
        float top = L.top + 0.15f;
        if (L.telescopic) {
            // alça de puxar (estilo mala) na traseira
            c.box(x0 + 3.2f, L.top - 6, z1, x0 + 3.9f, 23.4f, z1 + 0.6f, METAL);
            c.box(x1 - 3.9f, L.top - 6, z1, x1 - 3.2f, 23.4f, z1 + 0.6f, METAL);
            c.box(x0 + 2.9f, 23.2f, z1 - 0.1f, x1 - 2.9f, 24f, z1 + 0.8f, RUBBER);
            // pegadores nas laterais
            c.box(x0 - 0.4f, L.top - 4, 7, x0, L.top - 2.6f, 9, TRIM);
            c.box(x1, L.top - 4, 7, x1 + 0.4f, L.top - 2.6f, 9, TRIM);
        } else {
            float hTop = type == SpeakerType.PARTY_SMALL ? 13.5f : 21.5f;
            float hx0 = x0 + 1.4f, hx1 = x1 - 1.4f;
            c.box(hx0, top, 7.5f, hx0 + 0.8f, hTop - 0.6f, 8.5f, TRIM);
            c.box(hx1 - 0.8f, top, 7.5f, hx1, hTop - 0.6f, 8.5f, TRIM);
            c.box(hx0, hTop - 0.9f, 7.3f, hx1, hTop, 8.7f, RUBBER);
        }

        // ---- luzes: cor gira com o tempo e "pula" com o grave
        float hueBase = (float) ((t * 0.06) % 1.0) + bass * 0.12f;
        float glowB = playing ? 0.3f + 0.7f * Math.min(1f, bass * 1.25f) : 0.12f;
        float glowT = playing ? 0.25f + 0.75f * Math.min(1f, treble * 1.3f) : 0.1f;

        // barra estroboscópica: pisca branco nas batidas fortes
        float[] s = L.strobe;
        if (bass > 0.72f) c.glow(gray(0.55f + 0.45f * Math.min(1f, (bass - 0.72f) / 0.2f)));
        else c.glow(hsv(hueBase + 0.5f, 0.6f, glowT * 0.7f));
        c.plate(s[0], s[1], s[2], s[3], zf - 0.12f, zf, WHITE, WHITE);
        c.reset();

        // faixas de LED nas quinas da frente (só a grande)
        if (L.sideStrips) {
            c.glow(hsv(hueBase + 0.25f, 0.9f, glowT));
            c.plate(x0 + 0.5f, 2.2f, x0 + 0.95f, L.top - 1.6f, zf - 0.1f, zf, LED, LED);
            c.plate(x1 - 0.95f, 2.2f, x1 - 0.5f, L.top - 1.6f, zf - 0.1f, zf, LED, LED);
            c.reset();
        }

        // ---- woofers
        float R = L.wooferR;
        float maxE = R * 0.12f;                 // curso máximo do cone
        float rimFront = zf - (maxE + 0.35f);   // o aro é mais fundo que o curso: o cone nunca sai do aro
        float wobble = (float) (0.8 + 0.2 * Math.sin(t * Math.PI * 2 * 8));
        float e = maxE * (1f - (float) Math.exp(-2.4f * bass)) * wobble;
        float grow = 1f + Math.min(1f, bass) * 0.03f;
        for (int i = 0; i < L.woofers.length; i++) {
            float cx = L.woofers[i][0], cy = L.woofers[i][1];
            c.glow(hsv(hueBase + i * 0.33f, 0.95f, glowB));
            c.face(cx, cy, R * 1.2f, zf - 0.03f, LED_RING);
            c.reset();
            c.ring(cx, cy, R * 1.03f, rimFront, zf - 0.04f, RIM, 6);
            c.face(cx, cy, R * 0.87f, zf - 0.05f, SURROUND);
            c.face(cx, cy, R * 0.72f * grow, zf - 0.1f - e * 0.8f, CONE);
            c.face(cx, cy, R * 0.28f * grow, zf - 0.16f - e, CAP);
        }

        // ---- tweeters
        float jitter = (float) (0.7 + 0.3 * Math.sin(t * Math.PI * 2 * 23));
        float et = 0.12f * Math.min(1f, treble) * jitter;
        for (float[] tw : L.tweeters) {
            c.ring(tw[0], tw[1], L.tweeterR * 1.15f, zf - 0.3f, zf - 0.04f, RIM, 3);
            c.face(tw[0], tw[1], L.tweeterR * 0.78f, zf - 0.08f - et, TWEETER);
        }
    }

    /** Cantos do modelo (em blocos), para o item. */
    public static float[][] extents(SpeakerType type) {
        Layout L = layout(type);
        float h = L.telescopic ? 24 : type == SpeakerType.PARTY_SMALL ? 13.5f : 21.5f;
        float pad = L.wheels ? 0.6f : 0.1f;
        return new float[][]{
                {(8 - L.w / 2 - pad) / 16, 0, (8 - L.d / 2 - 1) / 16},
                {(8 + L.w / 2 + pad) / 16, h / 16, (8 + L.d / 2 + 1) / 16}
        };
    }

    private static int gray(float v) {
        int g = Math.round(Math.max(0, Math.min(1, v)) * 255);
        return 0xFF000000 | g << 16 | g << 8 | g;
    }

    private static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        int i = (int) (h * 6);
        float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), u = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = u; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = u; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = u; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }
}
