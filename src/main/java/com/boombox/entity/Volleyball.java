package com.boombox.entity;

import com.boombox.Achievements;
import com.boombox.bar.Blocks34;
import com.boombox.bar.VolleyNetBlock;
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
import net.minecraft.world.entity.EntitySpawnReason;
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
 * Bola e juiz do vôlei de praia (3.5). A quadra fica vazia até alguém falar com o organizador: aí chegam três aldeões
 * e o jogo é 2 contra 2 (você e um parceiro contra dois). <b>Amistoso</b> (times sorteados) ou <b>campeonato</b> (você
 * escolhe o nome e a cor do seu time; 3 jogos e o troféu para o campeão).
 * <p>
 * Cada lado tem até 3 toques e ninguém toca duas vezes seguidas. Botões: esquerdo = manchete (passa para o parceiro;
 * no 3º toque manda para o outro lado), V = levantar (bola alta perto da rede), direito = cortar (pule e corte para
 * onde estiver olhando). A bola que cai dentro da quadra (a marca encostando na linha vale) é ponto de quem atacou;
 * fora, é ponto do outro time.
 */
public class Volleyball extends Entity {
    private static final double G = 0.028, DRAG = 0.985, RADIUS = 0.25;
    public static final int[] TEAM_COLORS = {0xFF8A1E, 0x1EC8E6};

