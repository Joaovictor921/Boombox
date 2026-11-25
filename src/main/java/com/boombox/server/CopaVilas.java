package com.boombox.server;

import com.boombox.Achievements;
import com.boombox.BoomboxMod;
import com.boombox.WorldStats;
import com.boombox.bar.Blocks34;
import com.boombox.entity.Ball;
import com.boombox.entity.Cup;
import com.boombox.entity.Raver;
import com.boombox.entity.Sports;
import com.boombox.village.Villages;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 3.9: jogos marcados em outro campo e a rivalidade entre vilas.
 * <ul>
 *     <li><b>Final da várzea no estádio</b>: a copa da várzea chega na final e a vila tem estádio: a final vai para lá.</li>
 *     <li><b>Copa das Vilas</b>: quem ganha a copa da várzea se classifica. Entram as 8 vilas mais perto (o time de cada
 *     uma), em mata-mata: quartas, semifinal e final. O estádio de cada fase é sorteado entre os das vilas da copa, e
 *     uma seta na tela leva até lá. O jogo começa quando alguém do time chega no estádio.</li>
 *     <li><b>Clássico</b>: cada vila tem uma rival (a vila mais perto). O retrospecto fica salvo, e quem passa na vila
 *     depois do clássico ouve provocação (ou desculpa) dos moradores.</li>
 * </ul>
 */
public class CopaVilas extends SavedData {
    /** Um jogo marcado: onde, quem joga, a copa e (na Copa das Vilas) as vilas de cada time. */
    public static final class Booking {
        public int kind; // 0 = final da várzea no estádio, 1 = Copa das Vilas
        public BlockPos venue;
        public String venueName = "", villageName = "";
        public final Map<UUID, Integer> roster = new LinkedHashMap<>();
        public Cup cup;
        public boolean classico;
        public Villages.Info home, away;
        /** Copa das Vilas: a vila de cada time (índice do time na copa); null = time sem vila conhecida. */
        public final Villages.Info[] teams = new Villages.Info[8];
        /** Vila rival da vila do time (para marcar clássico). */
        public long rivalId;

        public int positionOf(UUID id) {
            return roster.getOrDefault(id, 6);
        }
    }

    private final List<Booking> bookings = new ArrayList<>();
    /** Retrospecto dos clássicos: "idMenor:idMaior" → [vitórias do menor, empates, vitórias do maior]. */
    private final Map<String, int[]> rivalry = new LinkedHashMap<>();
    /** Último clássico de cada vila: [nome da outra vila, gols da vila, gols da outra]. */
    private final Map<Long, String[]> lastClassico = new HashMap<>();
    /** Recorde do treino de cobrança (nome → pontos). */
    private final Map<String, Integer> training = new LinkedHashMap<>();

    /** Vila rival de cada vila (descoberta na hora; não precisa salvar). */
    private static final Map<Long, Villages.Info> RIVAL = new HashMap<>();
    private static final Set<Long> RIVAL_NONE = new HashSet<>();
    private static final Set<Long> RIVAL_SEARCH = new HashSet<>();
    /** Quem já ouviu provocação em cada vila (e quando). */
    private static final Map<String, Long> TEASED = new HashMap<>();

    public static final SavedDataType<CopaVilas> TYPE = new SavedDataType<>(BoomboxMod.id("copa_vilas"), CopaVilas::new,
            Codec.STRING.fieldOf("data").codec().xmap(CopaVilas::decode, CopaVilas::encode), null);

    public static CopaVilas get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Booking> bookings() {
        return bookings;
    }

    // ------------------------------------------------------------ treino de cobrança
    /** Guarda os pontos do treino; devolve o recorde da pessoa (já contando esse). */
    public int trainingRecord(String name, int pts) {
        int best = Math.max(training.getOrDefault(name, 0), pts);
        training.put(name, best);
        setDirty();
        return best;
    }

