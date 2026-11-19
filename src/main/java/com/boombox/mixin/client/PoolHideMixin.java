package com.boombox.mixin.client;

import com.boombox.client.PoolClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Na sinuca, quem está colado na câmera (aldeão em volta da mesa, o próprio jogador) some para não tapar a vista. */
@Mixin(EntityRenderDispatcher.class)
public abstract class PoolHideMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void boombox$hideNearPoolCamera(E entity, Frustum frustum, double camX, double camY, double camZ, float pt,
                                                                CallbackInfoReturnable<Boolean> cir) {
        if (!PoolClient.active()) return;
        Vec3 c = PoolClient.cameraPos(pt);
        if (c == null) return;
        if (entity.getBoundingBox().inflate(0.6).contains(c) || entity.position().distanceToSqr(c) < 1.2 * 1.2) cir.setReturnValue(false);
    }
}
