package com.boombox.entity;

import com.boombox.Achievements;
import com.boombox.bar.BarBlocks;
import com.boombox.bar.Cups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bola do futebol de várzea e o "juiz" da partida. Acha sozinha os dois gols do campo e as linhas, e apita:
 * <ul>
 *     <li><b>Gol</b>: conta no placar e recomeça do meio com saída de quem tomou o gol.</li>
 *     <li><b>Lateral</b>: bola saiu pela linha do lado; recomeça de onde saiu, com o outro time.</li>
 *     <li><b>Tiro de meta / escanteio</b>: saiu pela linha de fundo; quem chutou por último define qual dos dois.</li>
 *     <li><b>Falta</b>: carrinho na disputa de bola (ou bater num jogador); tiro livre de onde foi.</li>
 * </ul>
 * Time A (azul) defende o gol 0 e ataca o gol 1; time B (vermelho) o contrário. Jogador entra num time clicando com o
 * botão direito na bola (azul → vermelho → fora) e ganha a camisa do time.
 */
public class Ball extends Entity {
    public static final int TEAM_A = 0, TEAM_B = 1;
    public static final String[] TEAM_NAMES = {"Azul", "Vermelho"};
    public static final int[] TEAM_COLORS = {0x2D5BE0, 0xE03030};
    public static final int KICKOFF = 0, THROW_IN = 1, GOAL_KICK = 2, CORNER = 3, FREE_KICK = 4;
    private static final String[] RESTART_NAMES = {"Saída de bola", "Lateral", "Tiro de meta", "Escanteio", "Falta"};

    private static final EntityDataAccessor<Integer> SCORE_A = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCORE_B = SynchedEntityData.defineId(Ball.class, EntityDataSerializers.INT);
    /** Centros dos dois gols (0 = do time A, 1 = do time B); null = ainda não achou. */
    private Vec3 goal0, goal1;
    /** Eixo do campo (do gol 0 para o 1), eixo lateral, comprimento e meia largura (até as linhas laterais). */
    private Vec3 axis, side;
    private double length, halfWidth = 8;
    private Vec3 center;
    private UUID lastKicker;
    private int lastTeam = -1;
    /** Bola parada (reinício): tipo, time que cobra, lugar e ticks até o aldeão cobrar. */
    private int restartKind = -1, restartTeam, restartTimer;
    private Vec3 restartSpot;
    private int goalPause;
    /** Jogadores (gente) em cada time. */
    private final Map<UUID, Integer> humans = new HashMap<>();

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

    /** Centro do gol do time (0 = gol do time A, 1 = gol do time B). */
    public Vec3 goal(int i) {
        return i == 0 ? goal0 : goal1;
    }

    public Vec3 center() {
        return center;
    }

    public boolean restarting() {
        return restartKind >= 0 || goalPause > 0;
    }

    public int restartTeam() {
        return restartTeam;
    }

    public Vec3 restartSpot() {
        return restartSpot;
    }

    /** Teste/comando: bola rolando (sem bola parada). */
    public void forcePlay() {
        restartKind = -1;
        goalPause = 0;
    }

    public boolean restartTimerDone() {
        return restartTimer <= 0;
    }

    public int restartKind() {
        return restartKind;
    }

    /** Pode chutar agora? (na bola parada só o time que cobra) */
    public boolean canKick(int team) {
        if (goalPause > 0) return false;
        return restartKind < 0 || team == restartTeam;
    }

    public int teamOf(Entity e) {
        if (e instanceof Raver r) return r.role() == RaverRoles.TEAM_A ? TEAM_A : r.role() == RaverRoles.TEAM_B ? TEAM_B : -1;
        Integer t = humans.get(e.getUUID());
        return t == null ? -1 : t;
    }

    /** Coordenadas do campo: u ao longo (0 no gol 0, length no gol 1), v para o lado (0 no meio). */
    public double u(Vec3 p) {
        return p.subtract(goal0).dot(axis);
    }

    public double v(Vec3 p) {
        return p.subtract(center).dot(side);
    }

    public Vec3 fieldPoint(double u, double v) {
        return goal0.add(axis.scale(u)).add(side.scale(v)).with(net.minecraft.core.Direction.Axis.Y, getY());
    }

