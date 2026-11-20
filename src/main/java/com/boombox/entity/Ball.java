package com.boombox.entity;

import com.boombox.Achievements;
import com.boombox.bar.BarBlocks;
import com.boombox.bar.Blocks34;
import com.boombox.bar.Cups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bola do futebol de várzea, o juiz e o campeonato. A bola acha sozinha os dois gols e as linhas, e apita:
 * gol, lateral, tiro de meta, escanteio, falta e pênalti (falta dentro da área). A partida tem dois tempos com
 * relógio (placar numa barra no alto da tela), intervalo e apito final.
 * <p>
 * <b>Campeonato (3.4):</b> 4 times — o da vila (time A, que joga em casa) e 3 visitantes de outras vilas. Cada
 * partida aqui vale uma rodada; os outros dois times jogam "fora" no mesmo dia. Depois de 3 rodadas sai o campeão,
 * e quem jogou pelo time campeão leva a Taça da Várzea.
 * <p>
 * Pessoas entram num time clicando com o botão direito na bola. Encostar na bola correndo conduz; bater na bola
 * passa (mirando num companheiro, o passe vai nele), correndo é chute forte e agachado é cavadinha.
 */
public class Ball extends Entity {
    public static final int TEAM_A = 0, TEAM_B = 1;
    public static final String[] TEAM_NAMES = {"Azul", "Vermelho"};
    public static final int[] TEAM_COLORS = {0x2D5BE0, 0xE03030};
    public static final int KICKOFF = 0, THROW_IN = 1, GOAL_KICK = 2, CORNER = 3, FREE_KICK = 4, PENALTY = 5;
    /** Duração de cada tempo e do intervalo (ticks). */
    public static final int HALF = 3000, BREAK = 300, AFTER = 400;
    private static final double GRAVITY = 0.045, ROLL = 0.955, AIR = 0.99, MAX_SPEED = 1.4;

