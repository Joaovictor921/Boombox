package com.boombox.mixin.client;

import com.boombox.client.PoolClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Na sinuca os cliques são do taco: não quebra nem usa nada. */
@Mixin(Minecraft.class)
public abstract class PoolClickMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void boombox$noUse(CallbackInfo ci) {
        if (PoolClient.active()) ci.cancel();
        else if (com.boombox.client.SportsClient.active()) {
            // 3.5: no futebol o direito é passe; no vôlei, cortada
            com.boombox.client.SportsClient.click(false);
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void boombox$noAttack(CallbackInfoReturnable<Boolean> cir) {
        if (PoolClient.active()) cir.setReturnValue(false);
        else if (com.boombox.client.SportsClient.active()) {
            // 3.5: no futebol o esquerdo é chute; no vôlei, manchete
            com.boombox.client.SportsClient.click(true);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void boombox$noBreak(boolean down, CallbackInfo ci) {
        if (PoolClient.active() || com.boombox.client.SportsClient.active()) ci.cancel();
    }
}
