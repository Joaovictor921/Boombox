package com.boombox.mixin;

import com.boombox.village.DjLeoHouse;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avisa quando a casa do DJ Leo entrou numa vila (para não colocar outra). */
@Mixin(PoolElementStructurePiece.class)
public abstract class PoolElementStructurePieceMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;"
            + "Lnet/minecraft/world/level/levelgen/structure/pools/StructurePoolElement;Lnet/minecraft/core/BlockPos;I"
            + "Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"
            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V", at = @At("TAIL"))
    private void boombox$placed(StructureTemplateManager manager, StructurePoolElement element, BlockPos pos, int groundLevelDelta,
                                Rotation rotation, BoundingBox box, LiquidSettings liquid, CallbackInfo ci) {
        DjLeoHouse.piecePlaced(element);
    }
}
