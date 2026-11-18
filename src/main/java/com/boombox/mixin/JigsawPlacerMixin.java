package com.boombox.mixin;

import com.boombox.village.DjLeoHouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uma estrutura de peças (ex.: uma vila) começou a ser montada. */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class JigsawPlacerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void boombox$start(CallbackInfo ci) {
        DjLeoHouse.startPlacement();
    }
}
