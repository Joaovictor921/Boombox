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
 * Bola do futebol de várzea, o juiz e as partidas (3.5).
 * <ul>
 *     <li>Sem ninguém marcar jogo, os aldeões jogam uma pelada entre eles (times sorteados).</li>
 *     <li>Fale com o <b>organizador</b> do campo: <b>amistoso</b> (times sorteados, cores diferentes) ou <b>campeonato</b>
 *     (você escolhe o nome e a cor do seu time; 3 rodadas contra times de outras vilas e a Taça para o campeão).
 *     Você entra no time da casa (time A), no lugar do centroavante.</li>
 *     <li>Passou por cima da bola, ela gruda no seu pé. Esquerdo chuta, direito passa (vai em quem estiver melhor ou
 *     para onde você olha), R é carrinho (rouba de longe, mas pode ser falta) e G é colocar o pé (desarme sem falta).</li>
 *     <li>O juiz apita gol, lateral, tiro de meta, escanteio, falta e pênalti; dois tempos com relógio e placar.</li>
 * </ul>
 */
public class Ball extends Entity {
    public static final int TEAM_A = 0, TEAM_B = 1;
    public static final String[] TEAM_NAMES = {"Azul", "Vermelho"};
    public static final int[] TEAM_COLORS = {0x2D5BE0, 0xE03030};
    public static final int KICKOFF = 0, THROW_IN = 1, GOAL_KICK = 2, CORNER = 3, FREE_KICK = 4, PENALTY = 5;
    public static final int HALF = 2100, BREAK = 200, AFTER = 300; // 3.9: jogo mais rápido (era 3000/300/400)
    private static final double GRAVITY = 0.045, ROLL = 0.955, AIR = 0.99, MAX_SPEED = 1.5;
    /** 0 = os aldeões jogando sozinhos, 1 = amistoso marcado por alguém, 2 = campeonato. */
    public static final int AUTO = 0, FRIENDLY = 1, LEAGUE = 2;
    /** 3.8: desafio de pênaltis do Seu Zé (5 cobranças da pessoa, depois 5 contra ela, valendo esmeraldas). */
    public static final int CHALLENGE = 3;
    /** 3.9: treino de cobrança (alvos no gol, 10 chutes de lugares diferentes). */
    public static final int TRAINING = 4;

