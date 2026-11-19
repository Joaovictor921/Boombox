package com.boombox.entity;

import com.boombox.bar.BarBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Papéis especiais dos aldeões festeiros (3.3): o bêbado do bar, os feirantes da feira de domingo e os boleiros
 * do futebol de várzea. Também cuida de quem está na fila da balada.
 */
public final class RaverRoles {
    public static final int NONE = 0, DRUNK = 1, FEIRANTE = 2, TEAM_A = 3, TEAM_B = 4, BARTENDER = 5;

    private RaverRoles() {}

    /** true = o papel cuidou do tick (o resto do comportamento normal não roda). */
    static boolean tick(Raver r, ServerLevel level) {
        if (r.queueSpot != null) return queue(r, level);
        if (r.settle > 0) r.settle--;
        return switch (r.role()) {
            case DRUNK -> drunk(r, level);
            case FEIRANTE -> feirante(r, level);
            case TEAM_A, TEAM_B -> football(r, level);
            case BARTENDER -> bartender(r, level);
            default -> false;
        };
    }

    private static void sayNear(ServerLevel level, Vec3 at, double radius, String msg) {
        for (var p : level.players()) if (p.position().distanceTo(at) < radius) p.sendSystemMessage(Component.literal(msg));
    }

    // ------------------------------------------------------------ fila da balada
    private static boolean queue(Raver r, ServerLevel level) {
        if (r.queueSpot.equals(Vec3.ZERO)) {
            // ainda sem lugar (ou sobrou de antes do mundo ser salvo): se ninguém der lugar, vai embora
            if (r.tickCount > 40) r.discard();
            return true;
        }
        if (r.tickCount % 10 == 0 && r.position().distanceTo(r.queueSpot) > 0.6) r.getNavigation().moveTo(r.queueSpot.x, r.queueSpot.y, r.queueSpot.z, 0.55);
        if (r.queueLook != null && r.getNavigation().isDone()) r.getLookControl().setLookAt(r.queueLook);
        // amanheceu e ainda está na fila: desiste e vai embora
        if (!level.isDarkOutside() && r.tickCount % 100 == 0) r.discard();
        return true;
    }

    // ------------------------------------------------------------ o bêbado do bar
    private static final String[] SONGS = {
            "♪ Lá-lá-laiá, lá-lá-laiá... hic! ♪", "♪ Ô saudade, ô saudade... ♪ (desafinado)", "♪ Tum-tum-tum, ê-ê-ê... ♪",
            "♪ Mais uma, Seu Zé, só mais uma... ♪", "♪ Eu vou pra casa, mas eu volto amanhã... hic! ♪", "♪ Lua, ó lua... ♪ (abraçado no poste)"};

