package com.boombox.mixin.client;

import com.boombox.client.ClientBoomboxManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.parrot.Parrot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Papagaios perto de uma caixa tocando dançam (igual fazem com a jukebox). */
@Mixin(Parrot.class)
public abstract class ParrotMixin {
    @Shadow private boolean partyParrot;

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void boombox$dance(CallbackInfo ci) {
        Parrot self = (Parrot) (Object) this;
        if (!self.level().isClientSide() || self.isPassenger()) return;
        // o jogo desliga a dança todo tick quando não é jukebox; aqui liga de novo se tem caixa tocando perto
        if (ClientBoomboxManager.playingNear(Minecraft.getInstance(), self.position(), 6.0) != null) partyParrot = true;
    }
}
