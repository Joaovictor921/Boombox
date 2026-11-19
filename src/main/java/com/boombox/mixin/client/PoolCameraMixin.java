package com.boombox.mixin.client;

import com.boombox.client.PoolClient;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Na sinuca a câmera fica presa na mesa (atrás da branca ou vendo a mesa de cima). */
@Mixin(Camera.class)
public abstract class PoolCameraMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void boombox$poolCamera(float partialTicks, CallbackInfo ci) {
        Vec3 p = PoolClient.cameraPos(partialTicks);
        if (p == null) return;
        detached = true;
        setRotation(PoolClient.cameraYaw(partialTicks), PoolClient.cameraPitch(partialTicks));
        setPosition(p.x, p.y, p.z);
    }
}