    private static final EntityDataAccessor<Integer> SCORE_A = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCORE_B = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.INT);
    /** Centros dos dois gols (0 = do time A, 1 = do time B); null = ainda não achou. */
    private Vec3 goal0, goal1;
    private Vec3 axis, side;
    private double length, halfWidth = 8;
    private Vec3 center;
    private UUID lastKicker;
    private int lastTeam = -1;
    private int restartKind = -1, restartTeam, restartTimer;
    private Vec3 restartSpot;
    private int goalPause;
    private int lastKickTick = -100;
    /** Partida: 0 = 1º tempo, 1 = intervalo, 2 = 2º tempo, 3 = fim de jogo. */
    private int phase, clock;
    /** Goleiro segurando a bola (nas mãos) e por quanto tempo. */
    private UUID keeperHold;
    private int holdTicks;
    private final Map<UUID, Integer> humans = new HashMap<>();
    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private ServerBossEvent bar;

    // ---- campeonato
    private static final String[][] VISITORS = {
            {"Real Taiga", "2E8B3A"}, {"Atlético Savana", "E0B020"}, {"Grêmio da Neve", "F0F0F0"}, {"União da Planície", "8E24AA"},
            {"Juventude do Deserto", "E66A10"}, {"Ferroviário do Pântano", "6D4C2F"}, {"Estrela do Mar", "10B0C8"}, {"Independente do Morro", "202020"}};
    private final String[] teams = {"", "", "", ""};
    private final int[] colors = new int[4], pts = new int[4], gf = new int[4], ga = new int[4];
    private int round, season;
    /** Quem jogou nesta temporada, e por qual time (índice no campeonato). */
    private final Map<UUID, Set<Integer>> playedFor = new HashMap<>();

    public Ball(EntityType<? extends Ball> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCORE_A, 0);
        builder.define(SCORE_B, 0);
    }

    public int scoreA() {
        return entityData.get(SCORE_A);
    }

    public int scoreB() {
        return entityData.get(SCORE_B);
    }

    public boolean hasGoals() {
        return goal0 != null && goal1 != null;
    }

    public Vec3 goal(int i) {
        return i == 0 ? goal0 : goal1;
    }

    public Vec3 center() {
        return center;
    }

    public double length() {
        return length;
    }

    public double halfWidth() {
        return halfWidth;
    }

    /** Bola parada, gol, intervalo ou fim de jogo: ninguém disputa. */
    public boolean restarting() {
        return restartKind >= 0 || goalPause > 0 || phase == 1 || phase == 3;
    }

    public boolean inPlay() {
        return !restarting() && keeperHold == null;
    }

    public int restartTeam() {
        return restartTeam;
    }

    public Vec3 restartSpot() {
        return restartSpot;
    }

    public void forcePlay() {
        restartKind = -1;
        goalPause = 0;
        keeperHold = null;
        if (phase == 1 || phase == 3) phase = 0;
    }

    public boolean restartTimerDone() {
        return restartTimer <= 0;
    }

    /** O cobrador está demorando (não achou o lugar ideal): cobra de onde estiver. */
    public boolean restartOverdue() {
        return restartTimer < -50;
    }

    public int restartKind() {
        return restartKind;
    }

    /** O outro time tocou na bola agora há pouco (ela ainda "é" dele: só dá para tomar no bote). */
    public boolean freshFor(int team) {
        return lastTeam >= 0 && lastTeam != team && tickCount - lastKickTick < 12;
    }

    public UUID keeperHold() {
        return keeperHold;
    }

    public int holdTicks() {
        return holdTicks;
    }

    public boolean canKick(int team) {
        if (goalPause > 0 || phase == 1 || phase == 3) return false;
        return restartKind < 0 || team == restartTeam;
    }

    public int teamOf(Entity e) {
        if (e instanceof Raver r) return r.role() == RaverRoles.TEAM_A ? TEAM_A : r.role() == RaverRoles.TEAM_B ? TEAM_B : -1;
        Integer t = humans.get(e.getUUID());
        return t == null ? -1 : t;
    }

    /** Nome do time A ou B nesta partida (do campeonato). */
    public String teamName(int t) {
        int i = t == TEAM_A ? 0 : Math.min(3, round + 1);
        return teams[i].isEmpty() ? TEAM_NAMES[t] : teams[i];
    }

    public int teamColor(int t) {
        int i = t == TEAM_A ? 0 : Math.min(3, round + 1);
        return teams[i].isEmpty() ? TEAM_COLORS[t] : colors[i];
    }

    public double u(Vec3 p) {
        return p.subtract(goal0).dot(axis);
    }

    public double v(Vec3 p) {
        return p.subtract(center).dot(side);
    }

    /** u medido a partir do gol do próprio time (0 = no próprio gol, length = no gol adversário). */
    public double uFor(int team, Vec3 p) {
        return team == TEAM_A ? u(p) : length - u(p);
    }

    public Vec3 fieldPoint(double u, double v) {
        return goal0.add(axis.scale(u)).add(side.scale(v)).with(net.minecraft.core.Direction.Axis.Y, getY());
    }

    /** Ponto do campo em coordenadas do time (uOwn a partir do próprio gol, v para o lado). */
    public Vec3 teamPoint(int team, double uOwn, double v) {
        return team == TEAM_A ? fieldPoint(uOwn, v) : fieldPoint(length - uOwn, -v);
    }

    /** Dentro da grande área do gol "goal" (0 ou 1)? */
    public boolean inBox(int goal, Vec3 p) {
        double uu = goal == 0 ? u(p) : length - u(p);
        return uu >= -0.3 && uu < 5.5 && Math.abs(v(p)) < 4.5;
    }

    public void kick(Entity who, Vec3 vel) {
        int team = teamOf(who);
        if (!canKick(team)) return;
        if (restartKind >= 0) restartKind = -1; // cobrou: bola rolando
        keeperHold = null;
        double sp = vel.length();
        if (sp > MAX_SPEED) vel = vel.scale(MAX_SPEED / sp);
        setDeltaMovement(vel);
        lastKicker = who.getUUID();
        lastTeam = team;
        lastKickTick = tickCount;
        playSound(SoundEvents.WOOL_HIT, Math.min(1.2f, 0.5f + (float) sp), 0.7f + random.nextFloat() * 0.2f);
    }

    /** O goleiro agarra a bola (fica com ela nas mãos). */
    public void catchBall(Raver keeper) {
        keeperHold = keeper.getUUID();
        holdTicks = 0;
        lastTeam = teamOf(keeper);
        lastKicker = keeper.getUUID();
        setDeltaMovement(Vec3.ZERO);
        playSound(SoundEvents.WOOL_PLACE, 1f, 0.8f);
    }

    @Override
    public void tick() {
        super.tick();
        if (keeperHold != null && level() instanceof ServerLevel sl0) {
            // nas mãos do goleiro
            Entity k = sl0.getEntity(keeperHold);
            if (k == null || !k.isAlive()) keeperHold = null;
            else {
                Vec3 f = k.getLookAngle();
                setPos(k.getX() + f.x * 0.45, k.getY() + 1.0, k.getZ() + f.z * 0.45);
                setDeltaMovement(Vec3.ZERO);
                holdTicks++;
            }
        } else {
            Vec3 v = getDeltaMovement().add(0, -GRAVITY, 0);
            Vec3 before = v;
            move(MoverType.SELF, v);
            v = getDeltaMovement();
            if (horizontalCollision) {
                if (Math.abs(getX() - xo) < Math.abs(before.x) * 0.5) v = new Vec3(-before.x * 0.5, v.y, v.z);
                if (Math.abs(getZ() - zo) < Math.abs(before.z) * 0.5) v = new Vec3(v.x, v.y, -before.z * 0.5);
            }
            // rola no chão (perde velocidade aos poucos) e quica um pouco quando cai do alto
            if (onGround()) v = new Vec3(v.x * ROLL, before.y < -0.15 ? -before.y * 0.5 : 0, v.z * ROLL);
            else v = new Vec3(v.x * AIR, v.y * 0.98, v.z * AIR);
            if (v.horizontalDistanceSqr() < 4e-5) v = new Vec3(0, v.y, 0);
            setDeltaMovement(v);
        }
        if (!(level() instanceof ServerLevel sl)) return;
        if (!hasGoals()) {
            if (tickCount % 20 == 1) findField(sl);
            return;
        }
        if (tickCount % 20 == 0) scoreboard(sl);
        if (tickCount % 1200 == 600) clearTrees(sl);
        tickClock(sl);
        if (phase == 1 || phase == 3) return;
        if (goalPause > 0) {
            if (--goalPause == 0) setRestart(sl, KICKOFF, lastTeam == TEAM_A ? TEAM_B : TEAM_A, center, false);
            return;
        }
        if (restartKind >= 0) {
            setPos(restartSpot.x, getY(), restartSpot.z);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (restartTimer > -10000) restartTimer--;
            return;
        }
        if (keeperHold != null) return;
        dribble(sl);
        Vec3 p = position();
        BlockPos at = blockPosition();
        if (sl.getBlockState(at).is(BarBlocks.GOAL) || sl.getBlockState(at.above()).is(BarBlocks.GOAL)) {
            goal(sl, u(p) < length / 2 ? 0 : 1);
            return;
        }
        double u = u(p), w = v(p);
        if (Math.abs(w) > halfWidth + 0.3) {
            int team = lastTeam == TEAM_A ? TEAM_B : TEAM_A;
            setRestart(sl, THROW_IN, team, fieldPoint(Math.max(1, Math.min(length - 1, u)), Math.signum(w) * (halfWidth - 0.3)), true);
        } else if (u < -0.2 || u > length + 0.2) {
            int end = u < 0 ? 0 : 1;
            int defender = end == 0 ? TEAM_A : TEAM_B;
            double su = end == 0 ? 0.8 : length - 0.8;
            if (lastTeam == defender) {
                setRestart(sl, CORNER, 1 - defender, fieldPoint(su, Math.signum(w == 0 ? 1 : w) * (halfWidth - 0.3)), true);
            } else {
                setRestart(sl, GOAL_KICK, defender, fieldPoint(end == 0 ? 2.5 : length - 2.5, Math.signum(w == 0 ? 1 : w) * 2.5), true);
            }
        }
    }

    /** Relógio da partida: 1º tempo, intervalo, 2º tempo e fim de jogo (depois começa a próxima rodada). */
    private void tickClock(ServerLevel sl) {
        boolean anyone = sl.players().stream().anyMatch(p -> p.distanceTo(this) < 64);
        if (!anyone) return; // ninguém olhando: o jogo "espera"
        clock++;
        if (phase == 0 && clock >= HALF && !restarting()) {
            phase = 1;
            clock = 0;
            whistle(sl, 3);
            announce("§e§lPriii, priii, priii! §r§fFim do primeiro tempo. §7(" + score() + ")", true);
            goHome(sl);
        } else if (phase == 1 && clock >= BREAK) {
            phase = 2;
            clock = 0;
            announce("§fComeça o §esegundo tempo§f!", false);
            setRestart(sl, KICKOFF, TEAM_B, center, true);
        } else if (phase == 2 && clock >= HALF && !restarting()) {
            phase = 3;
            clock = 0;
            whistle(sl, 3);
            endMatch(sl);
        } else if (phase == 3 && clock >= AFTER) {
            phase = 0;
            clock = 0;
            entityData.set(SCORE_A, 0);
            entityData.set(SCORE_B, 0);
            startMatch(sl);
        }
    }

    private void whistle(ServerLevel sl, int times) {
        for (int i = 0; i < times; i++) {
            sl.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 1.4f, 1.9f - i * 0.05f);
        }
    }

    private void goHome(ServerLevel sl) {
        setPos(center.x, getY(), center.z);
        setDeltaMovement(Vec3.ZERO);
        keeperHold = null;
        restartKind = -1;
    }

    private String score() {
        return teamName(TEAM_A) + " " + scoreA() + " x " + scoreB() + " " + teamName(TEAM_B);
    }

    /** Relógio "de futebol": 45 minutos por tempo. */
    public String minute() {
        if (phase == 1) return "Intervalo";
        if (phase == 3) return "Fim de jogo";
        int m = (int) (clock * 45L / HALF) + (phase == 2 ? 45 : 0);
        return (phase == 0 ? "1º tempo " : "2º tempo ") + Math.min(phase == 0 ? 45 : 90, m) + "'";
    }

    /** Placar na barra do alto da tela de quem está perto (e na barra de ação de quem joga). */
    private void scoreboard(ServerLevel sl) {
        if (bar == null) bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(""), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setName(Component.literal("⚽ " + score() + "  •  " + minute() + (teams[0].isEmpty() ? "" : "  •  Rodada " + (round + 1) + "/3")));
        bar.setProgress(phase == 1 || phase == 3 ? 1f : Math.min(1f, clock / (float) HALF));
        for (ServerPlayer p : sl.players()) {
            boolean near = p.distanceTo(this) < 48;
            if (near && !bar.getPlayers().contains(p)) bar.addPlayer(p);
            else if (!near && bar.getPlayers().contains(p)) bar.removePlayer(p);
        }
        for (var e : humans.entrySet()) {
            if (sl.getEntity(e.getKey()) instanceof ServerPlayer p && p.distanceTo(this) < 48) {
                p.sendOverlayMessage(Component.literal("§7Você joga no §f" + teamName(e.getValue())
                        + "§7 • bater = passe • correndo = chute • agachado = cavadinha"));
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (bar != null) bar.removeAllPlayers();
        super.remove(reason);
    }

    // ------------------------------------------------------------ conduzir a bola (gente)
    /** Pessoa do time correndo colada na bola: ela vai junto, um pouquinho à frente (condução de verdade). */
    private void dribble(ServerLevel sl) {
        for (var e : humans.entrySet()) {
            if (!(sl.getEntity(e.getKey()) instanceof ServerPlayer p)) continue;
            Vec3 now = p.position(), before = lastPos.put(p.getUUID(), now);
            if (before == null || !canKick(e.getValue())) continue;
            Vec3 move = new Vec3(now.x - before.x, 0, now.z - before.z);
            double sp = move.length();
            Vec3 d = new Vec3(getX() - p.getX(), 0, getZ() - p.getZ());
            if (sp < 0.06 || d.length() > 1.05 || getY() - p.getY() > 0.8) continue;
            Vec3 dir = move.normalize();
            if (d.normalize().dot(dir) < 0.2) continue; // a bola tem que estar na frente
            Vec3 vel = dir.scale(sp * 1.18 + 0.03);
            setDeltaMovement(vel.x, getDeltaMovement().y, vel.z);
            lastKicker = p.getUUID();
            lastTeam = e.getValue();
        }
    }

    // ------------------------------------------------------------ apitos
    public void setRestart(ServerLevel sl, int kind, int team, Vec3 spot, boolean whistle) {
        restartKind = kind;
        restartTeam = team;
        restartSpot = spot;
        restartTimer = kind == KICKOFF ? 80 : kind == PENALTY ? 90 : 50;
        keeperHold = null;
        setPos(spot.x, spot.y + 0.3, spot.z);
        setDeltaMovement(Vec3.ZERO);
        if (whistle) whistle(sl, 1);
        String who = teamName(team);
        String msg = switch (kind) {
            case THROW_IN -> "§ePriii! Saiu pela lateral. §fLateral para o " + who + ".";
            case GOAL_KICK -> "§ePriii! Saiu pela linha de fundo. §fTiro de meta do " + who + ".";
            case CORNER -> "§ePriii! Escanteio para o " + who + "!";
            case FREE_KICK -> "§c§lFALTA! §fTiro livre para o " + who + ".";
            case PENALTY -> "§c§lPÊNALTI! §fFalta dentro da área: pênalti para o " + who + "!";
            default -> "§fSaída de bola do " + who + ".";
        };
        announce(msg, true);
    }

    /** Falta: tiro livre (ou pênalti, se foi dentro da área de quem fez a falta). */
    public void foul(int fouledTeam) {
        if (!(level() instanceof ServerLevel sl) || restarting() || !hasGoals()) return;
        Vec3 p = position();
        int defendingGoal = fouledTeam == TEAM_A ? 1 : 0; // o time que sofreu ataca o gol do outro
        if (inBox(defendingGoal, p)) {
            setRestart(sl, PENALTY, fouledTeam, fieldPoint(defendingGoal == 0 ? 4 : length - 4, 0), true);
            return;
        }
        if (random.nextInt(4) == 0) announce("§e§l🟨 Cartão amarelo! §r§7O juiz não perdoou.", false);
        setRestart(sl, FREE_KICK, fouledTeam, fieldPoint(Math.max(1, Math.min(length - 1, u(p))), Math.max(-halfWidth + 0.5, Math.min(halfWidth - 0.5, v(p)))), true);
    }

    private void announce(String msg, boolean withScore) {
        if (!(level() instanceof ServerLevel sl)) return;
        for (var p : sl.players()) {
            if (p.distanceTo(this) < 48) p.sendSystemMessage(Component.literal(msg));
        }
    }

    private void goal(ServerLevel sl, int which) {
        int scorer = which == 0 ? TEAM_B : TEAM_A;
        if (scorer == TEAM_A) entityData.set(SCORE_A, scoreA() + 1);
        else entityData.set(SCORE_B, scoreB() + 1);
        lastTeam = scorer;
        goalPause = 80;
        sl.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, 1.2f);
        sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 1, getZ(), 50, 0.6, 0.6, 0.6, 0.12);
        String who = "";
        if (lastKicker != null && sl.getEntity(lastKicker) instanceof ServerPlayer p) {
            who = " §e(" + p.getName().getString() + ")";
            Achievements.award(p, "gol_na_varzea");
            Cups.give(p, Cups.VARZEA);
        }
        announce("§a§lGOOOOOL do " + teamName(scorer) + "!" + who + " §r§f" + score(), true);
        for (Raver r : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(48), r -> teamOf(r) == scorer)) r.celebrate(true);
    }

    // ------------------------------------------------------------ campeonato
    private String place(ServerLevel sl) {
        String b = sl.getBiome(BlockPos.containing(center)).unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
        if (b.contains("desert")) return "do Deserto";
        if (b.contains("savanna")) return "da Savana";
        if (b.contains("snow") || b.contains("ice")) return "da Neve";
        if (b.contains("taiga")) return "da Taiga";
        return "da Planície";
    }

    /** Nova temporada: o time da vila e 3 visitantes sorteados. */
    private void newSeason(ServerLevel sl) {
        String home = "Unidos " + place(sl);
        teams[0] = home;
        colors[0] = 0x2D5BE0;
        List<String[]> pool = new ArrayList<>(List.of(VISITORS));
        java.util.Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        int k = 1;
        for (String[] t : pool) {
            if (k > 3) break;
            String suffix = home.substring(home.indexOf(' ') + 1);
            if (t[0].endsWith(suffix.substring(suffix.indexOf(' ') + 1))) continue; // nada de dois times do mesmo lugar
            teams[k] = t[0];
            colors[k] = Integer.parseInt(t[1], 16);
            k++;
        }
        for (int i = 0; i < 4; i++) pts[i] = gf[i] = ga[i] = 0;
        round = 0;
        season++;
        playedFor.clear();
        announce("§6§l🏆 Começou o Campeonato da Várzea! §r§f" + teams[0] + ", " + teams[1] + ", " + teams[2] + " e " + teams[3] + ".", false);
    }

    /** Começo de cada partida: camisas dos times e saída de bola. */
    private void startMatch(ServerLevel sl) {
        if (teams[0].isEmpty() || round >= 3) newSeason(sl);
        for (Raver r : sl.getEntitiesOfClass(Raver.class, new AABB(center, center).inflate(40, 8, 40), r -> teamOf(r) >= 0)) {
            r.setShirt(teamColor(teamOf(r)) | 0xFF000000);
        }
        for (var e : humans.entrySet()) {
            if (sl.getEntity(e.getKey()) instanceof ServerPlayer p) giveShirt(p, e.getValue());
        }
        announce("§6Rodada " + (round + 1) + " de 3: §f" + teamName(TEAM_A) + " §7x §f" + teamName(TEAM_B)
                + " §7(" + teamName(TEAM_B) + " veio de outra vila para o jogo)", false);
        setRestart(sl, KICKOFF, TEAM_A, center, true);
    }

    private void endMatch(ServerLevel sl) {
        int a = scoreA(), b = scoreB(), home = 0, away = Math.min(3, round + 1);
        String result = a == b ? "Empate!" : "Vitória do " + teamName(a > b ? TEAM_A : TEAM_B) + "!";
        announce("§e§lFIM DE JOGO! §r§f" + score() + " §7— " + result, true);
        int winner = a > b ? TEAM_A : b > a ? TEAM_B : -1;
        for (Raver r : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(48), r -> teamOf(r) >= 0)) r.celebrate(teamOf(r) == winner);
        // quem jogou esta partida, e por qual time
        for (var e : humans.entrySet()) {
            playedFor.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(e.getValue() == TEAM_A ? home : away);
        }
        record(home, away, a, b);
        // os outros dois times jogaram fora
        List<Integer> others = new ArrayList<>();
        for (int i = 1; i < 4; i++) if (i != away) others.add(i);
        int ga1 = random.nextInt(4), ga2 = random.nextInt(4);
        record(others.get(0), others.get(1), ga1, ga2);
        announce("§7Na outra partida da rodada: " + teams[others.get(0)] + " " + ga1 + " x " + ga2 + " " + teams[others.get(1)], false);
        round++;
        table(sl, null);
        if (round >= 3) {
            int champ = standings().get(0);
            announce("§6§l🏆 " + teams[champ].toUpperCase() + " É CAMPEÃO DA VÁRZEA! 🏆", false);
            sl.sendParticles(ParticleTypes.FIREWORK, center.x, center.y + 3, center.z, 120, 3, 2, 3, 0.2);
            for (var e : playedFor.entrySet()) {
                if (e.getValue().contains(champ) && sl.getEntity(e.getKey()) instanceof ServerPlayer p) {
                    ItemStack cup = new ItemStack(Blocks34.TACA_VARZEA);
                    if (!p.getInventory().add(cup)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), cup);
                    Achievements.award(p, "campeao_da_varzea");
                    p.sendSystemMessage(Component.literal("§6Você foi campeão pelo " + teams[champ] + " e levou a Taça da Várzea!"));
                }
            }
        }
    }

    private void record(int x, int y, int sx, int sy) {
        gf[x] += sx; ga[x] += sy; gf[y] += sy; ga[y] += sx;
        if (sx > sy) pts[x] += 3;
        else if (sy > sx) pts[y] += 3;
        else { pts[x]++; pts[y]++; }
    }

    private List<Integer> standings() {
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        order.sort((p, q) -> pts[p] != pts[q] ? pts[q] - pts[p] : (gf[q] - ga[q]) != (gf[p] - ga[p]) ? (gf[q] - ga[q]) - (gf[p] - ga[p]) : gf[q] - gf[p]);
        return order;
    }

    /** Tabela do campeonato no chat (de todo mundo perto, ou só de uma pessoa). */
    public void table(ServerLevel sl, ServerPlayer only) {
        if (teams[0].isEmpty()) return;
        List<String> lines = new ArrayList<>();
        lines.add("§6§l🏆 Tabela do Campeonato da Várzea §r§7(rodada " + Math.min(3, round) + "/3)");
        int pos = 1;
        for (int i : standings()) {
            lines.add("§f" + pos++ + ". " + teams[i] + " §7— §e" + pts[i] + " pts §7(" + gf[i] + " gols pró, " + ga[i] + " contra)");
        }
        for (ServerPlayer p : only != null ? List.of(only) : sl.players()) {
            if (only == null && p.distanceTo(this) > 48) continue;
            for (String l : lines) p.sendSystemMessage(Component.literal(l));
        }
    }

    private void findField(ServerLevel sl) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(blockPosition().offset(-30, -3, -30), blockPosition().offset(30, 4, 30))) {
            if (sl.getBlockState(p).is(BarBlocks.GOAL)) found.add(p.immutable());
        }
        if (found.size() < 2) return;
        BlockPos a = found.get(0), far = a;
        for (BlockPos p : found) if (p.distSqr(a) > far.distSqr(a)) far = p;
        if (far.distSqr(a) < 64) return;
        double ax = 0, az = 0, bx = 0, bz = 0, y = Math.floor(getY());
        int na = 0, nb = 0;
        for (BlockPos p : found) {
            if (p.distSqr(a) < p.distSqr(far)) { ax += p.getX() + 0.5; az += p.getZ() + 0.5; na++; }
            else { bx += p.getX() + 0.5; bz += p.getZ() + 0.5; nb++; }
        }
        Vec3 ga = new Vec3(ax / na, y, az / na), gb = new Vec3(bx / nb, y, bz / nb);
        var teamA = sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(40), r -> r.role() == RaverRoles.TEAM_A);
        double da = 0, db = 0;
        for (Raver r : teamA) { da += r.position().distanceTo(ga); db += r.position().distanceTo(gb); }
        if (da <= db) { goal0 = ga; goal1 = gb; } else { goal0 = gb; goal1 = ga; }
        Vec3 d = goal1.subtract(goal0);
        length = d.horizontalDistance();
        axis = new Vec3(d.x, 0, d.z).normalize();
        side = new Vec3(-axis.z, 0, axis.x);
        center = goal0.add(axis.scale(length / 2));
        halfWidth = 8;
        for (int k = 3; k < 20; k++) {
            BlockPos q = BlockPos.containing(center.add(side.scale(k))).below();
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(sl.getBlockState(q).getBlock()).getPath().equals("white_concrete_powder")) { halfWidth = k; break; }
        }
        phase = 0;
        clock = 0;
        clearTrees(sl);
        startMatch(sl);
    }

    /**
     * Árvore que nasceu dentro do campo (a vila gera as árvores depois do campinho): tira tronco, folhas, cipó, mato e
     * flores de dentro das linhas, até 16 blocos de altura.
     */
    private void clearTrees(ServerLevel sl) {
        int removed = 0;
        int y0 = (int) Math.floor(center.y);
        double hw = halfWidth + 1.5;
        for (double u = -2; u <= length + 2; u += 0.5) {
            for (double w = -hw; w <= hw; w += 0.5) {
                Vec3 p = goal0.add(axis.scale(u)).add(side.scale(w));
                for (int dy = 0; dy <= 16; dy++) {
                    BlockPos q = BlockPos.containing(p.x, y0 + dy, p.z);
                    var st = sl.getBlockState(q);
                    if (st.isAir() || st.is(BarBlocks.GOAL)) continue;
                    boolean tree = st.is(net.minecraft.tags.BlockTags.LOGS) || st.is(net.minecraft.tags.BlockTags.LEAVES)
                            || st.is(net.minecraft.tags.BlockTags.SAPLINGS) || st.is(net.minecraft.tags.BlockTags.FLOWERS)
                            || st.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES) || st.is(net.minecraft.world.level.block.Blocks.BEE_NEST);
                    if (tree) {
                        sl.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) com.boombox.BoomboxMod.LOGGER.info("Boombox: tirei {} blocos de árvore/mato de dentro do campo de várzea", removed);
    }

    // ------------------------------------------------------------ gente jogando
    private void giveShirt(ServerPlayer sp, int team) {
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        boolean ours = chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME)
                && chest.getHoverName().getString().startsWith("Camisa do");
        if (!chest.isEmpty() && !ours) return;
        ItemStack shirt = new ItemStack(Items.LEATHER_CHESTPLATE);
        shirt.set(DataComponents.DYED_COLOR, new DyedItemColor(teamColor(team)));
        shirt.set(DataComponents.CUSTOM_NAME, Component.literal("Camisa do " + teamName(team)));
        sp.setItemSlot(EquipmentSlot.CHEST, shirt);
    }

    /** Botão direito: entra no time da vila, depois no visitante, depois sai. Agachado: mostra a tabela. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
        if (!(player instanceof ServerPlayer sp) || !(level() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (sp.isSecondaryUseActive()) {
            table(sl, sp);
            return InteractionResult.SUCCESS;
        }
        Integer t = humans.get(sp.getUUID());
        int next = t == null ? TEAM_A : t == TEAM_A ? TEAM_B : -1;
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        boolean ourShirt = chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME)
                && chest.getHoverName().getString().startsWith("Camisa do");
        if (ourShirt) sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        if (next < 0) {
            humans.remove(sp.getUUID());
            sp.sendSystemMessage(Component.literal("§7Você saiu do jogo. Valeu pela pelada!"));
            return InteractionResult.SUCCESS;
        }
        humans.put(sp.getUUID(), next);
        giveShirt(sp, next);
        sp.sendSystemMessage(Component.literal("§aVocê entrou no " + teamName(next) + "! §7Corra colado na bola para conduzir. "
                + "Bata na bola para passar (mire num companheiro), correndo para chutar forte, agachado para a cavadinha. "
                + "Clique de novo para trocar de time; agachado + clique mostra a tabela do campeonato."));
        playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer p)) return false;
        int team = teamOf(p);
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 vel;
        if (p.isShiftKeyDown()) {
            vel = flat.scale(0.55).add(0, 0.5 + Math.max(0, look.y) * 0.3, 0);         // cavadinha
        } else if (p.isSprinting()) {
            vel = flat.scale(1.15).add(0, 0.1 + Math.max(0, look.y) * 0.6, 0);         // chute forte
        } else {
            vel = flat.scale(0.75).add(0, 0.05 + Math.max(0, look.y) * 0.45, 0);       // passe / chute colocado
            // passe: se tem companheiro na direção que você olha, a bola vai no pé dele
            if (team >= 0 && hasGoals()) {
                Entity best = null;
                double bestCos = Math.cos(Math.toRadians(25));
                for (Raver r : level.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(24), r -> teamOf(r) == team)) {
                    Vec3 to = new Vec3(r.getX() - getX(), 0, r.getZ() - getZ());
                    double dist = to.length();
                    if (dist < 2 || dist > 24) continue;
                    double c = to.normalize().dot(flat);
                    if (c > bestCos) { bestCos = c; best = r; }
                }
                if (best != null) vel = passTo(position(), best.position().add(best.getDeltaMovement().scale(10)));
            }
        }
        kick(p, vel);
        return false;
    }

    /** Velocidade de um passe rasteiro que chega devagar no ponto "to". */
    public static Vec3 passTo(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        double dist = d.length();
        double sp = Math.min(1.1, dist * (1 - ROLL) + 0.12);
        return d.normalize().scale(sp).add(0, 0.04, 0);
    }

    /** Esbarrão: só de gente (os aldeões tocam na bola "de propósito", pela IA). */
    @Override
    public void push(Entity other) {
        if (!(other instanceof Player) || restarting() || keeperHold != null) return;
        if (getDeltaMovement().horizontalDistance() > 0.3) return;
        Vec3 d = new Vec3(getX() - other.getX(), 0, getZ() - other.getZ());
        if (d.lengthSqr() < 1e-4) return;
        Vec3 vel = d.normalize().scale(0.2);
        setDeltaMovement(vel.x, getDeltaMovement().y + 0.02, vel.z);
        if (!level().isClientSide()) {
            lastKicker = other.getUUID();
            int t = teamOf(other);
            if (t >= 0) lastTeam = t;
        }
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(SCORE_A, in.getIntOr("ScoreA", 0));
        entityData.set(SCORE_B, in.getIntOr("ScoreB", 0));
        phase = in.getIntOr("Phase", 0);
        clock = in.getIntOr("Clock", 0);
        round = in.getIntOr("Round", 0);
        season = in.getIntOr("Season", 0);
        String[] t = in.getStringOr("Teams", "").split("\\|", -1);
        String[] c = in.getStringOr("League", "").split(",");
        if (t.length == 4 && c.length == 16) {
            for (int i = 0; i < 4; i++) {
                teams[i] = t[i];
                colors[i] = Integer.parseInt(c[i * 4]);
                pts[i] = Integer.parseInt(c[i * 4 + 1]);
                gf[i] = Integer.parseInt(c[i * 4 + 2]);
                ga[i] = Integer.parseInt(c[i * 4 + 3]);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("ScoreA", scoreA());
        out.putInt("ScoreB", scoreB());
        out.putInt("Phase", phase);
        out.putInt("Clock", clock);
        out.putInt("Round", round);
        out.putInt("Season", season);
        out.putString("Teams", String.join("|", teams));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) b.append(',');
            b.append(colors[i]).append(',').append(pts[i]).append(',').append(gf[i]).append(',').append(ga[i]);
        }
        out.putString("League", b.toString());
    }
}
