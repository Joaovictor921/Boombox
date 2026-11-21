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
        public boolean bar;
        public boolean cards;
        public int instrument;
        public boolean glow;
        public int role;
        public float hicSway;
        /** 3.4: cor da camisa do time (0 = padrão) e fantasia do baile (-1 = sem). */
        public int shirt;
        public int costume = -1;
        /** 3.5: goleiro voando na bola (-1 / 1). */
        public int dive;
    }

    private final float[] levels = new float[2];

    public RaverRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
        addLayer(new Clothes(this));
        addLayer(new Beer(this)); // cerveja na mão (no bar)
        addLayer(new Extras(this)); // camisa do time da várzea e pulseira de luz
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
        s.bar = e.atBar();
        s.cards = e.playingCards();
        s.instrument = e.instrument();
        s.glow = e.glow();
        s.role = e.role();
        s.shirt = e.getShirt();
        s.dive = e.dive();
        s.costume = s.role == 0 && !s.bar && com.boombox.bar.Fantasia.tonight(e.level()) ? com.boombox.bar.Fantasia.costumeOf(e.getUUID()) : -1;
        net.minecraft.client.renderer.entity.state.HoldingEntityRenderState.extractHoldingEntityRenderState(e, s, itemModelResolver);
        ClientBoomboxManager.State playing = ClientBoomboxManager.playingNear(Minecraft.getInstance(), e.position(), 24);
        boolean sport = s.role >= com.boombox.entity.RaverRoles.TEAM_A && s.role != com.boombox.entity.RaverRoles.BARTENDER
                && s.role != com.boombox.entity.RaverRoles.LUAU && s.role != com.boombox.entity.RaverRoles.VIP;
        s.dancing = playing != null && !s.bar && !sport;
        if (playing != null) {
            ClientBoomboxManager.levelsOf(playing, levels);
            s.bass = levels[0];
        } else {
            s.bass = 0;
        }
    }

    /** Goleiro voando: o corpo deita de lado, na direção do pulo. */
    @Override
    protected void setupRotations(State s, com.mojang.blaze3d.vertex.PoseStack ps, float bodyRot, float scale) {
        super.setupRotations(s, ps, bodyRot, scale);
        if (s.dive != 0) {
            ps.translate(0f, 0.6f, 0f);
            ps.rotateDegrees(com.mojang.math.Axis.ZP, s.dive * 75f);
            ps.translate(0f, -0.6f, 0f);
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

    /** Item segurado nos braços cruzados (como os aldeões normais seguram as coisas). */
    private static class Beer extends RenderLayer<State, Model> {
        Beer(RenderLayerParent<State, Model> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack ps, SubmitNodeCollector collector, int light, State s, float yRot, float xRot) {
            if (s.heldItem.isEmpty()) return;
            ps.pushPose();
            getParentModel().translateToArms(s, ps);
            ps.rotate(com.mojang.math.Axis.XP, 0.75f);
            ps.scale(1.07f, 1.07f, 1.07f);
            ps.translate(0f, 0.13f, -0.34f);
            ps.rotate(com.mojang.math.Axis.XP, (float) Math.PI);
            s.heldItem.submit(ps, collector, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, s.outlineColor);
            ps.popPose();
        }
    }

    private static final Identifier SHIRT = com.boombox.BoomboxMod.id("textures/entity/raver_shirt.png");

    /** Camisa do time (azul ou vermelho) por cima da roupa e pulseira de luz nos braços cruzados. */
    private static class Extras extends RenderLayer<State, Model> {
        Extras(RenderLayerParent<State, Model> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack ps, SubmitNodeCollector collector, int light, State s, float yRot, float xRot) {
            if (s.isInvisible) return;
            boolean football = s.role == com.boombox.entity.RaverRoles.TEAM_A || s.role == com.boombox.entity.RaverRoles.TEAM_B;
            boolean volley = s.role == com.boombox.entity.RaverRoles.VOLEI_A || s.role == com.boombox.entity.RaverRoles.VOLEI_B;
            if (football || volley) {
                int c = s.shirt != 0 ? s.shirt : s.role == com.boombox.entity.RaverRoles.TEAM_A ? 0xFF2D5BE0 : s.role == com.boombox.entity.RaverRoles.TEAM_B ? 0xFFE03030
                        : s.role == com.boombox.entity.RaverRoles.VOLEI_A ? 0xFFFF8A1E : 0xFF1EC8E6;
                renderColoredCutoutModel(getParentModel(), SHIRT, ps, collector, light, s, c, 3);
            }
            if (s.costume >= 0) {
                ps.pushPose();
                getParentModel().getHead().translateAndRotate(ps);
                Renderers34.costume(ps, collector, light, s.costume, -10f);
                ps.popPose();
            }
            if (s.glow) {
                int color = com.boombox.client.render.Renderers33.braceletColor(new net.minecraft.world.phys.Vec3(s.x, s.y, s.z), s.seed);
                ps.pushPose();
                getParentModel().arms().translateAndRotate(ps);
                collector.submitCustomGeometry(ps, PartyGeometry.RENDER_TYPE, (pose, vc) -> {
                    Geo g = new Geo(pose, vc, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 8);
                    g.glow(color);
                    g.box(-8.3f, 1.7f, -2.3f, -6.6f, 6.3f, 2.3f, 9);
                    g.box(6.6f, 1.7f, -2.3f, 8.3f, 6.3f, 2.3f, 9);
                    g.reset();
                });
                ps.popPose();
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

        public ModelPart arms() {
            return arms;
        }

        @Override
        public void setupAnim(VillagerRenderState state) {
            super.setupAnim(state);
            if (state instanceof State m && m.instrument > 0) {
                // pagode: pandeiro batendo rápido, cavaquinho "palhetando"
                float t = m.ageInTicks + m.seed * 40f;
                if (m.instrument == 1) arms.xRot -= 0.35f + 0.25f * (float) Math.abs(Math.sin(t * 1.2f));
                else arms.zRot = 0.15f * (float) Math.sin(t * 2.4f);
                head.xRot += 0.1f * (float) Math.sin(t * 0.6f);
                return;
            }
            if (state instanceof State c && c.bar && c.cards) {
                // truco: de vez em quando levanta os braços e bate a carta na mesa
                float cycle = (c.ageInTicks + c.seed * 300f) % 70f;
                if (cycle < 10f) {
                    float k = (float) Math.sin(cycle / 10f * Math.PI);
                    arms.xRot -= 0.8f * k;
                    head.xRot += 0.3f * k;
                }
                return;
            }
            if (state instanceof State b && b.bar) {
                // gole: de tempos em tempos levanta a cabeça e os braços com a cerveja
                float cycle = (b.ageInTicks + b.seed * 300f) % 220f;
                if (cycle < 30f) {
                    float k = (float) Math.sin(cycle / 30f * Math.PI);
                    head.xRot -= 0.7f * k;
                    arms.xRot -= 0.9f * k;
                }
                return;
            }
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
