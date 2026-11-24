package com.boombox;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 3.8: o que fica salvo no mundo: ranking do bar (artilheiros, copas e sinuca), a Liga dos Amigos (o time de cada
 * pessoa e a tabela) e as esmeraldas das apostas (a pagar e as que estão "na mesa" de um jogo em andamento).
 */
public class WorldStats extends SavedData {
    public static final int GOALS = 0, CUPS = 1, SINUCA = 2;
    public static final String[] CATEGORY = {"Artilheiros", "Copas", "Sinuca"};

    /** Nome da pessoa → [gols, copas ganhas (futebol e vôlei), torneios de sinuca]. */
    private final Map<String, int[]> stats = new LinkedHashMap<>();
    /** Liga dos Amigos: o time de cada pessoa. */
    private final Map<UUID, Team> league = new LinkedHashMap<>();
    /** Esmeraldas que a pessoa tem para receber (ganhou aposta fora do jogo, ou o mundo fechou no meio do jogo). */
    private final Map<UUID, Integer> pending = new HashMap<>();
    /** Esmeraldas apostadas num jogo que ainda não acabou. */
    private final Map<UUID, Integer> escrow = new HashMap<>();

    public static final class Team {
        public String owner, name;
        public int color;
        /** jogos, vitórias, empates, derrotas, gols pró, gols contra */
        public int j, v, e, d, gp, gc;

        public int points() {
            return v * 3 + e;
        }
    }

    public static final SavedDataType<WorldStats> TYPE = new SavedDataType<>(BoomboxMod.id("estatisticas"), WorldStats::new,
            Codec.STRING.fieldOf("data").codec().xmap(WorldStats::decode, WorldStats::encode), null);

    public static WorldStats get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // ------------------------------------------------------------ ranking
    public void add(String name, int category, int amount) {
        if (name == null || name.isBlank()) return;
        stats.computeIfAbsent(name, k -> new int[3])[category] += amount;
        setDirty();
    }

    public int stat(String name, int category) {
        int[] s = stats.get(name);
        return s == null ? 0 : s[category];
    }

