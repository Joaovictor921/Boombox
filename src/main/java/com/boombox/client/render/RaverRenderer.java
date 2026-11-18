package com.boombox.client.render;

import com.boombox.client.ClientBoomboxManager;
import com.boombox.entity.Raver;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;

/** Aldeão festeiro: modelo e roupas de aldeão normal, com dança quando tem música perto. */
public class RaverRenderer extends MobRenderer<Raver, RaverRenderer.State, RaverRenderer.Model> {
    private static final Identifier BASE = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");

    public static class State extends VillagerRenderState {
        public int look = -1;
        public boolean dancing;
        public float bass;
        public float seed;
    }

    private final float[] levels = new float[2];

    public RaverRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
        addLayer(new Clothes(this));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Raver e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        s.look = e.look();
        s.seed = (e.getId() * 0.618f) % 1f;
        ClientBoomboxManager.State playing = ClientBoomboxManager.playingNear(Minecraft.getInstance(), e.position(), 24);
        s.dancing = playing != null;
        if (playing != null) {
            ClientBoomboxManager.levelsOf(playing, levels);
            s.bass = levels[0];
        } else {
            s.bass = 0;
        }
    }

    @Override
    public Identifier getTextureLocation(State s) {
        return BASE;
    }

    /** Roupa do bioma + roupa da profissão por cima (como os aldeões normais). */
    private static class Clothes extends RenderLayer<State, Model> {
        Clothes(RenderLayerParent<State, Model> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack ps, SubmitNodeCollector collector, int light, State s, float yRot, float xRot) {
            if (s.isInvisible || s.look < 0) return;
            String type = Raver.TYPES[Math.min(Raver.TYPES.length - 1, s.look / 16)];
            renderColoredCutoutModel(getParentModel(), Identifier.withDefaultNamespace("textures/entity/villager/type/" + type + ".png"),
                    ps, collector, light, s, -1, 1);
            int prof = s.look % 16;
            if (prof < Raver.PROFESSIONS.length) {
                renderColoredCutoutModel(getParentModel(),
                        Identifier.withDefaultNamespace("textures/entity/villager/profession/" + Raver.PROFESSIONS[prof] + ".png"),
                        ps, collector, light, s, -1, 2);
            }
        }
    }

    /** Modelo de aldeão com a dança: cabeça balançando, braços para cima e corpo gingando. */
    public static class Model extends VillagerModel {
        private final ModelPart head, arms, body, rightLeg, leftLeg;

        public Model(ModelPart root) {
            super(root);
            head = root.getChild("head");
            arms = root.getChild("arms");
            body = root.getChild("body");
            rightLeg = root.getChild("right_leg");
            leftLeg = root.getChild("left_leg");
        }

        @Override
        public void setupAnim(VillagerRenderState state) {
            super.setupAnim(state);
            if (!(state instanceof State s) || !s.dancing) return;
            float t = s.ageInTicks + s.seed * 40f;
            float beat = (float) Math.sin(t * 0.65f);
            float kick = Math.min(1f, s.bass * 1.4f);
            head.xRot += 0.15f + 0.25f * Math.abs(beat) * (0.5f + 0.5f * kick);
            head.zRot = 0.12f * (float) Math.sin(t * 0.325f);
            // braços (que nos aldeões ficam cruzados) sobem e descem no ritmo: "mãos para o alto"
            arms.xRot = -2.2f + 0.45f * beat;
            body.zRot = 0.08f * (float) Math.sin(t * 0.325f);
            rightLeg.xRot = 0.25f * beat;
            leftLeg.xRot = -0.25f * beat;
        }
    }
}
