package com.boombox.mixin.client;

import com.boombox.BoomboxItem;
import com.boombox.SpeakerType;
import com.boombox.client.render.HeldBoomboxLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pose dos braços de quem carrega uma caixa:
 * <ul>
 *   <li>Boombox: braço levantado, segurando pela alça ao lado da cabeça.</li>
 *   <li>Caixa pequena: braço esticado para baixo, carregando pela alça (balança pouco, por causa do peso).</li>
 *   <li>Caixa média: os dois braços na frente, abraçando a caixa.</li>
 *   <li>Caixa grande: braço para trás, puxando pela alça de puxar, com a caixa nas rodinhas.</li>
 * </ul>
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart head;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void boombox$holdSpeaker(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState)) return;
        SpeakerType right = BoomboxItem.type(state.rightHandItemStack);
        SpeakerType left = BoomboxItem.type(state.leftHandItemStack);
        if (right != null) pose(state, right, rightArm, -1);
        if (left != null && right != SpeakerType.PARTY_MEDIUM) pose(state, left, leftArm, 1);
    }

    /** side: -1 = braço direito (lado -X do modelo), 1 = esquerdo. */
    private void pose(HumanoidRenderState state, SpeakerType type, ModelPart arm, int side) {
        float bob = (float) Math.sin(state.ageInTicks * 0.15f) * 0.03f;
        switch (type) {
            case BOOMBOX -> {
                arm.xRot = (float) -Math.PI + bob;
                arm.yRot = 0f;
                arm.zRot = side * HeldBoomboxLayer.ARM_SPREAD;
                head.zRot += side * 0.12f; // inclina a cabeça para a música
            }
            case PARTY_SMALL -> {
                arm.xRot *= 0.35f; // a caixa pesa: o braço balança pouco
                arm.yRot = 0f;
                arm.zRot = -side * HeldBoomboxLayer.SMALL_ARM_OUT;
            }
            case PARTY_MEDIUM -> {
                // os dois braços na frente, segurando a caixa pelos lados
                float walkBob = (float) Math.sin(state.walkAnimationPos * 0.6662f) * 0.04f * state.walkAnimationSpeed;
                for (int s = -1; s <= 1; s += 2) {
                    ModelPart a = s < 0 ? rightArm : leftArm;
                    a.xRot = HeldBoomboxLayer.MEDIUM_ARM_X + bob * 0.5f + walkBob;
                    a.yRot = 0f;
                    a.zRot = s * HeldBoomboxLayer.MEDIUM_ARM_IN;
                }
            }
            case PARTY_LARGE -> {
                // braço para trás, firme na alça de puxar
                arm.xRot = HeldBoomboxLayer.LARGE_ARM_X + bob * 0.3f;
                arm.yRot = 0f;
                arm.zRot = -side * HeldBoomboxLayer.LARGE_ARM_OUT;
            }
        }
    }
}
