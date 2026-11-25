package com.boombox.server;

import com.boombox.bar.Blocks39;
import com.boombox.bar.FacingBlock;
import com.boombox.entity.Ball;
import com.boombox.entity.ModEntities;
import com.boombox.entity.Raver;
import com.boombox.entity.RaverRoles;
import com.boombox.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 3.9: pelada na rua. De dia, numa vila perto de alguém, às vezes 4 crianças montam uma pelada numa rua reta com dois
 * chinelos de trave em cada ponta. Quem passar pode entrar: é só chutar a bola (botão esquerdo) que você vira o
 * reforço do time que está perdendo (ou do que tem menos gente). Acaba em uns 2 minutos e meio, ou quando um time
 * faz 5 gols. Aí as crianças pegam os chinelos e vão embora.
 */
public final class StreetPelada {
    private StreetPelada() {}

    public static final int TEAM_COLORS[] = {0xF0D020, 0x2D5BE0};
    public static final String[] TEAM_NAMES = {"Amarelinho", "Azulão"};
    private static final int LENGTH = 14, DURATION = 20 * 150, WIN = 5;

    /** Uma pelada: a bola, as crianças, os chinelos e o placar. */
    public static final class Game {
        public ServerLevel sl;
        public UUID ball;
        public final List<UUID> kids = new ArrayList<>();
        public final List<BlockPos> chinelos = new ArrayList<>();
        /** Meio do gol de cada ponta (a 0 é defendida pelo time 0) e as direções da rua. */
        public Vec3 g0, g1, axis, side;
        public double y;
        public final int[] score = new int[2];
        public final Map<UUID, Integer> humans = new HashMap<>();
        public int ticks, pause;
        public UUID lastKicker;
        public int lastTeam = -1;

        public Vec3 goal(int team) {
            return team == 0 ? g0 : g1;
        }

        /** Gol que esse time ataca. */
        public Vec3 attack(int team) {
            return team == 0 ? g1 : g0;
        }

        public Vec3 middle() {
            return g0.add(g1).scale(0.5);
        }
    }

    private static final List<Game> GAMES = new ArrayList<>();

    public static List<Game> games() {
        return GAMES;
    }