    /** As n primeiras pessoas da categoria (nome e número), da maior para a menor. */
    public List<Map.Entry<String, Integer>> top(int category, int n) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>();
        for (var e : stats.entrySet()) if (e.getValue()[category] > 0) out.add(Map.entry(e.getKey(), e.getValue()[category]));
        out.sort((a, b) -> b.getValue() - a.getValue());
        return out.size() > n ? out.subList(0, n) : out;
    }

    /** Texto do ranking (para o quadro e a tela): categoria por linha, "nome=número" separados por tab. */
    public String rankingData(int n) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < 3; c++) {
            if (c > 0) sb.append('\n');
            boolean first = true;
            for (var e : top(c, n)) {
                if (!first) sb.append('\t');
                sb.append(clean(e.getKey())).append('=').append(e.getValue());
                first = false;
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ Liga dos Amigos
    public Team team(UUID id) {
        return league.get(id);
    }

    public void setTeam(ServerPlayer p, String name, int color) {
        Team t = league.computeIfAbsent(p.getUUID(), k -> new Team());
        t.owner = p.getName().getString();
        t.name = name;
        t.color = color;
        setDirty();
    }

    public void removeTeam(UUID id) {
        if (league.remove(id) != null) setDirty();
    }

    public void recordLeague(UUID a, UUID b, int ga, int gb) {
        Team ta = league.get(a), tb = league.get(b);
        if (ta == null || tb == null) return;
        apply(ta, ga, gb);
        apply(tb, gb, ga);
        setDirty();
    }

    private static void apply(Team t, int gf, int ga) {
        t.j++;
        t.gp += gf;
        t.gc += ga;
        if (gf > ga) t.v++;
        else if (gf == ga) t.e++;
        else t.d++;
    }

    /** Tabela da liga, já ordenada (pontos, vitórias, saldo, gols pró). */
    public List<Team> table() {
        List<Team> out = new ArrayList<>(league.values());
        out.sort((x, y) -> x.points() != y.points() ? y.points() - x.points() : x.v != y.v ? y.v - x.v
                : (x.gp - x.gc) != (y.gp - y.gc) ? (y.gp - y.gc) - (x.gp - x.gc) : y.gp - x.gp);
        return out;
    }

    /** Dados da tela da liga: o meu time na primeira linha, depois a tabela (uma linha por time). */
    public String leagueData(UUID me) {
        StringBuilder sb = new StringBuilder();
        Team mine = league.get(me);
        sb.append(mine == null ? "" : clean(mine.name) + "\t" + mine.color);
        for (Team t : table()) {
            sb.append('\n').append(clean(t.name)).append('\t').append(t.color).append('\t').append(clean(t.owner)).append('\t')
                    .append(t.points()).append('\t').append(t.j).append('\t').append(t.v).append('\t').append(t.e).append('\t')
                    .append(t.d).append('\t').append(t.gp).append('\t').append(t.gc);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ esmeraldas das apostas
    public void hold(UUID id, int amount) {
        escrow.merge(id, amount, Integer::sum);
        setDirty();
    }

    public void release(UUID id, int amount) {
        int left = escrow.getOrDefault(id, 0) - amount;
        if (left > 0) escrow.put(id, left);
        else escrow.remove(id);
        setDirty();
    }

    /** Paga agora (se a pessoa está no mundo) ou guarda para quando ela entrar. */
    public void pay(MinecraftServer server, UUID id, int amount) {
        if (amount <= 0) return;
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) giveEmeralds(p, amount);
        else {
            pending.merge(id, amount, Integer::sum);
            setDirty();
        }
    }

    /** O mundo abriu: apostas de jogos que não acabaram voltam para quem apostou. */
    public void refundOpenBets() {
        if (escrow.isEmpty()) return;
        for (var e : escrow.entrySet()) pending.merge(e.getKey(), e.getValue(), Integer::sum);
        escrow.clear();
        setDirty();
    }

    public void onJoin(ServerPlayer p) {
        Integer n = pending.remove(p.getUUID());
        if (n == null || n <= 0) return;
        setDirty();
        giveEmeralds(p, n);
        p.sendSystemMessage(Component.literal("§a$ Você recebeu " + n + " esmeralda(s) de apostas que estavam guardadas para você."));
    }

    public static void giveEmeralds(ServerPlayer p, int n) {
        while (n > 0) {
            int k = Math.min(64, n);
            ItemStack em = new ItemStack(Items.EMERALD, k);
            if (!p.getInventory().add(em)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), em);
            n -= k;
        }
    }

    public static int countEmeralds(net.minecraft.world.entity.player.Player p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) n += s.getCount();
        }
        return n;
    }

    public static void takeEmeralds(net.minecraft.world.entity.player.Player p, int amount) {
        for (int i = 0; i < p.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.is(Items.EMERALD)) continue;
            int t = Math.min(amount, s.getCount());
            s.shrink(t);
            amount -= t;
        }
    }

    // ------------------------------------------------------------ salvar
    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('=', '-');
    }

    private String encode() {
        StringBuilder sb = new StringBuilder("v1");
        for (var e : stats.entrySet()) {
            int[] s = e.getValue();
            sb.append("\nS\t").append(clean(e.getKey())).append('\t').append(s[0]).append('\t').append(s[1]).append('\t').append(s[2]);
        }
        for (var e : league.entrySet()) {
            Team t = e.getValue();
            sb.append("\nL\t").append(e.getKey()).append('\t').append(clean(t.owner)).append('\t').append(clean(t.name)).append('\t').append(t.color)
                    .append('\t').append(t.j).append('\t').append(t.v).append('\t').append(t.e).append('\t').append(t.d)
                    .append('\t').append(t.gp).append('\t').append(t.gc);
        }
        for (var e : pending.entrySet()) sb.append("\nP\t").append(e.getKey()).append('\t').append(e.getValue());
        for (var e : escrow.entrySet()) sb.append("\nE\t").append(e.getKey()).append('\t').append(e.getValue());
        return sb.toString();
    }

    private static WorldStats decode(String data) {
        WorldStats w = new WorldStats();
        for (String line : data.split("\n")) {
            String[] f = line.split("\t", -1);
            try {
                switch (f[0]) {
                    case "S" -> w.stats.put(f[1], new int[]{Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4])});
                    case "L" -> {
                        Team t = new Team();
                        t.owner = f[2];
                        t.name = f[3];
                        t.color = Integer.parseInt(f[4]);
                        t.j = Integer.parseInt(f[5]);
                        t.v = Integer.parseInt(f[6]);
                        t.e = Integer.parseInt(f[7]);
                        t.d = Integer.parseInt(f[8]);
                        t.gp = Integer.parseInt(f[9]);
                        t.gc = Integer.parseInt(f[10]);
                        w.league.put(UUID.fromString(f[1]), t);
                    }
                    case "P" -> w.pending.put(UUID.fromString(f[1]), Integer.parseInt(f[2]));
                    case "E" -> w.escrow.put(UUID.fromString(f[1]), Integer.parseInt(f[2]));
                    default -> { }
                }
            } catch (RuntimeException ignored) {
                // linha estragada: pula
            }
        }
        return w;
    }
}
