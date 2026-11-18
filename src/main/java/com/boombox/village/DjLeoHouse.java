package com.boombox.village;

import com.boombox.BoomboxMod;
import com.boombox.mixin.StructurePiecesBuilderAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Coloca a casa de rave do DJ Leo (16 x 32) em todas as vilas: planície, deserto, savana, neve e taiga.
 * <p>
 * A casa é grande demais para os "lotes" das ruas da vila (no máximo 16 x 16, contando a rua), então ela
 * não entra pelo sorteio normal de casas. Depois que a vila inteira foi montada, procuramos um lugar livre e
 * plano encostado numa das ruas, com a porta virada para a rua, e a casa entra como mais uma peça da vila
 * (o terreno em volta é ajustado como nas outras casas).
 */
public final class DjLeoHouse {
    public static final String TEMPLATE = BoomboxMod.MOD_ID + ":djleo_house";
    /** Bar do Seu Zé: metade das vilas ganha o bar no lugar da balada. */
    public static final String BAR_TEMPLATE = BoomboxMod.MOD_ID + ":ze_bar";
    private static final Set<String> VILLAGE_STARTS = Set.of(
            "village/plains/town_centers", "village/desert/town_centers", "village/savanna/town_centers",
            "village/snowy/town_centers", "village/taiga/town_centers");
    /** Degrau da porta no molde (a porta fica no lado oeste, x = 0). */
    private static final BlockPos DOORSTEP = new BlockPos(0, 0, 7), BAR_DOORSTEP = new BlockPos(0, 0, 4);
    /** Desnível máximo do terreno embaixo da casa. */
    private static final int MAX_SLOPE = 3;
    /** Morro: aceita até esse desnível se não houver lugar melhor (o terreno da vila se ajusta em volta). */
    private static final int MAX_SLOPE_FALLBACK = 7;

    private static volatile StructurePoolElement element, barElement;

    private DjLeoHouse() {}

    private static StructurePoolElement element(boolean bar) {
        if (bar) {
            StructurePoolElement e = barElement;
            if (e == null) barElement = e = StructurePoolElement.single(BAR_TEMPLATE).apply(StructureTemplatePool.Projection.RIGID);
            return e;
        }
        StructurePoolElement e = element;
        if (e == null) element = e = StructurePoolElement.single(TEMPLATE).apply(StructureTemplatePool.Projection.RIGID);
        return e;
    }

    /** A estrutura começa pelo centro de uma vila? */
    public static boolean isVillage(Holder<StructureTemplatePool> startPool) {
        return startPool.unwrapKey().map(k -> VILLAGE_STARTS.contains(k.identifier().getPath())).orElse(false);
    }

