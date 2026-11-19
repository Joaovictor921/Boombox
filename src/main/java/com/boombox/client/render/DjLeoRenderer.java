package com.boombox.client.render;

import com.boombox.BoomboxMod;
import com.boombox.client.ClientBoomboxManager;
import com.boombox.entity.DjLeo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** DJ Leo: corpo de jogador com a skin dele; dança quando tem uma caixa tocando perto. */
public class DjLeoRenderer extends HumanoidMobRenderer<DjLeo, DjLeoRenderer.State, DjLeoRenderer.Model> {
    public static final Identifier TEXTURE = BoomboxMod.id("textures/entity/dj_leo.png");

    public static class State extends HumanoidRenderState {
        public boolean dancing;
        public float bass;
        public boolean sleeping;
    }

    private final float[] levels = new float[2];

    public DjLeoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DjLeo entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.sleeping = entity.sleepingDay();
        ClientBoomboxManager.State playing = ClientBoomboxManager.playingNear(Minecraft.getInstance(), entity.position(), 16);
        state.dancing = playing != null;
        if (playing != null) {
            ClientBoomboxManager.levelsOf(playing, levels);
            state.bass = levels[0];
        } else {
            state.bass = 0;
        }
    }

    /** Dormindo: deitado no chão, de barriga para cima. */
    @Override
    protected void setupRotations(State state, com.mojang.blaze3d.vertex.PoseStack ps, float bodyRot, float scale) {
        super.setupRotations(state, ps, bodyRot, scale);
        if (state.sleeping) {
            ps.translate(0f, 0.25f, 0f);
            ps.rotateDegrees(com.mojang.math.Axis.XP, -90f);
        }
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return TEXTURE;
    }

    /** Modelo de jogador (com a segunda camada da skin) e a dança do DJ. */
    public static class Model extends HumanoidModel<State> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            if (s.sleeping) {
                head.xRot = 0f;
                head.yRot = 0f;
                rightArm.xRot = leftArm.xRot = 0f;
                rightLeg.xRot = leftLeg.xRot = 0f;
                return;
            }
            if (!s.dancing) return;
            float t = s.ageInTicks;
            float beat = (float) Math.sin(t * 0.65f);             // ~124 batidas por minuto
            float kick = Math.min(1f, s.bass * 1.4f);
            head.xRot += 0.18f + 0.22f * Math.abs(beat) * (0.6f + 0.4f * kick); // balança a cabeça
            if (((int) (t / 80)) % 2 == 0) {
                // uma mão no fone/mesa, a outra para cima no ritmo
                rightArm.xRot = -2.7f + 0.35f * beat;
                rightArm.zRot = 0.25f;
                leftArm.xRot = -1.15f;
                leftArm.yRot = 0.45f + 0.25f * (float) Math.sin(t * 1.3f); // "scratch" na mesa
                leftArm.zRot = 0f;
            } else {
                // as duas mãos para cima
                rightArm.xRot = -2.9f + 0.3f * beat;
                leftArm.xRot = -2.9f - 0.3f * beat;
                rightArm.zRot = 0.35f;
                leftArm.zRot = -0.35f;
            }
            body.yRot = 0.12f * (float) Math.sin(t * 0.325f);
        }
    }
}
