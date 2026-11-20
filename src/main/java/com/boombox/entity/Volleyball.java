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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bola e juiz do vôlei de praia (2 contra 2). Acha a rede sozinha e comanda os aldeões dos dois times:
 * saque, recepção (levanta para o parceiro), ataque por cima da rede, até 3 toques de cada lado. Bola no chão da
 * quadra é ponto de quem atacou; fora da quadra é ponto do outro time. Jogo até 15, com 2 de vantagem.
 * Clique com o botão direito na bola para entrar num time (no lugar de um aldeão).
 */
public class Volleyball extends Entity {
    public static final String[] TEAM_NAMES = {"Sol", "Mar"};
    public static final int[] TEAM_COLORS = {0xFF8A1E, 0x1EC8E6};
    private static final double G = 0.028, DRAG = 0.985;
    private static final int WIN = 15;

    private static final EntityDataAccessor<Integer> SCORE0 = SynchedEntityData.defineId(Volleyball.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCORE1 = SynchedEntityData.defineId(Volleyball.class, EntityDataSerializers.INT);

    /** Quadra: centro da rede, normal (do lado 0 para o lado 1), direção da rede, meia largura, comprimento de cada lado. */
    private Vec3 netCenter, normal, along;
    private double halfWidth = 3.5, halfLength = 7, groundY, netTop;
    /** Saque: lado que saca, quem saca (vez de cada um), ticks até sacar. */
    private int serveSide, serverIdx, serveTimer = 60;
    private boolean serving = true;
    private UUID server;
    private int pause, touches, touchSide = -1, gameOver;
    private UUID lastToucher;
    private int lastSide = -1;
    private final Map<UUID, Integer> humans = new HashMap<>();
    /** Aldeões que estão no banco (deram lugar a uma pessoa). */
    private final List<UUID> benched = new ArrayList<>();

    public Volleyball(EntityType<? extends Volleyball> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SCORE0, 0);
        b.define(SCORE1, 0);
    }

    public int score(int side) {
        return entityData.get(side == 0 ? SCORE0 : SCORE1);
    }

    public boolean hasCourt() {
        return netCenter != null;
    }

    /** Distância (com sinal) até a rede: negativa no lado 0, positiva no lado 1. */
    public double d(Vec3 p) {
        return p.subtract(netCenter).dot(normal);
    }

    public double w(Vec3 p) {
        return p.subtract(netCenter).dot(along);
    }

    public Vec3 courtPoint(double d, double w) {
        return new Vec3(netCenter.x + normal.x * d + along.x * w, groundY, netCenter.z + normal.z * d + along.z * w);
    }

    public int sideOf(Entity e) {
        if (e instanceof Raver r) {
            if (benched.contains(r.getUUID())) return -1;
            return r.role() == RaverRoles.VOLEI_A ? 0 : r.role() == RaverRoles.VOLEI_B ? 1 : -1;
        }
        Integer t = humans.get(e.getUUID());
        return t == null ? -1 : t;
    }

    public boolean serving() {
        return serving;
    }

    /** Estado para o teste. */
    public String debug() {
        return String.format(java.util.Locale.ROOT, "pos=(%.1f,%.1f,%.1f) vel=(%.2f,%.2f,%.2f) sacando=%s pausa=%d toques=%d lado=%d chão=%s y0=%.1f",
                getX(), getY(), getZ(), getDeltaMovement().x, getDeltaMovement().y, getDeltaMovement().z, serving, pause, touches, touchSide, onGround(), groundY);
    }

    public boolean paused() {
        return pause > 0 || gameOver > 0;
    }

