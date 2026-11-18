package com.boombox.mixin.client;

import com.boombox.BoomboxItem;
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

/** Levanta o braço que segura a boombox, com a mão na alça, ao lado da cabeça. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart head;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void boombox$holdBoombox(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState)) return;
        float bob = (float) Math.sin(state.ageInTicks * 0.15f) * 0.03f;
        if (BoomboxItem.isBoombox(state.rightHandItemStack)) {
            rightArm.xRot = (float) -Math.PI + bob;
            rightArm.yRot = 0f;
            rightArm.zRot = -HeldBoomboxLayer.ARM_SPREAD;
            head.zRot -= 0.12f; // inclina a cabeça para a música
        }
        if (BoomboxItem.isBoombox(state.leftHandItemStack)) {
            leftArm.xRot = (float) -Math.PI + bob;
            leftArm.yRot = 0f;
            leftArm.zRot = HeldBoomboxLayer.ARM_SPREAD;
            head.zRot += 0.12f;
        }
    }
}