    private static final EntityDataAccessor<Integer> SCORE0 = SynchedEntityData.defineId(Volleyball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCORE1 = SynchedEntityData.defineId(Volleyball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> NAME0 = SynchedEntityData.defineId(Volleyball.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NAME1 = SynchedEntityData.defineId(Volleyball.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> COLOR0 = SynchedEntityData.defineId(Volleyball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR1 = SynchedEntityData.defineId(Volleyball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> INFO = SynchedEntityData.defineId(Volleyball.class, EntityDataSerializers.STRING);

    private Vec3 netCenter, normal, along;
    private double halfWidth = 3.5, halfLength = 7, groundY, netTop;
    /** Partida rolando? (sem partida a quadra fica vazia, esperando alguém falar com o organizador) */
    private boolean active;
    private int target = 15;
    private int serveSide, serveTimer = 60;
    private final int[] serverIdx = new int[2];
    private boolean serving;
    private UUID server;
    private int pause, touches, touchSide = -1, gameOver;
    private UUID lastToucher;
    private int lastSide = -1;
    private final Map<UUID, Integer> humans = new HashMap<>();
    /** Aldeões que vieram jogar (somem no fim). */
    private final Set<UUID> players = new HashSet<>();
    private final Map<Integer, Integer> missed = new HashMap<>();

    // campeonato
    private boolean league;
    private final String[] teams = {"", "", "", ""};
    private final int[] colors = new int[4], pts = new int[4], wins = new int[4];
    private int round;

    public Volleyball(EntityType<? extends Volleyball> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SCORE0, 0);
        b.define(SCORE1, 0);
        b.define(NAME0, "Sol");
        b.define(NAME1, "Mar");
        b.define(COLOR0, TEAM_COLORS[0]);
        b.define(COLOR1, TEAM_COLORS[1]);
        b.define(INFO, "Fale com o organizador");
    }

    public int score(int side) { return entityData.get(side == 0 ? SCORE0 : SCORE1); }
    public String teamName(int side) { return entityData.get(side == 0 ? NAME0 : NAME1); }
    public int teamColor(int side) { return entityData.get(side == 0 ? COLOR0 : COLOR1); }
    public String info() { return entityData.get(INFO); }
    public boolean hasCourt() { return netCenter != null; }
    public boolean active() { return active; }
    public boolean serving() { return serving; }
    public boolean paused() { return pause > 0 || gameOver > 0; }

    public double d(Vec3 p) { return p.subtract(netCenter).dot(normal); }
    public double w(Vec3 p) { return p.subtract(netCenter).dot(along); }

    public Vec3 courtPoint(double d, double w) {
        return new Vec3(netCenter.x + normal.x * d + along.x * w, groundY, netCenter.z + normal.z * d + along.z * w);
    }

    public int sideOf(Entity e) {
        if (e instanceof Raver r) {
            if (!players.contains(r.getUUID())) return -1;
            return r.role() == RaverRoles.VOLEI_A ? 0 : r.role() == RaverRoles.VOLEI_B ? 1 : -1;
        }
        Integer t = humans.get(e.getUUID());
        return t == null ? -1 : t;
    }

    public String debug() {
        return String.format(java.util.Locale.ROOT, "pos=(%.1f,%.1f,%.1f) vel=(%.2f,%.2f,%.2f) ativo=%s sacando=%s pausa=%d toques=%d lado=%d",
                getX(), getY(), getZ(), getDeltaMovement().x, getDeltaMovement().y, getDeltaMovement().z, active, serving, pause, touches, touchSide);
    }

    // ------------------------------------------------------------ física
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) {
            if (!onGround()) {
                Vec3 v = getDeltaMovement();
                setDeltaMovement(v.x * DRAG, (v.y - G) * DRAG, v.z * DRAG);
                move(MoverType.SELF, getDeltaMovement());
            }
            return;
        }
        if (!hasCourt()) {
            if (tickCount % 20 == 1) findCourt(sl);
            rest();
            return;
        }
        if (tickCount % 20 == 0) {
            ensureOrganizer(sl);
            for (var e : humans.entrySet()) {
                if (sl.getEntity(e.getKey()) instanceof ServerPlayer p && p.distanceTo(this) < 40) {
                    p.sendOverlayMessage(Component.literal("§7Esq: manchete • V: levantar • Dir: cortar  §8|  §f" + teamName(0) + " " + score(0) + " x " + score(1) + " " + teamName(1)));
                }
            }
        }
        if (!active) {
            rest();
            return;
        }
        List<Raver> team = team(sl);
        if (gameOver > 0) {
            if (--gameOver == 0) afterGame(sl);
            idle(team);
            rest();
            return;
        }
        if (pause > 0) {
            if (--pause == 0) startServe(sl, team);
            idle(team);
            rest();
            return;
        }
        if (serving) {
            tickServe(sl, team);
            return;
        }
        Vec3 before = getDeltaMovement();
        Vec3 v = new Vec3(before.x * DRAG, (before.y - G) * DRAG, before.z * DRAG);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        Vec3 after = getDeltaMovement();
        if (horizontalCollision) {
            // bateu na rede: perde a força e cai do mesmo lado
            after = new Vec3(-v.x * 0.15, Math.min(after.y, 0.05), -v.z * 0.15);
            sl.playSound(null, blockPosition(), SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 0.8f, 1.3f);
            if (random.nextInt(2) == 0) say(sl, "§7Na rede!");
        }
        setDeltaMovement(after);
        if (onGround() || getY() <= groundY + 0.02) {
            landed(sl);
            return;
        }
        tickRally(sl, team);
    }

    /** Bola parada no chão (sem jogo ou entre os pontos). */
    private void rest() {
        Vec3 v = getDeltaMovement();
        setDeltaMovement(v.x * 0.6, v.y - 0.04, v.z * 0.6);
        move(MoverType.SELF, getDeltaMovement());
    }

    private List<Raver> team(ServerLevel sl) {
        return sl.getEntitiesOfClass(Raver.class, new AABB(netCenter, netCenter).inflate(24, 6, 24), r -> players.contains(r.getUUID()));
    }

    public Vec3 predict(double h) {
        Vec3 p = position(), v = getDeltaMovement();
        for (int i = 0; i < 160; i++) {
            v = new Vec3(v.x * DRAG, (v.y - G) * DRAG, v.z * DRAG);
            p = p.add(v);
            if (v.y < 0 && p.y <= h) return p;
        }
        return p;
    }

    /** Velocidade para a bola sair de "from", subir até "apex" e cair em "to" (com o arrasto do ar). */
    private static Vec3 lob(Vec3 from, Vec3 to, double apex) {
        apex = Math.max(apex, Math.max(from.y, to.y) + 0.5);
        double up = Math.sqrt(2 * G * (apex - from.y)), tu = up / G, td = Math.sqrt(2 * (apex - to.y) / G);
        double t = tu + td;
        double k = (1 - DRAG) / (1 - Math.pow(DRAG, t));
        return new Vec3((to.x - from.x) * k, up * 1.04, (to.z - from.z) * k);
    }

    /** Toque: conta os 3 toques e não deixa o mesmo tocar duas vezes seguidas. */
    private boolean hit(ServerLevel sl, Entity who, Vec3 vel) {
        int side = sideOf(who);
        if (side != touchSide) {
            touchSide = side;
            touches = 0;
        } else if (who.getUUID().equals(lastToucher) && touches > 0) {
            say(sl, "§cDois toques seguidos de " + who.getName().getString() + "! §fPonto do " + teamName(1 - side) + ".");
            point(sl, 1 - side);
            return false;
        }
        touches++;
        lastToucher = who.getUUID();
        lastSide = side;
        setDeltaMovement(vel);
        sl.playSound(null, blockPosition(), SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 1f, 0.9f + random.nextFloat() * 0.3f);
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 3, 0.1, 0.1, 0.1, 0.01);
        if (touches > 3) {
            say(sl, "§c4 toques! §fPonto do " + teamName(1 - side) + ".");
            point(sl, 1 - side);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ saque
    private List<Entity> sidePlayers(ServerLevel sl, List<Raver> team, int side) {
        List<Entity> out = new ArrayList<>();
        for (Raver r : team) if (sideOf(r) == side) out.add(r);
        for (var e : humans.entrySet()) {
            if (e.getValue() == side && sl.getEntity(e.getKey()) instanceof ServerPlayer p && p.distanceTo(this) < 40) out.add(p);
        }
        out.sort((a, b) -> a.getUUID().compareTo(b.getUUID()));
        return out;
    }

    private void startServe(ServerLevel sl, List<Raver> team) {
        serving = true;
        serveTimer = 50;
        touches = 0;
        touchSide = -1;
        lastToucher = null;
        missed.clear();
        List<Entity> ps = sidePlayers(sl, team, serveSide);
        server = ps.isEmpty() ? null : ps.get(Math.floorMod(serverIdx[serveSide], ps.size())).getUUID();
        entityData.set(INFO, (league ? "Jogo " + (round + 1) + "/3 • " : "Amistoso • ") + "saque: " + teamName(serveSide));
        if (server != null && sl.getEntity(server) instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.literal("§eSeu saque! §fVá para o fundo da quadra e clique: esquerdo = saque por baixo, direito = saque viagem (forte)."));
        }
    }

    private void tickServe(ServerLevel sl, List<Raver> team) {
        Entity s = server == null ? null : sl.getEntity(server);
        if (s == null || sideOf(s) != serveSide) {
            startServe(sl, team);
            s = server == null ? null : sl.getEntity(server);
            if (s == null) { idle(team); return; }
        }
        double sign = serveSide == 0 ? -1 : 1;
        Vec3 spot = courtPoint(sign * (halfLength + 0.8), 0);
        if (s instanceof Raver r) {
            if (r.position().distanceTo(spot) > 0.7) RaverRoles.go(r, spot.x, spot.y, spot.z, 0.7);
            r.getLookControl().setLookAt(netCenter.x, netCenter.y + 1, netCenter.z);
        }
        Vec3 f = s.getLookAngle();
        Vec3 hand = s.position().add(new Vec3(f.x, 0, f.z).normalize().scale(0.45)).add(0, 1.5, 0);
        setPos(hand.x, hand.y, hand.z);
        setDeltaMovement(Vec3.ZERO);
        positionTeam(team);
        if (--serveTimer > 0) return;
        if (s instanceof Raver r && r.position().distanceTo(spot) < 1.6) {
            double td = -sign * (2.5 + random.nextDouble() * (halfLength - 3)), tw = (random.nextDouble() - 0.5) * 2 * (halfWidth - 0.6);
            if (random.nextInt(12) == 0) td = -sign * (halfLength + 1.5);
            serve(sl, r, lob(position(), courtPoint(td, tw), netTop + 2.5 + random.nextDouble()));
            r.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        } else if (s instanceof ServerPlayer && serveTimer < -400) {
            serve(sl, s, lob(position(), courtPoint(-sign * 4, 0), netTop + 3));
        }
    }

    private void serve(ServerLevel sl, Entity who, Vec3 vel) {
        serving = false;
        // depois do saque a bola é do outro lado: o lado que recebe começa os 3 toques do zero
        touchSide = sideOf(who);
        touches = 3;
        lastToucher = who.getUUID();
        lastSide = touchSide;
        setDeltaMovement(vel);
        sl.playSound(null, blockPosition(), SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 1.2f, 0.8f);
    }

    // ------------------------------------------------------------ rally
    private void tickRally(ServerLevel sl, List<Raver> team) {
        Vec3 land = predict(groundY + 1.4);
        int landSide = d(land) < 0 ? 0 : 1;
        boolean inCourt = Math.abs(w(land)) <= halfWidth + 0.4 && Math.abs(d(land)) <= halfLength + 0.4;
        for (int side = 0; side < 2; side++) {
            List<Entity> ps = sidePlayers(sl, team, side);
            boolean hasHuman = ps.stream().anyMatch(e -> e instanceof Player);
            Entity chaser = null;
            if (landSide == side && (inCourt || touchSide == side)) {
                double best = 1e9;
                for (Entity e : ps) {
                    if (e.getUUID().equals(lastToucher) && touchSide == side) continue;
                    double dd = e.position().distanceTo(new Vec3(land.x, e.getY(), land.z));
                    if (e instanceof Player) dd -= 1.0; // a pessoa tem preferência: o parceiro deixa
                    if (dd < best) { best = dd; chaser = e; }
                }
            }
            for (Entity e : ps) {
                if (!(e instanceof Raver r)) continue;
                r.getLookControl().setLookAt(this);
                if (e == chaser) {
                    RaverRoles.go(r, land.x, groundY, land.z, 0.95);
                    tryHit(sl, r, ps, side);
                } else {
                    double dd = (side == 0 ? -1 : 1) * (touchSide == side ? 1.6 : 4.5);
                    double ww = (ps.indexOf(e) == 0 ? -1 : 1) * halfWidth * 0.45;
                    Vec3 spot = courtPoint(dd, ww);
                    if (r.position().distanceTo(spot) > 0.8) RaverRoles.go(r, spot.x, spot.y, spot.z, 0.7);
                }
            }
        }
    }

    private void tryHit(ServerLevel sl, Raver r, List<Entity> mates, int side) {
        Vec3 b = position();
        double horiz = Math.hypot(b.x - r.getX(), b.z - r.getZ()), dy = b.y - r.getY();
        if (horiz > 1.5 || dy < 0.4 || dy > 3.0 || getDeltaMovement().y > 0.08) return;
        if (missed.getOrDefault(r.getId(), 0) > tickCount) return;
        if (touchSide != side) {
            double speed = getDeltaMovement().length();
            double fail = 0.1 + speed * 0.42 + horiz * 0.1;
            if (random.nextDouble() < fail) {
                missed.put(r.getId(), tickCount + 40);
                r.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                return;
            }
        }
        double sign = side == 0 ? -1 : 1;
        int n = touchSide == side ? touches : 0;
        Entity mate = null;
        for (Entity m : mates) if (m != r) mate = m;
        Vec3 vel;
        if (n == 0 && mate != null) {
            Vec3 to = new Vec3(mate.getX(), groundY + 2.2, mate.getZ()).lerp(courtPoint(sign * 1.6, w(mate.position())), 0.6);
            vel = lob(b, to, b.y + 2.6 + random.nextDouble());
        } else if (n == 1 && mate != null && (mate instanceof Player || random.nextInt(3) == 0)) {
            Vec3 to = courtPoint(sign * 1.3, Math.max(-halfWidth + 0.8, Math.min(halfWidth - 0.8, w(mate.position())))).add(0, 2.6, 0);
            vel = lob(b, to, b.y + 2.4);
            if (mate instanceof ServerPlayer p) p.sendOverlayMessage(Component.literal("§e§lLEVANTOU! §r§fPule e corte (botão direito)!"));
        } else {
            double td = -sign * (2 + random.nextDouble() * (halfLength - 2.5)), tw = (random.nextDouble() - 0.5) * 2 * (halfWidth - 0.4);
            if (random.nextInt(10) == 0) tw *= 1.8;
            Vec3 to = courtPoint(td, tw);
            boolean spike = Math.abs(d(b)) < 2.5 && b.y > groundY + 2.0;
            if (spike) {
                if (r.onGround()) r.jumpFromGround();
                double t = 14 + random.nextInt(6);
                vel = new Vec3((to.x - b.x) / t, (to.y - b.y) / t + 0.5 * G * t, (to.z - b.z) / t);
                if (random.nextInt(9) == 0) vel = vel.multiply(1, 0.5, 1);
            } else {
                vel = lob(b, to, netTop + 1.6 + random.nextDouble() * 1.5);
            }
        }
        r.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        hit(sl, r, vel);
    }

    private void idle(List<Raver> team) {
        for (Raver r : team) {
            int side = sideOf(r);
            if (side < 0) continue;
            double sign = side == 0 ? -1 : 1;
            Vec3 spot = courtPoint(sign * (2 + (r.getId() % 2) * 2.5), ((r.getId() % 2) * 2 - 1) * halfWidth * 0.4);
            if (r.position().distanceTo(spot) > 1.0 && r.tickCount % 10 == 0) RaverRoles.go(r, spot.x, spot.y, spot.z, 0.6);
        }
    }

    private void positionTeam(List<Raver> team) {
        for (Raver r : team) {
            if (r.getUUID().equals(server)) continue;
            int side = sideOf(r);
            if (side < 0) continue;
            double sign = side == 0 ? -1 : 1;
            boolean front = r.getId() % 2 == 0;
            Vec3 spot = courtPoint(sign * (front ? 2 : 4.5), (front ? -1 : 1) * halfWidth * 0.4);
            if (r.position().distanceTo(spot) > 0.8 && r.tickCount % 8 == 0) RaverRoles.go(r, spot.x, spot.y, spot.z, 0.6);
            r.getLookControl().setLookAt(this);
        }
    }

    // ------------------------------------------------------------ botões da pessoa
    /** Manchete, levantada ou cortada (ver {@link Sports}); também o saque. */
    public void playerAction(ServerPlayer p, int action) {
        if (!(level() instanceof ServerLevel sl) || !active || paused()) return;
        int side = sideOf(p);
        if (side < 0) return;
        double sign = side == 0 ? -1 : 1;
        Vec3 look = p.getLookAngle();
        if (serving) {
            if (!p.getUUID().equals(server)) return;
            Vec3 aim = aimOnGround(p, -sign);
            Vec3 vel = action == Sports.VOL_SPIKE
                    ? lob(position(), aim, netTop + 1.0 + random.nextDouble() * 0.4)   // viagem: forte e baixo
                    : lob(position(), aim, netTop + 3.0);
            serve(sl, p, vel);
            return;
        }
        Vec3 b = position();
        double horiz = Math.hypot(b.x - p.getX(), b.z - p.getZ()), dy = b.y - p.getY();
        if (horiz > 3.0 || dy < -0.6 || dy > 3.6) {
            p.sendOverlayMessage(Component.literal("§7Longe da bola!"));
            return;
        }
        Entity mate = null;
        for (Entity m : sidePlayers(sl, team(sl), side)) if (m != p) mate = m;
        int n = touchSide == side ? touches : 0;
        Vec3 vel;
        switch (action) {
            case Sports.VOL_SPIKE -> {
                if (dy < 1.2) {
                    p.sendOverlayMessage(Component.literal("§7Bola baixa demais para cortar: use a manchete (esquerdo)."));
                    return;
                }
                Vec3 aim = aimOnGround(p, -sign);
                boolean jumping = !p.onGround();
                double t = jumping ? 11 + random.nextInt(4) : 18;
                vel = new Vec3((aim.x - b.x) / t, (aim.y - b.y) / t + 0.5 * G * t, (aim.z - b.z) / t);
                if (jumping && Math.abs(d(b)) < 3) p.sendOverlayMessage(Component.literal("§c§lCORTOU!"));
            }
            case Sports.VOL_SET -> {
                double ww = mate == null ? w(p.position()) : w(mate.position());
                Vec3 to = courtPoint(sign * 1.3, Math.max(-halfWidth + 0.8, Math.min(halfWidth - 0.8, ww))).add(0, 2.6, 0);
                vel = lob(b, to, b.y + 2.5);
            }
            default -> {
                if (n < 2 && mate != null) {
                    Vec3 to = new Vec3(mate.getX(), groundY + 2.2, mate.getZ()).lerp(courtPoint(sign * 1.6, w(mate.position())), 0.5);
                    vel = lob(b, to, b.y + 2.8);
                } else {
                    vel = lob(b, aimOnGround(p, -sign), netTop + 2.2);
                }
            }
        }
        p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        hit(sl, p, vel);
    }

    /** Onde o olhar da pessoa encosta no chão do outro lado (dá para mandar para fora, até 2 blocos). */
    private Vec3 aimOnGround(ServerPlayer p, double otherSign) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 hitP;
        if (look.y < -0.05) {
            double t = (eye.y - groundY) / -look.y;
            hitP = eye.add(look.scale(t));
        } else {
            hitP = eye.add(new Vec3(look.x, 0, look.z).normalize().scale(10));
        }
        double dd = d(hitP), ww = w(hitP);
        if (Math.signum(dd) != Math.signum(otherSign) || Math.abs(dd) < 1) dd = otherSign * (2 + random.nextDouble() * 3);
        dd = Math.signum(dd) * Math.min(Math.abs(dd), halfLength + 2);
        ww = Math.max(-halfWidth - 2, Math.min(halfWidth + 2, ww));
        return courtPoint(dd, ww);
    }

    // ------------------------------------------------------------ pontos
    /** A bola tocou o chão: vê pela marca se foi dentro (encostar na linha vale) ou fora. */
    private void landed(ServerLevel sl) {
        Vec3 p = position();
        double outW = Math.abs(w(p)) - halfWidth, outD = Math.abs(d(p)) - halfLength;
        boolean in = outW <= RADIUS && outD <= RADIUS;
        int sideHit = d(p) < 0 ? 0 : 1;
        sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.05, p.z, 14, 0.15, 0.02, 0.15, 0.02);
        sl.sendParticles(in ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ANGRY_VILLAGER, p.x, p.y + 0.3, p.z, 4, 0.2, 0.1, 0.2, 0);
        setDeltaMovement(Vec3.ZERO);
        int winner;
        if (in) {
            winner = 1 - sideHit;
            say(sl, "§a§lDENTRO! §r§fA bola caiu na quadra do " + teamName(sideHit) + ".");
        } else {
            winner = lastSide < 0 ? 1 - sideHit : 1 - lastSide;
            double by = Math.max(outW, outD) - RADIUS;
            say(sl, "§c§lFORA! §r§7(por " + String.format(java.util.Locale.ROOT, "%.1f", Math.max(0.1, by)) + " bloco)");
        }
        point(sl, winner);
    }

    private void point(ServerLevel sl, int winner) {
        if (winner == 0) entityData.set(SCORE0, score(0) + 1);
        else entityData.set(SCORE1, score(1) + 1);
        setDeltaMovement(Vec3.ZERO);
        if (winner != serveSide) serverIdx[winner]++;
        serveSide = winner;
        pause = 60;
        serving = false;
        int a = score(0), b = score(1);
        say(sl, "§ePonto do " + teamName(winner) + "! §6" + teamName(0) + " " + a + " x " + b + " " + teamName(1));
        for (Raver r : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(24), r -> sideOf(r) == winner)) r.celebrate(true);
        if ((a >= target || b >= target) && Math.abs(a - b) >= 2) endGame(sl, winner);
    }

