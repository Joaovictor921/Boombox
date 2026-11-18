package com.boombox.mixin;

import com.boombox.village.DjLeoHouse;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;

/** Depois de montar uma vila, acrescenta a casa do DJ Leo (ver DjLeoHouse). */
@Mixin(JigsawStructure.class)
public abstract class JigsawStructureMixin {
    @Shadow @Final private Holder<StructureTemplatePool> startPool;

    @Inject(method = "findGenerationPoint", at = @At("RETURN"), cancellable = true)
    private void boombox$djLeoHouse(Structure.GenerationContext ctx, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (!DjLeoHouse.isVillage(startPool)) return;
        Optional<Structure.GenerationStub> result = cir.getReturnValue();
        if (result.isEmpty()) return;
        Structure.GenerationStub stub = result.get();
        Optional<Consumer<StructurePiecesBuilder>> gen = stub.generator().left();
        if (gen.isEmpty()) return;
        Consumer<StructurePiecesBuilder> village = gen.get();
        cir.setReturnValue(Optional.of(new Structure.GenerationStub(stub.position(), builder -> {
            village.accept(builder);
            DjLeoHouse.addToVillage(builder, ctx, stub.position());
        })));
    }
}
