package com.boombox.village;

import com.boombox.entity.Sports;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 3.9: cada vila tem um nome, um time (nome e cor), um estádio (nome e estilo) e uma vila rival (a mais perto). Tudo
 * sai do lugar da vila no mundo, então é sempre o mesmo. Também procura as vilas vizinhas (para a Copa das Vilas).
 */
public final class Villages {
    private Villages() {}

    private static final String[] PLACES = {"Ladeira", "Esperança", "Boa Vista", "Pedra Branca", "Serra Azul", "Sol Nascente",
            "Rio Claro", "Ipê Roxo", "Bela Vista", "Água Doce", "Três Pontes", "Vale Verde", "Santa Brisa", "Morro Alto",
            "Lagoa Seca", "Capim Dourado", "Pé de Serra", "Cachoeirinha", "Barra Mansa", "Campo Lindo", "Coqueiral",
            "Areia Branca", "Pinheiral", "Mangueiral", "Brejo Grande", "Rio Fundo", "Piraquara", "Taquaral",
            "Jatobá", "Cajueiro", "Poço Fundo", "Ventania", "Buriti", "Carnaúba", "Sabiá", "Bem-te-vi", "Palmeiral", "Itaquera",
            "Pedra Furada", "Água Branca", "Lajeado", "Ribeirão Verde", "Mata Fresca", "Cerrado Alto"};
    /** Metade das vilas ganha um complemento no nome (para duas vilas não terem o mesmo nome). */
    private static final String[] SUFFIX = {" do Norte", " do Sul", " de Cima", " de Baixo", " Velha", " Nova", " da Serra", " do Vale"};
    private static final String[] CLUBS = {"Atlético {p}", "Esporte Clube {p}", "União {p}", "Grêmio {p}", "Independente de {p}",
            "{p} Futebol Clube", "Associação Atlética {p}", "Sociedade Esportiva {p}", "Operário de {p}", "Unidos de {p}"};
    /** Estilo de cada estádio (o molde estadio_N) e o jeito do nome. */
    public static final String[] STADIUM_STYLE = {"Caldeirão de {p}", "Arena {p}", "Alçapão de {p}", "Estádio Municipal de {p}"};
    public static final int STADIUMS = STADIUM_STYLE.length;

    /** Uma vila: onde fica, nome, time e estádio (null = a vila não tem estádio, por exemplo vila de antes da 3.9). */
    public record Info(long id, BlockPos center, String place, String team, int color, String stadiumName, BlockPos stadium, BlockPos varzea) {
        public String name() {
            return "Vila " + place;
        }

        /** Onde se joga nessa vila: o estádio, ou o campo de várzea se não tiver estádio. */
        public BlockPos venue() {
            return stadium != null ? stadium : varzea;
        }

        public String venueName() {
            return stadium != null ? stadiumName : "Campo de várzea da " + name();
        }
    }

    private static Random rnd(long id, long salt) {
        return new Random(id * 0x9E3779B97F4A7C15L ^ salt);
    }

    /** Nome (sem o "Vila") de uma vila. */
    public static String place(long id) {
        Random r = rnd(id, 11);
        String p = PLACES[r.nextInt(PLACES.length)];
        return r.nextBoolean() ? p + SUFFIX[r.nextInt(SUFFIX.length)] : p;
    }

    /** Estilo do estádio de uma vila (0..STADIUMS-1); o mesmo sorteio é usado na hora de montar a vila. */
    public static int stadiumStyle(long id) {
        return rnd(id, 23).nextInt(STADIUMS);
    }

    public static Info info(StructureStart start) {
        long id = start.getChunkPos().pack();
        var box = start.getBoundingBox();
        BlockPos center = new BlockPos(start.getChunkPos().getMinBlockX() + 8, (box.minY() + box.maxY()) / 2, start.getChunkPos().getMinBlockZ() + 8);
        String place = place(id);
        Random r = rnd(id, 37);
        String team = CLUBS[r.nextInt(CLUBS.length)].replace("{p}", place);
        int color = Sports.COLORS[r.nextInt(Sports.COLORS.length)];
        BlockPos stadium = null, varzea = null;
        int style = -1;
        for (StructurePiece pc : start.getPieces()) {
            if (!(pc instanceof PoolElementStructurePiece pe)) continue;
            String el = pe.getElement().toString();
            var b = pc.getBoundingBox();
            BlockPos mid = new BlockPos((b.minX() + b.maxX()) / 2, b.minY() + 1, (b.minZ() + b.maxZ()) / 2);
            int k = el.indexOf("boombox:estadio_");
            if (k >= 0) {
                stadium = mid;
                char c = el.length() > k + 16 ? el.charAt(k + 16) : '1';
                style = Character.isDigit(c) ? c - '1' : 0;
            } else if (el.contains("boombox:varzea")) {
                varzea = mid;
            }
        }
        if (style < 0) style = stadiumStyle(id);
        String stadiumName = STADIUM_STYLE[Math.max(0, Math.min(STADIUMS - 1, style))].replace("{p}", place);
        return new Info(id, center, place, team, color, stadiumName, stadium, varzea);
    }