    /** Chute (de aldeão ou jogador). */
    public void kick(Entity who, Vec3 vel) {
        int team = teamOf(who);
        if (!canKick(team)) return;
        if (restartKind >= 0) {
            restartKind = -1; // cobrou: bola rolando
            announce("§7(bola rolando)", false);
        }
        setDeltaMovement(vel);
        lastKicker = who.getUUID();
        lastTeam = team;
        playSound(SoundEvents.WOOL_HIT, 1f, 0.7f + random.nextFloat() * 0.2f);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement().add(0, -0.04, 0);
        Vec3 before = v;
        move(MoverType.SELF, v);
        v = getDeltaMovement();
        if (horizontalCollision) {
            if (Math.abs(getX() - xo) < Math.abs(before.x) * 0.5) v = new Vec3(-before.x * 0.55, v.y, v.z);
            if (Math.abs(getZ() - zo) < Math.abs(before.z) * 0.5) v = new Vec3(v.x, v.y, -before.z * 0.55);
        }
        if (onGround()) v = new Vec3(v.x * 0.935, before.y < -0.12 ? -before.y * 0.45 : 0, v.z * 0.935);
        else v = v.scale(0.985);
        if (v.horizontalDistanceSqr() < 1e-5) v = new Vec3(0, v.y, 0);
        setDeltaMovement(v);
        if (!(level() instanceof ServerLevel sl)) return;
        if (!hasGoals()) {
            if (tickCount % 20 == 1) findField(sl);
            return;
        }
        // placar na tela de quem está jogando
        if (tickCount % 40 == 0) {
            String placar = "§9Azul " + scoreA() + " §fx §c" + scoreB() + " Vermelho";
            for (var e : humans.entrySet()) {
                if (sl.getEntity(e.getKey()) instanceof ServerPlayer p && p.distanceTo(this) < 48) {
                    p.sendOverlayMessage(Component.literal(placar + (e.getValue() == TEAM_A ? "  §9(você: Azul)" : "  §c(você: Vermelho)")));
                }
            }
        }
        if (goalPause > 0) {
            if (--goalPause == 0) {
                // depois do gol: bola no meio, saída de quem tomou
                setRestart(sl, KICKOFF, lastTeam == TEAM_A ? TEAM_B : TEAM_A, center, false);
            }
            return;
        }
        if (restartKind >= 0) {
            // bola parada no lugar da cobrança
            setPos(restartSpot.x, getY(), restartSpot.z);
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (restartTimer > 0) restartTimer--;
            return;
        }
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
            int end = u < 0 ? 0 : 1;                 // linha de fundo do gol "end" (que é do time end)
            int defender = end == 0 ? TEAM_A : TEAM_B;
            double su = end == 0 ? 0.8 : length - 0.8;
            if (lastTeam == defender) {
                // o defensor mandou para fora: escanteio para o atacante
                setRestart(sl, CORNER, 1 - defender, fieldPoint(su, Math.signum(w == 0 ? 1 : w) * (halfWidth - 0.3)), true);
            } else {
                setRestart(sl, GOAL_KICK, defender, fieldPoint(end == 0 ? 2.5 : length - 2.5, Math.signum(w == 0 ? 1 : w) * 2.5), true);
            }
        }
    }

    /** Apita uma bola parada. */
    public void setRestart(ServerLevel sl, int kind, int team, Vec3 spot, boolean whistle) {
        restartKind = kind;
        restartTeam = team;
        restartSpot = spot;
        restartTimer = kind == KICKOFF ? 80 : 50;
        setPos(spot.x, spot.y + 0.3, spot.z);
        setDeltaMovement(Vec3.ZERO);
        if (whistle) sl.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 1.2f, 1.9f);
        String who = TEAM_NAMES[team];
        String msg = switch (kind) {
            case THROW_IN -> "§ePriii! Saiu pela lateral. §fLateral do time " + who + ".";
            case GOAL_KICK -> "§ePriii! Saiu pela linha de fundo. §fTiro de meta do time " + who + ".";
            case CORNER -> "§ePriii! Escanteio para o time " + who + "!";
            case FREE_KICK -> "§c§lFALTA! §fTiro livre para o time " + who + ".";
            default -> "§fSaída de bola do time " + who + ".";
        };
        announce(msg, true);
    }

    /** Falta (carrinho na disputa ou pancada num jogador): tiro livre do time que sofreu, de onde a bola está. */
    public void foul(int fouledTeam) {
        if (!(level() instanceof ServerLevel sl) || restarting() || !hasGoals()) return;
        Vec3 p = position();
        setRestart(sl, FREE_KICK, fouledTeam, fieldPoint(Math.max(1, Math.min(length - 1, u(p))), Math.max(-halfWidth + 0.5, Math.min(halfWidth - 0.5, v(p)))), true);
    }

    private void announce(String msg, boolean withScore) {
        if (!(level() instanceof ServerLevel sl)) return;
        String placar = "§9Azul " + scoreA() + " §fx §c" + scoreB() + " Vermelho";
        for (var p : sl.players()) {
            if (p.distanceTo(this) < 48) {
                p.sendSystemMessage(Component.literal(msg));
                if (withScore) p.sendOverlayMessage(Component.literal(placar));
            }
        }
    }

    /** Gol no gol "which" (0 = gol do time A): marca o time que ataca esse gol. */
    private void goal(ServerLevel sl, int which) {
        int scorer = which == 0 ? TEAM_B : TEAM_A;
        if (scorer == TEAM_A) entityData.set(SCORE_A, scoreA() + 1);
        else entityData.set(SCORE_B, scoreB() + 1);
        lastTeam = scorer;
        goalPause = 70;
        sl.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1f, 1.2f);
        sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 1, getZ(), 50, 0.6, 0.6, 0.6, 0.12);
        String who = "";
        if (lastKicker != null && sl.getEntity(lastKicker) instanceof ServerPlayer p) {
            who = " §e(" + p.getName().getString() + ")";
            Achievements.award(p, "gol_na_varzea");
            Cups.give(p, Cups.VARZEA);
        }
        announce("§a§lGOOOOOL do time " + TEAM_NAMES[scorer] + "!" + who, true);
        for (Raver r : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(48), r -> teamOf(r) == scorer)) r.celebrate(true);
    }

    /** Procura os blocos de gol e as linhas: monta as coordenadas do campo. */
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
        // gol 0 = o do time A: o mais perto dos boleiros azuis
        var teamA = sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(40), r -> r.role() == RaverRoles.TEAM_A);
        double da = 0, db = 0;
        for (Raver r : teamA) { da += r.position().distanceTo(ga); db += r.position().distanceTo(gb); }
        if (da <= db) { goal0 = ga; goal1 = gb; } else { goal0 = gb; goal1 = ga; }
        Vec3 d = goal1.subtract(goal0);
        length = d.horizontalDistance();
        axis = new Vec3(d.x, 0, d.z).normalize();
        side = new Vec3(-axis.z, 0, axis.x);
        center = goal0.add(axis.scale(length / 2));
        // meia largura: até a linha lateral pintada (pó de concreto branco) no chão
        halfWidth = 8;
        for (int k = 3; k < 20; k++) {
            BlockPos q = BlockPos.containing(center.add(side.scale(k))).below();
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(sl.getBlockState(q).getBlock()).getPath().equals("white_concrete_powder")) { halfWidth = k; break; }
        }
        setRestart(sl, KICKOFF, TEAM_A, center, false);
    }

    // ------------------------------------------------------------ jogadores de verdade
    /** Botão direito na bola: entra no time azul, depois no vermelho, depois sai. Ganha a camisa do time. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        Integer t = humans.get(sp.getUUID());
        int next = t == null ? TEAM_A : t == TEAM_A ? TEAM_B : -1;
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        boolean ourShirt = chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME)
                && chest.getHoverName().getString().startsWith("Camisa do Time");
        if (ourShirt) sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        if (next < 0) {
            humans.remove(sp.getUUID());
            sp.sendSystemMessage(Component.literal("§7Você saiu do jogo. Valeu pela pelada!"));
            return InteractionResult.SUCCESS;
        }
        humans.put(sp.getUUID(), next);
        if (sp.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            ItemStack shirt = new ItemStack(Items.LEATHER_CHESTPLATE);
            shirt.set(DataComponents.DYED_COLOR, new DyedItemColor(TEAM_COLORS[next]));
            shirt.set(DataComponents.CUSTOM_NAME, Component.literal("Camisa do Time " + TEAM_NAMES[next]));
            sp.setItemSlot(EquipmentSlot.CHEST, shirt);
        }
        sp.sendSystemMessage(Component.literal((next == TEAM_A ? "§9" : "§c") + "Você entrou no time " + TEAM_NAMES[next]
                + "! §7Bata na bola para chutar (correndo chuta mais forte), encoste nela para conduzir. Clique de novo para trocar de time."));
        playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player p) {
            Vec3 look = p.getLookAngle();
            Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
            kick(p, dir.scale(p.isSprinting() ? 1.0 : 0.7).add(0, 0.12 + Math.max(0, look.y) * 0.55, 0));
        }
        return false;
    }

    @Override
    public void push(Entity other) {
        if (other instanceof Ball || restarting()) return;
        if (getDeltaMovement().horizontalDistance() > 0.25) return; // já está rolando: esbarrão não soma
        Vec3 d = new Vec3(getX() - other.getX(), 0, getZ() - other.getZ());
        if (d.lengthSqr() < 1e-4) return;
        Vec3 vel = d.normalize().scale(0.22 + Math.min(0.2, other.getDeltaMovement().horizontalDistance() * 0.8));
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
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("ScoreA", scoreA());
        out.putInt("ScoreB", scoreB());
    }
}
