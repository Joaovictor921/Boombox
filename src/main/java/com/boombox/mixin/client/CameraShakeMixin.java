package com.boombox.mixin.client;

import com.boombox.client.ClientBoomboxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A câmera treme no grave forte quando você está colado numa caixa alta (como a imagem vibrando no show). */
@Mixin(GameRenderer.class)
public abstract class CameraShakeMixin {
    @Inject(method = "bobHurt", at = @At("TAIL"))
    private void boombox$bassShake(CameraRenderState camera, PoseStack ps, CallbackInfo ci) {
        float s = ClientBoomboxManager.cameraShake(Minecraft.getInstance());
        if (s <= 0.001f) return;
        double t = System.nanoTime() / 1e9;
        // vibração rápida (~45 Hz) com um pouco de irregularidade, no máximo ~0,7 grau
        float a = (float) (Math.sin(t * 2 * Math.PI * 45) * 0.6 + Math.sin(t * 2 * Math.PI * 31 + 1.3) * 0.4);
        float b = (float) (Math.sin(t * 2 * Math.PI * 38 + 0.7) * 0.6 + Math.sin(t * 2 * Math.PI * 53) * 0.4);
        ps.rotateDegrees(Axis.ZP, a * 0.7f * s);
        ps.rotateDegrees(Axis.XP, b * 0.45f * s);
    }
}