    private static boolean drunk(Raver r, ServerLevel level) {
        if (!r.hasCustomName()) r.setCustomName(Component.literal("Tio Tonho"));
        if (r.barHome == null) r.barHome = r.blockPosition();
        long t = level.getOverworldClockTime() % 24000;
        boolean goingHome = t >= 22000 || t < 1500;   // fim da noite: hora de ir embora
        boolean barTime = t >= 12500 && t < 22000;    // a noite inteira no bar
        if (barTime) {
            r.houseSpot = null;
            if (r.blockPosition().distSqr(r.barHome) > 9) {
                if (r.hasHome()) r.clearHome();
                if (r.tickCount % 40 == 0) r.getNavigation().moveTo(r.barHome.getX() + 0.5, r.barHome.getY(), r.barHome.getZ() + 0.5, 0.5);
                return true;
            }
            if (!r.hasHome()) r.setHomeTo(r.barHome, 2);
            // tá alto: de vez em quando cambaleia e solta um "hic"
            if (r.getRandom().nextInt(200) == 0) {
                r.playSound(SoundEvents.VILLAGER_AMBIENT, 0.8f, 0.6f);
                level.sendParticles(ParticleTypes.BUBBLE_POP, r.getX(), r.getEyeY(), r.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
            }
            return false; // resto do bar (cerveja na mão, truco)
        }
        if (r.houseSpot == null) {
            // "casa" dele: um lugar a uns 20 blocos do bar onde dê para chegar andando
            for (int tries = 0; tries < 10 && r.houseSpot == null; tries++) {
                double a = r.getRandom().nextDouble() * Math.PI * 2;
                int x = r.barHome.getX() + (int) (Math.cos(a) * 18), z = r.barHome.getZ() + (int) (Math.sin(a) * 18);
                BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                var path = r.getNavigation().createPath(spot, 1);
                if (path != null && path.canReach()) r.houseSpot = spot;
            }
            if (r.houseSpot == null) r.houseSpot = r.barHome.offset(12, 0, 0);
        }
        if (r.hasHome() && r.getHomePosition().distSqr(r.houseSpot) > 4) r.clearHome();
        if (goingHome && r.blockPosition().distSqr(r.houseSpot) > 9) {
            if (r.tickCount % 30 == 0) r.getNavigation().moveTo(r.houseSpot.getX() + 0.5, r.houseSpot.getY(), r.houseSpot.getZ() + 0.5, 0.7);
            // cambaleando: empurrõezinhos para os lados
            if (r.tickCount % 11 == 0 && r.onGround()) {
                double a = r.getRandom().nextDouble() * Math.PI * 2;
                r.push(Math.cos(a) * 0.06, 0, Math.sin(a) * 0.06);
            }
            if (r.tickCount % 70 == 0) {
                r.playSound(SoundEvents.VILLAGER_AMBIENT, 1f, 0.55f + r.getRandom().nextFloat() * 0.2f);
                level.sendParticles(ParticleTypes.NOTE, r.getX(), r.getEyeY() + 0.5, r.getZ(), 2, 0.3, 0.1, 0.3, 1);
                sayNear(level, r.position(), 14, "§7<Tio Tonho> §o" + SONGS[r.getRandom().nextInt(SONGS.length)]);
            }
            return true;
        }
        // de dia: dormindo em casa (parado)
        if (!r.hasHome()) r.setHomeTo(r.houseSpot, 2);
        if (r.getRandom().nextInt(400) == 0) level.sendParticles(ParticleTypes.CLOUD, r.getX(), r.getEyeY() + 0.4, r.getZ(), 1, 0.1, 0.1, 0.1, 0);
        return true;
    }

    // ------------------------------------------------------------ barraqueiro da praia
    /**
     * Vira um aldeão de verdade (pescador, parado atrás do balcão) com as trocas da barraca: água de coco, gelada,
     * espetinho, cadeira de praia e guarda-sol. Usa a tela de trocas normal do jogo.
     */
    private static boolean bartender(Raver r, ServerLevel level) {
        var v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (v == null) return true;
        v.snapTo(r.getX(), r.getY(), r.getZ(), r.getYRot(), 0);
        var data = v.getVillagerData()
                .withProfession(level.registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.FISHERMAN)
                .withLevel(5);
        v.setVillagerData(data);
        v.setVillagerXp(1);
        var offers = v.getOffers(); // (no servidor só dá para mexer na lista do próprio aldeão)
        offers.clear();
        offers.add(trade(Items.EMERALD, 1, BarBlocks.AGUA_DE_COCO, 3));
        offers.add(trade(Items.EMERALD, 1, com.boombox.ModBlocks.BEER, 2));
        offers.add(trade(Items.EMERALD, 1, BarBlocks.ESPETINHO, 2));
        offers.add(trade(Items.EMERALD, 1, BarBlocks.PASTEL, 2));
        offers.add(trade(Items.EMERALD, 2, BarBlocks.BEACH_CHAIR.asItem(), 1));
        offers.add(trade(Items.EMERALD, 3, BarBlocks.PARASOL.asItem(), 1));
        offers.add(trade(Items.EMERALD, 2, BarBlocks.DRINK_NEON, 1));
        v.setCustomName(Component.literal("Barraqueiro"));
        v.setCustomNameVisible(true);
        v.setNoAi(true);
        v.setPersistenceRequired();
        level.addFreshEntity(v);
        r.discard();
        return true;
    }

    private static net.minecraft.world.item.trading.MerchantOffer trade(net.minecraft.world.item.Item buy, int n, net.minecraft.world.item.Item sell, int m) {
        return new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(buy, n), new ItemStack(sell, m), 100000, 1, 0f);
    }

    // ------------------------------------------------------------ feirantes (feira de domingo)
    private static final String[][] SHOUTS = {
            {"Olha a banana, olha a maçã! Três por uma esmeralda!", "Melancia docinha, freguesa! Pode provar!", "Fruta fresquinha, chegou agora!"},
            {"Pastel quentinho! Carne, queijo e de vento!", "Olha o pastel, saindo do óleo agora!", "Pastel de feira, o melhor da vila!"},
            {"Peixe fresquinho, pescado hoje cedo!", "Olha o bacalhau... quer dizer, o bacalhau de vila!", "Salmão no precinho, freguês!"}};