    // ------------------------------------------------------------ campo esperando o jogo marcado
    /** Chamado pela bola parada (pelada dos aldeões) de tempos em tempos: começa o jogo marcado para este campo. */
    public static void tickVenue(ServerLevel sl, Ball ball) {
        if (ball.stadium && ball.stadiumName.isEmpty() && ball.tickCount % 400 == 17) {
            Villages.Info v = Villages.at(sl, ball.blockPosition());
            ball.stadiumName = v != null ? v.stadiumName() : "Estádio da Vila";
        }
        CopaVilas data = get(sl.getServer());
        if (data.bookings.isEmpty() || ball.center() == null) return;
        for (Booking b : data.bookings) {
            if (b.venue == null || Math.hypot(b.venue.getX() + 0.5 - ball.center().x, b.venue.getZ() + 0.5 - ball.center().z) > 40) continue;
            List<ServerPlayer> here = new ArrayList<>();
            for (UUID id : b.roster.keySet()) {
                ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
                if (p != null && p.level() == sl && p.position().distanceTo(ball.center()) < 45) here.add(p);
            }
            if (here.isEmpty()) continue;
            if (b.cup.opponent() < 0) continue;
            if (ball.stadium && ball.stadiumName.isEmpty()) ball.stadiumName = b.venueName;
            ball.startBooked(sl, b, here);
            return;
        }
    }

    /** Fim de um jogo de copa: Copa das Vilas, final da várzea no estádio, classificação. true = acabou o jogo aqui. */
    public static boolean afterCupMatch(ServerLevel sl, Ball ball, Cup cup, int copaKind, Booking booking, Map<UUID, Integer> roster) {
        CopaVilas data = get(sl.getServer());
        if (booking != null) {
            data.bookings.remove(booking);
            data.setDirty();
        }
        if (cup.vilas) {
            if (cup.finished()) {
                if (cup.champion() == 0) vilasChampion(sl, ball, cup, roster);
                return true;
            }
            nextVilasStage(sl, booking, cup, roster);
            return true;
        }
        if (cup.champion() == 0) {
            // campeão da várzea: vai para a Copa das Vilas
            qualify(sl, ball, cup.names[0], cup.colors[0], roster);
            return copaKind >= 0;
        }
        if (copaKind < 0 && !ball.stadium && cup.stage == Cup.FINAL && !cup.out && cup.opponent() >= 0) {
            Villages.Info v = Villages.at(sl, ball.blockPosition());
            if (v != null && v.stadium() != null) {
                Booking b = new Booking();
                b.kind = 0;
                b.cup = cup;
                b.venue = v.stadium();
                b.venueName = v.stadiumName();
                b.villageName = v.name();
                b.roster.putAll(roster);
                data.bookings.add(b);
                data.setDirty();
                tell(sl, roster, "§6§l★ A final vai ser no estádio! §r§fO " + cup.names[0] + " x " + cup.names[cup.opponent()]
                        + " é no §e" + v.stadiumName() + "§f, aqui na " + v.name() + ". Siga a seta: o jogo começa quando você chegar.");
                navigateAll(sl, roster, v.stadiumName(), v.stadium());
                return true;
            }
        }
        return copaKind >= 0;
    }

    /** Desistiu do jogo marcado: a vaga é perdida. */
    public static void cancel(ServerLevel sl, Booking b, ServerPlayer who) {
        CopaVilas data = get(sl.getServer());
        if (data.bookings.remove(b)) data.setDirty();
        who.sendSystemMessage(Component.literal(b.kind == 1 ? "§7Você desistiu da Copa das Vilas. O " + b.cup.names[0] + " saiu da competição."
                : "§7Você desistiu da final. Fale com o organizador para começar outra copa."));
    }

    // ------------------------------------------------------------ Copa das Vilas
    /** Só para o teste: classifica direto para a Copa das Vilas. */
    public static void testQualify(ServerLevel sl, Ball ball, String team, int color, Map<UUID, Integer> roster) {
        qualify(sl, ball, team, color, roster);
    }

    private static void qualify(ServerLevel sl, Ball ball, String team, int color, Map<UUID, Integer> roster) {
        Villages.Info home = Villages.at(sl, ball.blockPosition());
        if (home == null) home = Villages.nearest(sl, ball.blockPosition());
        if (home == null) {
            tell(sl, roster, "§7Campeão! Mas não achei outras vilas por perto para uma Copa das Vilas.");
            return;
        }
        Villages.Info h = home;
        tell(sl, roster, "§6§l🏆 Classificado para a COPA DAS VILAS! §r§fO " + team + " vai representar a " + home.name()
                + " contra os campeões das vilas vizinhas. O organizador está ligando para elas...");
        Villages.searchNearby(sl, home.center(), 8, list -> buildVilas(sl, h, list, team, color, roster));
    }

