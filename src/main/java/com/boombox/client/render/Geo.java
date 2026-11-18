package com.boombox.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Ajudante para desenhar modelos em código. Coordenadas em pixels (16 = 1 bloco), frente para -Z.
 * A textura é uma grade 4x4 de regiões; a região 3 é usada nas laterais das peças redondas.
 */
final class Geo {
    static final int SIDE_DARK = 3;
    /** Luz máxima (para LEDs que brilham no escuro). */
    static final int FULL_BRIGHT = 0xF000F0;

    final PoseStack.Pose pose;
    final VertexConsumer vc;
    final int baseLight, overlay;
    int light;
    /** Cor ARGB multiplicada na textura (-1 = sem tingir). */
    int color = -1;

    /** Quantas linhas de regiões a textura tem (4 colunas sempre). */
    final int rows;

    Geo(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay) {
        this(pose, vc, light, overlay, 4);
    }

    Geo(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, int rows) {
        this.rows = rows;
        this.pose = pose;
        this.vc = vc;
        this.baseLight = light;
        this.light = light;
        this.overlay = overlay;
    }

    /** Liga o "brilho" (cor e luz própria) para as próximas peças; reset() desliga. */
    void glow(int argb) {
        color = argb;
        light = FULL_BRIGHT;
    }

    void reset() {
        color = -1;
        light = baseLight;
    }

    void box(float x0, float y0, float z0, float x1, float y1, float z1, int region) {
        box(x0, y0, z0, x1, y1, z1, region, region, region, region);
    }

    /** Caixa com regiões separadas para frente (-Z), lados, topo/baixo e traseira (+Z). */
    void box(float x0, float y0, float z0, float x1, float y1, float z1, int front, int sides, int topBottom, int back) {
        x0 /= 16; y0 /= 16; z0 /= 16; x1 /= 16; y1 /= 16; z1 /= 16;
        quad(front, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, true);
        quad(back, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, false);
        quad(sides, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, false);
        quad(sides, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, false);
        quad(topBottom, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, false);
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

    /** Só a face da frente de um disco/anel (o centro transparente da textura fica vazado). */
    void face(float cx, float cy, float r, float z, int region) {
        float x0 = (cx - r) / 16, y0 = (cy - r) / 16, x1 = (cx + r) / 16, y1 = (cy + r) / 16, zf = z / 16;
        quad(region, 0, 0, -1, x0, y0, zf, x0, y1, zf, x1, y1, zf, x1, y0, zf, true);
    }

    /**
     * Aro do alto-falante com espessura: várias camadas da mesma textura entre z0 (frente) e z1,
     * para que, visto de lado, pareça uma borda sólida que esconde o cone.
     */
    void ring(float cx, float cy, float r, float z0, float z1, int region, int layers) {
        for (int i = 0; i < layers; i++) {
            face(cx, cy, r, z0 + (z1 - z0) * i / Math.max(1, layers - 1), region);
        }
    }

    /** Disco virado para o lado (±X), ex.: rodas. angle gira o disco (radianos) em volta do centro. */
    void sideDisc(float x, float cy, float cz, float r, int region, boolean positive, float angle) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle), xf = x / 16;
        // cantos (dy, dz) girados
        float[][] k = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        float[] ys = new float[4], zs = new float[4];
        for (int i = 0; i < 4; i++) {
            ys[i] = (cy + k[i][0] * c - k[i][1] * s) / 16;
            zs[i] = (cz + k[i][0] * s + k[i][1] * c) / 16;
        }
        // ordem igual à do disco sem giro: (y0,z0) (y1,z0) (y1,z1) (y0,z1)
        if (positive) quad(region, 1, 0, 0, xf, ys[0], zs[0], xf, ys[1], zs[1], xf, ys[2], zs[2], xf, ys[3], zs[3], false);
        else quad(region, -1, 0, 0, xf, ys[0], zs[0], xf, ys[3], zs[3], xf, ys[2], zs[2], xf, ys[1], zs[1], false);
    }

    /**
     * Um quadrilátero no sentido anti-horário visto de fora.
     * frontFace=true usa o mapeamento "em pé" da textura para quem olha a frente.
     */
    void quad(int region, float nx, float ny, float nz,
              float ax, float ay, float az, float bx, float by, float bz,
              float cx, float cy, float cz, float dx, float dy, float dz, boolean frontFace) {
        float rh = 1f / rows;
        float u0 = (region % 4) * 0.25f + 0.25f / 32, v0 = (region / 4) * rh + rh / 32;
        float u1 = u0 + 0.25f - 0.25f / 16, v1 = v0 + rh - rh / 16;
        if (frontFace) {
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
                .setColor(color)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