    private static boolean feirante(Raver r, ServerLevel level) {
        int k = Math.max(0, Math.min(2, r.stall));
        var want = k == 0 ? Items.APPLE : k == 1 ? BarBlocks.PASTEL : Items.COD;
        if (!r.getMainHandItem().is(want)) r.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(want));
        if (!r.hasHome()) r.setHomeTo(r.blockPosition(), 1);
        if (com.boombox.bar.GrillBlockEntity.isSunday() && r.getRandom().nextInt(320) == 0) {
            boolean anyone = level.players().stream().anyMatch(p -> p.distanceTo(r) < 16);
            if (anyone) {
                r.playSound(SoundEvents.VILLAGER_YES, 1f, 1.1f);
                sayNear(level, r.position(), 16, "§2<Feirante> §f" + SHOUTS[k][r.getRandom().nextInt(SHOUTS[k].length)]);
            }
        }
        return true;
    }

    // ------------------------------------------------------------ futebol de várzea
    private static boolean football(Raver r, ServerLevel level) {
        if (r.fieldHome == null) r.fieldHome = r.blockPosition();
        if (!r.getMainHandItem().isEmpty()) r.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        long t = level.getOverworldClockTime() % 24000;
        Ball ball = r.ball == null ? null : (level.getEntity(r.ball) instanceof Ball b && b.isAlive() ? b : null);
        if (ball == null && r.tickCount % 40 == 0) {
            var near = level.getEntitiesOfClass(Ball.class, new AABB(r.blockPosition()).inflate(40));
            if (!near.isEmpty()) r.ball = near.get(0).getUUID();
        }
        boolean day = t < 12300;
        if (ball == null || !day || !ball.hasGoals()) {
            if (r.hasHome() && r.getHomePosition().distSqr(r.fieldHome) > 4) r.clearHome();
            if (!r.hasHome()) r.setHomeTo(r.fieldHome, 2);
            return true;
        }
        if (r.hasHome()) r.clearHome();
        if (r.tickCount % 4 != 0) return true;
        int team = ball.teamOf(r);
        Vec3 attack = ball.goal(team == Ball.TEAM_A ? 1 : 0), own = ball.goal(team == Ball.TEAM_A ? 0 : 1);
        Vec3 b = ball.position();
        Vec3 home = Vec3.atBottomCenterOf(r.fieldHome);
        boolean keeper = r.stall == 1;
        Vec3 toGoal = new Vec3(attack.x - b.x, 0, attack.z - b.z).normalize();

        // ---- bola parada: o mais perto do time que cobra vai cobrar; o resto se posiciona
        if (ball.restarting()) {
            Vec3 spot = ball.restartSpot() == null ? b : ball.restartSpot();
            boolean ours = ball.restartKind() >= 0 && ball.restartTeam() == team;
            boolean taker = false;
            if (ours) {
                boolean gk = ball.restartKind() == Ball.GOAL_KICK;
                var mates = level.getEntitiesOfClass(Raver.class, new AABB(spot, spot).inflate(48),
                        m -> ball.teamOf(m) == team && (gk ? m.stall == 1 : m.stall != 1));
                mates.sort((p, q) -> Double.compare(p.distanceToSqr(spot), q.distanceToSqr(spot)));
                taker = !mates.isEmpty() && mates.get(0) == r;
                // gente do time perto da bola: deixa a pessoa cobrar
                boolean human = level.players().stream().anyMatch(p -> ball.teamOf(p) == team && p.position().distanceTo(spot) < 4);
                if (human) taker = false;
            }
            if (taker) {
                Vec3 target = ball.restartKind() == Ball.KICKOFF ? passTarget(r, ball, team, level, b) : attack;
                Vec3 dir = new Vec3(target.x - spot.x, 0, target.z - spot.z).normalize();
                Vec3 behind = spot.subtract(dir.scale(0.9));
                if (r.position().distanceTo(behind) > 0.8) {
                    r.getNavigation().moveTo(behind.x, behind.y, behind.z, 0.7);
                } else if (ball.restartTimerDone()) {
                    double power = switch (ball.restartKind()) {
                        case Ball.KICKOFF -> 0.35;
                        case Ball.GOAL_KICK -> 0.95;
                        case Ball.CORNER -> 0.7;
                        default -> Math.min(0.85, 0.35 + spot.distanceTo(target) * 0.03);
                    };
                    double lift = ball.restartKind() == Ball.GOAL_KICK || ball.restartKind() == Ball.CORNER ? 0.28 : 0.08;
                    ball.kick(r, dir.scale(power).add(0, lift, 0));
                }
            } else {
                // posição: na saída cada um no seu lugar; nas outras, perto do lugar mas longe (3 blocos) da cobrança
                Vec3 pos = ball.restartKind() == Ball.KICKOFF || ball.restartKind() < 0 ? home : home.lerp(spot, keeper ? 0 : 0.35);
                if (!ours && pos.distanceTo(spot) < 3.5) pos = spot.add(pos.subtract(spot).normalize().scale(3.5));
                r.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.6);
            }
            return true;
        }

        // ---- goleiro: fica no gol, acompanha a bola de lado e despacha quando ela chega
        if (keeper) {
            Vec3 axis = new Vec3(attack.x - own.x, 0, attack.z - own.z).normalize();
            Vec3 sideV = new Vec3(-axis.z, 0, axis.x);
            double lateral = Math.max(-1.6, Math.min(1.6, b.subtract(own).dot(sideV)));
            Vec3 pos = own.add(axis.scale(1.6)).add(sideV.scale(lateral));
            double d = r.position().distanceTo(b);
            if (d < 1.5 && ball.canKick(team)) {
                Vec3 dir = axis.add(sideV.scale((r.getRandom().nextDouble() - 0.5) * 0.8)).normalize();
                ball.kick(r, dir.scale(0.9).add(0, 0.3, 0));
            } else if (b.distanceTo(own) < 5) {
                r.getNavigation().moveTo(b.x, b.y, b.z, 0.8); // sai do gol para pegar
            } else {
                r.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.6);
            }
            return true;
        }

        // ---- linha: os dois mais perto da bola vão nela; os outros acompanham a jogada mantendo a posição
        var mates = level.getEntitiesOfClass(Raver.class, new AABB(b, b).inflate(48), m -> ball.teamOf(m) == team && m.stall != 1);
        mates.sort((p, q) -> Double.compare(p.distanceToSqr(b), q.distanceToSqr(b)));
        int rank = mates.indexOf(r);
        if (rank >= 2) {
            Vec3 pos = home.lerp(b, 0.4).add((r.getRandom().nextDouble() - 0.5) * 2, 0, (r.getRandom().nextDouble() - 0.5) * 2);
            r.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.55);
            return true;
        }
        double d = r.position().distanceTo(b);
        if (d < 1.4 && ball.canKick(team)) {
            // carrinho: disputa com o adversário colado na bola às vezes vira falta
            var rivals = level.getEntitiesOfClass(Raver.class, new AABB(b, b).inflate(1.2), m -> ball.teamOf(m) == 1 - team);
            if (!rivals.isEmpty() && r.getRandom().nextInt(16) == 0) {
                r.playSound(SoundEvents.VILLAGER_HURT, 0.8f, 1f);
                ball.foul(1 - team);
                return true;
            }
            double dist = b.distanceTo(attack);
            if (dist < 11) {
                // chute a gol, mirando num canto
                Vec3 axis = new Vec3(attack.x - b.x, 0, attack.z - b.z).normalize();
                Vec3 sideV = new Vec3(-axis.z, 0, axis.x);
                Vec3 aim = attack.add(sideV.scale((r.getRandom().nextBoolean() ? 1 : -1) * (0.4 + r.getRandom().nextDouble())));
                Vec3 dir = new Vec3(aim.x - b.x, 0, aim.z - b.z).normalize();
                ball.kick(r, dir.scale(0.62 + r.getRandom().nextDouble() * 0.15).add(0, 0.12 + r.getRandom().nextDouble() * 0.12, 0));
            } else {
                Vec3 target = r.getRandom().nextInt(3) == 0 ? passTarget(r, ball, team, level, b) : null;
                if (target != null) {
                    Vec3 dir = new Vec3(target.x - b.x, 0, target.z - b.z).normalize();
                    ball.kick(r, dir.scale(Math.min(0.75, 0.25 + b.distanceTo(target) * 0.045)).add(0, 0.05, 0));
                } else {
                    // conduz: toquinho para a frente, um pouco torto
                    double spread = (r.getRandom().nextDouble() - 0.5) * 0.6;
                    Vec3 dir = new Vec3(toGoal.x * Math.cos(spread) - toGoal.z * Math.sin(spread), 0, toGoal.x * Math.sin(spread) + toGoal.z * Math.cos(spread));
                    ball.kick(r, dir.scale(0.28).add(0, 0.03, 0));
                }
            }
        } else {
            Vec3 behind = b.subtract(toGoal.scale(0.8));
            r.getNavigation().moveTo(d > 2.5 ? behind.x : b.x, b.y, d > 2.5 ? behind.z : b.z, 0.75);
        }
        return true;
    }

    /** Companheiro mais adiantado (mais perto do gol adversário) para tocar a bola. */
    private static Vec3 passTarget(Raver r, Ball ball, int team, ServerLevel level, Vec3 from) {
        Vec3 attack = ball.goal(team == Ball.TEAM_A ? 1 : 0);
        Raver best = null;
        double bestScore = 1e9;
        for (Raver m : level.getEntitiesOfClass(Raver.class, new AABB(from, from).inflate(20), m -> m != r && ball.teamOf(m) == team && m.stall != 1)) {
            double score = m.position().distanceTo(attack) + m.position().distanceTo(from) * 0.3;
            if (m.position().distanceTo(from) > 3 && score < bestScore) { bestScore = score; best = m; }
        }
        return best == null ? attack : best.position();
    }
}