    // ------------------------------------------------------------ começar
    public static void tick(MinecraftServer server) {
        for (Game g : new ArrayList<>(GAMES)) tickGame(g);
        if (server.getTickCount() % 600 != 300) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel sl) || p.isSpectator()) continue;
            long t = sl.getOverworldClockTime() % 24000;
            if (t < 1000 || t > 11000 || sl.isRaining()) continue;
            if (sl.getRandom().nextInt(4) != 0) continue;
            if (GAMES.stream().anyMatch(g -> g.sl == sl && g.middle().distanceTo(p.position()) < 150)) continue;
            if (Villages.at(sl, p.blockPosition()) == null) continue;
            start(sl, p.blockPosition());
        }
    }

    /** Procura uma rua reta (14 blocos de caminho de terra) perto do ponto e começa a pelada lá. */
    public static Game start(ServerLevel sl, BlockPos near) {
        // olha todos os pontos em volta (em ordem sorteada) e fica com a primeira rua reta que achar
        var rnd = sl.getRandom();
        List<int[]> pts = new ArrayList<>();
        for (int dx = -28; dx <= 28; dx++) for (int dz = -28; dz <= 28; dz++) pts.add(new int[]{dx, dz});
        java.util.Collections.shuffle(pts, new java.util.Random(rnd.nextLong()));
        for (int[] d0 : pts) {
            int x = near.getX() + d0[0], z = near.getZ() + d0[1];
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos s = new BlockPos(x, y, z);
            if (!sl.getBlockState(s).is(Blocks.DIRT_PATH)) continue;
            if (near.distSqr(s) < 6 * 6) continue; // não começa em cima de quem passou
            for (Direction d : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                if (straight(sl, s, d)) return create(sl, s, d);
            }
        }
        return null;
    }

    /** Rua reta: caminho de terra na mesma altura por LENGTH blocos, com ar em cima, e larga (o lado também é caminho). */
    private static boolean straight(ServerLevel sl, BlockPos s, Direction d) {
        Direction l = d.getClockWise();
        for (int i = 0; i <= LENGTH; i++) {
            BlockPos q = s.relative(d, i);
            if (!sl.getBlockState(q).is(Blocks.DIRT_PATH)) return false;
            if (!sl.getBlockState(q.above()).isAir() || !sl.getBlockState(q.above(2)).isAir()) return false;
            if (i == 0 || i == LENGTH) {
                // na ponta: os dois chinelos vão um de cada lado do meio da rua
                for (int k : new int[]{-1, 1}) {
                    BlockPos c = q.relative(l, k);
                    if (!sl.getBlockState(c.above()).isAir() || sl.getBlockState(c).isAir()) return false;
                }
            }
        }
        return true;
    }

    private static Game create(ServerLevel sl, BlockPos s, Direction d) {
        Game g = new Game();
        g.sl = sl;
        Direction l = d.getClockWise();
        g.y = s.getY() + 1;
        g.axis = new Vec3(d.getStepX(), 0, d.getStepZ());
        g.side = new Vec3(l.getStepX(), 0, l.getStepZ());
        g.g0 = new Vec3(s.getX() + 0.5, g.y, s.getZ() + 0.5);
        BlockPos e = s.relative(d, LENGTH);
        g.g1 = new Vec3(e.getX() + 0.5, g.y, e.getZ() + 0.5);
        for (BlockPos end : new BlockPos[]{s, e}) {
            for (int k : new int[]{-1, 1}) {
                BlockPos c = end.relative(l, k).above();
                sl.setBlock(c, Blocks39.CHINELO.defaultBlockState().setValue(FacingBlock.FACING, d), 3);
                g.chinelos.add(c);
            }
        }
        Ball ball = ModEntities.BALL.create(sl, EntitySpawnReason.EVENT);
        if (ball == null) return null;
        ball.street = true;
        Vec3 m = g.middle();
        ball.snapTo(m.x, g.y + 0.2, m.z, 0, 0);
        sl.addFreshEntity(ball);
        g.ball = ball.getUUID();
        for (int i = 0; i < 4; i++) {
            Raver k = ModEntities.RAVER.create(sl, EntitySpawnReason.EVENT);
            if (k == null) continue;
            int team = i % 2;
            Vec3 at = m.add(g.axis.scale(team == 0 ? -3 - i : 3 + i)).add(g.side.scale(i < 2 ? 0.6 : -0.6));
            k.snapTo(at.x, g.y, at.z, 0, 0);
            k.setRole(RaverRoles.PELADA);
            k.stall = 200 + team;
            k.ball = ball.getUUID();
            k.setShirt(0xFF000000 | TEAM_COLORS[team]);
            var scale = k.getAttribute(Attributes.SCALE);
            if (scale != null) scale.setBaseValue(0.62);
            sl.addFreshEntity(k);
            g.kids.add(k.getUUID());
        }
        GAMES.add(g);
        for (ServerPlayer p : sl.players()) {
            if (p.position().distanceTo(m) < 40) {
                p.sendSystemMessage(Component.literal("§e⚽ As crianças montaram uma pelada na rua, com chinelo de trave! §7Chute a bola (botão esquerdo) para entrar no time."));
            }
        }
        return g;
    }

    // ------------------------------------------------------------ jogo
    public static Game gameOf(UUID ball) {
        for (Game g : GAMES) if (g.ball.equals(ball)) return g;
        return null;
    }

    /** Tique da bola da rua (chamado pela própria bola): gol entre os chinelos e bola que saiu da rua. */
    public static void ballTick(ServerLevel sl, Ball ball) {
        Game g = gameOf(ball.getUUID());
        if (g == null) {
            ball.discard(); // pelada que acabou (ou o mundo foi fechado no meio)
            return;
        }
        if (g.pause > 0) return;
        Vec3 p = ball.position();
        Vec3 d = p.subtract(g.g0);
        double u = d.dot(g.axis), lat = d.dot(g.side);
        if (Math.abs(lat) < 1.0 && p.y - g.y < 0.9 && (u < -0.2 || u > LENGTH + 0.2)) {
            goal(g, ball, u < 0 ? 1 : 0);
            return;
        }
        if (Math.abs(lat) > 3.5 || u < -2.5 || u > LENGTH + 2.5 || p.y < g.y - 2) {
            // saiu da rua: uma criança "busca" e põe de volta no meio da rua
            double cu = Math.max(1, Math.min(LENGTH - 1, u));
            Vec3 back = g.g0.add(g.axis.scale(cu));
            ball.setPos(back.x, g.y + 0.3, back.z);
            ball.setDeltaMovement(Vec3.ZERO);
            if (g.lastKicker != null && g.humans.containsKey(g.lastKicker) && sl.getRandom().nextInt(2) == 0) {
                say(g, "Ô, tio! Chuta pra rua, não pro quintal da vizinha!");
            }
        }
    }

    private static void goal(Game g, Ball ball, int team) {
        g.score[team]++;
        g.pause = 50;
        ball.setDeltaMovement(Vec3.ZERO);
        g.sl.sendParticles(ParticleTypes.FIREWORK, ball.getX(), ball.getY() + 0.5, ball.getZ(), 20, 0.3, 0.3, 0.3, 0.08);
        g.sl.playSound(null, ball.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1.6f);
        String who = g.lastKicker != null && g.humans.containsKey(g.lastKicker) && g.lastTeam == team
                && g.sl.getEntity(g.lastKicker) instanceof ServerPlayer p ? p.getName().getString() : null;
        String[] shouts = who != null
                ? new String[]{"Golaço do " + who + "! Esse tio joga muito!", "Gol do reforço! Valeu, " + who + "!", who + " fez gol! Agora ninguém segura o " + TEAM_NAMES[team] + "!"}
                : new String[]{"GOOOL! Passou entre os chinelos!", "É gol! Foi no meio da trave de chinelo!", "Gol do " + TEAM_NAMES[team] + "! Chupa!",
                "Foi gol, foi gol! Não foi na trave, o chinelo tava aqui!"};
        say(g, shouts[g.sl.getRandom().nextInt(shouts.length)]);
        for (ServerPlayer p : g.sl.players()) {
            if (p.position().distanceTo(g.middle()) < 40) {
                p.sendOverlayMessage(Component.literal("§e⚽ Pelada da rua: §f" + TEAM_NAMES[0] + " " + g.score[0] + " x " + g.score[1] + " " + TEAM_NAMES[1]));
            }
        }
        for (UUID id : g.kids) if (g.sl.getEntity(id) instanceof Raver k) k.celebrate(k.stall - 200 == team);
        if (g.score[team] >= WIN) g.ticks = DURATION;
    }

    private static void tickGame(Game g) {
        g.ticks++;
        if (g.pause > 0 && --g.pause == 0 && g.sl.getEntity(g.ball) instanceof Ball b) {
            Vec3 m = g.middle();
            b.setPos(m.x, g.y + 0.3, m.z);
            b.setDeltaMovement(Vec3.ZERO);
        }
        boolean anyone = g.sl.players().stream().anyMatch(p -> p.position().distanceTo(g.middle()) < 96);
        if (g.ticks >= DURATION || !anyone || !(g.sl.getEntity(g.ball) instanceof Ball)) end(g);
    }

    /** Fim: as crianças pegam os chinelos e vão para casa. */
    public static void end(Game g) {
        GAMES.remove(g);
        int a = g.score[0], b = g.score[1];
        String fim = a == b ? "Empatou " + a + " a " + b + "! Amanhã tem revanche!"
                : "Acabou! " + TEAM_NAMES[a > b ? 0 : 1] + " ganhou de " + Math.max(a, b) + " a " + Math.min(a, b) + "! Mãe tá chamando pra jantar!";
        say(g, fim);
        for (BlockPos c : g.chinelos) if (g.sl.getBlockState(c).is(Blocks39.CHINELO)) g.sl.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
        if (g.sl.getEntity(g.ball) instanceof Ball ball) ball.discard();
        for (UUID id : g.kids) {
            if (g.sl.getEntity(id) instanceof Raver k) {
                g.sl.sendParticles(ParticleTypes.POOF, k.getX(), k.getY() + 0.5, k.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
                k.discard();
            }
        }
    }

    /** O mundo vai fechar: tira tudo (chinelo não fica esquecido na rua). */
    public static void clear() {
        for (Game g : new ArrayList<>(GAMES)) {
            for (BlockPos c : g.chinelos) if (g.sl.getBlockState(c).is(Blocks39.CHINELO)) g.sl.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
            if (g.sl.getEntity(g.ball) instanceof Ball ball) ball.discard();
            for (UUID id : g.kids) if (g.sl.getEntity(id) instanceof Raver k) k.discard();
        }
        GAMES.clear();
    }

    /** A pessoa chutou a bola da rua: entra no time que precisa de reforço e a bola vai para onde ela olha. */
    public static void playerKick(ServerPlayer p, Ball ball) {
        Game g = gameOf(ball.getUUID());
        if (g == null) return;
        Integer team = g.humans.get(p.getUUID());
        if (team == null) {
            int[] count = new int[2];
            for (int t : g.humans.values()) count[t]++;
            team = g.score[0] != g.score[1] ? (g.score[0] < g.score[1] ? 0 : 1) : count[0] <= count[1] ? 0 : 1;
            g.humans.put(p.getUUID(), team);
            say(g, "Ê! " + p.getName().getString() + " vai jogar com a gente! Você é do " + TEAM_NAMES[team] + ", ataca pra " + (team == 0 ? "lá" : "cá") + "!");
            p.sendSystemMessage(Component.literal("§e⚽ Você entrou na pelada da rua pelo §f" + TEAM_NAMES[team] + "§e. Faça gol entre os chinelos do outro lado!"));
        }
        if (p.distanceTo(ball) > 3.2 || g.pause > 0) return;
        Vec3 look = p.getLookAngle();
        Vec3 f = new Vec3(look.x, 0, look.z);
        if (f.lengthSqr() < 1e-4) return;
        ball.setDeltaMovement(f.normalize().scale(0.75).add(0, 0.06, 0));
        g.lastKicker = p.getUUID();
        g.lastTeam = team;
        g.sl.playSound(null, ball.blockPosition(), SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    /** Criança chutou. */
    public static void kidKick(Game g, Raver k, Ball ball, Vec3 vel) {
        ball.setDeltaMovement(vel);
        g.lastKicker = k.getUUID();
        g.lastTeam = k.stall - 200;
        g.sl.playSound(null, ball.blockPosition(), SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 0.6f, 1.4f);
    }

    public static String info(Game g) {
        return TEAM_NAMES[0] + " " + g.score[0] + " x " + g.score[1] + " " + TEAM_NAMES[1];
    }

    private static void say(Game g, String msg) {
        for (ServerPlayer p : g.sl.players()) {
            if (p.position().distanceTo(g.middle()) < 40) p.sendSystemMessage(Component.literal("§7» Criançada da rua: §f" + msg));
        }
    }
}