    private static final EntityDataAccessor<Integer> SCORE_A = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCORE_B = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> NAME_A = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NAME_B = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> COLOR_A = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR_B = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.INT);
    /** Linha de baixo do placar ("1º tempo 23'", "Rodada 2/3"...). */
    private static final EntityDataAccessor<String> INFO = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.STRING);

    private Vec3 goal0, goal1, axis, side, center;
    private double length, halfWidth = 8;
    private UUID lastKicker;
    private int lastTeam = -1, lastKickTick = -100;
    private int restartKind = -1, restartTeam, restartTimer;
    private UUID restartTaker;
    /** Boca do gol (meia largura e altura), medida pelos blocos de gol: a bola que passa da linha aqui dentro é gol. */
    private double goalHalf = 1.5, goalHeight = 2;
    /** Goleiro de verdade (pessoa) voando na bola: até quando. */
    private final Map<UUID, Integer> diving = new HashMap<>();
    /** 3.7.1: de onde o goleiro saiu no pulo (o corpo cobre o caminho todo, não só onde ele está agora). */
    private final Map<UUID, Vec3> diveFrom = new HashMap<>();
    private Vec3 restartSpot;
    private int goalPause;
    private int phase, clock;
    private UUID keeperHold;
    private int holdTicks;
    /** Pessoas jogando (sempre no time A) e aldeões no banco (deram lugar a uma pessoa). */
    private final Map<UUID, Integer> humans = new HashMap<>();
    /** 3.5.3: posição que cada pessoa escolheu (índice em {@link Sports#POSITIONS}). */
    private final Map<UUID, Integer> humanPos = new HashMap<>();
    private final Set<UUID> benched = new HashSet<>();
    /** Bola grudada no pé de uma pessoa (e quando cada um pode grudar de novo depois de chutar). */
    private UUID owner;
    private final Map<UUID, Integer> ownCooldown = new HashMap<>();
    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private ServerBossEvent bar;

    private int mode = AUTO;
    /** 3.6: campeonato em formato de copa (grupos, quartas, semi e final). */
    private Cup cup;
    private final Map<UUID, Set<Integer>> playedFor = new HashMap<>();
    /** Prorrogação do mata-mata: tempo máximo (gol de ouro) e quem ganhou nos pênaltis (-1 = não teve). */
    public static final int EXTRA = 800;
    private int shootout = -1;
    /** 3.7: depois do gol, o time que marcou não passa do meio até o outro tocar na bola (-1 = livre). */
    private int kickoffLock = -1;
    /** 3.7: barreira da falta (aldeões do time que defende) e quanto tempo ela ainda pode travar a bola. */
    private final List<UUID> wall = new ArrayList<>();
    private int wallTimer;
    /** 3.7.1: quem da barreira pulou nessa cobrança (os outros ficam no chão, mais baixos). */
    private final java.util.Set<UUID> wallJump = new java.util.HashSet<>();
    /** Quantas vezes a barreira travou a bola (para o teste). */
    public int wallHits;

    // ---- 3.8
    /** Narrador da partida. */
    public final Narrator narrator = new Narrator(this);
    /** Último chute ao gol (para o narrador falar da defesa ou do chute para fora). */
    private int shotTick = -100, shotTeam = -1;
    /** Desafio de pênaltis: quem, quanto apostou, cobrança (0-4 a pessoa bate, 5-9 ela defende) e estado. */
    private UUID chPlayer, chKicker;
    private int chBet, chRound, chState, chTimer, chKickTick, chPause;
    /** Liga dos Amigos: os dois capitães (null = esse jogo não vale para a liga). */
    private UUID leagueA, leagueB;
    /** Apostas da copa: pessoa → [time, esmeraldas]. */
    private final Map<UUID, int[]> bets = new java.util.LinkedHashMap<>();
    public static final int FANS = 5;

    // ---- 3.9
    /** Campo de estádio (molde estadio_N): torcida maior na arquibancada e nome do estádio no placar. */
    public boolean stadium;
    /** Nome do estádio (sai da vila; vazio até descobrir). */
    public String stadiumName = "";
    /** Pelada na rua (crianças com chinelo de trave): a bola só faz a física; quem cuida do jogo é a StreetPelada. */
    public boolean street;
    /** Jogo marcado em outro lugar (final da várzea no estádio = 0, Copa das Vilas = 1); -1 = jogo daqui mesmo. */
    private int copaKind = -1;
    private com.boombox.server.CopaVilas.Booking booking;
    /** A final da várzea foi para o estádio: depois do apito, este campo não continua a copa. */
    private boolean movedAway;
    /** Clássico contra a vila rival (nome das duas vilas, para as provocações e o retrospecto). */
    private boolean classico;
    private com.boombox.village.Villages.Info homeVillage, awayVillage;
    /** Craque da partida: pontos de cada um no jogo (gol 3, defesa 2, chute ao gol 1) e o nome. */
    private final Map<UUID, Integer> craque = new HashMap<>();
    private final Map<UUID, String> craqueName = new HashMap<>();
    /** De onde saiu o último chute e se foi com quem chutou no ar (voleio). */
    private Vec3 lastKickPos;
    private boolean lastKickAir;
    /** VAR: tempo do juiz no monitor, time do gol em análise e se o gol vai valer. */
    private int varTimer, varScorer = -1, varWhich;
    private boolean varGood, varKickAir;
    /** Quem fez o gol em análise (o lance é guardado: ninguém mexe na bola enquanto o juiz vê o monitor). */
    private UUID varKicker;
    private Vec3 varKickPos;
    /** Treino de cobrança: quem, chute (0-9), estado, alvo da vez (0-3 = cantos), pontos. */
    private UUID trPlayer, trKicker;
    private int trShot, trState, trTimer, trTarget, trPoints, trKickTick, trPause;
    public static final int TRAINING_SHOTS = 10;
    /** Chance de VAR em cada gol de jogo marcado (o teste muda). */
    public float varChance = 0.15f;
    public float varGoodChance = 0.65f;

    public int kickoffLock() {
        return kickoffLock;
    }

    /** Tamanho da barreira: falta no campo de quem cobra = sem barreira; perto do meio 1; no meio do ataque 2; perto do gol 3. */
    public int wallSizeFor(int team, Vec3 spot) {
        double f = uFor(team, spot) / length;
        if (f <= 0.5) return 0;
        double d = spot.distanceTo(goal(team == TEAM_A ? 1 : 0));
        return d <= 12 ? 3 : d <= 17 ? 2 : 1;
    }

    public int wallIndex(Entity e) {
        return wall.indexOf(e.getUUID());
    }

    public int wallCount() {
        return wall.size();
    }

    /** Lugar do i-ésimo da barreira: 3,5 blocos da bola, na linha do gol, lado a lado. */
    public Vec3 wallSpot(int i) {
        Vec3 g = goal(restartTeam == TEAM_A ? 1 : 0);
        Vec3 dir = new Vec3(g.x - restartSpot.x, 0, g.z - restartSpot.z).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double off = (i - (wall.size() - 1) / 2.0) * 0.75;
        return restartSpot.add(dir.scale(3.5)).add(side.scale(off));
    }

    public Cup cup() {
        return cup;
    }

    public int phase() {
        return phase;
    }

    /** Só para o teste: placar final e apito do fim do segundo tempo no próximo tique. */
    public void testEnd(int a, int b) {
        entityData.set(SCORE_A, a);
        entityData.set(SCORE_B, b);
        restartKind = -1;
        goalPause = 0;
        keeperHold = null;
        phase = 2;
        clock = HALF;
    }

    /** Só para o teste: acaba o intervalo de depois do jogo (vai para o próximo jogo). */
    public void testSkipAfter() {
        if (phase == 3) clock = AFTER;
    }

    /** Só para o teste: fim da prorrogação no próximo tique. */
    public void testEndExtra() {
        if (phase == 4) {
            clock = EXTRA;
            restartKind = -1;
            goalPause = 0;
            keeperHold = null;
        }
    }

    public Ball(EntityType<? extends Ball> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SCORE_A, 0);
        b.define(SCORE_B, 0);
        b.define(NAME_A, TEAM_NAMES[0]);
        b.define(NAME_B, TEAM_NAMES[1]);
        b.define(COLOR_A, TEAM_COLORS[0]);
        b.define(COLOR_B, TEAM_COLORS[1]);
        b.define(INFO, "");
    }

    public int scoreA() { return entityData.get(SCORE_A); }
    public int scoreB() { return entityData.get(SCORE_B); }
    public String teamName(int t) { return entityData.get(t == TEAM_A ? NAME_A : NAME_B); }
    public int teamColor(int t) { return entityData.get(t == TEAM_A ? COLOR_A : COLOR_B); }
    public String info() { return entityData.get(INFO); }
    public boolean hasGoals() { return goal0 != null && goal1 != null; }
    public Vec3 goal(int i) { return i == 0 ? goal0 : goal1; }
    public Vec3 center() { return center; }
    public double length() { return length; }
    public double halfWidth() { return halfWidth; }
    public int mode() { return mode; }
    public UUID keeperHold() { return keeperHold; }
    public int holdTicks() { return holdTicks; }
    public int restartKind() { return restartKind; }
    public int restartTeam() { return restartTeam; }
    public Vec3 restartSpot() { return restartSpot; }
    public boolean restartTimerDone() { return restartTimer <= 0; }
    public boolean restartOverdue() { return restartTimer < -50; }

    public boolean restarting() {
        return restartKind >= 0 || goalPause > 0 || phase == 1 || phase == 3;
    }

    public boolean inPlay() {
        return !restarting() && keeperHold == null;
    }

    public void forcePlay() {
        restartKind = -1;
        goalPause = 0;
        keeperHold = null;
        if (phase == 1 || phase == 3) phase = 0;
    }

    /** O outro time tocou na bola agora há pouco (ela ainda "é" dele: só dá para tomar no bote). */
    public boolean freshFor(int team) {
        return lastTeam >= 0 && lastTeam != team && tickCount - lastKickTick < 12;
    }

    /** Pessoa com a bola grudada no pé (ou null). */
    public Player owner() {
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    public int ownerTeam() {
        return owner == null ? -1 : humans.getOrDefault(owner, -1);
    }

    public void loseOwner() {
        if (owner != null) ownCooldown.put(owner, tickCount + 20);
        owner = null;
    }

    public boolean canKick(int team) {
        if (goalPause > 0 || varTimer > 0 || phase == 1 || phase == 3) return false;
        return restartKind < 0 || team == restartTeam;
    }

    /** Time que tocou por último na bola. */
    public int lastTeam() {
        return lastTeam;
    }

    public boolean isBenched(Entity e) {
        return benched.contains(e.getUUID());
    }

    public int teamOf(Entity e) {
        if (e instanceof Raver r) {
            if (benched.contains(r.getUUID())) return -1;
            return r.role() == RaverRoles.TEAM_A ? TEAM_A : r.role() == RaverRoles.TEAM_B ? TEAM_B : -1;
        }
        Integer t = humans.get(e.getUUID());
        return t == null ? -1 : t;
    }

    /** Pessoas do time (para os aldeões passarem a bola para elas). */
    public List<ServerPlayer> humansOf(ServerLevel sl, int team) {
        List<ServerPlayer> out = new ArrayList<>();
        for (var e : humans.entrySet()) {
            if (e.getValue() == team && sl.getEntity(e.getKey()) instanceof ServerPlayer p && p.distanceTo(this) < 80) out.add(p);
        }
        return out;
    }

    /** Placar em texto ("Time A 1 x 0 Time B"). */
    public String scoreLine() {
        return score();
    }

    /** Nome de quem tocou na bola, do jeito do narrador ("Fulano" ou "o camisa 9 do Time"). */
    public String nameOf(Entity e) {
        if (e instanceof Player p) return p.getName().getString();
        if (e instanceof Raver r) {
            int t = teamOf(r);
            return "o camisa " + r.stall + (t >= 0 ? " do " + teamName(t) : "");
        }
        return "alguém";
    }

    /** 3.8: o goleiro pegou ou espalmou: o narrador fala da defesa. */
    public void noteSave(Entity keeper) {
        if (tickCount - shotTick < 60) addCraque(keeper, 2);
        if (tickCount - shotTick < 60) {
            narrator.save(nameOf(keeper));
            shotTick = -100;
        }
    }

    public double u(Vec3 p) { return p.subtract(goal0).dot(axis); }
    public double v(Vec3 p) { return p.subtract(center).dot(side); }
    public double uFor(int team, Vec3 p) { return team == TEAM_A ? u(p) : length - u(p); }

    public Vec3 fieldPoint(double u, double v) {
        // sempre na altura do gramado (antes usava a altura da bola: a cobrança ficava "boiando" no ar)
        return goal0.add(axis.scale(u)).add(side.scale(v)).with(net.minecraft.core.Direction.Axis.Y, center == null ? getY() : center.y);
    }

    public Vec3 teamPoint(int team, double uOwn, double v) {
        return team == TEAM_A ? fieldPoint(uOwn, v) : fieldPoint(length - uOwn, -v);
    }

    /** v do ponto do jeito que o time enxerga (negativo = esquerda de quem ataca). */
    public double vFor(int team, Vec3 p) {
        return team == TEAM_A ? v(p) : -v(p);
    }

    public boolean inBox(int goal, Vec3 p) {
        double uu = goal == 0 ? u(p) : length - u(p);
        return uu >= -0.3 && uu < 6.5 && Math.abs(v(p)) < 6;
    }

    public void kick(Entity who, Vec3 vel) {
        int team = teamOf(who);
        if (!canKick(team)) return;
        int rk = restartKind;
        if (restartKind == FREE_KICK && !wall.isEmpty() && level() instanceof ServerLevel sl) {
            wallTimer = 30;
            wallJump.clear();
            for (UUID id : wall) {
                if (sl.getEntity(id) instanceof Raver r && r.onGround() && random.nextDouble() < 0.35) {
                    r.jumpFromGround();
                    wallJump.add(id);
                }
            }
        }
        if (restartKind >= 0) restartKind = -1;
        if (kickoffLock >= 0 && team == 1 - kickoffLock) kickoffLock = -1;
        keeperHold = null;
        if (owner != null) loseOwner();
        double sp = vel.length();
        if (sp > MAX_SPEED) vel = vel.scale(MAX_SPEED / sp);
        setDeltaMovement(vel);
        lastKicker = who.getUUID();
        lastTeam = team;
        lastKickTick = tickCount;
        lastKickPos = position();
        lastKickAir = !who.onGround() && getY() > groundY() + 0.8;
        playSound(SoundEvents.WOOL_HIT, Math.min(1.2f, 0.5f + (float) sp), 0.7f + random.nextFloat() * 0.2f);
        // 3.8: chute ao gol (o narrador fala, e depois conta se foi defesa ou para fora)
        if (hasGoals() && team >= 0 && (rk < 0 || rk == FREE_KICK || rk == PENALTY)) {
            Vec3 g = goal(team == TEAM_A ? 1 : 0);
            Vec3 d = new Vec3(g.x - getX(), 0, g.z - getZ());
            Vec3 f = new Vec3(vel.x, 0, vel.z);
            if (d.length() < 26 && f.length() > 0.7 && f.normalize().dot(d.normalize()) > 0.85) {
                shotTick = tickCount;
                shotTeam = team;
                narrator.shot(nameOf(who));
                addCraque(who, 1);
            }
        }
    }

    public void catchBall(Raver keeper) {
        loseOwner();
        keeperHold = keeper.getUUID();
        holdTicks = 0;
        lastTeam = teamOf(keeper);
        lastKicker = keeper.getUUID();
        setDeltaMovement(Vec3.ZERO);
        playSound(SoundEvents.WOOL_PLACE, 1f, 0.8f);
    }

    // ------------------------------------------------------------ física e juiz
    @Override
    public void tick() {
        super.tick();
        boolean carried = false;
        if (level() instanceof ServerLevel sl0) {
            if (keeperHold != null) {
                Entity k = sl0.getEntity(keeperHold);
                if (k == null || !k.isAlive()) keeperHold = null;
                else {
                    Vec3 f = k.getLookAngle();
                    setPos(k.getX() + f.x * 0.45, k.getY() + 1.0, k.getZ() + f.z * 0.45);
                    setDeltaMovement(Vec3.ZERO);
                    holdTicks++;
                    carried = true;
                }
            } else if (owner != null) {
                carried = carry(sl0);
            }
        }
        if (!carried) {
            Vec3 v = getDeltaMovement().add(0, -GRAVITY, 0);
            Vec3 before = v;
            // 3.5.3: a gravidade tem que ficar na velocidade (antes ela se perdia e a bola "flutuava" no ar)
            setDeltaMovement(v);
            move(MoverType.SELF, v);
            v = getDeltaMovement();
            if (horizontalCollision) {
                if (Math.abs(getX() - xo) < Math.abs(before.x) * 0.5) v = new Vec3(-before.x * 0.5, v.y, v.z);
                if (Math.abs(getZ() - zo) < Math.abs(before.z) * 0.5) v = new Vec3(v.x, v.y, -before.z * 0.5);
            }
            if (onGround()) v = new Vec3(v.x * ROLL, before.y < -0.22 ? -before.y * 0.35 : 0, v.z * ROLL);
            else v = new Vec3(v.x * AIR, v.y * 0.98, v.z * AIR);
            if (v.horizontalDistanceSqr() < 4e-5) v = new Vec3(0, v.y, 0);
            setDeltaMovement(v);
        }
        if (!(level() instanceof ServerLevel sl)) return;
        if (street) {
            com.boombox.server.StreetPelada.ballTick(sl, this);
            return;
        }
        if (wallTimer > 0) {
            wallTimer--;
            for (UUID id : wall) {
                if (!(sl.getEntity(id) instanceof Raver r)) continue;
                double hd = Math.hypot(getX() - r.getX(), getZ() - r.getZ());
                double top = wallJump.contains(id) ? 2.2 : 1.8; // no chão a barreira cobre 1,8 bloco; pulando, 2,2
                if (hd < 0.55 && getY() < groundY() + top) {
                    Vec3 v = getDeltaMovement();
                    setDeltaMovement(-v.x * 0.35 + (random.nextDouble() - 0.5) * 0.2, Math.max(0.12, Math.abs(v.y) * 0.5), -v.z * 0.35 + (random.nextDouble() - 0.5) * 0.2);
                    lastTeam = teamOf(r);
                    lastKicker = r.getUUID();
                    lastKickTick = tickCount;
                    playSound(SoundEvents.WOOL_HIT, 1f, 0.6f);
                    for (ServerPlayer p : sl.players()) if (p.distanceTo(this) < 40) p.sendOverlayMessage(Component.literal("§eNa barreira!"));
                    wallHits++;
                    narrator.wallHit();
                    wallTimer = 0;
                    wall.clear();
                    break;
                }
            }
            if (wallTimer == 0) wall.clear();
        }
        if (kickoffLock >= 0 && tickCount % 2 == 0) holdHalf(sl);
        if (tickCount % 2 == 1) keepRivalsAway(sl);
        if (!hasGoals()) {
            if (tickCount % 20 == 1) findField(sl);
            return;
        }
        if (tickCount % 20 == 0) scoreboard(sl);
        if (tickCount % 1200 == 600) clearTrees(sl);
        if (tickCount % 200 == 40) ensureOrganizer(sl);
        if (tickCount % 10 == 5) guard(sl);
        if (mode == CHALLENGE) {
            tickChallenge(sl);
            return;
        }
        if (mode == TRAINING) {
            tickTraining(sl);
            return;
        }
        if (mode == AUTO && tickCount % 40 == 17) com.boombox.server.CopaVilas.tickVenue(sl, this);
        if (varTimer > 0) {
            tickVar(sl);
            return;
        }
        if (tickCount % 20 == 11) cheer(sl);
        tickClock(sl);
        if (phase == 1 || phase == 3) return;
        if (goalPause > 0) {
            if (--goalPause == 0) setRestart(sl, KICKOFF, lastTeam == TEAM_A ? TEAM_B : TEAM_A, center, false);
            return;
        }
        if (restartKind >= 0) {
            setPos(restartSpot.x, center.y, restartSpot.z);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (restartTimer > -10000) restartTimer--;
            return;
        }
        if (keeperHold != null) return;
        if (owner == null) grab(sl);
        Vec3 p = position();
        BlockPos at = blockPosition();
        if (Blocks34.isGoal(sl.getBlockState(at)) || Blocks34.isGoal(sl.getBlockState(at.above()))) {
            goal(sl, u(p) < length / 2 ? 0 : 1);
            return;
        }
        {
            double gu = u(p);
            boolean mouth = Math.abs(v(p)) < goalHalf - 0.1 && p.y - center.y < goalHeight - 0.1;
            if (mouth && (gu < 0.3 || gu > length - 0.3)) {
                goal(sl, gu < length / 2 ? 0 : 1);
                return;
            }
        }
        keeperSaves(sl);
        if (tickCount % 20 == 3) narratePlay(sl);
        double u = u(p), w = v(p);
        if (Math.abs(w) > halfWidth + 0.3) {
            int team = lastTeam == TEAM_A ? TEAM_B : TEAM_A;
            setRestart(sl, THROW_IN, team, fieldPoint(Math.max(1, Math.min(length - 1, u)), Math.signum(w) * (halfWidth - 0.4)), true);
        } else if (u < -0.2 || u > length + 0.2) {
            int end = u < 0 ? 0 : 1;
            int defender = end == 0 ? TEAM_A : TEAM_B;
            double su = end == 0 ? 0.8 : length - 0.8;
            if (lastTeam == defender) {
                setRestart(sl, CORNER, 1 - defender, fieldPoint(su, Math.signum(w == 0 ? 1 : w) * (halfWidth - 0.4)), true);
            } else {
                if (tickCount - shotTick < 60) {
                    narrator.wide();
                    shotTick = -100;
                }
                setRestart(sl, GOAL_KICK, defender, fieldPoint(end == 0 ? 2.5 : length - 2.5, Math.signum(w == 0 ? 1 : w) * 2.5), true);
            }
        }
    }

    /** Pessoa do time passando por cima da bola: ela gruda no pé. */
    private void grab(ServerLevel sl) {
        if (getY() > groundY() + 0.9) return; // bola alta passa por cima
        for (var e : humans.entrySet()) {
            if (!(sl.getEntity(e.getKey()) instanceof ServerPlayer p) || !canKick(e.getValue())) continue;
            if (ownCooldown.getOrDefault(p.getUUID(), 0) > tickCount) continue;
            double d = Math.hypot(getX() - p.getX(), getZ() - p.getZ());
            if (d < 0.9 && Math.abs(getY() - p.getY()) < 1.0) {
                owner = p.getUUID();
                lastKicker = p.getUUID();
                lastTeam = e.getValue();
                lastKickTick = tickCount;
                playSound(SoundEvents.WOOL_STEP, 0.6f, 1.2f);
                return;
            }
        }
    }

    private double groundY() {
        return center == null ? getY() : center.y;
    }

    /** Bola colada no pé de quem está com ela: vai na frente, para onde a pessoa olha. */
    private boolean carry(ServerLevel sl) {
        if (!(sl.getEntity(owner) instanceof ServerPlayer p) || !p.isAlive() || restarting() || teamOf(p) < 0 || p.distanceTo(this) > 3) {
            owner = null;
            return false;
        }
        float yaw = p.getYRot() * ((float) Math.PI / 180f);
        Vec3 f = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 now = p.position(), before = lastPos.put(p.getUUID(), now);
        Vec3 vel = before == null ? Vec3.ZERO : now.subtract(before);
        Vec3 target = now.add(f.scale(0.6 + Math.min(0.4, vel.horizontalDistance() * 1.5)));
        // não atravessa parede: anda até lá com colisão; se travar, solta a bola
        move(MoverType.SELF, new Vec3(target.x - getX(), 0, target.z - getZ()));
        if (getY() < p.getY()) setPos(getX(), p.getY(), getZ());
        setDeltaMovement(vel.x, 0, vel.z);
        lastTeam = humans.getOrDefault(owner, lastTeam);
        lastKickTick = tickCount;
        if (Math.hypot(getX() - target.x, getZ() - target.z) > 0.6) loseOwner();
        return true;
    }

    // ------------------------------------------------------------ botões de quem está jogando
    /** Chute, passe, carrinho ou desarme (ver {@link Sports}). */
    public void playerAction(ServerPlayer p, int action) {
        playerAction(p, action, 0f);
    }

    /** Posição que essa pessoa escolheu (-1 = nenhuma). */
    public int positionOf(Player p) {
        return humanPos.getOrDefault(p.getUUID(), -1);
    }

    public void playerAction(ServerPlayer p, int action, float power) {
        if (!(level() instanceof ServerLevel sl) || !hasGoals()) return;
        int team = teamOf(p);
        if (team < 0) return;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        double reach = Math.hypot(getX() - p.getX(), getZ() - p.getZ());
        boolean mine = p.getUUID().equals(owner);
        switch (action) {
            case Sports.FUT_SHOOT -> {
                if ((!mine && reach > 2.0) || !canKick(team)) return;
                double charge = power > 0 ? Math.min(1, power) : (p.isSprinting() ? 0.8 : 0.55);
                if (p.isShiftKeyDown()) {
                    // cavadinha: por cima, devagar
                    kick(p, flat.scale(0.4 + charge * 0.3).add(0, 0.45 + charge * 0.15, 0));
                } else {
                    // 3.7.1: quanto mais tempo segurando, mais forte E mais alto (bomba cheia sobe; para chutar
                    // rasteiro é preciso dosar a força)
                    double speed = 0.55 + charge * 0.95;
                    double lift = 0.02 + charge * charge * 0.55 + Math.max(0, look.y + 0.05) * 0.5;
                    kick(p, flat.scale(speed).add(0, lift, 0));
                }
                ownCooldown.put(p.getUUID(), tickCount + 12);
            }
            case Sports.FUT_PASS -> {
                if ((!mine && reach > 2.0) || !canKick(team)) return;
                Entity best = bestMate(sl, p, team, flat);
                Vec3 vel;
                if (best != null) {
                    Vec3 to = best.position().add(best.getDeltaMovement().scale(8));
                    vel = passTo(position(), to);
                    if (position().distanceTo(to) > 14) vel = vel.multiply(0.8, 1, 0.8).add(0, 0.3, 0); // lançamento
                } else {
                    vel = flat.scale(0.65).add(0, 0.04, 0);
                }
                kick(p, vel);
                ownCooldown.put(p.getUUID(), tickCount + 12);
            }
            case Sports.FUT_SLIDE -> slide(sl, p, team, flat);
            case Sports.GK_DIVE -> dive(sl, p, look);
            case Sports.FUT_POKE -> poke(sl, p, team);
            default -> { }
        }
    }

    /** Companheiro para o passe: o que está na direção do olhar; senão o mais livre e mais adiantado. */
    private Entity bestMate(ServerLevel sl, ServerPlayer p, int team, Vec3 flat) {
        List<Entity> mates = new ArrayList<>(sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(30), r -> teamOf(r) == team));
        for (ServerPlayer h : humansOf(sl, team)) if (h != p) mates.add(h);
        var rivals = sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(30), r -> teamOf(r) == 1 - team);
        Entity best = null;
        double bestScore = -1e9;
        for (Entity m : mates) {
            Vec3 to = new Vec3(m.getX() - getX(), 0, m.getZ() - getZ());
            double dist = to.length();
            if (dist < 2.5 || dist > 30) continue;
            double aim = to.normalize().dot(flat);
            double free = 8;
            for (Raver o : rivals) free = Math.min(free, o.position().distanceTo(m.position()));
            double score = aim * 12 + free + (uFor(team, m.position()) - uFor(team, position())) * 0.3 - dist * 0.1;
            if (aim < 0.3) score -= 20;
            if (score > bestScore) { bestScore = score; best = m; }
        }
        return best;
    }

    /** Goleiro de verdade: pula para onde está olhando (de lado, para cima ou para baixo) e agarra a bola que alcançar. */
    private void dive(ServerLevel sl, ServerPlayer p, Vec3 look) {
        if (positionOf(p) != 0) {
            p.sendOverlayMessage(Component.literal("§7Só o goleiro pula na bola (escolha \"Goleiro\" no organizador)."));
            return;
        }
        // 3.7.1: o "está no chão" do jogador chega do cliente com atraso; perto do chão já dá para pular
        if (!p.onGround() && p.getY() - groundY() > 0.6) return;
        if (diving.containsKey(p.getUUID())) return;
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(1, 0, 0);
        flat = flat.normalize();
        double up = 0.42 + Math.max(0, look.y) * 0.35;
        p.setDeltaMovement(flat.scale(0.9).add(0, up, 0));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
        sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.7f);
        diving.put(p.getUUID(), tickCount + 16);
        diveFrom.put(p.getUUID(), p.position());
    }

    /** Pessoa goleira voando: se alcança a bola, agarra (ela fica grudada, e aí dá para repor). */
    private void keeperSaves(ServerLevel sl) {
        if (diving.isEmpty()) return;
        diving.entrySet().removeIf(e -> e.getValue() < tickCount);
        diveFrom.keySet().retainAll(diving.keySet());
        for (UUID id : diving.keySet()) {
            if (!(sl.getEntity(id) instanceof ServerPlayer p) || teamOf(p) < 0) continue;
            double hd = Math.hypot(getX() - p.getX(), getZ() - p.getZ()), dy = getY() - p.getY();
            // distância da bola até o caminho do pulo (de onde saiu até onde está)
            Vec3 a = diveFrom.getOrDefault(id, p.position());
            Vec3 seg = new Vec3(p.getX() - a.x, 0, p.getZ() - a.z), toBall = new Vec3(getX() - a.x, 0, getZ() - a.z);
            double t = seg.lengthSqr() < 1e-6 ? 0 : Math.max(0, Math.min(1, toBall.dot(seg) / seg.lengthSqr()));
            double path = toBall.subtract(seg.scale(t)).length();
            if ((hd < 2.2 || path < 1.6) && dy > -0.8 && dy < 2.6) {
                owner = p.getUUID();
                lastTeam = teamOf(p);
                lastKicker = p.getUUID();
                lastKickTick = tickCount;
                setDeltaMovement(Vec3.ZERO);
                diving.remove(id);
                playSound(SoundEvents.WOOL_PLACE, 1f, 0.8f);
                if (mode != CHALLENGE) announce("§b§lQUE DEFESA! §r§f" + p.getName().getString() + " voou e pegou a bola!");
                noteSave(p);
                return;
            }
        }
    }

    /** Carrinho: o jogador desliza para a frente e tira a bola de longe. Se pegar o adversário em vez da bola, é falta. */
    private void slide(ServerLevel sl, ServerPlayer p, int team, Vec3 flat) {
        if (!inPlay()) return;
        p.setDeltaMovement(flat.scale(1.1).add(0, 0.12, 0));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
        sl.playSound(null, p.blockPosition(), SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, 1f, 0.8f);
        Vec3 toBall = new Vec3(getX() - p.getX(), 0, getZ() - p.getZ());
        double d = toBall.length();
        Raver victim = null;
        double vd = 99;
        for (Raver o : sl.getEntitiesOfClass(Raver.class, p.getBoundingBox().inflate(3), o -> teamOf(o) == 1 - team)) {
            double od = o.distanceTo(p);
            if (od < vd) { vd = od; victim = o; }
        }
        boolean ballInFront = d < 3.2 && (d < 0.8 || toBall.normalize().dot(flat) > 0.4);
        if (ballInFront) {
            boolean behind = victim != null && vd < 1.6 && victim.getLookAngle().dot(flat) > 0.5;
            if (victim != null && vd < 1.4 && random.nextDouble() < (behind ? 0.4 : 0.12)) {
                victim.playSound(SoundEvents.VILLAGER_HURT, 1f, 1f);
                p.sendSystemMessage(Component.literal("§cO juiz viu o carrinho no pé do adversário!"));
                foul(1 - team, victim);
                return;
            }
            Vec3 sideV = new Vec3(-flat.z, 0, flat.x).scale(random.nextBoolean() ? 0.25 : -0.25);
            kick(p, flat.scale(0.55).add(sideV).add(0, 0.06, 0));
            if (victim != null) victim.settle = 25;
            p.sendOverlayMessage(Component.literal("§aCarrinho limpo!"));
        } else if (victim != null && vd < 1.6) {
            victim.playSound(SoundEvents.VILLAGER_HURT, 1f, 1f);
            p.sendSystemMessage(Component.literal("§cCarrinho sem bola: falta!"));
            foul(1 - team, victim);
        }
    }

    /** Colocar o pé: rouba a bola de perto, sem risco de falta (mas nem sempre dá certo). */
    private void poke(ServerLevel sl, ServerPlayer p, int team) {
        if (!inPlay()) return;
        double d = Math.hypot(getX() - p.getX(), getZ() - p.getZ());
        if (d > 1.9) return;
        boolean contested = !sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(1.3), o -> teamOf(o) == 1 - team).isEmpty();
        if (!contested || random.nextDouble() < 0.6) {
            owner = p.getUUID();
            lastTeam = team;
            lastKicker = p.getUUID();
            lastKickTick = tickCount;
            for (Raver o : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(1.6), o -> teamOf(o) == 1 - team)) o.settle = 20;
            playSound(SoundEvents.WOOL_HIT, 0.7f, 1.3f);
            if (contested) p.sendOverlayMessage(Component.literal("§aRoubou a bola!"));
        } else {
            p.sendOverlayMessage(Component.literal("§7Quase! Ele protegeu a bola."));
        }
    }

    // ------------------------------------------------------------ relógio, placar e partidas
    private void tickClock(ServerLevel sl) {
        boolean anyone = sl.players().stream().anyMatch(p -> p.distanceTo(this) < 80);
        if (!anyone) return;
        clock++;
        if (phase == 0 && clock >= HALF && !restarting()) {
            phase = 1;
            kickoffLock = -1;
            clock = 0;
            whistle(sl, 3);
            loseOwner();
            announce("§e§lPriii, priii, priii! §r§fFim do primeiro tempo. §7(" + score() + ")");
            narrator.halftime();
            goHome();
        } else if (phase == 1 && clock >= BREAK) {
            phase = 2;
            clock = 0;
            announce("§fComeça o §esegundo tempo§f!");
            narrator.secondHalf();
            setRestart(sl, KICKOFF, TEAM_B, center, true);
        } else if (phase == 2 && clock >= HALF && !restarting() && mode == LEAGUE && cup != null && cup.knockout() && scoreA() == scoreB()) {
            // 3.6: empate no mata-mata: prorrogação com gol de ouro
            phase = 4;
            clock = 0;
            shootout = -1;
            whistle(sl, 2);
            loseOwner();
            announce("§e§lEmpate! §r§fProrrogação com §egol de ouro§f: quem marcar primeiro passa. Se ninguém marcar, pênaltis.");
            setRestart(sl, KICKOFF, random.nextInt(2), center, true);
        } else if (phase == 4 && clock >= EXTRA && !restarting()) {
            // ninguém marcou: pênaltis
            phase = 3;
            clock = 0;
            whistle(sl, 3);
            loseOwner();
            shootout = random.nextInt(2);
            int win = 3 + random.nextInt(3), lose = win - 1 - random.nextInt(2);
            announce("§e§lPÊNALTIS! §r§f" + teamName(shootout) + " ganhou a disputa por " + win + " a " + lose + "!");
            endMatch(sl);
        } else if (phase == 2 && clock >= HALF && !restarting()) {
            phase = 3;
            clock = 0;
            whistle(sl, 3);
            loseOwner();
            endMatch(sl);
        } else if (phase == 3 && clock >= AFTER) {
            phase = 0;
            clock = 0;
            entityData.set(SCORE_A, 0);
            entityData.set(SCORE_B, 0);
            nextMatch(sl);
        }
        if (tickCount % 10 == 0) {
            entityData.set(INFO, minute() + (mode == LEAGUE && cup != null ? "  •  " + cup.stageName() : mode == FRIENDLY ? "  •  Amistoso" : ""));
        }
    }

    private void whistle(ServerLevel sl, int times) {
        for (int i = 0; i < times; i++) {
            sl.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 1.4f, 1.9f - i * 0.05f);
        }
    }

    private void goHome() {
        setPos(center.x, getY(), center.z);
        setDeltaMovement(Vec3.ZERO);
        keeperHold = null;
        restartKind = -1;
    }

    private String score() {
        return teamName(TEAM_A) + " " + scoreA() + " x " + scoreB() + " " + teamName(TEAM_B);
    }

    public String minute() {
        if (phase == 1) return "Intervalo";
        if (phase == 3) return "Fim de jogo";
        if (phase == 4) return "Prorrogação (gol de ouro) " + (int) (clock * 15L / EXTRA) + "'";
        int m = (int) (clock * 45L / HALF) + (phase == 2 ? 45 : 0);
        return (phase == 0 ? "1º tempo " : "2º tempo ") + Math.min(phase == 0 ? 45 : 90, m) + "'";
    }

    private void scoreboard(ServerLevel sl) {
        if (bar == null) bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(""), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setName(Component.literal("⚽ " + score() + "  •  " + info()));
        bar.setProgress(phase == 1 || phase == 3 ? 1f : Math.min(1f, clock / (float) (phase == 4 ? EXTRA : HALF)));
        for (ServerPlayer p : sl.players()) {
            boolean near = p.distanceTo(this) < 56;
            if (near && !bar.getPlayers().contains(p)) bar.addPlayer(p);
            else if (!near && bar.getPlayers().contains(p)) bar.removePlayer(p);
        }

    }

    @Override
    public void remove(RemovalReason reason) {
        if (bar != null) bar.removeAllPlayers();
        super.remove(reason);
    }

    public void setRestart(ServerLevel sl, int kind, int team, Vec3 spot, boolean whistle) {
        restartKind = kind;
        restartTeam = team;
        restartSpot = spot;
        restartTaker = null;
        restartTimer = kind == KICKOFF ? 80 : kind == PENALTY ? 90 : 50;
        wall.clear();
        wallTimer = 0;
        if (kind == FREE_KICK) {
            int n = wallSizeFor(team, spot);
            if (n > 0) {
                Vec3 g = goal(team == TEAM_A ? 1 : 0);
                Vec3 mid = spot.add(new Vec3(g.x - spot.x, 0, g.z - spot.z).normalize().scale(3.5));
                var defs = sl.getEntitiesOfClass(Raver.class, new AABB(center, center).inflate(50, 10, 50),
                        r -> teamOf(r) == 1 - team && r.stall != 1);
                defs.sort((a, b) -> Double.compare(a.distanceToSqr(mid), b.distanceToSqr(mid)));
                for (int i = 0; i < n && i < defs.size(); i++) wall.add(defs.get(i).getUUID());
                restartTimer = 100; // tempo para a barreira se formar
            }
        }
        keeperHold = null;
        loseOwner();
        setPos(spot.x, center == null ? spot.y : center.y, spot.z);
        setDeltaMovement(Vec3.ZERO);
        if (whistle) whistle(sl, 1);
        String who = teamName(team);
        String msg = switch (kind) {
            case THROW_IN -> "§ePriii! Saiu pela lateral. §fLateral para o " + who + ".";
            case GOAL_KICK -> "§ePriii! Saiu pela linha de fundo. §fTiro de meta do " + who + ".";
            case CORNER -> "§ePriii! Escanteio para o " + who + "!";
            case FREE_KICK -> "§c§lFALTA! §fTiro livre para o " + who + (wall.isEmpty() ? ", sem barreira." : ", barreira de " + wall.size() + ".");
            case PENALTY -> "§c§lPÊNALTI! §fFalta dentro da área: pênalti para o " + who + "!";
            default -> "§fSaída de bola do " + who + ".";
        };
        if (mode != CHALLENGE && mode != TRAINING) announce(msg);
        if (mode != TRAINING) narrator.restart(kind, who, !wall.isEmpty());
        if (mode == TRAINING) restartTaker = trPlayer;
        for (ServerPlayer h : humansOf(sl, team)) {
            if (h.position().distanceTo(spot) < 12 && kind != KICKOFF && mode != CHALLENGE && mode != TRAINING) {
                h.sendOverlayMessage(Component.literal("§eSua cobrança? Chegue na bola e chute (esquerdo) ou passe (direito)."));
            }
        }
    }

    public void foul(int fouledTeam) {
        foul(fouledTeam, null);
    }

    /** Falta: tiro livre (ou pênalti, dentro da área). Quem sofreu a falta é quem cobra (3.5.4). */
    public void foul(int fouledTeam, Entity victim) {
        if (!(level() instanceof ServerLevel sl) || restarting() || !hasGoals()) return;
        Vec3 p = position();
        int defendingGoal = fouledTeam == TEAM_A ? 1 : 0;
        if (inBox(defendingGoal, p)) {
            setRestart(sl, PENALTY, fouledTeam, fieldPoint(defendingGoal == 0 ? 5 : length - 5, 0), true);
        } else {
            if (random.nextInt(4) == 0) announce("§e§l🟨 Cartão amarelo! §r§7O juiz não perdoou.");
            setRestart(sl, FREE_KICK, fouledTeam, fieldPoint(Math.max(1, Math.min(length - 1, u(p))), Math.max(-halfWidth + 0.5, Math.min(halfWidth - 0.5, v(p)))), true);
        }
        if (victim != null && teamOf(victim) == fouledTeam) {
            restartTaker = victim.getUUID();
            if (victim instanceof ServerPlayer vp) vp.sendOverlayMessage(Component.literal("§eVocê sofreu a falta: a cobrança é sua!"));
        }
    }

    /** Quem tem que cobrar a bola parada (quem sofreu a falta), ou null. */
    public UUID restartTaker() {
        return restartTaker;
    }

    private void announce(String msg) {
        if (!(level() instanceof ServerLevel sl)) return;
        for (var p : sl.players()) if (p.distanceTo(this) < 56) p.sendSystemMessage(Component.literal(msg));
    }

    /**
     * 3.9: gol em jogo marcado: às vezes o lance é polêmico e o juiz vai ao monitor (VAR) antes de confirmar ou anular.
     */
    private void goal(ServerLevel sl, int which) {
        int scorer = which == 0 ? TEAM_B : TEAM_A;
        if ((mode == FRIENDLY || mode == LEAGUE) && phase != 4 && random.nextFloat() < varChance) {
            varTimer = 110;
            varScorer = scorer;
            varWhich = which;
            varGood = random.nextFloat() < varGoodChance;
            varKicker = lastKicker;
            varKickPos = lastKickPos;
            varKickAir = lastKickAir;
            loseOwner();
            setDeltaMovement(Vec3.ZERO);
            whistle(sl, 1);
            announce("§e§l▣ VAR! §r§fLance polêmico no gol do " + teamName(scorer) + "... o juiz vai ao monitor.");
            narrator.line(random.nextBoolean() ? "Calma, calma! O juiz pôs a mão no ouvido... vai ao monitor! Coração na mão!"
                    : "Ihhh, vai ter VAR! A torcida do " + teamName(1 - scorer) + " reclama de impedimento!");
            for (Raver f : sl.getEntitiesOfClass(Raver.class, new AABB(center, center).inflate(60, 12, 60), r -> r.role() == RaverRoles.TORCIDA)) {
                if (f.stall - 100 != scorer && f.onGround()) f.jumpFromGround();
            }
            return;
        }
        scoreGoal(sl, which);
    }

    /** Juiz no monitor: a bola fica parada; no fim, confirma o gol ou anula (tiro de meta para quem defendia). */
    private void tickVar(ServerLevel sl) {
        setDeltaMovement(Vec3.ZERO);
        varTimer--;
        if (varTimer % 30 == 0 && varTimer > 0) {
            for (ServerPlayer p : sl.players()) if (p.distanceTo(this) < 64) p.sendOverlayMessage(Component.literal("§e▣ O juiz está revendo o lance..."));
        }
        if (varTimer > 0) return;
        int scorer = varScorer;
        varScorer = -1;
        if (varGood) {
            announce("§a§l▣ Gol confirmado! §r§fO juiz voltou do monitor e apontou o meio de campo.");
            lastKicker = varKicker;
            lastKickPos = varKickPos;
            lastKickAir = varKickAir;
            lastKickTick = tickCount;
            scoreGoal(sl, varWhich);
            return;
        }
        String why = random.nextBoolean() ? "impedimento" : "falta no começo da jogada";
        announce("§c§l▣ Gol anulado! §r§fO juiz marcou " + why + ". A torcida do " + teamName(scorer) + " não se conforma!");
        narrator.line("Anulado! O VAR pegou " + why + ". Que balde de água fria no " + teamName(scorer) + "!");
        for (Raver f : sl.getEntitiesOfClass(Raver.class, new AABB(center, center).inflate(60, 12, 60), r -> r.role() == RaverRoles.TORCIDA)) {
            f.celebrate(f.stall - 100 != scorer);
        }
        int defender = varWhich == 0 ? TEAM_A : TEAM_B;
        setRestart(sl, GOAL_KICK, defender, fieldPoint(varWhich == 0 ? 2.5 : length - 2.5, 2.5), true);
    }

    private void scoreGoal(ServerLevel sl, int which) {
        int scorer = which == 0 ? TEAM_B : TEAM_A;
        if (scorer == TEAM_A) entityData.set(SCORE_A, scoreA() + 1);
        else entityData.set(SCORE_B, scoreB() + 1);
        lastTeam = scorer;
        goalPause = 80;
        kickoffLock = scorer;
        loseOwner();
        sl.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, 1.2f);
        sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 1, getZ(), 50, 0.6, 0.6, 0.6, 0.12);
        String who = "";
        String scorerName = null;
        if (lastKicker != null && sl.getEntity(lastKicker) instanceof ServerPlayer p) {
            who = " §e(" + p.getName().getString() + ")";
            scorerName = p.getName().getString();
            Achievements.award(p, "gol_na_varzea");
            Cups.give(p, Cups.VARZEA);
            // 3.8: ranking de artilheiros (gols de pessoas em jogo marcado)
            if (mode != AUTO && teamOf(p) == scorer) com.boombox.WorldStats.get(sl.getServer()).add(scorerName, com.boombox.WorldStats.GOALS, 1);
            if (mode != AUTO && teamOf(p) == scorer) golDePlaca(sl, p, which);
        } else if (lastKicker != null && sl.getEntity(lastKicker) instanceof Raver rk && teamOf(rk) == scorer) {
            scorerName = nameOf(rk);
        }
        if (lastKicker != null && sl.getEntity(lastKicker) instanceof Entity ke && teamOf(ke) == scorer) addCraque(ke, 3);
        announce("§a§lGOOOOOL do " + teamName(scorer) + "!" + who + " §r§f" + score());
        if (classico) classicoTaunt(sl, scorer);
        narrator.goal(scorerName, teamName(scorer));
        shotTick = -100;
        for (Raver r : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(60), r -> teamOf(r) == scorer)) r.celebrate(true);
        fansReact(sl, scorer);
        if (phase == 4) {
            // gol de ouro: acabou o jogo
            announce("§6§lGOL DE OURO! §r§fO " + teamName(scorer) + " passa de fase!");
            phase = 3;
            clock = 0;
            whistle(sl, 3);
            endMatch(sl);
        }
    }

    // ------------------------------------------------------------ amistoso e campeonato
    /** Organizador: alguém marcou um amistoso ou um campeonato. A pessoa entra no time A. */
    public void setup(ServerPlayer p, int setupMode, String name, int color) {
        setup(p, setupMode, name, color, 6);
    }

    public void setup(ServerPlayer p, int setupMode, String name, int color, int position) {
        if (!(level() instanceof ServerLevel sl) || !hasGoals()) return;
        if (mode == CHALLENGE) {
            p.sendSystemMessage(Component.literal("§7Tem um desafio de pênaltis do Seu Zé acontecendo agora. Espere acabar!"));
            return;
        }
        if (setupMode == Sports.JUNTAR || setupMode == Sports.CONTRA) {
            join(sl, p, position, setupMode == Sports.CONTRA ? TEAM_B : TEAM_A);
            return;
        }
        if ((setupMode == Sports.TREINO || setupMode == Sports.CLASSICO) && mode != AUTO) {
            p.sendSystemMessage(Component.literal("§7Tem jogo acontecendo aqui agora. Espere acabar!"));
            return;
        }
        if (setupMode == Sports.TREINO) {
            startTraining(sl, p);
            return;
        }
        if (setupMode == Sports.CLASSICO) {
            var pair = com.boombox.server.CopaVilas.rivalry(sl, this);
            if (pair == null) {
                p.sendSystemMessage(Component.literal("§7O organizador está ligando para a vila rival... tente de novo em alguns segundos."));
                return;
            }
            if (pair.length == 0) {
                p.sendSystemMessage(Component.literal("§7Não achei outra vila por perto para ser a rival desta."));
                return;
            }
            humans.clear();
            humanPos.clear();
            humanPos.put(p.getUUID(), Math.max(0, Math.min(Sports.POSITIONS.length - 1, position)));
            loseOwner();
            mode = FRIENDLY;
            classico = true;
            homeVillage = pair[0];
            awayVillage = pair[1];
            humans.put(p.getUUID(), TEAM_A);
            Sports.sendState(p, Sports.FOOTBALL, false, humanPos.get(p.getUUID()));
            p.sendSystemMessage(Component.literal("§c§l⚔ Clássico! §r§fVocê joga pelo §e" + homeVillage.team() + "§f (" + homeVillage.name() + ") contra o §c"
                    + awayVillage.team() + "§f, da " + awayVillage.name() + ", a vila rival."));
            startMatch(sl);
            return;
        }
        if (mode != AUTO && !humans.isEmpty() && !humans.containsKey(p.getUUID())) {
            String who = humansOf(sl, TEAM_A).stream().map(h -> h.getName().getString()).findFirst().orElse("alguém");
            p.sendSystemMessage(Component.literal("§7" + who + " já está jogando aqui. Use §e\"Entrar no time do amigo\"§7 ou §c\"Jogar contra o amigo\"§7!"));
            return;
        }
        humans.clear();
        humanPos.clear();
        humanPos.put(p.getUUID(), Math.max(0, Math.min(Sports.POSITIONS.length - 1, position)));
        loseOwner();
        classico = false;
        copaKind = -1;
        booking = null;
        if (setupMode == Sports.CAMPEONATO) {
            mode = LEAGUE;
            String n = name == null || name.isBlank() ? p.getName().getString() + " FC" : name.trim();
            if (n.length() > 24) n = n.substring(0, 24);
            cup = new Cup(false);
            cup.init(random, n, color);
            playedFor.clear();
            announce("§6§l🏆 Copa da Várzea! §r§f16 times em 4 grupos. O " + cup.names[0] + " está no §eGrupo A§f com "
                    + cup.names[1] + ", " + cup.names[2] + " e " + cup.names[3] + ". Os 2 primeiros vão para as quartas, depois semi e final.");
        } else {
            mode = FRIENDLY;
        }
        humans.put(p.getUUID(), TEAM_A);
        Sports.sendState(p, Sports.FOOTBALL, mode == LEAGUE, humanPos.get(p.getUUID()));
        p.sendSystemMessage(Component.literal("§aVocê está no jogo, de §e" + Sports.POSITIONS[humanPos.get(p.getUUID())]
                + "§a! §7Passe por cima da bola para dominar. As teclas aparecem embaixo da tela."));
        startMatch(sl);
    }

    /** Começo de uma partida: times, camisas e saída de bola. */
    private void startMatch(ServerLevel sl) {
        String a, b;
        int ca, cb;
        if (mode == LEAGUE && cup != null && cup.opponent() >= 0) {
            a = cup.names[0];
            ca = cup.colors[0];
            b = cup.names[cup.opponent()];
            cb = cup.colors[cup.opponent()];
            if (Sports.similar(ca, cb)) cb = Sports.contrast(ca);
        } else if (classico && homeVillage != null && awayVillage != null) {
            a = homeVillage.team();
            ca = homeVillage.color();
            b = awayVillage.team();
            cb = Sports.similar(ca, awayVillage.color()) ? Sports.contrast(ca) : awayVillage.color();
        } else {
            var t = Sports.randomTeams(random, false, 2, List.of(), List.of());
            a = t.get(0)[0];
            ca = Integer.parseInt(t.get(0)[1]);
            b = t.get(1)[0];
            cb = Integer.parseInt(t.get(1)[1]);
        }
        craque.clear();
        craqueName.clear();
        varTimer = 0;
        entityData.set(NAME_A, a);
        entityData.set(NAME_B, b);
        entityData.set(COLOR_A, ca);
        entityData.set(COLOR_B, cb);
        entityData.set(SCORE_A, 0);
        entityData.set(SCORE_B, 0);
        phase = 0;
        clock = 0;
        kickoffLock = -1;
        // cada pessoa entra no lugar do aldeão da posição que escolheu
        var all = rebench(sl);
        for (var e : humans.entrySet()) if (sl.getEntity(e.getKey()) instanceof ServerPlayer p) giveShirt(p, e.getValue());
        String kind = mode == LEAGUE && cup != null ? "§6" + cup.stageName() + ": " : classico ? "§c§l⚔ CLÁSSICO! §r" : mode == FRIENDLY ? "§eAmistoso: " : "§7Pelada dos aldeões: ";
        announce(kind + "§f" + a + " §7x §f" + b + (stadium && !stadiumName.isEmpty() && mode != AUTO ? " §7— no §e" + stadiumName : ""));
        if (classico && mode != AUTO) {
            narrator.line("É CLÁSSICO, minha gente! " + a + " contra " + b + ", vila contra vila! Aqui ninguém quer perder nem no par ou ímpar!");
        }
        leagueA = leagueB = null;
        setRestart(sl, KICKOFF, TEAM_A, center, true);
        spawnFans(sl);
        narrator.kickoff();
    }

    /** Põe no banco o aldeão da posição de cada pessoa do time e acerta as camisas. */
    private List<Raver> rebench(ServerLevel sl) {
        benched.clear();
        var all = sl.getEntitiesOfClass(Raver.class, new AABB(center, center).inflate(50, 10, 50), r -> r.role() == RaverRoles.TEAM_A || r.role() == RaverRoles.TEAM_B);
        for (var e : humans.entrySet()) {
            int role = e.getValue() == TEAM_A ? RaverRoles.TEAM_A : RaverRoles.TEAM_B;
            int[] slots = Sports.POSITION_SLOTS[humanPos.getOrDefault(e.getKey(), 6)];
            Raver pick = null;
            outer:
            for (int slot : slots) {
                for (Raver r : all) {
                    if (r.role() == role && !benched.contains(r.getUUID()) && r.stall == slot) { pick = r; break outer; }
                }
            }
            if (pick == null && slots[0] != 1) {
                for (Raver r : all) {
                    if (r.role() == role && !benched.contains(r.getUUID()) && r.stall != 1) { pick = r; break; } // campo sem essa posição
                }
            }
            if (pick != null) benched.add(pick.getUUID());
        }
        if (owner != null && benched.contains(owner)) loseOwner();
        for (Raver r : all) {
            int t = teamOf(r);
            r.setShirt(t < 0 ? 0 : (teamColor(t) | 0xFF000000));
        }
        return all;
    }

    public int benchedCount() {
        return benched.size();
    }

    /** 3.5.5: o amigo entra no time de quem já está jogando, na posição que escolheu (o aldeão dela vai para o banco). */
    public void join(ServerLevel sl, ServerPlayer p, int position) {
        join(sl, p, position, TEAM_A);
    }

    /** 3.7: entra no time A (com quem marcou o jogo) ou no time B (contra ele: jogador contra jogador). */
    public void join(ServerLevel sl, ServerPlayer p, int position, int team) {
        if (humans.containsKey(p.getUUID())) {
            p.sendSystemMessage(Component.literal("§7Você já está no jogo!"));
            return;
        }
        List<ServerPlayer> playing = new ArrayList<>(humansOf(sl, TEAM_A));
        playing.addAll(humansOf(sl, TEAM_B));
        if (mode == AUTO || playing.isEmpty()) {
            p.sendSystemMessage(Component.literal("§7Ninguém está jogando agora. Marque um amistoso ou um campeonato, e o seu amigo entra no seu time ou no time adversário."));
            return;
        }
        List<ServerPlayer> mates = humansOf(sl, team);
        if (mates.size() >= 4) {
            p.sendSystemMessage(Component.literal("§7Esse time já está cheio de gente (4 jogadores)."));
            return;
        }
        int pos = Math.max(0, Math.min(Sports.POSITIONS.length - 1, position));
        humanPos.put(p.getUUID(), pos);
        humans.put(p.getUUID(), team);
        rebench(sl);
        giveShirt(p, team);
        Sports.sendState(p, Sports.FOOTBALL, mode == LEAGUE, pos);
        String friend = playing.get(0).getName().getString();
        if (team == TEAM_A) {
            announce("§b" + p.getName().getString() + " entrou no " + teamName(TEAM_A) + " com " + friend + ", de " + Sports.POSITIONS[pos] + "!");
            p.sendSystemMessage(Component.literal("§aVocê está no time de " + friend + ", de §e" + Sports.POSITIONS[pos]
                    + "§a! §7O passe (botão direito) também vai para o seu amigo."));
        } else {
            announce("§c§l" + p.getName().getString() + " entrou no " + teamName(TEAM_B) + ", contra " + friend + "! §r§fJogador contra jogador, de "
                    + Sports.POSITIONS[pos] + ".");
            p.sendSystemMessage(Component.literal("§aVocê está no §c" + teamName(TEAM_B) + "§a, contra " + friend + ", de §e" + Sports.POSITIONS[pos] + "§a!"));
            if (mode == FRIENDLY && leagueA == null && mates.isEmpty()) leagueGame(sl, p);
        }
    }

    /** 3.7.1: área que o goleiro está protegendo (bola na mão dele ou tiro de meta): 0 ou 1, ou -1 = nenhuma. */
    public int protectedGoal() {
        if (keeperHold != null && level() instanceof ServerLevel sl && sl.getEntity(keeperHold) != null) {
            return teamOf(sl.getEntity(keeperHold)) == TEAM_A ? 0 : 1;
        }
        if (restartKind == GOAL_KICK) return restartTeam == TEAM_A ? 0 : 1;
        return -1;
    }

    /** Ponto logo fora da área desse gol (na mesma altura do campo). */
    public Vec3 outOfBox(int goal, Vec3 p) {
        if (!inBox(goal, p)) return p;
        double vv = v(p);
        // sai pela frente da área, ou pelo lado se já estiver bem aberto
        if (Math.abs(vv) > 4.5) return fieldPoint(goal == 0 ? Math.max(0.5, u(p)) : Math.min(length - 0.5, u(p)), Math.signum(vv) * 6.6);
        return fieldPoint(goal == 0 ? 7.2 : length - 7.2, vv);
    }

    /** Distância que o adversário tem que ficar da bola no lateral e no escanteio. */
    public static final double RESTART_GAP = 4.0;

    /**
     * 3.7.1: bola do goleiro (na mão ou tiro de meta): o time adversário não entra na área. Lateral e escanteio: o
     * adversário fica a 4 blocos da bola. Vale para as pessoas (os aldeões já se posicionam assim pela IA).
     */
    private void keepRivalsAway(ServerLevel sl) {
        int g = protectedGoal();
        if (g >= 0) {
            int rival = g == 0 ? TEAM_B : TEAM_A;
            for (ServerPlayer h : humansOf(sl, rival)) {
                if (!inBox(g, h.position())) continue;
                Vec3 out = outOfBox(g, h.position());
                h.teleportTo(out.x, Math.max(h.getY(), center.y), out.z);
                h.setDeltaMovement(Vec3.ZERO);
                h.sendOverlayMessage(Component.literal("§eBola do goleiro: o time adversário não pode entrar na área."));
            }
        }
        if (restartKind == THROW_IN || restartKind == CORNER) {
            for (ServerPlayer h : humansOf(sl, 1 - restartTeam)) {
                Vec3 d = new Vec3(h.getX() - restartSpot.x, 0, h.getZ() - restartSpot.z);
                if (d.length() >= RESTART_GAP) continue;
                Vec3 dir = d.lengthSqr() < 1e-4 ? new Vec3(center.x - restartSpot.x, 0, center.z - restartSpot.z).normalize() : d.normalize();
                Vec3 out = restartSpot.add(dir.scale(RESTART_GAP + 0.3));
                h.teleportTo(out.x, Math.max(h.getY(), center.y), out.z);
                h.setDeltaMovement(Vec3.ZERO);
                h.sendOverlayMessage(Component.literal("§eEspaço da cobrança: fique a 4 blocos da bola até ela ser cobrada."));
            }
        }
    }

    /** 3.7: depois do gol, quem marcou fica no seu campo até o outro time dar a saída (tocar na bola). */
    private void holdHalf(ServerLevel sl) {
        double half = length / 2;
        for (ServerPlayer h : humansOf(sl, kickoffLock)) {
            Vec3 pos = h.position();
            if (uFor(kickoffLock, pos) <= half + 0.2) continue;
            Vec3 back = teamPoint(kickoffLock, half - 1.0, vFor(kickoffLock, pos));
            h.teleportTo(back.x, Math.max(pos.y, center.y), back.z);
            h.setDeltaMovement(Vec3.ZERO);
            h.sendOverlayMessage(Component.literal("§eEspere o outro time dar a saída: até ele tocar na bola, o seu time fica no seu campo."));
        }
    }

    private void endMatch(ServerLevel sl) {
        int a = scoreA(), b = scoreB();
        int winner = a > b ? TEAM_A : b > a ? TEAM_B : -1;
        announce("§e§lFIM DE JOGO! §r§f" + score() + " §7— " + (winner < 0 ? "Empate!" : "Vitória do " + teamName(winner) + "!"));
        narrator.end();
        if (mode != AUTO) chooseCraque(sl);
        if (classico && homeVillage != null && awayVillage != null) com.boombox.server.CopaVilas.classicoResult(sl, this, homeVillage, awayVillage, a, b);
        for (Raver r : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(60), r -> teamOf(r) >= 0)) r.celebrate(teamOf(r) == winner);
        if (winner >= 0) fansReact(sl, winner);
        if (leagueA != null) {
            // 3.8: jogo da Liga dos Amigos: vai para a tabela
            var ws = com.boombox.WorldStats.get(sl.getServer());
            ws.recordLeague(leagueA, leagueB, a, b);
            var ta = ws.team(leagueA);
            int pos = ws.table().indexOf(ta) + 1;
            announce("§b★ Liga dos Amigos: §fresultado na tabela. " + (ta == null ? "" : ta.name + " está em " + pos + "º lugar.") + " §7(organizador → Liga dos amigos)");
            leagueA = leagueB = null;
        }
        if (mode != LEAGUE || cup == null) return;
        for (var e : humans.entrySet()) if (e.getValue() == TEAM_A) playedFor.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(0);
        int tie = a == b && cup.knockout() ? (shootout >= 0 ? shootout : random.nextInt(2)) : 0;
        shootout = -1;
        resolveBets(sl, a > b ? TEAM_A : b > a ? TEAM_B : cup.knockout() ? tie : -1);
        for (String m : cup.playerResult(a, b, tie, random)) announce(m);
        for (ServerPlayer h : humansOf(sl, TEAM_A)) Sports.sendCup(h, cup, Sports.FOOTBALL);
        movedAway = com.boombox.server.CopaVilas.afterCupMatch(sl, this, cup, copaKind, booking, rosterPositions());
        if (cup.champion() == 0 && !cup.vilas) {
            sl.sendParticles(ParticleTypes.FIREWORK, center.x, center.y + 3, center.z, 120, 3, 2, 3, 0.2);
            for (var e : playedFor.entrySet()) {
                if (sl.getEntity(e.getKey()) instanceof ServerPlayer p) {
                    ItemStack taca = new ItemStack(Blocks34.TACA_VARZEA);
                    if (!p.getInventory().add(taca)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), taca);
                    Achievements.award(p, "campeao_da_varzea");
                    com.boombox.WorldStats.get(sl.getServer()).add(p.getName().getString(), com.boombox.WorldStats.CUPS, 1);
                    p.sendSystemMessage(Component.literal("§6Você foi campeão da copa com o " + cup.names[0] + " e levou a Taça da Várzea!"));
                }
            }
        }
    }

    /**
     * 3.5.2: a pessoa sai do amistoso ou desiste do campeonato. Ela sai do time (a camisa vai embora), o aldeão que
     * estava no banco volta para o campo e os aldeões seguem numa pelada entre eles.
     */
    public void leave(ServerPlayer p) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (mode == CHALLENGE) {
            if (p.getUUID().equals(chPlayer)) {
                narrator.line(p.getName().getString() + " desistiu do desafio! A aposta fica com o Seu Zé.");
                endChallenge(sl);
            }
            return;
        }
        if (humans.remove(p.getUUID()) == null) return;
        if (p.getUUID().equals(leagueA) || p.getUUID().equals(leagueB)) {
            leagueA = leagueB = null;
            announce("§7Esse jogo não vale mais para a Liga dos Amigos.");
        }
        humanPos.remove(p.getUUID());
        if (p.getUUID().equals(owner)) loseOwner();
        Sports.sendState(p, Sports.NONE);
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME) && chest.getHoverName().getString().startsWith("Camisa do")) {
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
        boolean wasLeague = mode == LEAGUE;
        announce(wasLeague ? "§c" + p.getName().getString() + " desistiu do campeonato. §7O " + (cup == null ? "time" : cup.names[0]) + " saiu da competição."
                : "§7" + p.getName().getString() + " saiu do amistoso.");
        p.sendSystemMessage(Component.literal(wasLeague ? "§7Você desistiu do campeonato. Fale com o organizador quando quiser jogar de novo."
                : "§7Você saiu do jogo. Fale com o organizador quando quiser jogar de novo."));
        if (humans.isEmpty()) {
            // ninguém mais jogando: volta a pelada dos aldeões, com times novos
            resolveBets(sl, -1);
            if (booking != null) com.boombox.server.CopaVilas.cancel(sl, booking, p);
            booking = null;
            copaKind = -1;
            classico = false;
            if (mode == LEAGUE) cup = null;
            mode = AUTO;
            playedFor.clear();
            startMatch(sl);
        } else {
            rebench(sl); // o amigo continua: só o aldeão da posição de quem saiu volta para o campo
        }
    }

    /** Depois do apito final: próxima rodada do campeonato, ou volta a pelada dos aldeões. */
    private void nextMatch(ServerLevel sl) {
        if (mode == LEAGUE && cup != null && !cup.finished() && !movedAway && copaKind < 0) {
            startMatch(sl);
            return;
        }
        // 3.9: jogo marcado em outro lugar (ou Copa das Vilas): quem joga vai para o próximo estádio
        copaKind = -1;
        booking = null;
        movedAway = false;
        classico = false;
        if (mode == LEAGUE && cup != null) cup = null;
        for (var e : humans.entrySet()) {
            if (sl.getEntity(e.getKey()) instanceof ServerPlayer p) {
                Sports.sendState(p, Sports.NONE);
                p.sendSystemMessage(Component.literal("§7Valeu pelo jogo! Fale com o organizador do campo para jogar de novo."));
                ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
                if (chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME) && chest.getHoverName().getString().startsWith("Camisa do")) {
                    p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                }
            }
        }
        humans.clear();
        mode = AUTO;
        startMatch(sl);
    }

    /** Tabela da copa: abre a tela (grupos e chaveamento) para quem pediu. */
    public void table(ServerLevel sl, ServerPlayer only) {
        if (cup == null) {
            if (only != null) only.sendSystemMessage(Component.literal("§7" + score() + " — " + info() + ". Fale com o organizador para marcar um amistoso ou um campeonato."));
            return;
        }
        if (only != null) Sports.sendCup(only, cup, Sports.FOOTBALL);
    }

    // ------------------------------------------------------------ 3.8: narração do lance e torcida
    /** De vez em quando o narrador conta quem está com a bola e onde. */
    private void narratePlay(ServerLevel sl) {
        if (mode == AUTO || !inPlay()) return;
        Entity holder = owner == null ? null : sl.getEntity(owner);
        if (holder == null) {
            double best = 2.5;
            for (Entity e : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(2.5), r -> teamOf(r) >= 0)) {
                double d = e.distanceTo(this);
                if (d < best) { best = d; holder = e; }
            }
        }
        if (holder == null) return;
        int t = teamOf(holder);
        if (t < 0) return;
        narrator.tickPlay(nameOf(holder), t, uFor(t, position()) / length, vFor(t, position()));
    }

    private static final String[] CHANTS = {
            "Ô, ô, ô, o {t} chegou, a várzea é nossa, ô!", "Vamo, vamo, {t}! Hoje tem gol, hoje tem festa!",
            "Uh, uh, uh, é o {t}! Ninguém segura!", "Bate o tambor, levanta a bandeira: {t} na cabeça!",
            "Ê, ê, ê, o {t} vai vencer!", "Pra cima deles, {t}! A torcida tá contigo!",
            "Um, dois, três, é gol do {t} outra vez!", "Ninguém para o {t}, ô, ninguém para!"};

    /** Quantas vezes a torcida cantou (para o teste). */
    public int chants;

    /** A torcida canta de vez em quando (quem está atacando, ou um dos dois lados). */
    private void cheer(ServerLevel sl) {
        if (mode == AUTO || !inPlay() || random.nextInt(18) != 0) return;
        var fans = sl.getEntitiesOfClass(Raver.class, new AABB(center, center).inflate(60, 12, 60), r -> r.role() == RaverRoles.TORCIDA);
        if (fans.isEmpty()) return;
        int team = owner != null ? ownerTeam() : lastTeam >= 0 && random.nextBoolean() ? lastTeam : random.nextInt(2);
        if (team < 0) team = random.nextInt(2);
        String chant = CHANTS[random.nextInt(CHANTS.length)].replace("{t}", teamName(team));
        chants++;
        for (var p : sl.players()) if (p.distanceTo(this) < 64) p.sendSystemMessage(Component.literal("§7📣 Torcida do " + teamName(team) + ": §f" + chant));
        for (Raver f : fans) if (f.stall - 100 == team && f.onGround() && random.nextInt(2) == 0) f.jumpFromGround();
    }

    /** Gol ou vitória: a torcida do time comemora com sinalizador; a do outro reclama. */
    private void fansReact(ServerLevel sl, int team) {
        for (Raver f : sl.getEntitiesOfClass(Raver.class, new AABB(center, center).inflate(60, 12, 60), r -> r.role() == RaverRoles.TORCIDA)) {
            boolean ours = f.stall - 100 == team;
            f.celebrate(ours);
            if (ours) f.flare = 100;
        }
    }

    /** Torcida dos dois times na beira do campo (do lado oposto ao organizador), com camisa e bandeira do time. */
    private void spawnFans(ServerLevel sl) {
        for (Raver r : sl.getEntitiesOfClass(Raver.class, new AABB(center, center).inflate(60, 12, 60), r -> r.role() == RaverRoles.TORCIDA)) r.discard();
        if (mode == AUTO || mode == CHALLENGE || mode == TRAINING) return;
        int fans = stadium ? 14 : FANS;
        for (int team = 0; team < 2; team++) {
            for (int i = 0; i < fans; i++) {
                double uu, vv;
                if (stadium) {
                    // 3.9: no estádio cada torcida tem um lado da arquibancada, em três fileiras
                    uu = length * 0.1 + (i / 2) * (length * 0.8 / (fans / 2 - 1)) + (i % 2) * 1.5;
                    vv = (halfWidth + 3.5 + (i % 3) * 1.0) * (team == TEAM_A ? 1 : -1);
                } else {
                    uu = length * (team == TEAM_A ? 0.12 : 0.58) + i * (length * 0.3 / (FANS - 1));
                    vv = halfWidth + 2.8 + (i % 2) * 1.0;
                }
                Vec3 at = fieldPoint(uu, vv);
                int y = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
                if (Math.abs(y - center.y) > (stadium ? 8 : 4)) y = (int) Math.floor(center.y);
                Raver f = ModEntities.RAVER.create(sl, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                if (f == null) continue;
                Vec3 look = fieldPoint(uu, 0);
                float yaw = (float) (Math.toDegrees(Math.atan2(look.z - at.z, look.x - at.x)) - 90);
                f.snapTo(at.x, y, at.z, yaw, 0);
                f.setRole(RaverRoles.TORCIDA);
                f.stall = 100 + team;
                f.ball = getUUID();
                f.setShirt(0xFF000000 | teamColor(team));
                f.setItemSlot(EquipmentSlot.MAINHAND, banner(teamColor(team)));
                sl.addFreshEntity(f);
            }
        }
    }

    /** Bandeira da cor mais parecida com a do time. */
    public static ItemStack banner(int color) {
        net.minecraft.world.item.DyeColor best = net.minecraft.world.item.DyeColor.WHITE;
        long bd = Long.MAX_VALUE;
        for (var d : net.minecraft.world.item.DyeColor.values()) {
            int c = d.getTextureDiffuseColor();
            long dr = ((c >> 16) & 255) - ((color >> 16) & 255), dg = ((c >> 8) & 255) - ((color >> 8) & 255), db = (c & 255) - (color & 255);
            long dist = dr * dr + dg * dg + db * db;
            if (dist < bd) { bd = dist; best = d; }
        }
        return new ItemStack(Items.BANNER.pick(best));
    }

    // ------------------------------------------------------------ 3.8: Liga dos Amigos
    /** O amigo entrou contra quem marcou o amistoso: se os dois têm time na liga, o jogo vale pontos (com os times deles). */
    private void leagueGame(ServerLevel sl, ServerPlayer rival) {
        var as = humansOf(sl, TEAM_A);
        if (as.isEmpty()) return;
        ServerPlayer cap = as.get(0);
        var ws = com.boombox.WorldStats.get(sl.getServer());
        var ta = ws.team(cap.getUUID());
        var tb = ws.team(rival.getUUID());
        if (ta == null || tb == null) {
            String hint = "§7Dica: na §bLiga dos Amigos§7 (organizador → Liga dos amigos) cada um monta seu time, e o jogo contra o amigo vale pontos na tabela.";
            cap.sendSystemMessage(Component.literal(hint));
            rival.sendSystemMessage(Component.literal(hint));
            return;
        }
        int cb = Sports.similar(ta.color, tb.color) ? Sports.contrast(ta.color) : tb.color;
        entityData.set(NAME_A, ta.name);
        entityData.set(NAME_B, tb.name);
        entityData.set(COLOR_A, ta.color);
        entityData.set(COLOR_B, cb);
        entityData.set(SCORE_A, 0);
        entityData.set(SCORE_B, 0);
        phase = 0;
        clock = 0;
        kickoffLock = -1;
        rebench(sl);
        for (var e : humans.entrySet()) if (sl.getEntity(e.getKey()) instanceof ServerPlayer hp) giveShirt(hp, e.getValue());
        announce("§b§l★ Liga dos Amigos! §r§f" + ta.name + " §7x §f" + tb.name + " §7— vale pontos na tabela (vitória 3, empate 1).");
        setRestart(sl, KICKOFF, TEAM_A, center, true);
        spawnFans(sl);
        narrator.kickoff();
        leagueA = cap.getUUID();
        leagueB = rival.getUUID();
    }

    /** Só para o teste: esse jogo vale para a Liga dos Amigos? */
    public boolean leagueGameRunning() {
        return leagueA != null;
    }

    // ------------------------------------------------------------ 3.8: apostas nos jogos da copa
    /** Dá para apostar: jogo da copa, ainda no primeiro tempo. */
    public boolean betsOpen() {
        return mode == LEAGUE && cup != null && phase == 0 && !humans.isEmpty();
    }

    /** Dados da tela de apostas: times, se está aberta, total em cada time e a minha aposta. */
    public String betData(ServerPlayer p) {
        int pa = 0, pb = 0;
        for (int[] b : bets.values()) {
            if (b[0] == TEAM_A) pa += b[1];
            else pb += b[1];
        }
        int[] mine = bets.get(p.getUUID());
        return teamName(TEAM_A) + "\t" + teamName(TEAM_B) + "\t" + (betsOpen() ? 1 : 0) + "\t" + pa + "\t" + pb + "\t"
                + (mine == null ? -1 : mine[0]) + "\t" + (mine == null ? 0 : mine[1]) + "\t" + teamOf(p) + "\t" + (mode == LEAGUE && cup != null ? 1 : 0)
                + "\t" + teamColor(TEAM_A) + "\t" + teamColor(TEAM_B);
    }

    public void placeBet(ServerPlayer p, int team, int amount) {
        if (!(level() instanceof ServerLevel sl) || (team != TEAM_A && team != TEAM_B) || amount <= 0 || amount > 64) return;
        if (!betsOpen()) {
            p.sendSystemMessage(Component.literal(mode == LEAGUE ? "§7As apostas fecham no fim do primeiro tempo. Espere o próximo jogo da copa!"
                    : "§7Só dá para apostar nos jogos da copa (campeonato)."));
            return;
        }
        int my = teamOf(p);
        if (my >= 0 && my != team) {
            p.sendSystemMessage(Component.literal("§7Quem está jogando só pode apostar no próprio time!"));
            return;
        }
        int[] old = bets.get(p.getUUID());
        if (old != null && old[0] != team) {
            p.sendSystemMessage(Component.literal("§7Você já apostou no " + teamName(old[0]) + ". Não dá para trocar de lado!"));
            return;
        }
        boolean free = p.getAbilities().instabuild;
        if (!free && com.boombox.WorldStats.countEmeralds(p) < amount) {
            p.sendSystemMessage(Component.literal("§7Você não tem " + amount + " esmeralda(s)."));
            return;
        }
        if (!free) com.boombox.WorldStats.takeEmeralds(p, amount);
        com.boombox.WorldStats.get(sl.getServer()).hold(p.getUUID(), amount);
        bets.merge(p.getUUID(), new int[]{team, amount}, (x, y) -> new int[]{x[0], x[1] + y[1]});
        announce("§e$ " + p.getName().getString() + " apostou " + amount + " esmeralda(s) no " + teamName(team) + "!");
    }

    /** Fim do jogo: quem acertou divide tudo (do tamanho da aposta). Empate ou ninguém acertou: devolve. */
    private void resolveBets(ServerLevel sl, int winner) {
        if (bets.isEmpty()) return;
        var ws = com.boombox.WorldStats.get(sl.getServer());
        int pool = 0, win = 0;
        for (int[] b : bets.values()) {
            pool += b[1];
            if (b[0] == winner) win += b[1];
        }
        boolean refund = winner < 0 || win == 0;
        for (var e : bets.entrySet()) {
            int[] b = e.getValue();
            ws.release(e.getKey(), b[1]);
            int pay = refund ? b[1] : b[0] == winner ? (int) Math.floor((double) pool * b[1] / win) : 0;
            ws.pay(sl.getServer(), e.getKey(), pay);
            if (sl.getServer().getPlayerList().getPlayer(e.getKey()) instanceof ServerPlayer p) {
                p.sendSystemMessage(Component.literal(refund ? "§e$ Aposta devolvida: " + b[1] + " esmeralda(s)."
                        : pay > 0 ? "§a$ Você acertou a aposta e ganhou " + pay + " esmeralda(s)!" : "§c$ Não foi dessa vez: você perdeu " + b[1] + " esmeralda(s) na aposta."));
            }
        }
        if (!refund) announce("§e$ Apostas: " + pool + " esmeralda(s) divididas entre quem apostou no " + teamName(winner) + ".");
        bets.clear();
    }

    // ------------------------------------------------------------ 3.8: desafio de pênaltis do Seu Zé
    public boolean challengeRunning() {
        return mode == CHALLENGE;
    }

    /** Começa o desafio (a aposta já foi paga ao Seu Zé). false = o campo está ocupado. */
    public boolean startChallenge(ServerPlayer p, int bet) {
        if (!(level() instanceof ServerLevel sl) || !hasGoals() || mode != AUTO) return false;
        humans.clear();
        humanPos.clear();
        mode = CHALLENGE;
        chPlayer = p.getUUID();
        chBet = bet;
        chRound = 0;
        chState = 0;
        chTimer = 0;
        humans.put(p.getUUID(), TEAM_A);
        humanPos.put(p.getUUID(), 6);
        entityData.set(NAME_A, p.getName().getString());
        entityData.set(NAME_B, "Time do Seu Zé");
        entityData.set(COLOR_A, 0xF0D020);
        entityData.set(COLOR_B, 0x6D4C2F);
        entityData.set(SCORE_A, 0);
        entityData.set(SCORE_B, 0);
        phase = 0;
        clock = 0;
        kickoffLock = -1;
        goalPause = 0;
        restartKind = -1;
        keeperHold = null;
        loseOwner();
        bets.clear();
        rebench(sl);
        giveShirt(p, TEAM_A);
        spawnFans(sl);
        Sports.sendState(p, Sports.FOOTBALL, false, 6);
        narrator.challengeStart(p.getName().getString(), bet);
        return true;
    }

    /** Marca do pênalti do gol que a pessoa ataca (cobranças 1 a 5). */
    public Vec3 challengeSpot() {
        return fieldPoint(length - 5, 0);
    }

    private void tickChallenge(ServerLevel sl) {
        ServerPlayer p = chPlayer == null ? null : sl.getServer().getPlayerList().getPlayer(chPlayer);
        if (p == null || !p.isAlive() || p.level() != sl || p.distanceTo(this) > 120) {
            narrator.line("O desafio de pênaltis acabou: o desafiante sumiu. A aposta fica com o Seu Zé.");
            endChallenge(sl);
            return;
        }
        chTimer++;
        if (tickCount % 10 == 0) {
            entityData.set(INFO, chState == 0 ? "Desafio de pênaltis: venha para o campo"
                    : chRound < 5 ? "Pênaltis: você bate " + (chRound + 1) + "/5" : "Pênaltis: você defende " + (Math.min(chRound, 9) - 4) + "/5");
        }
        switch (chState) {
            case 0 -> {
                Vec3 spot = challengeSpot();
                double d = p.position().distanceTo(spot);
                if (d < 14) {
                    setupKick(sl, p);
                } else if (chTimer > 20 * 150) {
                    com.boombox.WorldStats.giveEmeralds(p, chBet);
                    p.sendSystemMessage(Component.literal("§7Você não veio para o campo: o Seu Zé devolveu a aposta."));
                    endChallenge(sl);
                } else if (chTimer % 40 == 1) {
                    p.sendOverlayMessage(Component.literal("§eDesafio de pênaltis: vá até o campo de futebol (" + (int) d + " blocos)."));
                }
            }
            case 1 -> {
                if (restartKind < 0) {
                    chState = 2;
                    chKickTick = tickCount;
                    chKicker = lastKicker;
                    return;
                }
                setPos(restartSpot.x, center.y, restartSpot.z);
                setDeltaMovement(0, getDeltaMovement().y, 0);
                restartTimer--;
                if (chRound < 5 && restartTimer < -20 * 30) challengeResult(sl, p, false);
                else if (chRound >= 5 && restartTimer < -20 * 15) challengeResult(sl, p, false);
            }
            case 2 -> {
                int target = chRound < 5 ? 1 : 0;
                Vec3 pos = position();
                double gu = u(pos);
                boolean net = Blocks34.isGoal(sl.getBlockState(blockPosition())) || Blocks34.isGoal(sl.getBlockState(blockPosition().above()));
                boolean mouth = Math.abs(v(pos)) < goalHalf - 0.1 && pos.y - center.y < goalHeight - 0.1 && (target == 0 ? gu < 0.3 : gu > length - 0.3);
                if ((net && (gu < length / 2 ? 0 : 1) == target) || mouth) {
                    challengeResult(sl, p, true);
                    return;
                }
                keeperSaves(sl);
                boolean touched = owner != null || keeperHold != null || (lastKicker != null && !lastKicker.equals(chKicker));
                boolean out = gu < -0.5 || gu > length + 0.5 || Math.abs(v(pos)) > halfWidth + 0.3;
                boolean dead = tickCount - chKickTick > 15 && getDeltaMovement().horizontalDistance() < 0.06;
                if (touched || out || dead || tickCount - chKickTick > 70) challengeResult(sl, p, false);
            }
            default -> {
                if (--chPause > 0) return;
                chRound++;
                if (chRound >= 10) finishChallenge(sl, p);
                else setupKick(sl, p);
            }
        }
    }

    /** Bola na marca: a pessoa bate (cobranças 1-5, no gol do outro lado) ou defende (6-10, no gol de cá). */
    private void setupKick(ServerLevel sl, ServerPlayer p) {
        chState = 1;
        boolean kicks = chRound < 5;
        humanPos.put(chPlayer, kicks ? 6 : 0);
        rebench(sl);
        Sports.sendState(p, Sports.FOOTBALL, false, kicks ? 6 : 0);
        Vec3 g1 = goal(1), g0 = goal(0);
        if (kicks) {
            setRestart(sl, PENALTY, TEAM_A, challengeSpot(), true);
            Vec3 at = fieldPoint(length - 7, 0);
            float yaw = (float) (Math.toDegrees(Math.atan2(g1.z - at.z, g1.x - at.x)) - 90);
            p.teleportTo(sl, at.x, center.y, at.z, java.util.Set.of(), yaw, 0f, true);
            p.sendOverlayMessage(Component.literal("§eCobrança " + (chRound + 1) + "/5: chegue na bola e chute (segure o botão para mais força)."));
        } else {
            setRestart(sl, PENALTY, TEAM_B, fieldPoint(5, 0), true);
            restartTimer = 120;
            Vec3 at = fieldPoint(0.7, 0);
            float yaw = (float) (Math.toDegrees(Math.atan2(g1.z - g0.z, g1.x - g0.x)) - 90);
            p.teleportTo(sl, at.x, center.y, at.z, java.util.Set.of(), yaw, 0f, true);
            p.sendOverlayMessage(Component.literal("§bVocê no gol! Defesa " + (chRound - 4) + "/5: use a tecla do pulo do goleiro na direção da bola."));
        }
        p.setDeltaMovement(Vec3.ZERO);
    }

    private void challengeResult(ServerLevel sl, ServerPlayer p, boolean goal) {
        boolean kicks = chRound < 5;
        if (goal) {
            if (kicks) entityData.set(SCORE_A, scoreA() + 1);
            else entityData.set(SCORE_B, scoreB() + 1);
            sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 1, getZ(), 30, 0.5, 0.5, 0.5, 0.1);
            sl.playSound(null, blockPosition(), kicks ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1.2f);
        }
        narrator.challengeKick(p.getName().getString(), kicks, goal);
        p.sendOverlayMessage(Component.literal("§f" + p.getName().getString() + " §e" + scoreA() + " x " + scoreB() + " §fTime do Seu Zé"));
        chState = 3;
        chPause = 50;
        restartKind = -1;
        diving.clear();
    }

    private void finishChallenge(ServerLevel sl, ServerPlayer p) {
        int a = scoreA(), b = scoreB();
        String who = p.getName().getString();
        if (a > b) {
            com.boombox.WorldStats.giveEmeralds(p, chBet * 2);
            narrator.line(who + " venceu o desafio por " + a + " a " + b + " e levou " + (chBet * 2) + " esmeraldas do Seu Zé! Que craque!");
            sl.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 2, p.getZ(), 60, 1, 1, 1, 0.15);
        } else if (a == b) {
            com.boombox.WorldStats.giveEmeralds(p, chBet);
            narrator.line("Empate em " + a + " a " + b + "! Ninguém ganha: a aposta de " + who + " volta pro bolso.");
        } else {
            narrator.line("O time do Seu Zé venceu por " + b + " a " + a + ". A aposta ficou no bar! Volta outro dia, " + who + "!");
        }
        endChallenge(sl);
    }

    private void endChallenge(ServerLevel sl) {
        for (UUID id : new ArrayList<>(humans.keySet())) {
            if (sl.getEntity(id) instanceof ServerPlayer hp) {
                Sports.sendState(hp, Sports.NONE);
                ItemStack chest = hp.getItemBySlot(EquipmentSlot.CHEST);
                if (chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME) && chest.getHoverName().getString().startsWith("Camisa do")) {
                    hp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                }
            }
        }
        humans.clear();
        humanPos.clear();
        chPlayer = null;
        chState = 0;
        diving.clear();
        mode = AUTO;
        startMatch(sl);
    }

    /** Só para o teste: estado do desafio. */
    public String challengeInfo() {
        return "rodada=" + chRound + " estado=" + chState + " placar=" + scoreA() + "x" + scoreB();
    }

    public int challengeRound() {
        return chRound;
    }

    public int challengeState() {
        return chState;
    }

    // ------------------------------------------------------------ 3.9: craque da partida
    private void addCraque(Entity e, int pts) {
        if (e == null || mode == AUTO) return;
        craque.merge(e.getUUID(), pts, Integer::sum);
        craqueName.put(e.getUUID(), nameOf(e));
    }

    /** Fim do jogo: o narrador elege o craque (quem mais fez gol, defesa e chute ao gol); pessoa ganha a medalha. */
    private void chooseCraque(ServerLevel sl) {
        UUID best = null;
        int bp = 0;
        for (var e : craque.entrySet()) {
            // empate: pessoa ganha do aldeão
            boolean human = sl.getEntity(e.getKey()) instanceof ServerPlayer;
            int pts = e.getValue() * 2 + (human ? 1 : 0);
            if (pts > bp) { bp = pts; best = e.getKey(); }
        }
        if (best == null) return;
        String name = craqueName.getOrDefault(best, "alguém");
        narrator.line("E o craque da partida é... " + name + "! Medalha no peito e foto no jornal da vila!");
        if (sl.getEntity(best) instanceof ServerPlayer p) {
            ItemStack medal = new ItemStack(com.boombox.bar.Blocks39.MEDALHA_CRAQUE);
            medal.set(DataComponents.CUSTOM_NAME, Component.literal("§6Medalha de Craque §7(" + teamName(teamOf(p) < 0 ? TEAM_A : teamOf(p)) + ")"));
            if (!p.getInventory().add(medal)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), medal);
            Achievements.award(p, "craque_da_partida");
            p.sendSystemMessage(Component.literal("§6★ Você foi o craque da partida e ganhou a Medalha de Craque!"));
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 1, 0.5, 0.3);
        }
    }

    /** Para o teste: quem está na frente na disputa de craque. */
    public String craqueInfo() {
        return craqueName.toString() + " " + craque;
    }

    // ------------------------------------------------------------ 3.9: gol de placa
    /**
     * Gol de pessoa de muito longe (20 blocos ou mais) ou de voleio (chutou no ar): vira uma placa na beira do campo
     * com o nome, como foi e o dia.
     */
    private void golDePlaca(ServerLevel sl, ServerPlayer p, int which) {
        if (lastKickPos == null || tickCount - lastKickTick > 120) return;
        double dist = lastKickPos.distanceTo(goal(which));
        boolean longe = dist >= 20, voleio = lastKickAir;
        if (!longe && !voleio) return;
        String como = longe ? "de " + (int) Math.round(dist) + " blocos" : "de voleio";
        long day = sl.getOverworldClockTime() / 24000 + 1;
        announce("§6§l★ GOL DE PLACA! §r§f" + p.getName().getString() + " marcou " + como + ". Esse vai para a placa do campo!");
        narrator.line("Isso é GOL DE PLACA! " + p.getName().getString() + " marcou " + como + "! Vão eternizar esse lance no muro!");
        Achievements.award(p, "gol_de_placa");
        // placas lado a lado na faixa entre a linha lateral e o muro, do lado do organizador
        for (int k = 0; k < 10; k++) {
            double uu = 6 + k * 4;
            if (uu > length - 4) break;
            Vec3 at = fieldPoint(uu, -(halfWidth + 1.0));
            BlockPos bp = BlockPos.containing(at.x, center.y + 0.1, at.z);
            if (!sl.getBlockState(bp).isAir() || sl.getBlockState(bp.below()).isAir()) continue;
            // de frente para o campo
            Vec3 to = fieldPoint(uu, 0);
            float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - at.x), to.z - at.z));
            int rot = Math.floorMod(Math.round(yaw / 22.5f) + 8, 16);
            sl.setBlock(bp, net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, rot), 3);
            if (sl.getBlockEntity(bp) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                List<Component> lines = List.of(Component.literal("★ GOL DE PLACA ★"), Component.literal(p.getName().getString()),
                        Component.literal(como), Component.literal("dia " + day));
                var text = new net.minecraft.world.level.block.entity.SignText(lines, lines, net.minecraft.world.item.DyeColor.YELLOW, true);
                sign.setText(text, net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
                sign.setText(text, net.minecraft.world.level.block.entity.SignTextSlot.BACK);
                sign.setWaxed(true);
                sign.setChanged();
                sl.sendBlockUpdated(bp, sl.getBlockState(bp), sl.getBlockState(bp), 3);
            }
            golsDePlaca++;
            break;
        }
    }

    /** Quantos gols de placa viraram placa (para o teste). */
    public int golsDePlaca;

    // ------------------------------------------------------------ 3.9: clássico
    private static final String[] TAUNTS = {
            "Ô, {o}, que fase! Vem pra cá aprender a jogar bola!", "Pode chorar, {o}! Hoje a vila é nossa!",
            "Silêncio aí, {o}! Cadê aquela torcida barulhenta?", "É freguês! É freguês! A {o} é nossa freguesa!",
            "Liga pra mãe, {o}, que hoje não vai dar!", "A {m} manda aqui! {o}, volta pra casa!"};

    /** Gol no clássico: a torcida de quem marcou provoca a outra vila. */
    private void classicoTaunt(ServerLevel sl, int scorer) {
        var mine = scorer == TEAM_A ? homeVillage : awayVillage;
        var other = scorer == TEAM_A ? awayVillage : homeVillage;
        String t = TAUNTS[random.nextInt(TAUNTS.length)].replace("{o}", other.name()).replace("{m}", mine.name());
        for (var p : sl.players()) if (p.distanceTo(this) < 64) p.sendSystemMessage(Component.literal("§7📣 Torcida da " + mine.name() + ": §f" + t));
    }

    public boolean classicoRunning() {
        return classico && mode != AUTO;
    }

    // ------------------------------------------------------------ 3.9: jogo marcado (final no estádio, Copa das Vilas)
    /** Começa o jogo marcado para este campo: quem está na lista e chegou entra no time A. */
    public void startBooked(ServerLevel sl, com.boombox.server.CopaVilas.Booking b, List<ServerPlayer> here) {
        humans.clear();
        humanPos.clear();
        playedFor.clear();
        loseOwner();
        bets.clear();
        for (ServerPlayer p : here) {
            humans.put(p.getUUID(), TEAM_A);
            humanPos.put(p.getUUID(), b.positionOf(p.getUUID()));
            Sports.sendState(p, Sports.FOOTBALL, true, humanPos.get(p.getUUID()));
        }
        mode = LEAGUE;
        cup = b.cup;
        copaKind = b.kind;
        booking = b;
        classico = b.classico;
        homeVillage = b.home;
        awayVillage = b.away;
        movedAway = false;
        startMatch(sl);
    }

    /** Quem jogou pelo time A (e a posição), para levar o jogo para outro campo. */
    private Map<UUID, Integer> rosterPositions() {
        Map<UUID, Integer> r = new java.util.LinkedHashMap<>();
        for (var e : humans.entrySet()) if (e.getValue() == TEAM_A) r.put(e.getKey(), humanPos.getOrDefault(e.getKey(), 6));
        for (UUID id : playedFor.keySet()) r.putIfAbsent(id, 6);
        return r;
    }

    public boolean bookedRunning() {
        return booking != null && mode == LEAGUE;
    }

    // ------------------------------------------------------------ 3.9: treino de cobrança
    public boolean trainingRunning() {
        return mode == TRAINING;
    }

    private static final String[] TARGET_NAMES = {"ângulo de cima à esquerda", "ângulo de cima à direita", "canto de baixo à esquerda", "canto de baixo à direita"};

    public void startTraining(ServerLevel sl, ServerPlayer p) {
        humans.clear();
        humanPos.clear();
        loseOwner();
        mode = TRAINING;
        trPlayer = p.getUUID();
        trShot = 0;
        trState = 0;
        trTimer = 0;
        trPoints = 0;
        humans.put(p.getUUID(), TEAM_A);
        humanPos.put(p.getUUID(), 6);
        entityData.set(NAME_A, p.getName().getString());
        entityData.set(NAME_B, "Alvos");
        entityData.set(SCORE_A, 0);
        entityData.set(SCORE_B, 0);
        phase = 0;
        clock = 0;
        restartKind = -1;
        keeperHold = null;
        rebench(sl);
        giveShirt(p, TEAM_A);
        spawnFans(sl);
        Sports.sendState(p, Sports.FOOTBALL, false, 6);
        p.sendSystemMessage(Component.literal("§b◎ Treino de cobrança! §f10 chutes de lugares diferentes. Acertar o alvo aceso vale §e3§f, "
                + "outro canto §e2§f e o meio do gol §e1§f. Sem goleiro: capricha na mira!"));
        setupShot(sl, p);
    }

    /** Lugar de cada chute do treino (distância do gol e lado). */
    private static final double[][] SHOTS = {{5, 0}, {9, 0}, {9, 4}, {9, -4}, {14, 0}, {14, 6}, {14, -6}, {18, 2}, {18, -2}, {22, 0}};

    private void setupShot(ServerLevel sl, ServerPlayer p) {
        trState = 1;
        trTarget = random.nextInt(4);
        double[] s = SHOTS[Math.min(trShot, SHOTS.length - 1)];
        Vec3 spot = fieldPoint(length - s[0], s[1]);
        setRestart(sl, FREE_KICK, TEAM_A, spot, true);
        wall.clear();
        Vec3 g1 = goal(1);
        Vec3 at = spot.add(new Vec3(spot.x - g1.x, 0, spot.z - g1.z).normalize().scale(2));
        float yaw = (float) (Math.toDegrees(Math.atan2(g1.z - at.z, g1.x - at.x)) - 90);
        p.teleportTo(sl, at.x, center.y, at.z, java.util.Set.of(), yaw, 0f, true);
        p.setDeltaMovement(Vec3.ZERO);
        p.sendOverlayMessage(Component.literal("§bChute " + (trShot + 1) + "/" + TRAINING_SHOTS + ": §emire no " + TARGET_NAMES[trTarget] + " §7(segure para mais força)"));
    }

    /** Centro do alvo i na boca do gol que a pessoa ataca (gol 1). */
    public Vec3 targetPoint(int i) {
        double lat = (goalHalf - 0.7) * (i % 2 == 0 ? 1 : -1);
        double h = i < 2 ? goalHeight - 0.6 : 0.5;
        Vec3 p = fieldPoint(length - 0.2, lat);
        return new Vec3(p.x, center.y + h, p.z);
    }

    private void tickTraining(ServerLevel sl) {
        ServerPlayer p = trPlayer == null ? null : sl.getServer().getPlayerList().getPlayer(trPlayer);
        if (p == null || !p.isAlive() || p.level() != sl || p.distanceTo(this) > 80) {
            endTraining(sl, null);
            return;
        }
        if (tickCount % 10 == 0) entityData.set(INFO, "Treino de cobrança: chute " + Math.min(trShot + 1, TRAINING_SHOTS) + "/" + TRAINING_SHOTS + " • " + trPoints + " pontos");
        // alvos: o aceso pisca em amarelo, os outros em branco
        if (tickCount % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                Vec3 c = targetPoint(i);
                Vec3 sd = side;
                int n = i == trTarget ? 10 : 5;
                for (int k = 0; k < n; k++) {
                    double a = k * Math.PI * 2 / n + tickCount * 0.05;
                    double r = 0.45;
                    sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(i == trTarget ? 0xFFD020 : 0xFFFFFF, i == trTarget ? 1.2f : 0.7f),
                            c.x + sd.x * Math.cos(a) * r, c.y + Math.sin(a) * r, c.z + sd.z * Math.cos(a) * r, 1, 0, 0, 0, 0);
                }
            }
        }
        switch (trState) {
            case 1 -> {
                if (restartKind < 0) {
                    trState = 2;
                    trKickTick = tickCount;
                    trKicker = lastKicker;
                    return;
                }
                setPos(restartSpot.x, center.y, restartSpot.z);
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (--restartTimer < -20 * 40) shotResult(sl, p, -1, false);
            }
            case 2 -> {
                Vec3 pos = position();
                double gu = u(pos);
                boolean in = gu > length - 0.3 && Math.abs(v(pos)) < goalHalf - 0.1 && pos.y - center.y < goalHeight - 0.1;
                boolean net = Blocks34.isGoal(sl.getBlockState(blockPosition())) || Blocks34.isGoal(sl.getBlockState(blockPosition().above()));
                if (in || (net && gu > length / 2)) {
                    // qual alvo: o canto mais perto de onde a bola entrou
                    int best = -1;
                    double bd = 0.95;
                    for (int i = 0; i < 4; i++) {
                        Vec3 t = targetPoint(i);
                        double d = Math.hypot(v(pos) - v(t), pos.y - t.y);
                        if (d < bd) { bd = d; best = i; }
                    }
                    shotResult(sl, p, best, true);
                    return;
                }
                boolean out = gu > length + 0.5 || gu < -0.5 || Math.abs(v(pos)) > halfWidth + 0.3;
                boolean dead = tickCount - trKickTick > 15 && getDeltaMovement().horizontalDistance() < 0.05;
                if (owner != null || out || dead || tickCount - trKickTick > 80) shotResult(sl, p, -1, false);
            }
            case 3 -> {
                if (--trPause > 0) return;
                trShot++;
                if (trShot >= TRAINING_SHOTS) endTraining(sl, p);
                else setupShot(sl, p);
            }
            default -> {
            }
        }
    }

    private void shotResult(ServerLevel sl, ServerPlayer p, int target, boolean goal) {
        int pts = !goal ? 0 : target == trTarget ? 3 : target >= 0 ? 2 : 1;
        trPoints += pts;
        entityData.set(SCORE_A, trPoints);
        String msg = !goal ? "§cPra fora! §70 pontos" : target == trTarget ? "§a§lNO ALVO! §r§e+3" : target >= 0 ? "§eNo canto! +2" : "§fGol no meio. +1";
        p.sendOverlayMessage(Component.literal(msg + " §7(total " + trPoints + ")"));
        if (pts == 3) {
            Vec3 t = targetPoint(trTarget);
            sl.sendParticles(ParticleTypes.FIREWORK, t.x, t.y, t.z, 30, 0.3, 0.3, 0.3, 0.1);
            sl.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, 1.4f);
        } else if (goal) {
            sl.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1f, 1.2f);
        }
        trState = 3;
        trPause = 40;
        restartKind = -1;
        loseOwner();
    }

    private void endTraining(ServerLevel sl, ServerPlayer p) {
        if (p != null) {
            int best = com.boombox.server.CopaVilas.get(sl.getServer()).trainingRecord(p.getName().getString(), trPoints);
            boolean record = trPoints >= best && trPoints > 0;
            p.sendSystemMessage(Component.literal("§b◎ Fim do treino: §e" + trPoints + " pontos §7de 30." + (record ? " §a§lNovo recorde seu!" : " §7Seu recorde: " + best + ".")));
            narrator.line(trPoints >= 24 ? "Que pontaria! " + p.getName().getString() + " fez " + trPoints + " pontos no treino. Tá pronto pra seleção!"
                    : trPoints >= 12 ? p.getName().getString() + " fez " + trPoints + " pontos no treino. Tá melhorando!"
                    : p.getName().getString() + " fez " + trPoints + " pontos. Treino é treino, jogo é jogo!");
            lastTraining = trPoints;
        }
        for (UUID id : new ArrayList<>(humans.keySet())) {
            if (sl.getEntity(id) instanceof ServerPlayer hp) {
                Sports.sendState(hp, Sports.NONE);
                ItemStack chest = hp.getItemBySlot(EquipmentSlot.CHEST);
                if (chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME) && chest.getHoverName().getString().startsWith("Camisa do")) {
                    hp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                }
            }
        }
        humans.clear();
        humanPos.clear();
        trPlayer = null;
        trState = 0;
        restartKind = -1;
        mode = AUTO;
        startMatch(sl);
    }

    /** Pontos do último treino (para o teste) e estado do treino. */
    public int lastTraining = -1;

    public String trainingInfo() {
        return "chute=" + trShot + " estado=" + trState + " alvo=" + trTarget + " pontos=" + trPoints;
    }

    public int trainingTarget() {
        return trTarget;
    }

    public int trainingState() {
        return trState;
    }

    private void findField(ServerLevel sl) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(blockPosition().offset(-40, -3, -40), blockPosition().offset(40, 4, 40))) {
            if (Blocks34.isGoal(sl.getBlockState(p))) found.add(p.immutable());
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
        Vec3 ga0 = new Vec3(ax / na, y, az / na), gb = new Vec3(bx / nb, y, bz / nb);
        // tamanho da boca do gol: colunas e linhas de blocos de um dos gols
        java.util.Set<Long> cols = new java.util.HashSet<>();
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos p : found) {
            if (p.distSqr(a) >= p.distSqr(far)) continue;
            cols.add(BlockPos.asLong(p.getX(), 0, p.getZ()));
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
        }
        goalHalf = cols.size() / 2.0;
        goalHeight = maxY - minY + 1;
        var teamA = sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(50), r -> r.role() == RaverRoles.TEAM_A);
        double da = 0, db = 0;
        for (Raver r : teamA) { da += r.position().distanceTo(ga0); db += r.position().distanceTo(gb); }
        if (da <= db) { goal0 = ga0; goal1 = gb; } else { goal0 = gb; goal1 = ga0; }
        Vec3 d = goal1.subtract(goal0);
        length = d.horizontalDistance();
        axis = new Vec3(d.x, 0, d.z).normalize();
        side = new Vec3(-axis.z, 0, axis.x);
        center = goal0.add(axis.scale(length / 2));
        halfWidth = 8;
        // procura a linha lateral de fora para dentro (de dentro para fora achava o círculo do meio de campo)
        for (int k = 24; k >= 5; k--) {
            BlockPos q = BlockPos.containing(center.add(side.scale(k))).below();
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(sl.getBlockState(q).getBlock()).getPath().equals("white_concrete_powder")) { halfWidth = k; break; }
        }
        clearTrees(sl);
        startMatch(sl);
    }

    /** Ponto dentro do campo (com folga de "margin" blocos para fora das linhas)? */
    public boolean inArea(Vec3 p, double margin) {
        if (!hasGoals()) return false;
        double uu = u(p), vv = v(p);
        return uu >= -2 - margin && uu <= length + 2 + margin && Math.abs(vv) <= halfWidth + 1.5 + margin && Math.abs(p.y - center.y) < 8 + margin;
    }

    /**
     * 3.5.1: ninguém de fora entra no campo. Aldeões, bichos e foliões que não estão no jogo vão para fora do muro;
     * monstro que nascer dentro some. Ficam os jogadores, o organizador e quem está assistindo de longe.
     */
    private void guard(ServerLevel sl) {
        AABB box = new AABB(center, center).inflate(length / 2 + 3, 6, length / 2 + 3);
        for (Entity e : sl.getEntities(this, box, e -> e instanceof net.minecraft.world.entity.LivingEntity && !(e instanceof Player))) {
            if (e instanceof Raver r && (r.role() == RaverRoles.TEAM_A || r.role() == RaverRoles.TEAM_B || r.role() == RaverRoles.ORG_FUT
                    || r.role() == RaverRoles.NARRADOR || r.role() == RaverRoles.TORCIDA)) continue;
            if (!inArea(e.position(), 0)) continue;
            double vv = v(e.position());
            Vec3 out = fieldPoint(Math.max(0, Math.min(length, u(e.position()))), Math.signum(vv == 0 ? 1 : vv) * (halfWidth + 5));
            Sports.evict(sl, e, out);
        }
    }

    /** Campo sem organizador (os da 3.3/3.4): chama um, na beira do campo perto do meio. */
    private void ensureOrganizer(ServerLevel sl) {
        if (sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(50), r -> r.role() == RaverRoles.NARRADOR).isEmpty()) {
            // 3.8: o narrador fica do outro lado do meio de campo, com o microfone
            Raver n = ModEntities.RAVER.create(sl, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (n != null) {
                Vec3 at = fieldPoint(length / 2 - 3, -(halfWidth + 1.2));
                n.snapTo(at.x, center.y, at.z, 0, 0);
                n.setRole(RaverRoles.NARRADOR);
                sl.addFreshEntity(n);
            }
        }
        if (!sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(50), r -> r.role() == RaverRoles.ORG_FUT).isEmpty()) return;
        Raver o = ModEntities.RAVER.create(sl, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (o == null) return;
        Vec3 at = fieldPoint(length / 2 + 3, -(halfWidth + 1.2));
        o.snapTo(at.x, center.y, at.z, 0, 0);
        o.setRole(RaverRoles.ORG_FUT);
        sl.addFreshEntity(o);
    }

    /** Árvore ou mato que nasceu dentro do campo: tira. */
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
                    if (st.isAir() || Blocks34.isGoal(st)) continue;
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

    private void giveShirt(ServerPlayer sp, int team) {
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        boolean ours = chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME) && chest.getHoverName().getString().startsWith("Camisa do");
        if (!chest.isEmpty() && !ours) return;
        ItemStack shirt = new ItemStack(Items.LEATHER_CHESTPLATE);
        shirt.set(DataComponents.DYED_COLOR, new DyedItemColor(teamColor(team)));
        shirt.set(DataComponents.CUSTOM_NAME, Component.literal("Camisa do " + teamName(team)));
        sp.setItemSlot(EquipmentSlot.CHEST, shirt);
    }

    /** Clique direito na bola (fora do jogo): placar e tabela. Para jogar, fale com o organizador. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
        if (street && player instanceof ServerPlayer sp) {
            var g = com.boombox.server.StreetPelada.gameOf(getUUID());
            if (g != null) sp.sendOverlayMessage(Component.literal("§e⚽ Pelada da rua: §f" + com.boombox.server.StreetPelada.info(g) + " §7(chute para entrar)"));
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level() instanceof ServerLevel sl && teamOf(sp) < 0) table(sl, sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (street) {
            if (source.getEntity() instanceof ServerPlayer p) com.boombox.server.StreetPelada.playerKick(p, this);
            return false;
        }
        // quem está jogando usa os botões; quem não está, só dá um toquinho
        if (source.getEntity() instanceof ServerPlayer p && teamOf(p) < 0 && inPlay()) {
            Vec3 look = p.getLookAngle();
            setDeltaMovement(new Vec3(look.x, 0, look.z).normalize().scale(0.5).add(0, 0.15, 0));
        }
        return false;
    }

    public static Vec3 passTo(Vec3 from, Vec3 to) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        double dist = d.length();
        double sp = Math.min(1.15, dist * (1 - ROLL) + 0.12);
        return d.normalize().scale(sp).add(0, 0.04, 0);
    }

    @Override
    public void push(Entity other) {
        // aldeões não empurram a bola (eles tocam "de propósito", pela IA); gente fora do jogo empurra de leve
        if (!(other instanceof Player p) || restarting() || keeperHold != null || owner != null || teamOf(p) >= 0) return;
        if (getDeltaMovement().horizontalDistance() > 0.3) return;
        Vec3 d = new Vec3(getX() - other.getX(), 0, getZ() - other.getZ());
        if (d.lengthSqr() < 1e-4) return;
        Vec3 vel = d.normalize().scale(0.2);
        setDeltaMovement(vel.x, getDeltaMovement().y + 0.02, vel.z);
    }

    @Override
    public boolean isPushable() { return true; }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean canBeCollidedWith(Entity other) { return false; }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(SCORE_A, in.getIntOr("ScoreA", 0));
        entityData.set(SCORE_B, in.getIntOr("ScoreB", 0));
        mode = in.getIntOr("Mode", AUTO);
        // ninguém fica "preso" num jogo depois de sair do mundo: volta a pelada dos aldeões
        mode = AUTO;
        stadium = in.getBooleanOr("Stadium", false);
        street = in.getBooleanOr("Street", false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("ScoreA", scoreA());
        out.putInt("ScoreB", scoreB());
        out.putInt("Mode", mode);
        out.putBoolean("Stadium", stadium);
        out.putBoolean("Street", street);
    }
}
