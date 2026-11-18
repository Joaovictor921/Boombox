package com.boombox.mixin;

import com.boombox.server.ServerBoomboxManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.allay.Allay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allays continuam dançando enquanto tem uma caixa do mod tocando perto (no jogo normal, só com jukebox). */
@Mixin(Allay.class)
public abstract class AllayMixin {
    @Inject(method = "shouldStopDancing", at = @At("HEAD"), cancellable = true)
    private void boombox$keepDancing(CallbackInfoReturnable<Boolean> cir) {
        Allay self = (Allay) (Object) this;
        if (self.level() instanceof ServerLevel level && ServerBoomboxManager.isPlayingNear(level, self.position(), 10)) {
            cir.setReturnValue(false);
        }
    }
}
