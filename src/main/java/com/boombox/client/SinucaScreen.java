package com.boombox.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sinuca contra um aldeão. Mire com o mouse, segure o botão para dar força e solte para a tacada.
 * As bolas batem nas tabelas e umas nas outras e caem nas 6 caçapas. Cada bola colorida vale 1 ponto e a preta 3;
 * se a branca cair, o outro ganha 1 ponto. Quem tiver mais pontos quando a mesa ficar vazia ganha.
 */
public class SinucaScreen extends Screen {
    private static final float TW = 300, TH = 150, R = 5f, POCKET = 9f, FRICTION = 0.985f;
    private static final int[] COLORS = {0xFFE8C020, 0xFF2050D0, 0xFFD02020, 0xFF7A2BB0, 0xFFE07020, 0xFF20A040, 0xFF8A2020};

    private static final class Ball {
        float x, y, vx, vy;
        final int color;
        final boolean cue, black;
        boolean in;

        Ball(float x, float y, int color, boolean cue, boolean black) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.cue = cue;
            this.black = black;
        }
    }

    private final List<Ball> balls = new ArrayList<>();
    private final Random rnd = new Random();
    private Ball cue;
    private boolean playerTurn = true, charging, moving, pottedThisShot, foulThisShot;
    private float power;
    private int myScore, hisScore, botDelay;
    private String message = "Sua vez: mire com o mouse, segure e solte";
    private double mouseX, mouseY;

    public SinucaScreen() {
        super(Component.literal("Sinuca"));
        cue = new Ball(TW * 0.25f, TH / 2, 0xFFF4F4F0, true, false);
        balls.add(cue);
        // triângulo de 8 bolas (a preta no meio)
        float sx = TW * 0.68f, sy = TH / 2;
        int k = 0;
        for (int row = 0; row < 4; row++) {
            for (int i = 0; i <= row; i++) {
                float bx = sx + row * (R * 1.8f), by = sy + (i - row / 2f) * (R * 2.05f);
                boolean black = row == 2 && i == 1;
                if (k >= 8) break;
                balls.add(new Ball(bx, by, black ? 0xFF101010 : COLORS[k % COLORS.length], false, black));
                k++;
            }
        }
    }

    private float left() {
        return width / 2f - TW / 2;
    }

    private float top() {
        return height / 2f - TH / 2 + 8;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Sair"), b -> onClose()).bounds(width / 2 - 20, (int) (top() + TH + 18), 40, 20).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (playerTurn && !moving && !cue.in) {
            charging = true;
            power = 0;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (charging) {
            charging = false;
            shoot((float) (mouseX - left()), (float) (mouseY - top()), power);
        }
        return super.mouseReleased(event);
    }

    private void shoot(float tx, float ty, float pow) {
        float dx = tx - cue.x, dy = ty - cue.y, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        float speed = 2f + pow * 9f;
        cue.vx = dx / len * speed;
        cue.vy = dy / len * speed;
        moving = true;
        pottedThisShot = foulThisShot = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (charging) power = Math.min(1f, power + 0.04f);
        if (moving) {
            for (int step = 0; step < 4; step++) physics();
            boolean still = balls.stream().allMatch(b -> b.in || (Math.abs(b.vx) < 0.03f && Math.abs(b.vy) < 0.03f));
            if (still) endShot();
        } else if (!playerTurn && --botDelay <= 0) {
            botShot();
        }
    }

    private void physics() {
        for (Ball b : balls) {
            if (b.in) continue;
            b.x += b.vx / 4;
            b.y += b.vy / 4;
            b.vx *= (float) Math.pow(FRICTION, 0.25);
            b.vy *= (float) Math.pow(FRICTION, 0.25);
            // caçapas: 4 cantos e meio das laterais compridas
            float[][] pockets = {{0, 0}, {TW / 2, 0}, {TW, 0}, {0, TH}, {TW / 2, TH}, {TW, TH}};
            for (float[] p : pockets) {
                if (Math.hypot(b.x - p[0], b.y - p[1]) < POCKET) {
                    b.in = true;
                    b.vx = b.vy = 0;
                    if (b.cue) foulThisShot = true;
                    else {
                        pottedThisShot = true;
                        int pts = b.black ? 3 : 1;
                        if (playerTurn) myScore += pts;
                        else hisScore += pts;
                    }
                }
            }
            if (b.in) continue;
            if (b.x < R) { b.x = R; b.vx = -b.vx * 0.85f; }
            if (b.x > TW - R) { b.x = TW - R; b.vx = -b.vx * 0.85f; }
            if (b.y < R) { b.y = R; b.vy = -b.vy * 0.85f; }
            if (b.y > TH - R) { b.y = TH - R; b.vy = -b.vy * 0.85f; }
        }
        // colisões entre bolas (elásticas, massas iguais)
        for (int i = 0; i < balls.size(); i++) {
            Ball a = balls.get(i);
            if (a.in) continue;
            for (int j = i + 1; j < balls.size(); j++) {
                Ball c = balls.get(j);
                if (c.in) continue;
                float dx = c.x - a.x, dy = c.y - a.y, d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= 2 * R || d < 0.001f) continue;
                float nx = dx / d, ny = dy / d, overlap = 2 * R - d;
                a.x -= nx * overlap / 2; a.y -= ny * overlap / 2;
                c.x += nx * overlap / 2; c.y += ny * overlap / 2;
                float rel = (a.vx - c.vx) * nx + (a.vy - c.vy) * ny;
                if (rel > 0) {
                    a.vx -= rel * nx; a.vy -= rel * ny;
                    c.vx += rel * nx; c.vy += rel * ny;
                }
            }
        }
    }

    private void endShot() {
        moving = false;
        for (Ball b : balls) b.vx = b.vy = 0;
        if (foulThisShot) {
            if (playerTurn) hisScore++;
            else myScore++;
            cue.in = false;
            cue.x = TW * 0.25f;
            cue.y = TH / 2;
            message = (playerTurn ? "A branca caiu! Ponto do aldeão." : "O aldeão matou a branca! Ponto seu.");
        }
        boolean over = balls.stream().filter(b -> !b.cue).allMatch(b -> b.in);
        if (over) {
            message = myScore > hisScore ? "§aVocê ganhou! " + myScore + " x " + hisScore
                    : myScore < hisScore ? "§cO aldeão ganhou. " + myScore + " x " + hisScore : "Empate! " + myScore + " x " + hisScore;
            playerTurn = true;
            return;
        }
        // acertou uma bola: joga de novo; senão passa a vez
        if (!pottedThisShot || foulThisShot) playerTurn = !playerTurn;
        if (!foulThisShot) message = playerTurn ? "Sua vez" : "Vez do aldeão...";
        botDelay = 30;
    }

    /** O aldeão escolhe a bola mais fácil para uma caçapa (com um pouco de erro). */
    private void botShot() {
        float[][] pockets = {{0, 0}, {TW / 2, 0}, {TW, 0}, {0, TH}, {TW / 2, TH}, {TW, TH}};
        float bestScore = -1, aimX = TW / 2, aimY = TH / 2;
        for (Ball b : balls) {
            if (b.in || b.cue) continue;
            for (float[] p : pockets) {
                float px = p[0] - b.x, py = p[1] - b.y, pl = (float) Math.hypot(px, py);
                // ponto de contato: atrás da bola, do lado oposto à caçapa
                float gx = b.x - px / pl * 2 * R, gy = b.y - py / pl * 2 * R;
                float cx = gx - cue.x, cy = gy - cue.y, cl = (float) Math.hypot(cx, cy);
                float align = (cx * px + cy * py) / (cl * pl + 0.001f);
                float sc = align * 2 - (cl + pl) / 400f;
                if (sc > bestScore) {
                    bestScore = sc;
                    aimX = gx + (rnd.nextFloat() - 0.5f) * 3f;
                    aimY = gy + (rnd.nextFloat() - 0.5f) * 3f;
                }
            }
        }
        shoot(aimX, aimY, 0.45f + rnd.nextFloat() * 0.4f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        mouseX = mx;
        mouseY = my;
        int l = (int) left(), t = (int) top();
        g.fill(l - 14, t - 14, l + (int) TW + 14, t + (int) TH + 14, 0xFF5A3418); // madeira
        g.fill(l, t, l + (int) TW, t + (int) TH, 0xFF1A7A3A);                     // feltro
        float[][] pockets = {{0, 0}, {TW / 2, 0}, {TW, 0}, {0, TH}, {TW / 2, TH}, {TW, TH}};
        for (float[] p : pockets) disc(g, l + p[0], t + p[1], POCKET, 0xFF050505);
        for (Ball b : balls) {
            if (b.in) continue;
            disc(g, l + b.x, t + b.y, R, b.color);
            if (!b.cue && !b.black) disc(g, l + b.x, t + b.y, R * 0.45f, 0xFFF4F4F0);
        }
        // taco e mira
        if (playerTurn && !moving && !cue.in) {
            float dx = (float) (mx - l - cue.x), dy = (float) (my - t - cue.y), len = (float) Math.max(1, Math.hypot(dx, dy));
            float ux = dx / len, uy = dy / len, back = 8 + power * 30;
            for (int i = 0; i < 60; i++) { // taco atrás da branca
                float px = cue.x - ux * (back + i), py = cue.y - uy * (back + i);
                g.fill(l + (int) px, t + (int) py, l + (int) px + 2, t + (int) py + 2, i < 6 ? 0xFFEEEEEE : 0xFFC8A060);
            }
            for (int i = 0; i < 40; i += 3) { // linha de mira
                float px = cue.x + ux * (R + i * 2), py = cue.y + uy * (R + i * 2);
                g.fill(l + (int) px, t + (int) py, l + (int) px + 1, t + (int) py + 1, 0x88FFFFFF);
            }
        }
        super.extractRenderState(g, mx, my, partialTick);
        g.centeredText(font, "§l♦ Sinuca no bar do Seu Zé ♦", width / 2, t - 34, 0xFFFFFFFF);
        g.centeredText(font, "Você §e" + myScore + "§f x §e" + hisScore + "§f Aldeão", width / 2, t - 24, 0xFFFFFFFF);
        g.centeredText(font, message, width / 2, t + (int) TH + 6, 0xFFFFE070);
        if (charging) g.fill(l, t + (int) TH + 16, l + (int) (60 * power), t + (int) TH + 20, 0xFFFF5030);
    }

    private static void disc(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
        int ri = (int) Math.ceil(r);
        for (int y = -ri; y <= ri; y++) {
            int w = (int) Math.sqrt(Math.max(0, r * r - y * y));
            g.fill((int) cx - w, (int) cy + y, (int) cx + w + 1, (int) cy + y + 1, color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
