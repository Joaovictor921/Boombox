package com.boombox.client.render;

import com.boombox.BoomboxItem;
import com.boombox.ModBlocks;
import com.boombox.SpeakerSettings;
import com.boombox.SpeakerType;
import net.minecraft.world.item.ItemStack;
import com.boombox.client.ClientBoomboxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Desenha a caixa que o jogador está carregando, com os alto-falantes e luzes animados.
 * O item normal da mão é escondido em ArmedEntityRenderStateMixin e os braços são posicionados
 * em HumanoidModelMixin.
 * <p>
 * Espaço do modelo do jogador: em pixels, Y para baixo (pescoço em 0, pés em 24), frente em -Z,
 * braço direito em -X.
 */
public class HeldBoomboxLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    // ---- boombox: ao lado da cabeça
    /** Distância (em pixels do modelo) do centro da boombox até o centro da cabeça. */
    public static final float SIDE_OFFSET = 9.5f;
    /** Altura do topo da alça (pixels do modelo; negativo = para cima a partir do pescoço). */
    public static final float HANDLE_TOP_Y = -9.1f;
    /** Ângulo que o braço abre para fora (radianos), casando com a posição da alça. */
    public static final float ARM_SPREAD = 0.38f;

    // ---- caixa pequena: pendurada na mão, braço esticado para baixo
    public static final float SMALL_ARM_OUT = 0.24f;
    // ---- caixa média: abraçada na frente do corpo
    public static final float MEDIUM_ARM_X = -0.55f;
    public static final float MEDIUM_ARM_IN = 0.06f;
    // ---- caixa grande: puxada pela alça, nas rodinhas
    public static final float LARGE_ARM_X = 0.42f;
    public static final float LARGE_ARM_OUT = 0.14f;

    /** Escala das caixas no jogador (a mesma da boombox). */
    private static final float SCALE = 0.75f;
    /** Do ombro até onde a mão segura (pixels). */
    private static final float GRIP = 10f;
    private static final float GROUND_Y = 24f;

    private final float[] levels = new float[2];

    public HeldBoomboxLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state,
                       float yRot, float xRot) {
        SpeakerType right = BoomboxItem.type(state.rightHandItemStack);
        SpeakerType left = BoomboxItem.type(state.leftHandItemStack);
        if (right == null && left == null) return;

        ClientBoomboxManager.levelsForEntity(state.id, levels);
        float bass = levels[0], treble = levels[1];
        if (right != null) draw(poseStack, collector, light, state, right, -1, bass, treble, settings(state.rightHandItemStack));
        if (left != null) draw(poseStack, collector, light, state, left, 1, bass, treble, settings(state.leftHandItemStack));
    }

    private static SpeakerSettings settings(ItemStack stack) {
        return stack == null ? SpeakerSettings.DEFAULT : stack.getOrDefault(ModBlocks.SETTINGS, SpeakerSettings.DEFAULT);
    }

    private void draw(PoseStack ps, SubmitNodeCollector collector, int light, AvatarRenderState state,
                      SpeakerType type, int side, float bass, float treble, SpeakerSettings settings) {
        PlayerModel model = getParentModel();
        ModelPart arm = side < 0 ? model.rightArm : model.leftArm;
        float wheelAngle = 0f;
        ps.pushPose();
        switch (type) {
            case BOOMBOX -> {
                model.body.translateAndRotate(ps);
                ps.translate(side * SIDE_OFFSET / 16f, HANDLE_TOP_Y / 16f, 0f);
                ps.rotateDegrees(Axis.ZP, 180f);          // volta o Y para cima
                ps.rotateDegrees(Axis.YP, side * 90f);    // alto-falantes virados para fora
                ps.scale(SCALE, SCALE, SCALE);
                ps.translate(-0.5f, -13.3f / 16f, -0.5f); // segura pelo topo da alça
            }
            case PARTY_SMALL -> {
                // pendurada pela alça de cima, balançando um pouco atrás do braço
                toHand(ps, arm);
                ps.rotateDegrees(Axis.XP, (float) Math.toDegrees(arm.xRot * 0.4f));
                ps.rotateDegrees(Axis.ZP, 180f);
                ps.rotateDegrees(Axis.YP, side * 90f);    // luzes viradas para fora
                ps.scale(SCALE, SCALE, SCALE);
                ps.translate(-0.5f, -13.5f / 16f, -0.5f); // topo da alça na mão
            }
            case PARTY_MEDIUM -> {
                // abraçada na frente da barriga, com as luzes para a frente
                model.body.translateAndRotate(ps);
                float walkBob = (float) Math.abs(Math.sin(state.walkAnimationPos * 0.6662f)) * 0.5f * state.walkAnimationSpeed;
                ps.translate(0f, (4.0f + walkBob) / 16f, -6.3f / 16f);
                ps.rotateDegrees(Axis.ZP, 180f);
                ps.scale(SCALE, SCALE, SCALE);
                ps.translate(-0.5f, -21.5f / 16f, -0.5f); // ponto de cima, no centro
            }
            case PARTY_LARGE -> {
                // puxada pela alça de puxar, inclinada, com as rodinhas no chão atrás do jogador
                toHand(ps, arm);
                float handY = arm.y + GRIP * (float) (Math.cos(arm.xRot) * Math.cos(arm.zRot));
                // da roda até a alça, no modelo da caixa: 22 px para cima e 2,25 px para trás
                float axleToHandle = (float) Math.hypot(22.0, 2.25) * SCALE;
                float drop = GROUND_Y - 1.6f * SCALE - handY;
                float tilt = (float) Math.acos(Math.max(-1f, Math.min(1f, drop / axleToHandle)));
                float lean = tilt - (float) Math.atan2(2.25, 22.0);
                ps.rotateDegrees(Axis.ZP, 180f);                  // Y para cima
                ps.rotateDegrees(Axis.YP, 180f);                  // alça virada para o jogador
                ps.rotateDegrees(Axis.XP, (float) Math.toDegrees(lean));
                ps.scale(SCALE, SCALE, SCALE);
                ps.translate(-0.5f, -23.6f / 16f, -14.35f / 16f); // centro da barra da alça na mão
                wheelAngle = -state.walkAnimationPos * 1.1f;      // rodinhas girando enquanto anda
            }
        }
        SpeakerRendering.submit(type, ps, collector, light, OverlayTexture.NO_OVERLAY, bass, treble, wheelAngle, settings);
        ps.popPose();
    }

    /** Vai até a mão (fim do braço) e desfaz o giro do braço, voltando aos eixos do modelo. */
    private static void toHand(PoseStack ps, ModelPart arm) {
        arm.translateAndRotate(ps);
        ps.translate(0f, GRIP / 16f, 0f);
        ps.rotateDegrees(Axis.XP, (float) Math.toDegrees(-arm.xRot));
        ps.rotateDegrees(Axis.YP, (float) Math.toDegrees(-arm.yRot));
        ps.rotateDegrees(Axis.ZP, (float) Math.toDegrees(-arm.zRot));
    }
}
