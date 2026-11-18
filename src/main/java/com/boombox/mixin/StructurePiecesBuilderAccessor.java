package com.boombox.mixin;

import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Peças já colocadas na estrutura (para achar as ruas da vila). */
@Mixin(StructurePiecesBuilder.class)
public interface StructurePiecesBuilderAccessor {
    @Accessor("pieces")
    List<StructurePiece> boombox$pieces();
}
