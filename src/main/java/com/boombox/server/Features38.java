package com.boombox.server;

import com.boombox.WorldStats;
import com.boombox.bar.RankingBoardBlock;
import com.boombox.bar.VillageMapItem;
import com.boombox.entity.Ball;
import com.boombox.entity.Raver;
import com.boombox.entity.RaverRoles;
import com.boombox.entity.Sports;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 3.8: telas novas (mapa da vila, Liga dos Amigos, apostas e ranking) do lado do servidor. */
public final class Features38 {
    /** Telas que o servidor abre no cliente. */
    public static final int UI_MAP = 1, UI_NAV = 2, UI_LEAGUE = 3, UI_BET = 4, UI_RANKING = 5;
    /** Botões que o cliente manda. */
    public static final int A_LEAGUE_OPEN = 1, A_LEAGUE_SAVE = 2, A_LEAGUE_DELETE = 3, A_BET_OPEN = 4, A_BET_PLACE = 5, A_RANKING_OPEN = 6;

    /** Lugares da vila: pedaço do nome do molde → nome no mapa. */
    private static final String[][] PLACES = {
            {"varzea", "Campo de futebol"}, {"ze_bar", "Bar do Seu Zé"}, {"djleo_house", "Balada do DJ Leo"},
            {"volei_praia", "Quadra de vôlei de praia"}, {"beach_hut", "Barraca de praia"}, {"pagode_laje", "Pagode na laje"},
            {"quermesse", "Quermesse"}, {"feira", "Feira de domingo"}, {"casa_piscina", "Casa com piscina"}, {"arraia", "Arraiá"}};

    private Features38() {}

    public static void send(ServerPlayer p, int kind, String data) {
        if (p instanceof FakePlayer) return;
        ServerPlayNetworking.send(p, new Payloads.Ui(kind, data));
    }

    public static void register() {
        VillageMapItem.openHook = Features38::openMap;
        RankingBoardBlock.openHook = Features38::openRanking;
        ServerPlayNetworking.registerGlobalReceiver(Payloads.UiAction.TYPE, (p, ctx) -> action(ctx.player(), p.kind(), p.data()));
    }

    public static void action(ServerPlayer p, int kind, String data) {
        ServerLevel sl = (ServerLevel) p.level();
        String[] f = data.split("\t", -1);
        try {
            switch (kind) {
                case A_LEAGUE_OPEN -> openLeague(p);
                case A_LEAGUE_SAVE -> saveTeam(p, f[0], Integer.parseInt(f[1]));
                case A_LEAGUE_DELETE -> {
                    WorldStats.get(sl.getServer()).removeTeam(p.getUUID());
                    p.sendSystemMessage(Component.literal("§7Seu time saiu da Liga dos Amigos."));
                    openLeague(p);
                }
                case A_BET_OPEN -> openBets(p, Integer.parseInt(f[0]));
                case A_BET_PLACE -> {
                    Ball b = ballOf(p, Integer.parseInt(f[0]));
                    if (b == null) return;
                    b.placeBet(p, Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                    openBets(p, Integer.parseInt(f[0]));
                }
                case A_RANKING_OPEN -> openRanking(p);
                default -> { }
            }
        } catch (RuntimeException ignored) {
            // pedido estragado: ignora
        }
    }

    // ------------------------------------------------------------ Liga dos Amigos
    public static void openLeague(ServerPlayer p) {
        send(p, UI_LEAGUE, WorldStats.get(p.level().getServer()).leagueData(p.getUUID()));
    }

    public static void saveTeam(ServerPlayer p, String rawName, int colorIndex) {
        String name = rawName == null ? "" : rawName.replaceAll("[\\p{Cntrl}§\\t]", "").trim();
        if (name.length() > 24) name = name.substring(0, 24);
        if (name.isEmpty()) {
            p.sendSystemMessage(Component.literal("§7Escreva o nome do seu time."));
            return;
        }
        int color = Sports.COLORS[Math.max(0, Math.min(Sports.COLORS.length - 1, colorIndex))];
        var ws = WorldStats.get(p.level().getServer());
        boolean isNew = ws.team(p.getUUID()) == null;
        ws.setTeam(p, name, color);
        p.sendSystemMessage(Component.literal(isNew ? "§b★ Time criado na Liga dos Amigos: §f" + name + "§b. Jogue contra um amigo que também tem time (organizador → \"Jogar contra o amigo\") e o jogo vale pontos!"
                : "§b★ Time atualizado: §f" + name));
        openLeague(p);
    }

    // ------------------------------------------------------------ apostas
    private static Ball ballOf(ServerPlayer p, int organizer) {
        if (!(p.level().getEntity(organizer) instanceof Raver org) || org.role() != RaverRoles.ORG_FUT || org.distanceTo(p) > 10) return null;
        Ball b = Sports.footballNear((ServerLevel) p.level(), org.position());
        return b == null || !b.hasGoals() ? null : b;
    }

    public static void openBets(ServerPlayer p, int organizer) {
        Ball b = ballOf(p, organizer);
        if (b == null) {
            p.sendSystemMessage(Component.literal("§7Chegue perto do organizador do campo para apostar."));
            return;
        }
        send(p, UI_BET, organizer + "\t" + b.betData(p));
    }

    // ------------------------------------------------------------ ranking
    public static void openRanking(ServerPlayer p) {
        send(p, UI_RANKING, WorldStats.get(p.level().getServer()).rankingData(10));
    }

    // ------------------------------------------------------------ mapa da vila
    public static void openMap(ServerPlayer p) {
        ServerLevel sl = (ServerLevel) p.level();
        List<String[]> found = villagePlaces(sl, p.blockPosition());
        if (found == null) {
            p.sendSystemMessage(Component.literal("§7O mapa não achou nenhuma vila por perto."));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String[] f : found) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(f[0]).append('\t').append(f[1]).append('\t').append(f[2]);
        }
        send(p, UI_MAP, sb.toString());
    }

    /** Lugares da vila mais perto: nome, x, z (o centro da vila primeiro). null = nenhuma vila. */
    public static List<String[]> villagePlaces(ServerLevel sl, BlockPos from) {
        var villages = sl.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(StructureTags.VILLAGE);
        if (villages.isEmpty()) return null;
        var hit = sl.getChunkSource().getGenerator().findNearestMapStructure(sl, villages.get(), from, 40, false);
        if (hit == null) return null;
        BlockPos c = hit.getFirst();
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"Centro da vila", Integer.toString(c.getX()), Integer.toString(c.getZ())});
        var chunk = sl.getChunk(c.getX() >> 4, c.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
        for (var st : chunk.getAllStarts().values()) {
            if (!st.isValid()) continue;
            for (var pc : st.getPieces()) {
                if (!(pc instanceof PoolElementStructurePiece pe)) continue;
                String el = pe.getElement().toString();
                if (!el.contains("boombox:")) continue;
                for (String[] pl : PLACES) {
                    if (!el.contains("boombox:" + pl[0])) continue;
                    var bc = pc.getBoundingBox().getCenter();
                    out.add(new String[]{pl[1], Integer.toString(bc.getX()), Integer.toString(bc.getZ())});
                    break;
                }
            }
        }
        return out;
    }

    /** Liga a seta na tela da pessoa apontando para esse lugar. */
    public static void navigate(ServerPlayer p, String name, Vec3 to) {
        send(p, UI_NAV, name + "\t" + (int) Math.floor(to.x) + "\t" + (int) Math.floor(to.z));
    }
}
