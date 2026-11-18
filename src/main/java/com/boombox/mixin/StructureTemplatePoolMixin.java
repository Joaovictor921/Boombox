package com.boombox.mixin;

import com.boombox.village.DjLeoHouse;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Casa do DJ Leo nas vilas (ver DjLeoHouse). */
@Mixin(StructureTemplatePool.class)
public abstract class StructureTemplatePoolMixin {
    @Inject(method = "getShuffledTemplates", at = @At("RETURN"), cancellable = true)
    private void boombox$djLeoHouse(RandomSource random, CallbackInfoReturnable<List<StructurePoolElement>> cir) {
        List<StructurePoolElement> list = cir.getReturnValue();
        List<StructurePoolElement> adjusted = DjLeoHouse.adjust((StructureTemplatePool) (Object) this, list);
        if (adjusted != list) cir.setReturnValue(adjusted);
    }
}