    /** A vila em que esse ponto está (dentro de alguma peça dela), ou null. */
    public static Info at(ServerLevel sl, BlockPos pos) {
        StructureStart s = sl.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
        return s != null && s.isValid() ? info(s) : null;
    }

    /** Vila mais perto desse ponto (até ~3 regiões de distância), já carregando o começo dela se precisar. */
    public static Info nearest(ServerLevel sl, BlockPos pos) {
        Info in = at(sl, pos);
        if (in != null) return in;
        var villages = sl.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(StructureTags.VILLAGE);
        if (villages.isEmpty()) return null;
        var hit = sl.getChunkSource().getGenerator().findNearestMapStructure(sl, villages.get(), pos, 3, false);
        if (hit == null) return null;
        var chunk = sl.getChunk(hit.getFirst().getX() >> 4, hit.getFirst().getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
        StructureStart s = chunk.getStartForStructure(hit.getSecond().value());
        return s != null && s.isValid() ? info(s) : null;
    }

    // ------------------------------------------------------------ busca das vilas vizinhas (aos poucos, sem travar)
    /** Busca em andamento: lugares possíveis de vila (um por região), olhados alguns por tique. */
    private static final class Search {
        final ServerLevel sl;
        final BlockPos from;
        final int want;
        final Consumer<List<Info>> done;
        final List<ChunkPos> todo = new ArrayList<>();
        final List<Info> found = new ArrayList<>();
        final Set<Long> seen = new HashSet<>();
        final Set<Structure> types = new HashSet<>();

        Search(ServerLevel sl, BlockPos from, int want, Consumer<List<Info>> done) {
            this.sl = sl;
            this.from = from;
            this.want = want;
            this.done = done;
        }
    }

    private static final List<Search> SEARCHES = new ArrayList<>();

    /**
     * Procura até "want" vilas perto de "from" (a mais perto primeiro) e chama "done" no fim. Olha os lugares em que
     * o jogo pode pôr uma vila (um por região de 34 chunks), até 4 regiões para cada lado.
     */
    public static void searchNearby(ServerLevel sl, BlockPos from, int want, Consumer<List<Info>> done) {
        Search s = new Search(sl, from, want, done);
        var lookup = sl.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var set = lookup.get(StructureTags.VILLAGE);
        if (set.isEmpty()) {
            done.accept(List.of());
            return;
        }
        RandomSpreadStructurePlacement placement = null;
        for (var h : set.get()) {
            s.types.add(h.value());
            if (placement == null) {
                for (var p : sl.getChunkSource().getGeneratorState().getPlacementsForStructure(h)) {
                    if (p instanceof RandomSpreadStructurePlacement rs) placement = rs;
                }
            }
        }
        if (placement == null) {
            done.accept(List.of());
            return;
        }
        long seed = sl.getChunkSource().getGeneratorState().getLevelSeed();
        int spacing = placement.spacing();
        int cx = from.getX() >> 4, cz = from.getZ() >> 4;
        int R = 4;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                ChunkPos c = placement.getPotentialStructureChunk(seed, cx + dx * spacing, cz + dz * spacing);
                s.todo.add(c);
            }
        }
        // mais perto primeiro
        s.todo.sort((a, b) -> Double.compare(d2(a, from), d2(b, from)));
        SEARCHES.add(s);
    }

    private static double d2(ChunkPos c, BlockPos p) {
        double x = c.getMiddleBlockX() - p.getX(), z = c.getMiddleBlockZ() - p.getZ();
        return x * x + z * z;
    }

    /** Chamado todo tique do servidor: olha alguns lugares de cada busca. */
    public static void tick() {
        if (SEARCHES.isEmpty()) return;
        Search s = SEARCHES.get(0);
        long t0 = System.nanoTime();
        while (!s.todo.isEmpty() && s.found.size() < s.want && System.nanoTime() - t0 < 8_000_000L) {
            ChunkPos c = s.todo.remove(0);
            try {
                var chunk = s.sl.getChunk(c.x(), c.z(), ChunkStatus.STRUCTURE_STARTS);
                for (var e : chunk.getAllStarts().entrySet()) {
                    if (!s.types.contains(e.getKey()) || !e.getValue().isValid()) continue;
                    Info in = info(e.getValue());
                    if (s.seen.add(in.id())) s.found.add(in);
                }
            } catch (RuntimeException ex) {
                com.boombox.BoomboxMod.LOGGER.warn("Boombox: não deu para olhar a vila em {}: {}", c, ex.toString());
            }
        }
        if (s.todo.isEmpty() || s.found.size() >= s.want) {
            SEARCHES.remove(0);
            s.found.sort((a, b) -> Double.compare(a.center().distSqr(s.from), b.center().distSqr(s.from)));
            s.done.accept(s.found);
        }
    }

    public static boolean searching() {
        return !SEARCHES.isEmpty();
    }
}