    private static void buildVilas(ServerLevel sl, Villages.Info home, List<Villages.Info> found, String team, int color, Map<UUID, Integer> roster) {
        Booking b = new Booking();
        b.kind = 1;
        b.roster.putAll(roster);
        Cup cup = new Cup(false);
        cup.names[0] = clean(team);
        cup.colors[0] = color & 0xFFFFFF;
        b.teams[0] = home;
        List<Integer> used = new ArrayList<>(List.of(cup.colors[0]));
        int n = 1;
        for (Villages.Info v : found) {
            if (v.id() == home.id()) continue;
            if (b.rivalId == 0) b.rivalId = v.id();
            if (n >= 8) break;
            cup.names[n] = clean(v.team());
            int c = v.color();
            for (int u : used) if (Sports.similar(c, u)) { c = -1; break; }
            if (c < 0) {
                for (int x : Sports.COLORS) {
                    boolean ok = true;
                    for (int u : used) if (Sports.similar(x, u)) ok = false;
                    if (ok) { c = x; break; }
                }
                if (c < 0) c = v.color();
            }
            cup.colors[n] = c;
            used.add(c);
            b.teams[n] = v;
            n++;
        }
        RandomSourceHolder r = new RandomSourceHolder(sl);
        if (n < 8) {
            // poucas vilas por perto: completa com times de vilas mais longe (sem estádio conhecido)
            List<String> taken = new ArrayList<>();
            for (int i = 0; i < n; i++) taken.add(cup.names[i]);
            var extra = Sports.randomTeams(sl.getRandom(), false, 8 - n, taken, used);
            for (String[] t : extra) {
                cup.names[n] = clean(t[0]);
                cup.colors[n] = Integer.parseInt(t[1]);
                n++;
            }
        }
        for (int i = 8; i < 16; i++) cup.names[i] = "";
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7));
        java.util.Collections.shuffle(order, r.random);
        int[] o = new int[8];
        for (int i = 0; i < 8; i++) o[i] = order.get(i);
        cup.startKnockout(o);
        b.cup = cup;
        StringBuilder sb = new StringBuilder("§6§l🏆 Copa das Vilas! §r§fQuartas: ");
        for (int k = 0; k < 4; k++) sb.append(k > 0 ? "§7, §f" : "").append(cup.names[o[2 * k]]).append(" x ").append(cup.names[o[2 * k + 1]]);
        tell(sl, roster, sb.toString());
        drawVenue(sl, b, r.random);
        CopaVilas data = get(sl.getServer());
        data.bookings.add(b);
        data.setDirty();
        announceBooking(sl, b);
    }

    /** Sorteia o estádio da fase (entre os das vilas da copa; se nenhuma tiver estádio, um campo de várzea). */
    private static void drawVenue(ServerLevel sl, Booking b, Random rnd) {
        List<Villages.Info> stadiums = new ArrayList<>(), fields = new ArrayList<>();
        for (Villages.Info v : b.teams) {
            if (v == null) continue;
            if (v.stadium() != null) stadiums.add(v);
            else if (v.varzea() != null) fields.add(v);
        }
        List<Villages.Info> pool = !stadiums.isEmpty() ? stadiums : fields;
        Villages.Info v = pool.isEmpty() ? b.teams[0] : pool.get(rnd.nextInt(pool.size()));
        b.venue = v.venue();
        b.venueName = v.venueName();
        b.villageName = v.name();
        int opp = b.cup.opponent();
        b.home = b.teams[0];
        b.away = opp >= 0 && opp < 8 ? b.teams[opp] : null;
        b.classico = b.away != null && b.away.id() == b.rivalId;
        if (b.venue == null) {
            // vila sem estádio nem campo conhecido: joga no lugar do centro da vila do time
            b.venue = b.teams[0].center();
            b.venueName = "Campo da " + b.teams[0].name();
        }
    }

    private static void announceBooking(ServerLevel sl, Booking b) {
        int opp = b.cup.opponent();
        if (opp < 0) return;
        String vs = b.cup.names[0] + " x " + b.cup.names[opp];
        tell(sl, b.roster, "§6" + b.cup.stageName() + ": §f" + vs + (b.classico ? " §c§l⚔ CLÁSSICO!" : "") + " §7— sorteado para o §e" + b.venueName
                + " §7(" + b.villageName + "). §fSiga a seta na tela: o jogo começa quando você chegar no estádio.");
        navigateAll(sl, b.roster, b.venueName, b.venue);
    }

    private static void nextVilasStage(ServerLevel sl, Booking old, Cup cup, Map<UUID, Integer> roster) {
        Booking b = new Booking();
        b.kind = 1;
        b.cup = cup;
        b.roster.putAll(roster);
        if (old != null) {
            System.arraycopy(old.teams, 0, b.teams, 0, 8);
            b.rivalId = old.rivalId;
            for (var e : old.roster.entrySet()) b.roster.putIfAbsent(e.getKey(), e.getValue());
        }
        drawVenue(sl, b, new Random(sl.getRandom().nextLong()));
        CopaVilas data = get(sl.getServer());
        data.bookings.add(b);
        data.setDirty();
        announceBooking(sl, b);
    }

    private static void vilasChampion(ServerLevel sl, Ball ball, Cup cup, Map<UUID, Integer> roster) {
        sl.sendParticles(ParticleTypes.FIREWORK, ball.center().x, ball.center().y + 3, ball.center().z, 200, 4, 3, 4, 0.25);
        for (UUID id : roster.keySet()) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            ItemStack taca = new ItemStack(Blocks34.TACA_VARZEA);
            taca.set(DataComponents.CUSTOM_NAME, Component.literal("§6Taça da Copa das Vilas"));
            taca.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            if (!p.getInventory().add(taca)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), taca);
            Achievements.award(p, "campeao_das_vilas");
            WorldStats.get(sl.getServer()).add(p.getName().getString(), WorldStats.CUPS, 1);
            p.sendSystemMessage(Component.literal("§6§l🏆 O " + cup.names[0] + " é CAMPEÃO DA COPA DAS VILAS! §r§fVocê levou a Taça da Copa das Vilas."));
        }
    }

    // ------------------------------------------------------------ clássico
    /**
     * A vila deste campo e a rival dela. null = ainda procurando (tente de novo daqui a pouco); vazio = não tem vila
     * (ou rival) por perto.
     */
    public static Villages.Info[] rivalry(ServerLevel sl, Ball ball) {
        Villages.Info home = Villages.at(sl, ball.blockPosition());
        if (home == null) return new Villages.Info[0];
        Villages.Info r = RIVAL.get(home.id());
        if (r != null) return new Villages.Info[]{home, r};
        if (RIVAL_NONE.contains(home.id())) return new Villages.Info[0];
        if (RIVAL_SEARCH.add(home.id())) {
            Villages.searchNearby(sl, home.center(), 2, list -> {
                RIVAL_SEARCH.remove(home.id());
                for (Villages.Info v : list) {
                    if (v.id() != home.id()) {
                        RIVAL.put(home.id(), v);
                        return;
                    }
                }
                RIVAL_NONE.add(home.id());
            });
        }
        return null;
    }

    private static String pairKey(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    /** Fim do clássico: retrospecto, festa na vila de quem ganhou e o que os moradores vão falar depois. */
    public static void classicoResult(ServerLevel sl, Ball ball, Villages.Info home, Villages.Info away, int gh, int ga) {
        CopaVilas data = get(sl.getServer());
        int[] r = data.rivalry.computeIfAbsent(pairKey(home.id(), away.id()), k -> new int[3]);
        boolean homeLow = home.id() < away.id();
        if (gh == ga) r[1]++;
        else if ((gh > ga) == homeLow) r[0]++;
        else r[2]++;
        int hw = homeLow ? r[0] : r[2], aw = homeLow ? r[2] : r[0];
        data.lastClassico.put(home.id(), new String[]{away.name(), Integer.toString(gh), Integer.toString(ga)});
        data.lastClassico.put(away.id(), new String[]{home.name(), Integer.toString(ga), Integer.toString(gh)});
        data.setDirty();
        Vec3 c = ball.center();
        for (var p : sl.players()) {
            if (p.position().distanceTo(c) > 80) continue;
            p.sendSystemMessage(Component.literal("§c⚔ Retrospecto do clássico: §f" + home.name() + " §e" + hw + "§f vitória(s), §7" + r[1] + " empate(s)§f, "
                    + away.name() + " §e" + aw + "§f vitória(s)."));
        }
        if (gh > ga) {
            // festa da vitória: fogos e todo mundo comemorando
            for (var p : sl.players()) if (p.position().distanceTo(c) < 80) p.sendSystemMessage(Component.literal("§a§l★ Festa na " + home.name()
                    + "! §r§fA vila inteira comemora a vitória no clássico. Hoje a balada é por conta da vitória!"));
            for (int i = 0; i < 6; i++) {
                double x = c.x + (sl.getRandom().nextDouble() - 0.5) * 30, z = c.z + (sl.getRandom().nextDouble() - 0.5) * 30;
                sl.sendParticles(ParticleTypes.FIREWORK, x, c.y + 8 + i, z, 60, 1.2, 1.2, 1.2, 0.2);
            }
            for (Raver rv : sl.getEntitiesOfClass(Raver.class, new AABB(c, c).inflate(80, 20, 80))) rv.celebrate(true);
            for (var v : sl.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, new AABB(c, c).inflate(80, 20, 80))) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2, v.getZ(), 6, 0.3, 0.3, 0.3, 0);
            }
        } else if (ga > gh) {
            for (var p : sl.players()) if (p.position().distanceTo(c) < 80) p.sendSystemMessage(Component.literal("§7A " + away.name()
                    + " vai fazer festa hoje... e a provocação já começou. Não passe por lá tão cedo!"));
        }
    }

    // ------------------------------------------------------------ tique do servidor
    /** A cada segundo: provocação de quem mora na vila que jogou o último clássico. */
    public static void tickServer(MinecraftServer server) {
        Villages.tick();
        if (server.getTickCount() % 40 != 0) return;
        CopaVilas data = get(server);
        if (data.lastClassico.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel sl)) continue;
            Villages.Info v;
            try {
                v = Villages.at(sl, p.blockPosition());
            } catch (RuntimeException e) {
                continue;
            }
            if (v == null) continue;
            String[] last = data.lastClassico.get(v.id());
            if (last == null) continue;
            String key = p.getUUID() + ":" + v.id();
            Long t = TEASED.get(key);
            if (t != null && server.getTickCount() - t < 20 * 60 * 8) continue;
            TEASED.put(key, (long) server.getTickCount());
            int mine = Integer.parseInt(last[1]), theirs = Integer.parseInt(last[2]);
            String msg;
            if (mine > theirs) {
                msg = pick(sl, "Bem-vindo à " + v.name() + "! Viu o clássico? " + mine + " a " + theirs + " na " + last[0] + "! Freguesia é freguesia!",
                        "Aqui é " + v.name() + ", terra do campeão do clássico! " + mine + " a " + theirs + ", pode anotar!",
                        "Ô, tá perdido? A " + last[0] + " é pra lá! Aqui só tem vencedor: " + mine + " a " + theirs + "!");
            } else if (mine < theirs) {
                msg = pick(sl, "Nem fala do clássico... " + theirs + " a " + mine + " pra " + last[0] + ". Mas o troco vem!",
                        "Juiz ladrão! Aquele " + theirs + " a " + mine + " não valeu! Na próxima a " + v.name() + " devolve!",
                        "Foi só um dia ruim. A " + last[0] + " que aproveite, que a revanche tá marcada!");
            } else {
                msg = pick(sl, "Empate no clássico com a " + last[0] + "... " + mine + " a " + theirs + ". Ninguém dorme em paz!",
                        "Clássico empatado, " + mine + " a " + mine + ". A gente tava melhor, viu?");
            }
            p.sendSystemMessage(Component.literal("§7» Morador da " + v.name() + ": §f" + msg));
        }
    }

    private static String pick(ServerLevel sl, String... s) {
        return s[sl.getRandom().nextInt(s.length)];
    }

    /** Quem entrou no mundo e tem jogo marcado: lembra e liga a seta. */
    public static void onJoin(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel sl)) return;
        for (Booking b : get(sl.getServer()).bookings) {
            if (!b.roster.containsKey(p.getUUID()) || b.cup.opponent() < 0) continue;
            p.sendSystemMessage(Component.literal("§6Você tem jogo marcado: §f" + b.cup.stageName() + ", " + b.cup.names[0] + " x " + b.cup.names[b.cup.opponent()]
                    + " no §e" + b.venueName + " §7(" + b.villageName + ")§f. Siga a seta!"));
            Features38.navigate(p, b.venueName, Vec3.atCenterOf(b.venue));
            return;
        }
    }

    private static void tell(ServerLevel sl, Map<UUID, Integer> roster, String msg) {
        for (UUID id : roster.keySet()) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p != null) p.sendSystemMessage(Component.literal(msg));
        }
    }

    private static void navigateAll(ServerLevel sl, Map<UUID, Integer> roster, String name, BlockPos to) {
        for (UUID id : roster.keySet()) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p != null) Features38.navigate(p, name, Vec3.atCenterOf(to));
        }
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace(';', ' ');
    }

    /** Sorteio com a semente do mundo (para a chave). */
    private static final class RandomSourceHolder {
        final Random random;

        RandomSourceHolder(ServerLevel sl) {
            random = new Random(sl.getRandom().nextLong());
        }
    }

    // ------------------------------------------------------------ salvar
    private static final char F = '\u001F', R = '\u001E';

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static String info(Villages.Info v) {
        if (v == null) return "-";
        return b64(v.id() + "\t" + pos(v.center()) + "\t" + v.place() + "\t" + v.team() + "\t" + v.color() + "\t" + v.stadiumName() + "\t"
                + pos(v.stadium()) + "\t" + pos(v.varzea()));
    }

    private static Villages.Info info(String s) {
        if (s.equals("-")) return null;
        String[] f = unb64(s).split("\t", -1);
        return new Villages.Info(Long.parseLong(f[0]), pos(f[1]), f[2], f[3], Integer.parseInt(f[4]), f[5], pos(f[6]), pos(f[7]));
    }

    private static String pos(BlockPos p) {
        return p == null ? "-" : p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static BlockPos pos(String s) {
        if (s.equals("-")) return null;
        String[] f = s.split(",");
        return new BlockPos(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
    }

    private String encode() {
        StringBuilder sb = new StringBuilder();
        for (Booking b : bookings) {
            StringBuilder ro = new StringBuilder();
            for (var e : b.roster.entrySet()) ro.append(ro.length() > 0 ? "," : "").append(e.getKey()).append("=").append(e.getValue());
            StringBuilder ts = new StringBuilder();
            for (int i = 0; i < 8; i++) ts.append(i > 0 ? "," : "").append(info(b.teams[i]));
            sb.append("B").append(F).append(b.kind).append(F).append(pos(b.venue)).append(F).append(b64(b.venueName)).append(F).append(b64(b.villageName))
                    .append(F).append(ro).append(F).append(b64(b.cup.encode())).append(F).append(b.classico ? 1 : 0).append(F).append(info(b.home))
                    .append(F).append(info(b.away)).append(F).append(ts).append(F).append(b.rivalId).append(R);
        }
        for (var e : rivalry.entrySet()) {
            sb.append("V").append(F).append(e.getKey()).append(F).append(e.getValue()[0]).append(F).append(e.getValue()[1]).append(F).append(e.getValue()[2]).append(R);
        }
        for (var e : lastClassico.entrySet()) {
            sb.append("L").append(F).append(e.getKey()).append(F).append(b64(e.getValue()[0])).append(F).append(e.getValue()[1]).append(F).append(e.getValue()[2]).append(R);
        }
        for (var e : training.entrySet()) sb.append("T").append(F).append(b64(e.getKey())).append(F).append(e.getValue()).append(R);
        return sb.toString();
    }

    private static CopaVilas decode(String s) {
        CopaVilas d = new CopaVilas();
        for (String line : s.split(String.valueOf(R))) {
            if (line.isEmpty()) continue;
            String[] f = line.split(String.valueOf(F), -1);
            try {
                switch (f[0]) {
                    case "B" -> {
                        Booking b = new Booking();
                        b.kind = Integer.parseInt(f[1]);
                        b.venue = pos(f[2]);
                        b.venueName = unb64(f[3]);
                        b.villageName = unb64(f[4]);
                        if (!f[5].isEmpty()) {
                            for (String r : f[5].split(",")) {
                                String[] kv = r.split("=");
                                b.roster.put(UUID.fromString(kv[0]), Integer.parseInt(kv[1]));
                            }
                        }
                        b.cup = Cup.decode(unb64(f[6]));
                        b.classico = f[7].equals("1");
                        b.home = info(f[8]);
                        b.away = info(f[9]);
                        String[] ts = f[10].split(",", -1);
                        for (int i = 0; i < 8 && i < ts.length; i++) b.teams[i] = info(ts[i]);
                        b.rivalId = Long.parseLong(f[11]);
                        d.bookings.add(b);
                    }
                    case "V" -> d.rivalry.put(f[1], new int[]{Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4])});
                    case "L" -> d.lastClassico.put(Long.parseLong(f[1]), new String[]{unb64(f[2]), f[3], f[4]});
                    case "T" -> d.training.put(unb64(f[1]), Integer.parseInt(f[2]));
                    default -> { }
                }
            } catch (RuntimeException e) {
                BoomboxMod.LOGGER.warn("Boombox: linha estragada na Copa das Vilas: {}", e.toString());
            }
        }
        return d;
    }

    /** Só para o teste: retrospecto de um clássico. */
    public int[] rivalryOf(long a, long b) {
        return rivalry.get(pairKey(a, b));
    }

    /** Só para o teste: a rival já descoberta de uma vila. */
    public static Villages.Info knownRival(long id) {
        return RIVAL.get(id);
    }
}
