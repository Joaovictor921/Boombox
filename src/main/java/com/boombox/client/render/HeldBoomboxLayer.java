package com.boombox.client.render;

import com.boombox.BoomboxItem;
import com.boombox.client.ClientBoomboxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Desenha a boombox ao lado da cabeça do jogador (braço levantado segurando pela alça),
 * com os alto-falantes animados. O item normal da mão é escondido em ArmedEntityRenderStateMixin.
 */
public class HeldBoomboxLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Distância (em pixels do modelo) do centro da boombox até o centro da cabeça. */
    public static final float SIDE_OFFSET = 9.5f;
    /** Altura do topo da alça (pixels do modelo; negativo = para cima a partir do pescoço). */
    public static final float HANDLE_TOP_Y = -9.1f;
    /** Ângulo que o braço abre para fora (radianos), casando com a posição da alça. */
    public static final float ARM_SPREAD = 0.38f;

    private final float[] levels = new float[2];

    public HeldBoomboxLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state,
                       float yRot, float xRot) {
        boolean right = BoomboxItem.isBoombox(state.rightHandItemStack);
        boolean left = BoomboxItem.isBoombox(state.leftHandItemStack);
        if (!right && !left) return;

        ClientBoomboxManager.levelsForEntity(state.id, levels);
        float bass = levels[0], treble = levels[1];
        if (right) drawSide(poseStack, collector, light, -1, bass, treble);
        if (left) drawSide(poseStack, collector, light, 1, bass, treble);
    }

    private void drawSide(PoseStack poseStack, SubmitNodeCollector collector, int light, int side, float bass, float treble) {
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        // espaço do modelo: Y para baixo, frente do jogador em -Z, braço direito em -X
        poseStack.translate(side * SIDE_OFFSET / 16f, HANDLE_TOP_Y / 16f, 0f);
        poseStack.rotateDegrees(Axis.ZP, 180f);          // volta o Y para cima
        poseStack.rotateDegrees(Axis.YP, side * 90f);    // alto-falantes virados para fora
        poseStack.scale(0.75f, 0.75f, 0.75f);
        poseStack.translate(-0.5f, -13.3f / 16f, -0.5f);           // segura pelo topo da alça
        collector.submitCustomGeometry(poseStack, BoomboxGeometry.RENDER_TYPE,
                (pose, vc) -> BoomboxGeometry.draw(pose, vc, light, OverlayTexture.NO_OVERLAY, bass, treble));
        poseStack.popPose();
    }
}