    // ------------------------------------------------------------ física
    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * DRAG, (v.y - G) * DRAG, v.z * DRAG);
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        if (!hasCourt()) {
            if (tickCount % 20 == 1) findCourt(sl);
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * 0.8, v.y - 0.04, v.z * 0.8);
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        List<Raver> team = sl.getEntitiesOfClass(Raver.class, new AABB(netCenter, netCenter).inflate(24, 6, 24),
                r -> r.role() == RaverRoles.VOLEI_A || r.role() == RaverRoles.VOLEI_B);
        if (tickCount % 20 == 0) scoreboard(sl);
        if (gameOver > 0) {
            if (--gameOver == 0) {
                entityData.set(SCORE0, 0);
                entityData.set(SCORE1, 0);
                serveSide = random.nextInt(2);
                startServe(sl, team);
                say(sl, "§e🏐 Começa um novo jogo de vôlei!");
            }
            idle(team);
            return;
        }
        if (pause > 0) {
            if (--pause == 0) startServe(sl, team);
            idle(team);
            return;
        }
        if (serving) {
            tickServe(sl, team);
            return;
        }
        // bola no ar
        Vec3 before = getDeltaMovement();
        Vec3 v = new Vec3(before.x * DRAG, (before.y - G) * DRAG, before.z * DRAG);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        Vec3 after = getDeltaMovement();
        if (horizontalCollision) {
            // bateu na rede (ou numa parede): perde força e cai
            after = new Vec3(-v.x * 0.25, after.y, -v.z * 0.25);
            playSound(SoundEvents.WOOL_HIT, 0.8f, 1.2f);
        }
        setDeltaMovement(after);
        if (onGround() || getY() <= groundY + 0.05) {
            landed(sl, team);
            return;
        }
        tickRally(sl, team);
    }

    /** Onde a bola vai estar quando descer até a altura h (para os aldeões correrem até lá). */
    public Vec3 predict(double h) {
        Vec3 p = position(), v = getDeltaMovement();
        for (int i = 0; i < 160; i++) {
            v = new Vec3(v.x * DRAG, (v.y - G) * DRAG, v.z * DRAG);
            p = p.add(v);
            if (v.y < 0 && p.y <= h) return p;
        }
        return p;
    }

    /** Velocidade para a bola sair de "from", subir até "apex" e cair em "to". */
    private static Vec3 lob(Vec3 from, Vec3 to, double apex) {
        apex = Math.max(apex, Math.max(from.y, to.y) + 0.5);
        double up = Math.sqrt(2 * G * (apex - from.y)), tu = up / G, td = Math.sqrt(2 * (apex - to.y) / G);
        double t = tu + td;
        double k = (1 - DRAG) / (1 - Math.pow(DRAG, t)); // o arrasto do ar encurta a viagem: compensa
        double dx = to.x - from.x, dz = to.z - from.z;
        return new Vec3(dx * k, up * 1.04, dz * k);
    }

    private void hit(ServerLevel sl, Entity who, Vec3 vel) {
        int side = sideOf(who);
        if (side != touchSide) {
            touchSide = side;
            touches = 0;
        }
        touches++;
        lastToucher = who.getUUID();
        lastSide = side;
        setDeltaMovement(vel);
        sl.playSound(null, blockPosition(), SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 1f, 0.9f + random.nextFloat() * 0.3f);
        if (touches > 3) {
            say(sl, "§c4 toques! §fPonto do time " + TEAM_NAMES[1 - side] + ".");
            point(sl, 1 - side);
        }
    }

    // ------------------------------------------------------------ saque
    private List<Entity> players(ServerLevel sl, List<Raver> team, int side) {
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
        List<Entity> ps = players(sl, team, serveSide);
        server = ps.isEmpty() ? null : ps.get(Math.floorMod(serverIdx, ps.size())).getUUID();
        if (server != null && sl.getEntity(server) instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.literal("§eSeu saque! §fBata na bola para sacar (mire para o outro lado da rede)."));
        }
    }

    private void tickServe(ServerLevel sl, List<Raver> team) {
        Entity s = server == null ? null : sl.getEntity(server);
        if (s == null || sideOf(s) != serveSide) {
            startServe(sl, team);
            if (server == null) {
                idle(team);
                return;
            }
            s = sl.getEntity(server);
            if (s == null) return;
        }
        // quem saca vai para trás da linha de fundo; a bola fica na mão dele
        double sign = serveSide == 0 ? -1 : 1;
        Vec3 spot = courtPoint(sign * (halfLength + 0.8), 0);
        if (s instanceof Raver r) {
            if (r.position().distanceTo(spot) > 0.7) RaverRoles.go(r, spot.x, spot.y, spot.z, 0.7);
            r.getLookControl().setLookAt(netCenter.x, netCenter.y + 1, netCenter.z);
        }
        Vec3 hand = s.position().add(normal.scale(-sign * 0.4)).add(0, 1.6, 0);
        setPos(hand.x, hand.y, hand.z);
        setDeltaMovement(Vec3.ZERO);
        positionTeam(sl, team, true);
        if (--serveTimer > 0) return;
        if (s instanceof Raver r && r.position().distanceTo(spot) < 1.5) {
            // saque por cima: mira num ponto do outro lado, às vezes erra
            double td = -sign * (2.5 + random.nextDouble() * (halfLength - 3)), tw = (random.nextDouble() - 0.5) * 2 * (halfWidth - 0.6);
            if (random.nextInt(12) == 0) td = -sign * (halfLength + 1.5); // saque para fora
            serving = false;
            hit(sl, r, lob(position(), courtPoint(td, tw), netTop + 2.5 + random.nextDouble()));
            touches = 0;
            touchSide = serveSide;
            r.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        } else if (s instanceof ServerPlayer && serveTimer < -200) {
            // a pessoa demorou demais: saque automático "por baixo"
            serving = false;
            hit(sl, s, lob(position(), courtPoint(-sign * 4, 0), netTop + 3));
            touches = 0;
            touchSide = serveSide;
        }
    }

    // ------------------------------------------------------------ rally (aldeões)
    private void tickRally(ServerLevel sl, List<Raver> team) {
        Vec3 land = predict(groundY + 1.4);
        int landSide = d(land) < 0 ? 0 : 1;
        boolean inCourt = Math.abs(w(land)) <= halfWidth + 0.4 && Math.abs(d(land)) <= halfLength + 0.4;
        for (int side = 0; side < 2; side++) {
            List<Entity> ps = players(sl, team, side);
            // quem vai na bola: o mais perto do ponto de queda (se a bola vem para o nosso lado e vai cair dentro)
            Entity chaser = null;
            if (landSide == side && (inCourt || touchSide == side)) {
                double best = 1e9;
                for (Entity e : ps) {
                    if (e.getUUID().equals(lastToucher) && touchSide == side && ps.size() > 1) continue; // não toca duas vezes seguidas
                    double dd = e.position().distanceTo(new Vec3(land.x, e.getY(), land.z));
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
                    // parceiro: perto da rede se vamos atacar, no fundo se vamos receber
                    double dd = (side == 0 ? -1 : 1) * (touchSide == side ? 1.6 : 4.5);
                    double ww = (ps.indexOf(e) == 0 ? -1 : 1) * halfWidth * 0.45;
                    Vec3 spot = courtPoint(dd, ww);
                    if (r.position().distanceTo(spot) > 0.8) RaverRoles.go(r, spot.x, spot.y, spot.z, 0.7);
                }
            }
        }
    }

    /** Aldeão que errou a bola (não tenta de novo nessa jogada): id → tick até quando. */
    private final Map<Integer, Integer> missed = new HashMap<>();

    private void tryHit(ServerLevel sl, Raver r, List<Entity> mates, int side) {
        Vec3 b = position();
        double horiz = Math.hypot(b.x - r.getX(), b.z - r.getZ()), dy = b.y - r.getY();
        if (horiz > 1.5 || dy < 0.4 || dy > 3.0 || getDeltaMovement().y > 0.08) return;
        if (missed.getOrDefault(r.getId(), 0) > tickCount) return;
        // bola que vem do outro lado: quanto mais forte e longe do corpo, mais difícil de pegar
        if (touchSide != side) {
            double speed = getDeltaMovement().length();
            double fail = 0.12 + speed * 0.45 + horiz * 0.12;
            if (random.nextDouble() < fail) {
                missed.put(r.getId(), tickCount + 40);
                r.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                return; // não alcançou!
            }
        }
        double sign = side == 0 ? -1 : 1;
        int n = touchSide == side ? touches : 0;
        Entity mate = null;
        for (Entity m : mates) if (m != r) mate = m;
        Vec3 vel;
        if (n == 0 && mate != null) {
            // recepção: levanta alto para o parceiro, perto da rede
            Vec3 to = new Vec3(mate.getX(), groundY + 2.2, mate.getZ()).lerp(courtPoint(sign * 1.4, w(mate.position())), 0.6);
            vel = lob(b, to, b.y + 2.6 + random.nextDouble());
        } else if (n == 1 && mate != null && random.nextInt(4) == 0) {
            // levantada: deixa a bola alta perto da rede para o parceiro atacar
            Vec3 to = courtPoint(sign * 1.2, w(mate.position()) * 0.5).add(0, 2.4, 0);
            vel = lob(b, to, b.y + 2.0);
        } else {
            // ataque: por cima da rede, num canto do outro lado (forte se está perto da rede)
            double td = -sign * (2 + random.nextDouble() * (halfLength - 2.5)), tw = (random.nextDouble() - 0.5) * 2 * (halfWidth - 0.4);
            if (random.nextInt(10) == 0) tw *= 1.8; // erro: para fora
            Vec3 to = courtPoint(td, tw);
            boolean spike = Math.abs(d(b)) < 2.5 && b.y > groundY + 2.0;
            if (spike) {
                if (r.onGround()) r.jumpFromGround();
                double t = 14 + random.nextInt(6);
                vel = new Vec3((to.x - b.x) / t, (to.y - b.y) / t + 0.5 * G * t, (to.z - b.z) / t);
                if (random.nextInt(9) == 0) vel = vel.multiply(1, 0.5, 1); // erro: na rede
            } else {
                vel = lob(b, to, netTop + 1.6 + random.nextDouble() * 1.5);
            }
        }
        r.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        hit(sl, r, vel);
    }

    /** Fora do rally: cada um no seu lugar. */
    private void idle(List<Raver> team) {
        if (!hasCourt()) return;
        for (Raver r : team) {
            int side = sideOf(r);
            Vec3 spot;
            if (side < 0) {
                spot = courtPoint(0, halfWidth + 2.5 + benched.indexOf(r.getUUID()));
            } else {
                double sign = side == 0 ? -1 : 1;
                spot = courtPoint(sign * (2 + (r.getId() % 2) * 2.5), ((r.getId() % 2) * 2 - 1) * halfWidth * 0.4);
            }
            if (r.position().distanceTo(spot) > 1.0 && r.tickCount % 10 == 0) RaverRoles.go(r, spot.x, spot.y, spot.z, 0.6);
        }
    }

    private void positionTeam(ServerLevel sl, List<Raver> team, boolean serve) {
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

    // ------------------------------------------------------------ pontos
    private void landed(ServerLevel sl, List<Raver> team) {
        Vec3 p = position();
        boolean in = Math.abs(w(p)) <= halfWidth + 0.15 && Math.abs(d(p)) <= halfLength + 0.15;
        int sideHit = d(p) < 0 ? 0 : 1;
        sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.1, p.z, 8, 0.2, 0.05, 0.2, 0.02);
        int winner;
        if (in) {
            winner = 1 - sideHit;                    // caiu na quadra: ponto de quem mandou para lá
            say(sl, "§fBola no chão do time " + TEAM_NAMES[sideHit] + "!");
        } else {
            winner = lastSide < 0 ? 1 - sideHit : 1 - lastSide; // fora: ponto contra quem tocou por último
            say(sl, "§fFora! A bola saiu.");
        }
        point(sl, winner);
    }

    private void point(ServerLevel sl, int winner) {
        if (winner == 0) entityData.set(SCORE0, score(0) + 1);
        else entityData.set(SCORE1, score(1) + 1);
        setDeltaMovement(Vec3.ZERO);
        if (winner != serveSide) serverIdx++; // virou o saque: troca quem saca
        serveSide = winner;
        pause = 60;
        serving = false;
        int a = score(0), b = score(1);
        say(sl, "§ePonto do time " + TEAM_NAMES[winner] + "! §6Sol " + a + " x " + b + " Mar");
        for (Raver r : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(24), r -> sideOf(r) == winner)) r.celebrate(true);
        if ((a >= WIN || b >= WIN) && Math.abs(a - b) >= 2) {
            gameOver = 200;
            say(sl, "§6§l🏐 FIM DE JOGO! §r§eO time " + TEAM_NAMES[winner] + " ganhou por " + Math.max(a, b) + " a " + Math.min(a, b) + "!");
            for (var e : humans.entrySet()) {
                if (e.getValue() == winner && sl.getEntity(e.getKey()) instanceof ServerPlayer p) {
                    ItemStack trophy = new ItemStack(Blocks34.TROFEU_VOLEI);
                    if (!p.getInventory().add(trophy)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), trophy);
                    Achievements.award(p, "campeao_do_volei");
                    p.sendSystemMessage(Component.literal("§6Você ganhou o Troféu do Vôlei de Praia!"));
                }
            }
            sl.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 2, getZ(), 60, 1, 1, 1, 0.15);
        }
    }

    private void say(ServerLevel sl, String msg) {
        for (ServerPlayer p : sl.players()) if (p.distanceTo(this) < 40) p.sendSystemMessage(Component.literal(msg));
    }

    private void scoreboard(ServerLevel sl) {
        String s = "§6Sol " + score(0) + " §fx §b" + score(1) + " Mar";
        for (var e : humans.entrySet()) {
            if (sl.getEntity(e.getKey()) instanceof ServerPlayer p && p.distanceTo(this) < 40) {
                p.sendOverlayMessage(Component.literal(s + (e.getValue() == 0 ? "  §6(você: Sol)" : "  §b(você: Mar)")));
            }
        }
    }

    /** Procura a rede (blocos de rede de vôlei em fila) e monta a quadra em volta. */
    private void findCourt(ServerLevel sl) {
        List<BlockPos> nets = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(blockPosition().offset(-16, -3, -16), blockPosition().offset(16, 3, 16))) {
            if (sl.getBlockState(p).is(Blocks34.VOLLEY_NET)) nets.add(p.immutable());
        }
        if (nets.isEmpty()) return;
        boolean ax = VolleyNetBlock.alongX(sl.getBlockState(nets.get(0)));
        double cx = 0, cz = 0;
        int minY = Integer.MAX_VALUE;
        double lo = 1e9, hi = -1e9;
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
        serveSide = random.nextInt(2);
        pause = 40;
    }

    // ------------------------------------------------------------ gente jogando
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
        if (!(player instanceof ServerPlayer sp) || !(level() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        Integer t = humans.get(sp.getUUID());
        int next = t == null ? 0 : t == 0 ? 1 : -1;
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(Items.LEATHER_CHESTPLATE) && chest.has(DataComponents.CUSTOM_NAME) && chest.getHoverName().getString().startsWith("Regata do Time")) {
            sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
        // quem sai do time devolve o lugar ao aldeão do banco
        if (t != null && !benched.isEmpty()) {
            for (UUID id : new ArrayList<>(benched)) {
                if (sl.getEntity(id) instanceof Raver r && (r.role() == RaverRoles.VOLEI_A ? 0 : 1) == t) {
                    benched.remove(id);
                    break;
                }
            }
        }
        if (next < 0) {
            humans.remove(sp.getUUID());
            sp.sendSystemMessage(Component.literal("§7Você saiu do vôlei."));
            return InteractionResult.SUCCESS;
        }
        humans.put(sp.getUUID(), next);
        // um aldeão desse lado vai para o banco
        int role = next == 0 ? RaverRoles.VOLEI_A : RaverRoles.VOLEI_B;
        for (Raver r : sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(24), r -> r.role() == role && !benched.contains(r.getUUID()))) {
            benched.add(r.getUUID());
            break;
        }
        if (sp.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            ItemStack shirt = new ItemStack(Items.LEATHER_CHESTPLATE);
            shirt.set(DataComponents.DYED_COLOR, new DyedItemColor(TEAM_COLORS[next]));
            shirt.set(DataComponents.CUSTOM_NAME, Component.literal("Regata do Time " + TEAM_NAMES[next]));
            sp.setItemSlot(EquipmentSlot.CHEST, shirt);
        }
        sp.sendSystemMessage(Component.literal((next == 0 ? "§6" : "§b") + "Você entrou no time " + TEAM_NAMES[next] + "! "
                + "§7Bata na bola para tocar: no chão ela sobe (manchete/levantada); pulando e olhando para baixo é cortada. "
                + "Até 3 toques de cada lado. Clique de novo para trocar de time."));
        playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel sl, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer p) || !hasCourt() || paused()) return false;
        if (sideOf(p) < 0 || p.distanceTo(this) > 4.5) return false;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 vel;
        if (serving) {
            if (!p.getUUID().equals(server)) return false;
            serving = false;
            vel = flat.scale(0.42).add(0, 0.62 + Math.max(0, look.y) * 0.2, 0);
            hit(sl, p, vel);
            touches = 0;
            touchSide = sideOf(p);
            return false;
        }
        if (!p.onGround() && look.y < 0.15) {
            vel = flat.scale(0.85).add(0, -0.2 + look.y * 0.4, 0); // cortada
        } else if (p.isSprinting()) {
            vel = flat.scale(0.5).add(0, 0.55, 0);                  // ataque de longe
        } else {
            vel = flat.scale(0.22).add(0, 0.7 + Math.max(0, look.y) * 0.15, 0); // manchete/levantada (sobe alto)
        }
        hit(sl, p, vel);
        return false;
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
        entityData.set(SCORE0, in.getIntOr("Score0", 0));
        entityData.set(SCORE1, in.getIntOr("Score1", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("Score0", score(0));
        out.putInt("Score1", score(1));
    }
}
