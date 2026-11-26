package com.boombox.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.HashSet;
import java.util.Set;

/**
 * 3.9.2: o jogo só monta uma estrutura nos chunks até 8 chunks do começo dela. O estádio (e outras peças do mod)
 * podem ficar mais longe que isso, e aí os chunks de fora ficavam sem nada (mato, árvore, rio no lugar da
 * arquibancada). Aqui, perto dos jogadores, cada chunk que devia ter uma peça do mod e não foi montado recebe a peça.
 */
public final class StadiumRepair {
    private StadiumRepair() {}

    /** Começos de vila já conferidos inteiros (nada mais a montar). */
    private static final Set<Long> DONE = new HashSet<>();
    /** Quantos chunks foram completados (para o teste). */
    public static int repaired;

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 7) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() instanceof ServerLevel sl) check(sl, p.blockPosition());
        }
    }

    public static void clear() {
        DONE.clear();
    }

    /** Confere as vilas que têm algum chunk perto desse ponto. */
    public static void check(ServerLevel sl, BlockPos near) {
        var villages = sl.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(StructureTags.VILLAGE);
        if (villages.isEmpty()) return;
        Set<Structure> types = new HashSet<>();
        for (var h : villages.get()) types.add(h.value());
        int cx = near.getX() >> 4, cz = near.getZ() >> 4;
        Set<Long> seen = new HashSet<>();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                LevelChunk c = sl.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (c == null) continue;
                for (var e : c.getAllReferences().entrySet()) {
                    if (!types.contains(e.getKey())) continue;
                    for (long start : e.getValue()) {
                        long key = start * 31 + e.getKey().hashCode();
                        if (DONE.contains(key) || !seen.add(key)) continue;
                        if (repairStart(sl, e.getKey(), start)) DONE.add(key);
                    }
                }
            }
        }
    }

    /** Monta o que falta das peças do mod desse começo de vila. Devolve true se não falta mais nada. */
    private static boolean repairStart(ServerLevel sl, Structure type, long startChunk) {
        ChunkPos sp = ChunkPos.unpack(startChunk);
        var sc = sl.getChunkSource().getChunk(sp.x(), sp.z(), ChunkStatus.STRUCTURE_STARTS, true);
        if (sc == null) return false;
        StructureStart start = sc.getStartForStructure(type);
        if (start == null || !start.isValid()) return true;
        boolean complete = true;
        BoundingBox first = start.getPieces().get(0).getBoundingBox();
        BlockPos pivot = new BlockPos(first.getCenter().getX(), first.minY(), first.getCenter().getZ());
        for (StructurePiece pc : start.getPieces()) {
            if (!(pc instanceof PoolElementStructurePiece pe) || !pe.getElement().toString().contains("boombox:")) continue;
            BoundingBox b = pc.getBoundingBox();
            for (int x = b.minX() >> 4; x <= b.maxX() >> 4; x++) {
                for (int z = b.minZ() >> 4; z <= b.maxZ() >> 4; z++) {
                    if (Math.max(Math.abs(x - sp.x()), Math.abs(z - sp.z())) <= 8) continue; // o jogo monta esses
                    LevelChunk c = sl.getChunkSource().getChunkNow(x, z);
                    if (c == null) { complete = false; continue; }
                    if (c.getReferencesForStructure(type).contains(startChunk)) continue; // já montado
                    // precisa dos vizinhos carregados (a peça põe blocos e bichos)
                    if (!neighbors(sl, x, z)) { complete = false; continue; }
                    ChunkPos cp = new ChunkPos(x, z);
                    BoundingBox box = new BoundingBox(cp.getMinBlockX(), sl.getMinY(), cp.getMinBlockZ(), cp.getMaxBlockX(), sl.getMaxY(), cp.getMaxBlockZ());
                    for (StructurePiece other : start.getPieces()) {
                        if (other instanceof PoolElementStructurePiece op && op.getElement().toString().contains("boombox:")
                                && other.getBoundingBox().intersects(box)) {
                            other.postProcess(sl, sl.structureManager(), sl.getChunkSource().getGenerator(),
                                    RandomSource.create(sl.getSeed() ^ cp.pack()), box, cp, pivot);
                            sl.getBlockTicks().clearArea(box); // o pó de concreto não cai na hora
                            BoundingBox ob = other.getBoundingBox();
                            support(sl, Math.max(ob.minX(), box.minX()), Math.max(ob.minZ(), box.minZ()),
                                    Math.min(ob.maxX(), box.maxX()), Math.min(ob.maxZ(), box.maxZ()), ob.minY());
                            clearAbove(sl, ob, box);
                        }
                    }
                    c.addReferenceForStructure(type, startChunk);
                    c.markUnsaved();
                    repaired++;
                    com.boombox.BoomboxMod.LOGGER.info("Boombox: montei o pedaço que faltava da vila no chunk {}", cp);
                }
            }
        }
        return complete;
    }

    /**
     * O gramado é de pó de concreto, que cai se tiver rio ou buraco embaixo. Onde o bloco em "y" é desses que caem e
     * embaixo está oco (ar, água, mato), enche de terra para baixo até o chão (no máximo 24 blocos).
     */
    public static int support(ServerLevel sl, int x0, int z0, int x1, int z1, int y) {
        int filled = 0;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                q.set(x, y, z);
                if (!sl.isLoaded(q) || !(sl.getBlockState(q).getBlock() instanceof net.minecraft.world.level.block.FallingBlock)) continue;
                for (int k = 1; k <= 24; k++) {
                    q.set(x, y - k, z);
                    var st = sl.getBlockState(q);
                    if (!st.isAir() && !st.canBeReplaced() && st.getFluidState().isEmpty()) break;
                    sl.setBlock(q, Blocks.DIRT.defaultBlockState(), 2);
                    filled++;
                }
            }
        }
        return filled;
    }

    private static boolean neighbors(ServerLevel sl, int x, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (sl.getChunkSource().getChunkNow(x + dx, z + dz) == null) return false;
        return true;
    }

    /** Tira tronco e folha que sobraram por cima da peça (árvore que cresceu ali antes de a peça ser montada). */
    private static void clearAbove(ServerLevel sl, BoundingBox piece, BoundingBox chunk) {
        int x0 = Math.max(piece.minX(), chunk.minX()), x1 = Math.min(piece.maxX(), chunk.maxX());
        int z0 = Math.max(piece.minZ(), chunk.minZ()), z1 = Math.min(piece.maxZ(), chunk.maxZ());
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = piece.maxY() + 1; y <= piece.maxY() + 24; y++) {
                    q.set(x, y, z);
                    var st = sl.getBlockState(q);
                    if (st.isAir()) continue;
                    boolean tree = st.is(BlockTags.LEAVES) || st.is(Blocks.SNOW) || st.is(Blocks.VINE)
                            || (st.is(BlockTags.LOGS) && !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().startsWith("stripped_"));
                    if (tree) sl.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }
}
