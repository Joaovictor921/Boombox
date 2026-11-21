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
    private static final BlockPos DOORSTEP = new BlockPos(0, 0, 7), BAR_DOORSTEP = new BlockPos(0, 0, 5);
    /** Desnível máximo do terreno embaixo da casa. */
    private static final int MAX_SLOPE = 3;
    /** Morro: aceita até esse desnível se não houver lugar melhor (o terreno da vila se ajusta em volta). */
    private static final int MAX_SLOPE_FALLBACK = 7;

    private static volatile StructurePoolElement element, barElement, smallElement;

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

    /** Arraiá da festa junina (só em vilas geradas em junho, pelo relógio do computador). */
    public static final String ARRAIA_TEMPLATE = BoomboxMod.MOD_ID + ":arraia";
    private static final BlockPos ARRAIA_DOORSTEP = new BlockPos(0, 0, 5);
    private static volatile StructurePoolElement arraiaElement;

    /** Barraca de praia (vilas perto do mar) e pagode na laje (algumas vilas). */
    public static final String BEACH_TEMPLATE = BoomboxMod.MOD_ID + ":beach_hut", PAGODE_TEMPLATE = BoomboxMod.MOD_ID + ":pagode_laje";
    private static final BlockPos BEACH_DOORSTEP = new BlockPos(0, 0, 4), PAGODE_DOORSTEP = new BlockPos(0, 0, 4);
    private static volatile StructurePoolElement beachElement, pagodeElement;

    /** Procura mar (água funda) em volta da vila; devolve o ponto de água mais perto do centro, ou null. */
    private static BlockPos findSea(Structure.GenerationContext ctx, BlockPos center) {
        var gen = ctx.chunkGenerator();
        BlockPos best = null;
        int found = 0;
        double bestD = Double.MAX_VALUE;
        for (int r = 24; r <= 80; r += 8) {
            for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI / 8;
                int x = center.getX() + (int) (Math.cos(ang) * r), z = center.getZ() + (int) (Math.sin(ang) * r);
                int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
                int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
                if (surface - floor >= 3 && surface <= gen.getSeaLevel() + 1) {
                    found++;
                    if (r < bestD) {
                        bestD = r;
                        best = new BlockPos(x, surface, z);
                    }
                }
            }
        }
        return found >= 4 ? best : null;
    }

    private static final String[] EXTRAS = {"quermesse", "feira", "varzea", "volei_praia", "casa_piscina", "varzea_small"};
    private static final StructurePoolElement[] extraElements = new StructurePoolElement[EXTRAS.length];

    private static StructurePoolElement extra(int i) {
        StructurePoolElement e = extraElements[i];
        if (e == null) extraElements[i] = e = StructurePoolElement.single(BoomboxMod.MOD_ID + ":" + EXTRAS[i]).apply(StructureTemplatePool.Projection.RIGID);
        return e;
    }

    /** Chamado depois que todas as peças da vila foram colocadas. */
    public static void addToVillage(StructurePiecesBuilder builder, Structure.GenerationContext ctx, BlockPos center) {
        List<StructurePiece> pieces = ((StructurePiecesBuilderAccessor) builder).boombox$pieces();
        if (pieces.isEmpty()) return;
        RandomSource rnd = RandomSource.create(ctx.seed() ^ center.asLong() ^ 0x5EEDD1E0L);
        Random shuffler = new Random(rnd.nextLong());
        // 50% balada do DJ Leo, 50% bar do Seu Zé (sorteio fixo por vila: o mesmo mundo dá sempre o mesmo)
        boolean bar = rnd.nextBoolean();
        if (!placeBeside(builder, ctx, center, element(bar), bar ? BAR_DOORSTEP : DOORSTEP, shuffler) && !bar) {
            // a balada com lounge é grande: se não couber, vai a versão sem a área externa
            StructurePoolElement small = smallElement;
            if (small == null) smallElement = small = StructurePoolElement.single(TEMPLATE + "_compact").apply(StructureTemplatePool.Projection.RIGID);
            placeBeside(builder, ctx, center, small, DOORSTEP, shuffler);
        }
        // vila perto do mar: barraca de praia do lado da água
        BlockPos sea = findSea(ctx, center);
        if (sea != null) {
            StructurePoolElement e = beachElement;
            if (e == null) beachElement = e = StructurePoolElement.single(BEACH_TEMPLATE).apply(StructureTemplatePool.Projection.RIGID);
            placeBeside(builder, ctx, sea, e, BEACH_DOORSTEP, shuffler);
            // 3.4: quadra de vôlei de praia em 70% das vilas com mar
            if (rnd.nextFloat() < 0.7f) placeBeside(builder, ctx, sea, extra(3), new BlockPos(0, 0, 6), shuffler);
        }
        // 3.4: casa com piscina (30% das vilas)
        if (rnd.nextFloat() < 0.3f) placeBeside(builder, ctx, center, extra(4), new BlockPos(0, 0, 4), shuffler);
        // 40% das vilas: pagode na laje
        if (rnd.nextFloat() < 0.4f) {
            StructurePoolElement e = pagodeElement;
            if (e == null) pagodeElement = e = StructurePoolElement.single(PAGODE_TEMPLATE).apply(StructureTemplatePool.Projection.RIGID);
            placeBeside(builder, ctx, center, e, PAGODE_DOORSTEP, shuffler);
        }
        // 3.3: quermesse (35%), feira de domingo (40%) e campinho de várzea (35%)
        if (rnd.nextFloat() < 0.35f) placeBeside(builder, ctx, center, extra(0), new BlockPos(0, 0, 6), shuffler);
        if (rnd.nextFloat() < 0.4f) placeBeside(builder, ctx, center, extra(1), new BlockPos(0, 0, 5), shuffler);
        if (rnd.nextFloat() < 0.35f && !placeBeside(builder, ctx, center, extra(2), new BlockPos(0, 0, 15), shuffler)) {
            // 3.5: o campo grande (60 x 38) não coube: vai o campinho menor
            placeBeside(builder, ctx, center, extra(5), new BlockPos(0, 0, 12), shuffler);
        }
        // em junho a vila também ganha um arraiá
        if (com.boombox.bar.ShowTime.isJune()) {
            StructurePoolElement a = arraiaElement;
            if (a == null) arraiaElement = a = StructurePoolElement.single(ARRAIA_TEMPLATE).apply(StructureTemplatePool.Projection.RIGID);
            placeBeside(builder, ctx, center, a, ARRAIA_DOORSTEP, shuffler);
        }
    }

    /** Coloca a peça encostada numa rua da vila, num lugar livre e de preferência plano. */
    private static boolean placeBeside(StructurePiecesBuilder builder, Structure.GenerationContext ctx, BlockPos center,
                                       StructurePoolElement house, BlockPos doorstep, Random shuffler) {
        List<StructurePiece> pieces = ((StructurePiecesBuilderAccessor) builder).boombox$pieces();
        var manager = ctx.structureTemplateManager();
        var gen = ctx.chunkGenerator();
        var heights = ctx.heightAccessor();
        var randomState = ctx.randomState();

        List<PoolElementStructurePiece> streets = new ArrayList<>();
        for (StructurePiece p : pieces) {
            if (p instanceof PoolElementStructurePiece pe && pe.getElement() instanceof SinglePoolElement s
                    && s.getTemplateLocation().getPath().contains("/streets/")) {
                streets.add(pe);
            }
        }
        // ruas mais perto do centro primeiro (com um pouco de sorte no meio)
        Collections.shuffle(streets, shuffler);
        // a sorte é sorteada uma vez por rua (sortear dentro do comparador quebra o sort)
        java.util.Map<PoolElementStructurePiece, Double> key = new java.util.IdentityHashMap<>();
        for (PoolElementStructurePiece st : streets) {
            key.put(st, dist(st.getBoundingBox(), center) + shuffler.nextInt(24));
        }
        streets.sort((a, b) -> Double.compare(key.get(a), key.get(b)));

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
                    return true;
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
            return true;
        }
        return false;
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