    private void endGame(ServerLevel sl, int winner) {
        gameOver = 200;
        pause = 0;
        int a = score(0), b = score(1);
        say(sl, "§6§l🏐 FIM DE JOGO! §r§eO " + teamName(winner) + " ganhou por " + Math.max(a, b) + " a " + Math.min(a, b) + "!");
        entityData.set(INFO, "Fim de jogo");
        sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 2, getZ(), 60, 1, 1, 1, 0.15);
        if (!league) {
            if (winner == 0) for (ServerPlayer p : humanPlayers(sl)) Achievements.award(p, "campeao_do_volei");
            return;
        }
        int opp = Math.min(3, round + 1);
        if (winner == 0) { pts[0] += 3; wins[0]++; } else { pts[opp] += 3; wins[opp]++; }
        List<Integer> others = new ArrayList<>();
        for (int i = 1; i < 4; i++) if (i != opp) others.add(i);
        int w = random.nextBoolean() ? others.get(0) : others.get(1);
        pts[w] += 3;
        wins[w]++;
        say(sl, "§7No outro jogo: " + teams[w] + " venceu " + teams[w == others.get(0) ? others.get(1) : others.get(0)] + ".");
        round++;
        if (round >= 3) {
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
            order.sort((x, y) -> pts[y] - pts[x]);
            int champ = order.get(0);
            say(sl, "§6§l🏆 " + teams[champ].toUpperCase() + " É CAMPEÃO DO VÔLEI DE PRAIA! 🏆");
            if (champ == 0) {
                for (ServerPlayer p : humanPlayers(sl)) {
                    ItemStack trophy = new ItemStack(Blocks34.TROFEU_VOLEI);
                    if (!p.getInventory().add(trophy)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), trophy);
                    Achievements.award(p, "campeao_do_volei");
                    p.sendSystemMessage(Component.literal("§6Você levou o Troféu do Vôlei de Praia!"));
                }
            }
        } else {
            StringBuilder t = new StringBuilder("§6Tabela: ");
            for (int i = 0; i < 4; i++) t.append("§f").append(teams[i]).append(" §e").append(pts[i]).append(i < 3 ? "§7, " : "");
            say(sl, t.toString());
        }
    }

    private List<ServerPlayer> humanPlayers(ServerLevel sl) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : humans.keySet()) if (sl.getEntity(id) instanceof ServerPlayer p) out.add(p);
        return out;
    }

    /** Depois do fim de jogo: próximo jogo do campeonato, ou a quadra esvazia. */
    private void afterGame(ServerLevel sl) {
        if (league && round < 3) {
            startGame(sl);
            return;
        }
        for (ServerPlayer p : humanPlayers(sl)) {
            Sports.sendState(p, Sports.NONE);
            p.sendSystemMessage(Component.literal("§7Valeu pelo jogo! Fale com o organizador para jogar de novo."));
            ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
            if (chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME) && chest.getHoverName().getString().startsWith("Regata")) {
                p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            }
        }
        for (Raver r : team(sl)) {
            sl.sendParticles(ParticleTypes.POOF, r.getX(), r.getY() + 1, r.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
            r.discard();
        }
        players.clear();
        humans.clear();
        active = false;
        entityData.set(SCORE0, 0);
        entityData.set(SCORE1, 0);
        entityData.set(INFO, "Fale com o organizador");
    }

    private void say(ServerLevel sl, String msg) {
        for (ServerPlayer p : sl.players()) if (p.distanceTo(this) < 40) p.sendSystemMessage(Component.literal(msg));
    }

    // ------------------------------------------------------------ organizador: amistoso ou campeonato
    public void setup(ServerPlayer p, int mode, String name, int color) {
        if (!(level() instanceof ServerLevel sl) || !hasCourt()) return;
        if (active && !humans.containsKey(p.getUUID())) {
            p.sendSystemMessage(Component.literal("§7Já tem jogo rolando nessa quadra. Espere acabar!"));
            return;
        }
        // tira quem estava jogando antes (inclusive os aldeões das quadras da 3.4)
        for (Raver r : sl.getEntitiesOfClass(Raver.class, new AABB(netCenter, netCenter).inflate(24, 6, 24),
                r -> r.role() == RaverRoles.VOLEI_A || r.role() == RaverRoles.VOLEI_B)) r.discard();
        players.clear();
        humans.clear();
        league = mode == Sports.CAMPEONATO;
        if (league) {
            String n = name == null || name.isBlank() ? p.getName().getString() + " Vôlei" : name.trim();
            if (n.length() > 24) n = n.substring(0, 24);
            teams[0] = n;
            colors[0] = color & 0xFFFFFF;
            var others = Sports.randomTeams(random, true, 3, List.of(n), List.of(colors[0]));
            for (int i = 0; i < 3; i++) {
                teams[i + 1] = others.get(i)[0];
                colors[i + 1] = Integer.parseInt(others.get(i)[1]);
            }
            for (int i = 0; i < 4; i++) pts[i] = wins[i] = 0;
            round = 0;
            say(sl, "§6§l🏆 Campeonato de Vôlei de Praia! §r§f" + teams[0] + " (seu time), " + teams[1] + ", " + teams[2] + " e " + teams[3] + ". 3 jogos até 12 pontos.");
        }
        humans.put(p.getUUID(), 0);
        Sports.sendState(p, Sports.VOLLEY);
        p.sendSystemMessage(Component.literal("§aVocê está no jogo! §7Até 3 toques por lado, um de cada. §fEsquerdo§7 = manchete (passa para o parceiro), "
                + "§fV§7 = levantar, §fdireito§7 = cortar (pule e mire onde quer que a bola caia)."));
        active = true;
        startGame(sl);
    }

    private void startGame(ServerLevel sl) {
        String a, b;
        int ca, cb;
        if (league) {
            a = teams[0];
            ca = colors[0];
            b = teams[Math.min(3, round + 1)];
            cb = colors[Math.min(3, round + 1)];
            target = 12;
        } else {
            var t = Sports.randomTeams(random, true, 2, List.of(), List.of());
            a = t.get(0)[0];
            ca = Integer.parseInt(t.get(0)[1]);
            b = t.get(1)[0];
            cb = Integer.parseInt(t.get(1)[1]);
            target = 15;
        }
        entityData.set(NAME0, a);
        entityData.set(NAME1, b);
        entityData.set(COLOR0, ca);
        entityData.set(COLOR1, cb);
        entityData.set(SCORE0, 0);
        entityData.set(SCORE1, 0);
        // aldeões: um parceiro do seu lado e dois do outro (os do outro lado trocam a cada jogo do campeonato)
        List<Raver> team = team(sl);
        boolean hasPartner = false;
        for (Raver r : team) {
            if (sideOf(r) == 1) { players.remove(r.getUUID()); r.discard(); }
            else hasPartner = true;
        }
        if (!hasPartner) spawnPlayer(sl, RaverRoles.VOLEI_A, -3, -1);
        spawnPlayer(sl, RaverRoles.VOLEI_B, 3, -1);
        spawnPlayer(sl, RaverRoles.VOLEI_B, 4.5, 1);
        for (Raver r : team(sl)) r.setShirt(0xFF000000 | teamColor(sideOf(r)));
        for (ServerPlayer p : humanPlayers(sl)) giveShirt(p, 0);
        say(sl, (league ? "§6Jogo " + (round + 1) + " de 3: " : "§eAmistoso: ") + "§f" + a + " §7x §f" + b + " §7(até " + target + " pontos)");
        serveSide = random.nextInt(2);
        gameOver = 0;
        pause = 60;
        serving = false;
        entityData.set(INFO, league ? "Jogo " + (round + 1) + "/3" : "Amistoso");
    }

    private void spawnPlayer(ServerLevel sl, int role, double dd, double ww) {
        Raver r = ModEntities.RAVER.create(sl, EntitySpawnReason.EVENT);
        if (r == null) return;
        Vec3 at = courtPoint(dd, ww * halfWidth * 0.4);
        Vec3 from = courtPoint(Math.signum(dd) * (halfLength + 2), halfWidth + 2);
        r.snapTo(from.x, groundY, from.z, 0, 0);
        r.setRole(role);
        r.fieldHome = BlockPos.containing(at);
        sl.addFreshEntity(r);
        players.add(r.getUUID());
        RaverRoles.go(r, at.x, at.y, at.z, 0.7);
    }

    private void giveShirt(ServerPlayer sp, int side) {
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        boolean ours = chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME) && chest.getHoverName().getString().startsWith("Regata");
        if (!chest.isEmpty() && !ours) return;
        ItemStack shirt = new ItemStack(Items.LEATHER_CHESTPLATE);
        shirt.set(DataComponents.DYED_COLOR, new DyedItemColor(teamColor(side)));
        shirt.set(DataComponents.CUSTOM_NAME, Component.literal("Regata do " + teamName(side)));
        sp.setItemSlot(EquipmentSlot.CHEST, shirt);
    }

    /** Quadra sem organizador (as da 3.4): chama um. */
    private void ensureOrganizer(ServerLevel sl) {
        if (tickCount % 200 != 20) return;
        if (!sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(24), r -> r.role() == RaverRoles.ORG_VOLEI).isEmpty()) return;
        Raver o = ModEntities.RAVER.create(sl, EntitySpawnReason.EVENT);
        if (o == null) return;
        Vec3 at = courtPoint(0, halfWidth + 2.5);
        o.snapTo(at.x, groundY, at.z, 0, 0);
        o.setRole(RaverRoles.ORG_VOLEI);
        sl.addFreshEntity(o);
    }

    private void findCourt(ServerLevel sl) {
        List<BlockPos> nets = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(blockPosition().offset(-16, -3, -16), blockPosition().offset(16, 3, 16))) {
            if (sl.getBlockState(p).is(Blocks34.VOLLEY_NET)) nets.add(p.immutable());
        }
        if (nets.isEmpty()) return;
        boolean ax = VolleyNetBlock.alongX(sl.getBlockState(nets.get(0)));
        double cx = 0, cz = 0, lo = 1e9, hi = -1e9;
        int minY = Integer.MAX_VALUE;
        for (BlockPos p : nets) {
            cx += p.getX() + 0.5;
            cz += p.getZ() + 0.5;
            minY = Math.min(minY, p.getY());
            double k = ax ? p.getX() : p.getZ();
            lo = Math.min(lo, k);
            hi = Math.max(hi, k + 1);
        }
        groundY = minY;
        netTop = minY + VolleyNetBlock.TOP;
        netCenter = new Vec3(cx / nets.size(), groundY, cz / nets.size());
        along = ax ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        normal = ax ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        halfWidth = Math.max(2.5, (hi - lo) / 2);
        halfLength = Math.max(5, Math.min(8, halfWidth * 2));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
        if (player instanceof ServerPlayer sp && sideOf(sp) < 0) {
            sp.sendSystemMessage(Component.literal(active ? "§6" + teamName(0) + " " + score(0) + " x " + score(1) + " " + teamName(1)
                    : "§7Quadra livre. Fale com o organizador do vôlei para marcar um amistoso ou um campeonato."));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel sl, DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer p && sideOf(p) >= 0) playerAction(p, Sports.VOL_BUMP);
        return false;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean canBeCollidedWith(Entity other) { return false; }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        // a partida não continua depois de sair do mundo: a quadra volta vazia
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
    }

    /** Para o teste: quantos aldeões vieram jogar. */
    public int playerCount() {
        return players.size();
    }
}