    /** Chamado depois que todas as peças da vila foram colocadas. */
    public static void addToVillage(StructurePiecesBuilder builder, Structure.GenerationContext ctx, BlockPos center) {
        List<StructurePiece> pieces = ((StructurePiecesBuilderAccessor) builder).boombox$pieces();
        if (pieces.isEmpty()) return;
        var manager = ctx.structureTemplateManager();
        var gen = ctx.chunkGenerator();
        var heights = ctx.heightAccessor();
        var randomState = ctx.randomState();
        RandomSource rnd = RandomSource.create(ctx.seed() ^ center.asLong() ^ 0x5EEDD1E0L);
        Random shuffler = new Random(rnd.nextLong());
        // 50% balada do DJ Leo, 50% bar do Seu Zé (sorteio fixo por vila: o mesmo mundo dá sempre o mesmo)
        boolean bar = rnd.nextBoolean();
        StructurePoolElement house = element(bar);
        BlockPos doorstep = bar ? BAR_DOORSTEP : DOORSTEP;

        List<PoolElementStructurePiece> streets = new ArrayList<>();
        for (StructurePiece p : pieces) {
            if (p instanceof PoolElementStructurePiece pe && pe.getElement() instanceof SinglePoolElement s
                    && s.getTemplateLocation().getPath().contains("/streets/")) {
                streets.add(pe);
            }
        }
        // ruas mais perto do centro primeiro (com um pouco de sorte no meio)
        Collections.shuffle(streets, shuffler);
        streets.sort((a, b) -> Double.compare(dist(a.getBoundingBox(), center) + shuffler.nextInt(24),
                dist(b.getBoundingBox(), center) + shuffler.nextInt(24)));

        // se nenhum lugar for plano, usa o menos inclinado (até MAX_SLOPE_FALLBACK)
        BoundingBox bestBox = null;
        BlockPos bestOrigin = null;
        Rotation bestRot = null;
        int bestSlope = Integer.MAX_VALUE;
        List<Direction> sides = new ArrayList<>(List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        for (PoolElementStructurePiece street : streets) {
            BoundingBox s = street.getBoundingBox();
            Collections.shuffle(sides, shuffler);
            for (Direction side : sides) {
                // a casa fica do lado "side" da rua; a porta olha para a rua
                Rotation rot = rotationFor(side.getOpposite());
                int sx = switch (side) {
                    case EAST -> s.maxX() + 1;
                    case WEST -> s.minX() - 1;
                    default -> (s.minX() + s.maxX()) / 2;
                };
                int sz = switch (side) {
                    case SOUTH -> s.maxZ() + 1;
                    case NORTH -> s.minZ() - 1;
                    default -> (s.minZ() + s.maxZ()) / 2;
                };
                int ground = gen.getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState) - 1;
                BlockPos step = new BlockPos(sx, ground, sz);
                BlockPos origin = step.subtract(StructureTemplate.transform(doorstep, Mirror.NONE, rot, BlockPos.ZERO));
                BoundingBox box = house.getBoundingBox(manager, origin, rot);
                if (builder.findCollisionPiece(box) != null) continue;
                int slope = slope(gen, heights, randomState, box, ground);
                if (slope < 0) continue; // água
                if (slope <= MAX_SLOPE) {
                    builder.addPiece(new PoolElementStructurePiece(manager, house, origin, 0, rot, box, LiquidSettings.IGNORE_WATERLOGGING));
                    return;
                }
                if (slope < bestSlope) {
                    bestSlope = slope;
                    bestBox = box;
                    bestOrigin = origin;
                    bestRot = rot;
                }
            }
        }
        if (bestBox != null && bestSlope <= MAX_SLOPE_FALLBACK) {
            builder.addPiece(new PoolElementStructurePiece(manager, house, bestOrigin, 0, bestRot, bestBox, LiquidSettings.IGNORE_WATERLOGGING));
        }
    }

    private static double dist(BoundingBox b, BlockPos c) {
        double x = (b.minX() + b.maxX()) / 2.0 - c.getX(), z = (b.minZ() + b.maxZ()) / 2.0 - c.getZ();
        return Math.sqrt(x * x + z * z);
    }

    /** Gira o molde (porta para oeste) para a porta olhar para "door". */
    private static Rotation rotationFor(Direction door) {
        for (Rotation r : Rotation.values()) {
            if (r.rotate(Direction.WEST) == door) return r;
        }
        return Rotation.NONE;
    }

    /** Maior desnível do terreno embaixo da casa, ou -1 se tiver água (nada de casa no meio do rio). */
    private static int slope(net.minecraft.world.level.chunk.ChunkGenerator gen, net.minecraft.world.level.LevelHeightAccessor heights,
                                      net.minecraft.world.level.levelgen.RandomState rs, BoundingBox box, int ground) {
        int[][] pts = {{box.minX(), box.minZ()}, {box.maxX(), box.minZ()}, {box.minX(), box.maxZ()}, {box.maxX(), box.maxZ()},
                {(box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2}};
        int worst = 0;
        for (int[] p : pts) {
            int surface = gen.getBaseHeight(p[0], p[1], Heightmap.Types.WORLD_SURFACE_WG, heights, rs) - 1;
            int floor = gen.getBaseHeight(p[0], p[1], Heightmap.Types.OCEAN_FLOOR_WG, heights, rs) - 1;
            if (surface - floor > 0) return -1;                   // água em cima do chão
            worst = Math.max(worst, Math.abs(surface - ground));
        }
        return worst;
    }
}
