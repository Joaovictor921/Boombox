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
    public static final int NONE = 0, DRUNK = 1, FEIRANTE = 2, TEAM_A = 3, TEAM_B = 4, BARTENDER = 5,
            VOLEI_A = 6, VOLEI_B = 7, LUAU = 8, VIP = 9, ORG_FUT = 10, ORG_VOLEI = 11;

    private RaverRoles() {}


    /** true = o papel cuidou do tick (o resto do comportamento normal não roda). */
    static boolean tick(Raver r, ServerLevel level) {
        if (r.queueSpot != null) return queue(r, level);
        if (r.settle > 0) r.settle--;
        if ((r.role() == NONE || r.role() == LUAU) && !r.atBar() && luau(r, level)) return true;
        if (r.role() == LUAU) {
            if (r.tickCount > 40) r.discard(); // convidado do luau sem luau: vai embora
            return true;
        }
        return switch (r.role()) {
            case VOLEI_A, VOLEI_B -> volley(r, level);
            case ORG_FUT, ORG_VOLEI -> organizer(r, level);
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

    /**
     * Anda até o ponto. Perto (até 4 blocos) vai reto, sem o "caminho" do Minecraft: o caminho achava que já tinha
     * chegado quando faltava 1 bloco e o aldeão ficava parado do lado da bola (a saída de bola nunca acontecia).
     */
    static void go(Raver r, double x, double y, double z, double speed) {
        double d = Math.hypot(x - r.getX(), z - r.getZ());
        if (d < 0.25) return;
        if (d < 4) {
            r.getNavigation().stop();
            r.getMoveControl().setWantedPosition(x, y, z, speed);
        } else {
            r.getNavigation().moveTo(x, y, z, speed);
        }
    }

    // ------------------------------------------------------------ futebol de várzea (3.4: bem mais realista)
    /**
     * Cada time joga num 2-1-1 (dois zagueiros, um meia, um atacante) mais o goleiro. O time "anda" junto com a bola
     * (sobe quando ataca, recua quando defende). Só um vai na bola de cada vez (o mais perto); o segundo mais perto dá
     * apoio (se oferece para o passe, ou faz a cobertura). Com a bola no pé o aldeão escolhe: chutar (perto do gol e
     * com espaço), tocar para um companheiro livre mais adiantado, ou conduzir. Sem a bola, quem chega dá o bote:
     * às vezes rouba, às vezes erra, às vezes faz falta (por trás é mais fácil ser falta).
     */
    private static boolean football(Raver r, ServerLevel level) {
        if (r.fieldHome == null) r.fieldHome = r.blockPosition();
        if (!r.getMainHandItem().isEmpty()) r.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        long t = level.getOverworldClockTime() % 24000;
        Ball ball = r.ball == null ? null : (level.getEntity(r.ball) instanceof Ball b && b.isAlive() ? b : null);
        if (ball == null && r.tickCount % 40 == 0) {
            var near = level.getEntitiesOfClass(Ball.class, new AABB(r.blockPosition()).inflate(40));
            if (!near.isEmpty()) r.ball = near.get(0).getUUID();
        }
        boolean day = t < 12300 || (ball != null && ball.mode() != Ball.AUTO);
        if (ball == null || !day || !ball.hasGoals()) {
            if (r.hasHome() && r.getHomePosition().distSqr(r.fieldHome) > 4) r.clearHome();
            if (!r.hasHome()) r.setHomeTo(r.fieldHome, 2);
            return true;
        }
        if (r.hasHome()) r.clearHome();
        if (r.tickCount % 3 != 0) return true;
        if (ball.isBenched(r)) {
            // deu lugar a uma pessoa: assiste do banco, na beira do campo
            Vec3 bench = ball.fieldPoint(ball.length() / 2 + (r.getId() % 5) - 2, ball.halfWidth() + 1.8);
            go(r, bench.x, bench.y, bench.z, 0.6);
            r.getLookControl().setLookAt(ball);
            return true;
        }
        int team = ball.teamOf(r);
        if (team < 0) return true;
        Vec3 attack = ball.goal(team == Ball.TEAM_A ? 1 : 0), own = ball.goal(team == Ball.TEAM_A ? 0 : 1);
        Vec3 b = ball.position();
        boolean keeper = r.stall == 1;
        r.getLookControl().setLookAt(b.x, b.y + 0.3, b.z);

        if (ball.restarting()) {
            restart(r, ball, level, team, attack, keeper);
            return true;
        }
        if (keeper) {
            goalkeeper(r, ball, level, team, attack, own);
            return true;
        }
        if (ball.keeperHold() != null) {
            // goleiro com a bola: os dois times voltam para a formação (e o adversário sai da área, 3.7.1)
            Vec3 slot = formationSlot(r, ball, level, team);
            int pg = ball.protectedGoal();
            if (pg >= 0 && pg != (team == Ball.TEAM_A ? 0 : 1)) slot = ball.outOfBox(pg, slot);
            go(r, slot.x, slot.y, slot.z, 0.6);
            return true;
        }

        // ---- quem vai na bola: o mais perto do time; o segundo dá apoio; o resto guarda posição
        var mates = level.getEntitiesOfClass(Raver.class, new AABB(b, b).inflate(48), m -> ball.teamOf(m) == team && m.stall != 1);
        mates.sort((p, q) -> Double.compare(p.distanceToSqr(b), q.distanceToSqr(b)));
        // 3.5.4: cada um na sua posição: só disputa a bola quem tem ela na sua zona (zagueiro não sobe, ponta fica aberta)
        var zoned = mates.stream().filter(m -> inZone(m, ball, team, b)).toList();
        double fBall = ball.uFor(team, b) / ball.length();
        // 3.6: fora da zona de todo mundo, vai só quem é do meio para a frente (o zagueiro nunca sobe atrás da bola)
        mates = new java.util.ArrayList<>(!zoned.isEmpty() ? zoned : mates.stream().filter(m -> !defender(m) || fBall < 0.4).toList());
        Raver chaser = mates.isEmpty() ? null : mates.get(0);
        int rank = mates.indexOf(r);
        if (rank < 0) rank = 99;
        if (rank == 1 && defender(r) && fBall > 0.4) rank = 99; // o zagueiro não vira apoio lá na frente
        if (defender(r) && fBall > 0.45) rank = 99; // e nem vai atrás da bola que passou do meio
        boolean humanNear = level.players().stream().anyMatch(p -> ball.teamOf(p) == team && p.position().distanceTo(b) < r.position().distanceTo(b));
        if (humanNear) rank++; // uma pessoa do time está mais perto: ela que vai na bola
        net.minecraft.world.entity.Entity carrierRival = null;
        for (Raver o : level.getEntitiesOfClass(Raver.class, new AABB(b, b).inflate(1.3), o -> ball.teamOf(o) == 1 - team)) carrierRival = o;
        if (ball.ownerTeam() == 1 - team) carrierRival = ball.owner(); // a pessoa do outro time com a bola no pé
        if (ball.ownerTeam() == team) rank = Math.max(rank, 1);       // a pessoa do nosso time está com ela: apoio
        boolean rivalHasIt = carrierRival != null;

        if (rank == 0) {
            double d = Math.hypot(b.x - r.getX(), b.z - r.getZ());
            double high = b.y - r.getY();
            if (high > 0.8) {
                // bola no alto: espera ela descer embaixo dela, ou cabeceia se dá
                if (d < 1.3 && high < 2.3 && ball.canKick(team) && r.settle == 0) {
                    header(r, ball, level, team, attack);
                } else {
                    Vec3 land = b.add(ball.getDeltaMovement().scale(Math.max(2, high * 4)));
                    if (defender(r) && ball.uFor(team, land) > ball.length() * 0.45) land = ball.teamPoint(team, ball.length() * 0.45, ball.vFor(team, land));
                    go(r, land.x, r.getY(), land.z, 0.95);
                }
                return true;
            }
            if (d < 1.35 && ball.canKick(team)) {
                // a bola é de quem chega primeiro: só dá o bote se o adversário está mais perto dela
                boolean rivalCloser = rivalHasIt && (carrierRival.position().distanceTo(b) + 0.15 < d || ball.freshFor(team) || ball.ownerTeam() == 1 - team);
                if (rivalCloser && r.settle == 0) {
                    tackle(r, ball, level, team, carrierRival, attack);
                } else if (r.settle == 0) {
                    onTheBall(r, ball, level, team, attack, own);
                }
            } else {
                // corre para a bola, chegando por trás dela (do lado do próprio gol)
                Vec3 toGoal = new Vec3(attack.x - b.x, 0, attack.z - b.z).normalize();
                Vec3 lead = b.add(ball.getDeltaMovement().scale(6));
                Vec3 target = d > 2.5 ? lead.subtract(toGoal.scale(0.7)) : b;
                if (defender(r) && ball.uFor(team, target) > ball.length() * 0.45) {
                    target = ball.teamPoint(team, ball.length() * 0.45, ball.vFor(team, target));
                }
                go(r, target.x, b.y, target.z, 0.95);
            }
            return true;
        }
        if (rank == 1) {
            // apoio: com a bola do nosso lado, se oferece adiantado e aberto; senão, cobre entre a bola e o gol
            boolean ours = !rivalHasIt && ball.getDeltaMovement().horizontalDistance() < 0.6;
            Vec3 spot;
            if (ours) {
                double uB = ball.uFor(team, b), vB = ball.v(b);
                double vOff = (team == Ball.TEAM_A ? 1 : -1) * (vB > 0 ? -4.5 : 4.5);
                spot = ball.teamPoint(team, Math.min(ball.length() - 3, uB + 5), Math.max(-ball.halfWidth() + 1, Math.min(ball.halfWidth() - 1, (team == Ball.TEAM_A ? vB : -vB) + vOff)));
            } else {
                spot = b.add(own.subtract(b).normalize().scale(4));
            }
            go(r, spot.x, spot.y, spot.z, 0.8);
            return true;
        }
        // 3.6: marcação: quando o outro time ataca, cada um pega um adversário da sua zona (pelo lado do próprio gol)
        boolean defending = rivalHasIt || (ball.ownerTeam() != team && ball.lastTeam() != team);
        if (defending) {
            Vec3 mark = markSpot(r, ball, level, team, own, chaser, carrierRival);
            if (mark != null) {
                go(r, mark.x, mark.y, mark.z, 0.8);
                return true;
            }
        }
        Vec3 slot = formationSlot(r, ball, level, team);
        if (r.position().distanceTo(slot) > 1.2) go(r, slot.x, slot.y, slot.z, 0.65);
        return true;
    }

    /**
     * 3.7: chute da falta por cima da barreira: procura (simulando a bola) uma força e uma altura que passem a barreira
     * (3,5 blocos, uns 2 de altura) e caiam no gol, mirando num canto. Às vezes não dá e bate na barreira.
     */
    private static Vec3 overTheWall(Raver r, Ball ball, Vec3 spot, int team) {
        var rnd = r.getRandom();
        Vec3 g = ball.goal(team == Ball.TEAM_A ? 1 : 0);
        Vec3 axis = new Vec3(g.x - spot.x, 0, g.z - spot.z).normalize();
        Vec3 sideV = new Vec3(-axis.z, 0, axis.x);
        Vec3 aim = g.add(sideV.scale((rnd.nextBoolean() ? 1 : -1) * (0.6 + rnd.nextDouble() * 0.8)));
        Vec3 dir = new Vec3(aim.x - spot.x, 0, aim.z - spot.z);
        double dist = dir.length();
        dir = dir.normalize();
        Vec3 best = null;
        for (int tries = 0; tries < 150 && best == null; tries++) {
            double h = 0.3 + rnd.nextDouble() * 0.45, vy = 0.5 + rnd.nextDouble() * 0.4;
            double x = 0, y = 0, vh = h, v = vy, atWall = 99, atGoal = -1;
            for (int t = 0; t < 80 && x < dist; t++) {
                v -= 0.045;
                x += vh;
                y += v;
                vh *= 0.99;
                v *= 0.98;
                if (x >= 2.6 && x <= 4.4) atWall = Math.min(atWall, y); // a barreira trava numa faixa, não num ponto
                if (x >= dist) atGoal = y;
                if (y < -0.5) break;
            }
            if (atWall < 99 && atWall > 2.1 && atGoal > 0.2 && atGoal < 2.7) best = dir.scale(h).add(0, vy, 0);
        }
        // não achou (ou "errou" de propósito, 25%): chute forte rasteiro, que pode bater na barreira
        if (best == null || rnd.nextDouble() < 0.15) best = dir.scale(1.0).add(0, 0.1 + rnd.nextDouble() * 0.1, 0);
        return best;
    }

    /** Zagueiro (fica atrás, não passa do meio). */
    static boolean defender(Raver m) {
        return m.stall == 3 || m.stall == 4 || m.stall == 12;
    }

    /** Ordem de quem escolhe primeiro quem marcar: zagueiro, laterais, volante, meias, pontas (o atacante não marca). */
    private static int markOrder(Raver m) {
        return switch (m.stall) {
            case 3, 4, 12 -> 0;
            case 2, 5 -> 1;
            case 6 -> 2;
            case 7, 8, 13 -> 3;
            case 9, 10, 14 -> 4;
            default -> 9;
        };
    }

    /** Onde cada posição marca (fração do campo a partir do próprio gol, e o lado): é a zona de defesa dela. */
    private static boolean markZone(Raver m, Ball ball, int team, Vec3 at) {
        double f = ball.uFor(team, at) / ball.length(), side = ball.vFor(team, at), hw = ball.halfWidth();
        return switch (m.stall) {
            case 3, 4, 12 -> f <= 0.45;
            case 2 -> f <= 0.65 && side <= hw * 0.3;
            case 5 -> f <= 0.65 && side >= -hw * 0.3;
            case 6 -> f <= 0.6;
            case 7, 8, 13 -> f >= 0.1 && f <= 0.75;
            case 9, 14 -> f >= 0.15 && f <= 0.65 && side <= hw * 0.4;
            case 10 -> f >= 0.15 && f <= 0.65 && side >= -hw * 0.4;
            default -> false;
        };
    }

    /**
     * 3.6: lugar de marcação desse aldeão: os do time, na ordem (zagueiro primeiro), pegam o adversário mais perigoso
     * (o mais perto do gol) que está na zona deles, e ficam entre ele e o gol. null = ninguém para marcar.
     */
    private static Vec3 markSpot(Raver r, Ball ball, ServerLevel level, int team, Vec3 own, Raver chaser, net.minecraft.world.entity.Entity carrier) {
        if (markOrder(r) >= 9) return null;
        Vec3 c = ball.center();
        var line = level.getEntitiesOfClass(Raver.class, new AABB(c, c).inflate(48),
                m -> ball.teamOf(m) == team && m.stall != 1 && m != chaser && markOrder(m) < 9);
        line.sort((p, q) -> markOrder(p) != markOrder(q) ? markOrder(p) - markOrder(q) : p.stall - q.stall);
        java.util.List<net.minecraft.world.entity.Entity> rivals = new java.util.ArrayList<>(
                level.getEntitiesOfClass(Raver.class, new AABB(c, c).inflate(48), o -> ball.teamOf(o) == 1 - team && o.stall != 1));
        rivals.addAll(ball.humansOf(level, 1 - team));
        if (carrier != null) rivals.remove(carrier); // esse é do que vai na bola
        rivals.sort((p, q) -> Double.compare(ball.uFor(team, p.position()), ball.uFor(team, q.position())));
        java.util.Set<net.minecraft.world.entity.Entity> taken = new java.util.HashSet<>();
        for (Raver m : line) {
            net.minecraft.world.entity.Entity pick = null;
            for (var o : rivals) {
                if (taken.contains(o) || !markZone(m, ball, team, o.position())) continue;
                pick = o;
                break;
            }
            if (pick == null) continue;
            taken.add(pick);
            if (m != r) continue;
            Vec3 o = pick.position();
            Vec3 toGoal = new Vec3(own.x - o.x, 0, own.z - o.z).normalize();
            Vec3 spot = o.add(toGoal.scale(1.6));
            double u = ball.uFor(team, spot), v = ball.vFor(team, spot);
            if (defender(r)) u = Math.min(u, ball.length() * 0.45);
            u = Math.max(1.5, u);
            return ball.teamPoint(team, u, Math.max(-ball.halfWidth() + 0.8, Math.min(ball.halfWidth() - 0.8, v)));
        }
        return null;
    }

    /**
     * 3.5: posições do 4-3-3 (stall = número da posição): 2 lateral esquerdo, 3 e 4 zagueiros, 5 lateral direito,
     * 6 volante, 7 e 8 meias, 9 ponta esquerda, 10 ponta direita, 11 centroavante. Para cada uma: u quando o time
     * defende, u quando ataca (fração do campo a partir do próprio gol) e o lado (fração da meia largura).
     */
    private static final double[][] POS433 = {
            {}, {}, {0.16, 0.52, -0.82}, {0.1, 0.34, -0.3}, {0.1, 0.34, 0.3}, {0.16, 0.52, 0.82},
            {0.24, 0.47, 0}, {0.33, 0.62, -0.38}, {0.33, 0.62, 0.38}, {0.45, 0.82, -0.82}, {0.45, 0.82, 0.82}, {0.55, 0.86, 0},
            {0.13, 0.36, 0}, {0.3, 0.56, 0.15}, {0.42, 0.78, -0.82}, {0.55, 0.86, 0.2}};
    public static final String[] POS_NAMES = {"", "Goleiro", "Lateral esquerdo", "Zagueiro", "Zagueiro", "Lateral direito", "Volante",
            "Meia", "Meia", "Ponta esquerda", "Ponta direita", "Centroavante"};

    /**
     * Zona de cada posição (fração do campo a partir do próprio gol, e o lado): zagueiro até o meio, laterais e pontas
     * do lado deles, atacante só da intermediária para a frente. Campos antigos (sem posição) valem o campo todo.
     */
    static boolean inZone(Raver m, Ball ball, int team, Vec3 at) {
        double f = ball.uFor(team, at) / ball.length(), side = ball.vFor(team, at), hw = ball.halfWidth();
        double margin = 0.08;
        return switch (m.stall) {
            case 3, 4, 12 -> f <= 0.45; // 3.6: zagueiro não passa do meio, sem folga
            case 2 -> f <= 0.7 && side <= hw * 0.2;
            case 5 -> f <= 0.7 && side >= -hw * 0.2;
            case 6 -> f >= 0.1 - margin && f <= 0.7 + margin;
            case 7, 8, 13 -> f >= 0.2 - margin && f <= 0.9 + margin;
            case 9, 14 -> f >= 0.35 - margin && side <= hw * 0.25;
            case 10 -> f >= 0.35 - margin && side >= -hw * 0.25;
            case 11, 15 -> f >= 0.4 - margin;
            default -> true;
        };
    }

    /** Ponta / lateral (cobra o lateral do lado dele). */
    private static boolean wide(Raver m) {
        return m.stall == 2 || m.stall == 5 || m.stall == 9 || m.stall == 10 || m.stall == 14;
    }

    /** Lugar na formação que acompanha a bola: o time sobe quando tem a bola e recua quando defende. */
    private static Vec3 formationSlot(Raver r, Ball ball, ServerLevel level, int team) {
        if (r.stall >= 2 && r.stall <= 15) {
            double[] s = POS433[r.stall];
            double L = ball.length(), hw = ball.halfWidth();
            boolean attacking = ball.ownerTeam() == team || ball.lastTeam() == team;
            double uBall = ball.uFor(team, ball.position()) / L;
            double base = attacking ? s[1] : s[0];
            double u = (base + (uBall - 0.5) * 0.3) * L;
            double v = s[2] * hw + ball.vFor(team, ball.position()) * 0.25;
            // um pouco de movimento (ninguém fica parado feito poste)
            double t = (r.tickCount + r.getId() * 37) * 0.02;
            u += Math.sin(t) * 1.2;
            v += Math.cos(t * 0.8) * 1.0;
            u = Math.max(2, Math.min(L - 2, u));
            if (defender(r)) u = Math.min(u, L * 0.42);
            if (r.stall == 2 || r.stall == 5) u = Math.min(u, L * 0.65);
            v = Math.max(-hw + 0.8, Math.min(hw - 0.8, v));
            return ball.teamPoint(team, u, v);
        }
        var line = level.getEntitiesOfClass(Raver.class, new AABB(ball.center(), ball.center()).inflate(48),
                m -> ball.teamOf(m) == team && m.stall != 1 && m.fieldHome != null);
        Vec3 own = ball.goal(team == Ball.TEAM_A ? 0 : 1);
        line.sort((p, q) -> Double.compare(Vec3.atCenterOf(p.fieldHome).distanceTo(own), Vec3.atCenterOf(q.fieldHome).distanceTo(own)));
        int i = Math.max(0, line.indexOf(r));
        double[][] shape = {{0.2, -0.45}, {0.2, 0.45}, {0.42, 0}, {0.62, 0}, {0.5, -0.6}, {0.5, 0.6}};
        double[] s = shape[Math.min(shape.length - 1, i)];
        double L = ball.length(), uBall = ball.uFor(team, ball.position()) / L;
        double u = (s[0] + (uBall - 0.5) * 0.5) * L;
        double v = s[1] * ball.halfWidth() + (team == Ball.TEAM_A ? 1 : -1) * ball.v(ball.position()) * 0.3;
        u = Math.max(2, Math.min(L - 2, u));
        v = Math.max(-ball.halfWidth() + 1, Math.min(ball.halfWidth() - 1, v));
        return ball.teamPoint(team, u, v);
    }

    /** Aldeão com a bola no pé: chuta, toca ou conduz. */
    private static void onTheBall(Raver r, Ball ball, ServerLevel level, int team, Vec3 attack, Vec3 own) {
        Vec3 b = ball.position();
        var rnd = r.getRandom();
        double distGoal = Math.hypot(attack.x - b.x, attack.z - b.z);
        var rivals = level.getEntitiesOfClass(Raver.class, new AABB(b, b).inflate(10), o -> ball.teamOf(o) == 1 - team);
        Vec3 toGoal = new Vec3(attack.x - b.x, 0, attack.z - b.z).normalize();
        Raver closest = null;
        double cd = 99;
        for (Raver o : rivals) {
            double dd = o.position().distanceTo(b);
            if (dd < cd) { cd = dd; closest = o; }
        }
        boolean pressed = cd < 2.2;
        // 1) chute a gol: perto e com o caminho livre (ou já dentro da área)
        double shootChance = distGoal < 7 ? 0.8 : distGoal < 11 ? 0.45 : distGoal < 15 ? 0.12 : 0;
        if (blocked(b, attack, rivals, 0.9)) shootChance *= 0.4;
        if (rnd.nextDouble() < shootChance) {
            Vec3 sideV = new Vec3(-toGoal.z, 0, toGoal.x);
            // mira no canto do lado contrário de onde o goleiro está
            double keeperSide = 0;
            for (Raver k : level.getEntitiesOfClass(Raver.class, new AABB(attack, attack).inflate(6), k -> ball.teamOf(k) == 1 - team && k.stall == 1)) {
                keeperSide = k.position().subtract(attack).dot(sideV);
            }
            double corner = keeperSide == 0 ? (rnd.nextBoolean() ? 1 : -1) : -Math.signum(keeperSide);
            Vec3 aim = attack.add(sideV.scale(corner * (1.0 + rnd.nextDouble() * 1.2)));
            Vec3 dir = new Vec3(aim.x - b.x, 0, aim.z - b.z).normalize();
            double power = 0.8 + Math.min(0.45, distGoal * 0.03) + rnd.nextDouble() * 0.15;
            double lift = 0.03 + rnd.nextDouble() * 0.07 + (distGoal > 12 ? 0.05 : 0);
            ball.kick(r, dir.scale(power).add(0, lift, 0));
            r.settle = 6;
            return;
        }
        // 2) passe: companheiro livre e mais adiantado (ou qualquer um livre, se está apertado)
        double bestScore = -1e9, myU = ball.uFor(team, b);
        java.util.List<net.minecraft.world.entity.Entity> cands = new java.util.ArrayList<>(
                level.getEntitiesOfClass(Raver.class, new AABB(b, b).inflate(26), m -> m != r && ball.teamOf(m) == team));
        cands.addAll(ball.humansOf(level, team));
        net.minecraft.world.entity.Entity best = null;
        for (net.minecraft.world.entity.Entity m : cands) {
            double dist = m.position().distanceTo(b);
            if (dist < 3 || dist > 24) continue;
            if (blocked(b, m.position(), rivals, 1.4)) continue;
            double gain = ball.uFor(team, m.position()) - myU;
            double free = 9;
            for (Raver o : rivals) free = Math.min(free, o.position().distanceTo(m.position()));
            boolean keeperM = m instanceof Raver rm && rm.stall == 1;
            double score = gain * 1.2 + free - dist * 0.15 - (keeperM ? 6 : 0) + (m instanceof net.minecraft.world.entity.player.Player ? 4 : 0);
            if (score > bestScore) { bestScore = score; best = m; }
        }
        // cruzamento: ponta (ou quem estiver aberto) no último terço manda a bola na área
        double vMe = ball.vFor(team, b);
        if (ball.uFor(team, b) > ball.length() * 0.7 && Math.abs(vMe) > ball.halfWidth() * 0.55 && rnd.nextDouble() < 0.5) {
            net.minecraft.world.entity.Entity target = null;
            double tb = 1e9;
            for (net.minecraft.world.entity.Entity m : cands) {
                if (!ball.inBox(team == Ball.TEAM_A ? 1 : 0, m.position())) continue;
                double sc = m.position().distanceTo(attack) - (m instanceof net.minecraft.world.entity.player.Player ? 3 : 0);
                if (sc < tb) { tb = sc; target = m; }
            }
            Vec3 to = target != null ? target.position() : ball.teamPoint(team, ball.length() - 4.5, (rnd.nextDouble() - 0.5) * 3);
            double dist = Math.hypot(to.x - b.x, to.z - b.z);
            Vec3 dir = new Vec3(to.x - b.x, 0, to.z - b.z).normalize();
            ball.kick(r, dir.scale(Math.min(1.05, 0.32 + dist * 0.045)).add(0, 0.3, 0));
            r.settle = 8;
            if (rnd.nextInt(3) == 0) level.players().stream().filter(p -> p.distanceTo(r) < 50).forEach(p ->
                    p.sendOverlayMessage(Component.literal("§eCruzou na área!")));
            return;
        }
        boolean wantPass = best != null && (pressed ? bestScore > -6 : bestScore > 3 && rnd.nextInt(3) == 0);
        if (best instanceof net.minecraft.world.entity.player.Player && bestScore > 0) wantPass = true; // tocou para você!
        if (wantPass) {
            Vec3 to = best.position().add(best.getDeltaMovement().scale(8));
            Vec3 vel = Ball.passTo(b, to);
            if (b.distanceTo(to) > 17 && rnd.nextInt(4) == 0) vel = vel.add(0, 0.22, 0).multiply(0.85, 1, 0.85); // lançamento pelo alto
            ball.kick(r, vel.add((rnd.nextDouble() - 0.5) * 0.06, 0, (rnd.nextDouble() - 0.5) * 0.06));
            r.settle = 8;
            return;
        }
        // 3) apertado e sem passe: chutão para frente (tira do perigo)
        if (pressed && ball.uFor(team, b) < ball.length() * 0.35) {
            Vec3 dir = toGoal.add((rnd.nextDouble() - 0.5) * 0.8, 0, (rnd.nextDouble() - 0.5) * 0.8).normalize();
            ball.kick(r, dir.scale(0.8).add(0, 0.18, 0));
            r.settle = 6;
            return;
        }
        // 4) marcador colado na frente: tenta o drible (bola para o lado e passa por ele) ou arrisca de longe
        if (pressed && closest != null) {
            Vec3 toRival = new Vec3(closest.getX() - b.x, 0, closest.getZ() - b.z).normalize();
            if (toRival.dot(toGoal) > 0.2) {
                if (rnd.nextDouble() < 0.25 && distGoal < 20) {
                    ball.kick(r, toGoal.scale(0.95).add(0, 0.25, 0)); // chute de longe
                    r.settle = 6;
                    return;
                }
                Vec3 sideV = new Vec3(-toGoal.z, 0, toGoal.x);
                if (sideV.dot(toRival) > 0) sideV = sideV.scale(-1);
                ball.kick(r, sideV.scale(0.4).add(toGoal.scale(0.28)).add(0, 0.03, 0));
                if (rnd.nextDouble() < 0.6) {
                    closest.settle = 20; // passou pelo marcador!
                    if (rnd.nextInt(4) == 0) level.players().stream().filter(p -> p.distanceTo(r) < 40).forEach(p ->
                            p.sendOverlayMessage(Component.literal("§eQue drible!")));
                }
                r.settle = 3;
                return;
            }
        }
        // 3.6: zagueiro não sai driblando: toca para alguém ou manda para frente
        if (defender(r) && ball.uFor(team, b) > ball.length() * 0.25) {
            if (best != null && bestScore > -8) {
                ball.kick(r, Ball.passTo(b, best.position().add(best.getDeltaMovement().scale(8))));
            } else {
                Vec3 dir = toGoal.add((rnd.nextDouble() - 0.5) * 0.5, 0, (rnd.nextDouble() - 0.5) * 0.5).normalize();
                ball.kick(r, dir.scale(0.85).add(0, 0.16, 0));
            }
            r.settle = 8;
            return;
        }
        // 5) conduz: toquinho para frente, desviando do marcador mais perto
        Vec3 dir = toGoal;
        if (closest != null && cd < 4) {
            Vec3 away = new Vec3(b.x - closest.getX(), 0, b.z - closest.getZ()).normalize();
            dir = toGoal.scale(0.7).add(away.scale(0.5)).normalize();
        }
        ball.kick(r, dir.scale(0.3 + rnd.nextDouble() * 0.06).add(0, 0.02, 0));
        r.settle = 2;
    }

    /** Tem adversário no caminho (perto da linha entre a e b)? */
    private static boolean blocked(Vec3 a, Vec3 b, java.util.List<Raver> rivals, double radius) {
        Vec3 ab = new Vec3(b.x - a.x, 0, b.z - a.z);
        double len = ab.length();
        if (len < 1e-3) return false;
        Vec3 dir = ab.scale(1 / len);
        for (Raver o : rivals) {
            Vec3 ao = new Vec3(o.getX() - a.x, 0, o.getZ() - a.z);
            double t = ao.dot(dir);
            if (t < 0.5 || t > len - 0.5) continue;
            if (ao.subtract(dir.scale(t)).length() < radius) return true;
        }
        return false;
    }

    /** Cabeçada: na área do adversário vai para o gol; senão, ajeita para um companheiro ou afasta. */
    private static void header(Raver r, Ball ball, ServerLevel level, int team, Vec3 attack) {
        Vec3 b = ball.position();
        Vec3 dir;
        double speed;
        if (ball.inBox(team == Ball.TEAM_A ? 1 : 0, b)) {
            Vec3 sideV = new Vec3(-(attack.z - b.z), 0, attack.x - b.x).normalize();
            Vec3 aim = attack.add(sideV.scale((r.getRandom().nextBoolean() ? 1 : -1) * (0.8 + r.getRandom().nextDouble())));
            dir = new Vec3(aim.x - b.x, 0, aim.z - b.z).normalize();
            speed = 0.7;
        } else {
            Vec3 to = nearestMate(r, ball, level, team, b);
            dir = new Vec3(to.x - b.x, 0, to.z - b.z).normalize();
            speed = Math.min(0.6, 0.25 + to.distanceTo(b) * 0.03);
        }
        ball.kick(r, dir.scale(speed).add(0, -0.08, 0));
        r.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        r.settle = 6;
    }

    /** Bote em quem está com a bola: rouba, erra ou faz falta (por trás, mais falta). */
    private static void tackle(Raver r, Ball ball, ServerLevel level, int team, net.minecraft.world.entity.Entity rival, Vec3 attack) {
        if (rival instanceof net.minecraft.world.entity.player.Player pl) {
            // pessoa com a bola no pé: o aldeão tenta tirar (às vezes consegue, às vezes faz falta)
            double roll = r.getRandom().nextDouble();
            if (roll < 0.06) {
                r.playSound(SoundEvents.VILLAGER_HURT, 0.8f, 1f);
                ball.foul(1 - team, rival);
                r.settle = 20;
            } else if (roll < 0.36) {
                ball.loseOwner();
                Vec3 toGoal = new Vec3(attack.x - r.getX(), 0, attack.z - r.getZ()).normalize();
                ball.kick(r, toGoal.scale(0.35).add(new Vec3(-toGoal.z, 0, toGoal.x).scale(r.getRandom().nextBoolean() ? 0.3 : -0.3)).add(0, 0.04, 0));
                r.settle = 2;
                pl.sendOverlayMessage(Component.literal("§cTomou a bola!"));
            } else {
                r.settle = 16;
            }
            return;
        }
        var rnd = r.getRandom();
        Vec3 rivalFacing = rival.getLookAngle();
        Vec3 fromMe = new Vec3(rival.getX() - r.getX(), 0, rival.getZ() - r.getZ()).normalize();
        boolean behind = fromMe.dot(new Vec3(rivalFacing.x, 0, rivalFacing.z).normalize()) > 0.4;
        double roll = rnd.nextDouble();
        double foul = behind ? 0.12 : 0.025;
        if (roll < foul) {
            r.playSound(SoundEvents.VILLAGER_HURT, 0.8f, 1f);
            ball.foul(1 - team, rival);
            r.settle = 20;
        } else if (roll < foul + 0.45) {
            // roubou: tira a bola na diagonal, para longe de quem estava com ela (que fica para trás um pouco)
            Vec3 toGoal = new Vec3(attack.x - r.getX(), 0, attack.z - r.getZ()).normalize();
            Vec3 sideV = new Vec3(-toGoal.z, 0, toGoal.x);
            Vec3 fromRival = new Vec3(ball.getX() - rival.getX(), 0, ball.getZ() - rival.getZ());
            if (sideV.dot(fromRival) < 0) sideV = sideV.scale(-1);
            ball.kick(r, toGoal.scale(0.3).add(sideV.scale(0.4)).add(0, 0.04, 0));
            if (rival instanceof Raver rr) rr.settle = 22;
            r.settle = 2;
        } else {
            r.settle = 24; // errou o bote: o outro passou
            if (rival instanceof Raver rr) rr.settle = 0;
        }
    }

    /** Goleiro: fica entre a bola e o gol; voa na bola quando chutam; agarra e repõe. */
    private static void goalkeeper(Raver r, Ball ball, ServerLevel level, int team, Vec3 attack, Vec3 own) {
        Vec3 b = ball.position();
        Vec3 axis = new Vec3(attack.x - own.x, 0, attack.z - own.z).normalize();
        Vec3 sideV = new Vec3(-axis.z, 0, axis.x);
        if (r.getUUID().equals(ball.keeperHold())) {
            // com a bola nas mãos: espera um pouco e repõe (mão para um companheiro livre, ou chutão)
            if (ball.holdTicks() < 40) return;
            Raver best = null;
            double bestD = 1e9;
            var rivals = level.getEntitiesOfClass(Raver.class, new AABB(b, b).inflate(30), o -> ball.teamOf(o) == 1 - team);
            for (Raver m : level.getEntitiesOfClass(Raver.class, new AABB(b, b).inflate(20), m -> m != r && ball.teamOf(m) == team)) {
                if (blocked(b, m.position(), rivals, 1.6)) continue;
                double dd = m.position().distanceTo(b);
                if (dd > 4 && dd < bestD) { bestD = dd; best = m; }
            }
            if (best != null) ball.kick(r, Ball.passTo(b, best.position()));
            else ball.kick(r, axis.add(sideV.scale((r.getRandom().nextDouble() - 0.5) * 0.9)).normalize().scale(0.8).add(0, 0.2, 0));
            return;
        }
        Vec3 vel = ball.getDeltaMovement();
        double toward = -vel.dot(axis); // bola vindo na direção do nosso gol
        double distGoal = b.distanceTo(own);
        if (toward > 0.3 && distGoal < 12) {
            // chute! onde a bola cruza a linha do goleiro (1 bloco à frente do gol)
            double along = b.subtract(own).dot(axis) - 1.0;
            double tt = along / Math.max(0.05, toward);
            Vec3 cross = b.add(vel.scale(tt));
            double lat = cross.subtract(own).dot(sideV);
            Vec3 dive = own.add(axis.scale(1.0)).add(sideV.scale(Math.max(-2.2, Math.min(2.2, lat))));
            go(r, dive.x, dive.y, dive.z, 1.2);
            double reach = Math.hypot(b.x - r.getX(), b.z - r.getZ());
            // voa na bola: pulo de lado na direção do chute
            double myLat = r.position().subtract(own).dot(sideV);
            if (reach < 3.4 && r.onGround() && r.settle == 0 && Math.abs(lat - myLat) > 0.5) {
                double dir = Math.signum(lat - myLat);
                r.setDeltaMovement(sideV.scale(dir * 0.5).add(0, 0.42, 0));
                r.setDive((int) dir);
            }
            if (reach < 1.8 && b.y - r.getY() < 2.6 && r.settle == 0) {
                double speed = vel.horizontalDistance();
                double low = b.y - own.y < 1.0 ? 0.22 : 0; // 3.7.1: rasteira o goleiro pega mais (só a bomba no alto entra fácil)
                double chance = Math.max(0.15, Math.min(0.92, 0.95 - speed * 0.35 - Math.abs(lat) * 0.12 + low));
                r.settle = 25;
                if (r.getRandom().nextDouble() < chance) {
                    if (r.onGround()) r.jumpFromGround();
                    if (speed < 0.9) {
                        ball.catchBall(r); // agarrou!
                    } else {
                        // espalmou para o lado
                        ball.kick(r, sideV.scale(Math.signum(lat == 0 ? 1 : lat) * 0.5).add(axis.scale(0.3)).add(0, 0.25, 0));
                    }
                    level.players().stream().filter(p -> p.distanceTo(r) < 40).forEach(p ->
                            p.sendOverlayMessage(Component.literal("§b§lQUE DEFESA! §r§fO goleiro pegou!")));
                }
            }
            return;
        }
        // bola solta perto da área e ninguém do outro time mais perto: sai para pegar
        boolean loose = vel.horizontalDistance() < 0.35 && distGoal < 3.5; // 3.5.3: goleiro fixo (só pega a que sobra na frente dele)
        if (loose) {
            double mine = r.position().distanceTo(b);
            boolean rivalCloser = level.getEntitiesOfClass(Raver.class, new AABB(b, b).inflate(mine), o -> ball.teamOf(o) == 1 - team).size() > 0;
            if (!rivalCloser) {
                go(r, b.x, b.y, b.z, 0.9);
                if (mine < 1.3 && ball.canKick(team)) ball.catchBall(r);
                return;
            }
        }
        // posição: na linha entre a bola e o centro do gol, um pouco à frente
        double lat = Math.max(-1.6, Math.min(1.6, b.subtract(own).dot(sideV) * 0.35));
        double out = 1.0;
        Vec3 pos = own.add(axis.scale(out)).add(sideV.scale(lat));
        if (r.position().distanceTo(pos) > 0.4) go(r, pos.x, pos.y, pos.z, 0.7);
    }

    /** Bola parada: o mais perto do time que cobra vai cobrar; os outros se posicionam (barreira longe 3 blocos). */
    private static void restart(Raver r, Ball ball, ServerLevel level, int team, Vec3 attack, boolean keeper) {
        Vec3 b = ball.position();
        Vec3 spot = ball.restartSpot() == null ? b : ball.restartSpot();
        int kind = ball.restartKind();
        boolean ours = kind >= 0 && ball.restartTeam() == team;
        boolean taker = false;
        if (ours) {
            boolean gk = kind == Ball.GOAL_KICK;
            var mates = level.getEntitiesOfClass(Raver.class, new AABB(spot, spot).inflate(48),
                    m -> ball.teamOf(m) == team && (gk ? m.stall == 1 : m.stall != 1));
            mates.sort((p, q) -> Double.compare(p.distanceToSqr(spot), q.distanceToSqr(spot)));
            if (kind == Ball.THROW_IN) {
                // lateral: quem cobra é o ponta (ou o lateral) daquele lado
                double sideSpot = ball.vFor(team, spot);
                var wings = mates.stream().filter(m -> wide(m) && Math.signum(POS433[m.stall][2]) == Math.signum(sideSpot)).toList();
                if (!wings.isEmpty()) mates = new java.util.ArrayList<>(wings);
            }
            taker = !mates.isEmpty() && mates.get(0) == r;
            java.util.UUID fouled = ball.restartTaker();
            if (fouled != null && level.getEntity(fouled) != null && level.getEntity(fouled).isAlive()) {
                taker = fouled.equals(r.getUUID()); // quem sofreu a falta é quem bate
            } else {
                boolean human = level.players().stream().anyMatch(p -> ball.teamOf(p) == team && p.position().distanceTo(spot) < 4);
                if (human) taker = false;
            }
        }
        // 3.7: barreira da falta
        if (!ours && kind == Ball.FREE_KICK && ball.wallIndex(r) >= 0) {
            Vec3 w = ball.wallSpot(ball.wallIndex(r));
            if (r.position().distanceTo(w) > 0.3) go(r, w.x, w.y, w.z, 1.0);
            r.getLookControl().setLookAt(spot.x, spot.y + 0.5, spot.z);
            return;
        }
        if (taker) {
            Vec3 target = switch (kind) {
                case Ball.KICKOFF, Ball.THROW_IN -> nearestMate(r, ball, level, team, spot);
                case Ball.GOAL_KICK -> nearestMate(r, ball, level, team, spot);
                case Ball.CORNER -> ball.goal(team == Ball.TEAM_A ? 1 : 0).add(ball.getLookAngle().scale(0)).lerp(spot, 0.25);
                default -> attack;
            };
            Vec3 dir = new Vec3(target.x - spot.x, 0, target.z - spot.z).normalize();
            Vec3 behind = spot.subtract(dir.scale(0.9));
            boolean placed = r.position().distanceTo(behind) <= 0.8;
            boolean overdue = ball.restartOverdue() && Math.hypot(r.getX() - spot.x, r.getZ() - spot.z) < 1.8;
            if (!placed && !overdue) {
                go(r, ball.restartOverdue() ? spot.x : behind.x, behind.y, ball.restartOverdue() ? spot.z : behind.z, 0.75);
            } else if (ball.restartTimerDone()) {
                Vec3 vel;
                if (kind == Ball.PENALTY) {
                    // pênalti: num dos cantos
                    Vec3 g = ball.goal(team == Ball.TEAM_A ? 1 : 0);
                    Vec3 sideV = new Vec3(-dir.z, 0, dir.x);
                    Vec3 aim = g.add(sideV.scale((r.getRandom().nextBoolean() ? 1 : -1) * (0.7 + r.getRandom().nextDouble() * 0.7)));
                    vel = new Vec3(aim.x - spot.x, 0, aim.z - spot.z).normalize().scale(1.0).add(0, 0.08 + r.getRandom().nextDouble() * 0.15, 0);
                } else if (kind == Ball.KICKOFF || kind == Ball.THROW_IN) {
                    vel = Ball.passTo(spot, target).add(0, kind == Ball.THROW_IN ? 0.2 : 0, 0);
                } else if (kind == Ball.GOAL_KICK) {
                    vel = Ball.passTo(spot, target);
                } else if (kind == Ball.CORNER) {
                    vel = dir.scale(Math.min(1.0, 0.35 + spot.distanceTo(target) * 0.06)).add(0, 0.35, 0);
                } else if (kind == Ball.FREE_KICK && ball.wallCount() > 0) {
                    vel = overTheWall(r, ball, spot, team);
                } else {
                    vel = dir.scale(Math.min(1.0, 0.45 + spot.distanceTo(target) * 0.035)).add(0, 0.12, 0);
                }
                ball.kick(r, vel);
            }
            return;
        }
        Vec3 pos;
        if (kind == Ball.KICKOFF || kind < 0) {
            pos = keeper ? ball.goal(team == Ball.TEAM_A ? 0 : 1).add(new Vec3(attack.x - b.x, 0, attack.z - b.z).normalize().scale(1.2))
                    : formationSlot(r, ball, level, team);
            // na saída, cada time no seu campo
            if (!keeper && ball.uFor(team, pos) > ball.length() / 2 - 1) pos = ball.teamPoint(team, ball.length() / 2 - 1.5, ball.v(pos) * (team == Ball.TEAM_A ? 1 : -1));
        } else if (kind == Ball.PENALTY && !ours && keeper) {
            pos = ball.goal(team == Ball.TEAM_A ? 0 : 1).add(new Vec3(spot.x - b.x, 0, spot.z - b.z)).add(0, 0, 0);
            Vec3 g = ball.goal(team == Ball.TEAM_A ? 0 : 1);
            pos = g.add(new Vec3(spot.x - g.x, 0, spot.z - g.z).normalize().scale(0.6));
            // goleiro no pênalti: escolhe um canto na hora do chute
            if (ball.restartTimerDone() && r.settle == 0) {
                Vec3 ax = new Vec3(spot.x - g.x, 0, spot.z - g.z).normalize();
                Vec3 sideV = new Vec3(-ax.z, 0, ax.x).scale(r.getRandom().nextBoolean() ? 1.3 : -1.3);
                pos = pos.add(sideV);
                r.settle = 40;
            }
        } else if (keeper) {
            Vec3 g = ball.goal(team == Ball.TEAM_A ? 0 : 1);
            pos = g.add(new Vec3(attack.x - g.x, 0, attack.z - g.z).normalize().scale(1.2));
        } else {
            pos = formationSlot(r, ball, level, team).lerp(spot, ours ? 0.3 : 0.15);
            double gap = kind == Ball.THROW_IN || kind == Ball.CORNER ? Ball.RESTART_GAP + 0.5 : 3.5;
            if (!ours && pos.distanceTo(spot) < gap) pos = spot.add(pos.subtract(spot).normalize().scale(gap));
            if (!ours && kind == Ball.GOAL_KICK) pos = ball.outOfBox(team == Ball.TEAM_A ? 1 : 0, pos);
            if (kind == Ball.PENALTY) {
                // no pênalti todo mundo fica fora da área
                Vec3 g = ball.goal(team == ball.restartTeam() ? (team == Ball.TEAM_A ? 1 : 0) : (team == Ball.TEAM_A ? 0 : 1));
                if (pos.distanceTo(g) < 7) pos = g.add(pos.subtract(g).normalize().scale(7));
            }
        }
        go(r, pos.x, pos.y, pos.z, 0.65);
    }

    /** Companheiro mais perto (para a saída de bola e o lateral). */
    private static Vec3 nearestMate(Raver r, Ball ball, ServerLevel level, int team, Vec3 from) {
        Raver best = null;
        double bd = 1e9;
        for (Raver m : level.getEntitiesOfClass(Raver.class, new AABB(from, from).inflate(20), m -> m != r && ball.teamOf(m) == team && m.stall != 1)) {
            double d = m.position().distanceTo(from);
            if (d > 2 && d < bd) { bd = d; best = m; }
        }
        return best == null ? ball.goal(team == Ball.TEAM_A ? 1 : 0) : best.position();
    }

    // ------------------------------------------------------------ organizador do campo / da quadra
    /** Fica parado na beira do campo, com a bola na mão, esperando alguém marcar um jogo. */
    private static boolean organizer(Raver r, ServerLevel level) {
        boolean fut = r.role() == ORG_FUT;
        if (!r.hasCustomName()) {
            r.setCustomName(Component.literal(fut ? "Organizador da Várzea" : "Organizador do Vôlei"));
            r.setCustomNameVisible(true);
        }
        var want = fut ? com.boombox.bar.Blocks34.BOLA_FUTEBOL : com.boombox.bar.Blocks34.BOLA_VOLEI;
        if (!r.getMainHandItem().is(want)) r.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(want));
        if (!r.hasHome()) r.setHomeTo(r.blockPosition(), 1);
        if (r.getRandom().nextInt(600) == 0) {
            sayNear(level, r.position(), 10, "§6<" + r.getCustomName().getString() + "> §f" + (fut
                    ? "Bora um jogo? Fala comigo: amistoso ou campeonato!"
                    : "Quadra livre! Fala comigo pra jogar um 2 contra 2."));
        }
        return true;
    }

    // ------------------------------------------------------------ vôlei de praia
    /** Os jogadores de vôlei são comandados pela bola (Volleyball); sem bola por perto ficam no lugar deles. */
    private static boolean volley(Raver r, ServerLevel level) {
        if (r.fieldHome == null) r.fieldHome = r.blockPosition();
        if (!r.getMainHandItem().isEmpty()) r.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (r.getShirt() == 0) r.setShirt(0xFF000000 | Volleyball.TEAM_COLORS[r.role() == VOLEI_A ? 0 : 1]);
        long t = level.getOverworldClockTime() % 24000;
        boolean day = t < 12300;
        boolean ball = !level.getEntitiesOfClass(Volleyball.class, new AABB(r.blockPosition()).inflate(24), Volleyball::hasCourt).isEmpty();
        if (!day || !ball) {
            if (!r.hasHome()) r.setHomeTo(r.fieldHome, 2);
            return true;
        }
        if (r.hasHome()) r.clearHome();
        return true;
    }

    // ------------------------------------------------------------ luau (noite de lua cheia na praia)
    /** Noite de lua cheia? (a lua muda a cada dia, num ciclo de 8 dias; o dia 0 é de lua cheia) */
    public static boolean luauNight(net.minecraft.world.level.Level level) {
        long clock = level.getOverworldClockTime();
        long tod = clock % 24000;
        boolean night = tod >= 12500 && tod < 23500;
        return night && (clock / 24000) % 8 == 0;
    }

    /**
     * Na praia, em noite de lua cheia, quem está perto de uma fogueira faz luau: a fogueira e as tochas de bambu
     * acendem, a turma senta em roda, um toca violão e o resto canta e bate palma. De manhã apaga tudo.
     * Devolve true se o aldeão está no luau.
     */
    static boolean luau(Raver r, ServerLevel level) {
        if (r.tickCount % 20 == 0 || r.luauFire == null) {
            boolean night = luauNight(level);
            if (!night) {
                if (r.luauFire != null) endLuau(level, r.luauFire);
                r.luauFire = null;
                if (r.role() == LUAU) r.discard();
                return false;
            }
            long day = level.getOverworldClockTime() / 24000;
            if (r.luauFire == null && r.luauDay != day) {
                r.luauDay = day; // procura a fogueira só uma vez por noite
                r.luauFire = findBeachFire(level, r.blockPosition());
            }
            if (r.luauFire == null) return false;
            startLuau(level, r.luauFire);
        }
        BlockPos fire = r.luauFire;
        // lugar na roda: em volta da fogueira, cada um no seu ângulo
        double a = (r.getId() * 2.399) % (Math.PI * 2);
        Vec3 spot = new Vec3(fire.getX() + 0.5 + Math.cos(a) * 2.6, fire.getY(), fire.getZ() + 0.5 + Math.sin(a) * 2.6);
        if (r.hasHome()) r.clearHome();
        if (r.position().distanceTo(spot) > 0.8) {
            if (r.tickCount % 20 == 0) r.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.55);
        } else {
            r.getLookControl().setLookAt(fire.getX() + 0.5, fire.getY() + 0.6, fire.getZ() + 0.5);
        }
        // o violeiro: o de menor id perto da fogueira
        boolean player = level.getEntitiesOfClass(Raver.class, new AABB(fire).inflate(6), o -> o.getId() < r.getId() && o.luauFire != null).isEmpty();
        var want = player ? com.boombox.bar.Blocks34.VIOLAO : net.minecraft.world.item.Items.AIR;
        if (!r.getMainHandItem().is(want)) r.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(want));
        r.setLuauGuitar(player);
        if (player && r.tickCount % 5 == 0 && r.getRandom().nextInt(3) != 0) {
            float[] scale = {0.5f, 0.6f, 0.67f, 0.75f, 0.84f, 1.0f, 1.12f};
            r.playSound(SoundEvents.NOTE_BLOCK_GUITAR.value(), 0.6f, scale[r.getRandom().nextInt(scale.length)]);
        }
        if (r.getRandom().nextInt(260) == 0) {
            level.sendParticles(ParticleTypes.NOTE, r.getX(), r.getEyeY() + 0.5, r.getZ(), 1, 0.2, 0.1, 0.2, 1);
            r.playSound(SoundEvents.VILLAGER_CELEBRATE, 0.6f, 1.1f);
        }
        if (r.tickCount % 100 == 0) {
            for (var p : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, new AABB(fire).inflate(8))) {
                com.boombox.Achievements.award(p, "luau_na_praia");
            }
        }
        return true;
    }

    /** Fogueira na areia, perto (é a da barraca de praia). */
    private static BlockPos findBeachFire(ServerLevel level, BlockPos near) {
        for (BlockPos p : BlockPos.betweenClosed(near.offset(-12, -3, -12), near.offset(12, 3, 12))) {
            var st = level.getBlockState(p);
            if (st.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock && level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.SAND)) {
                return p.immutable();
            }
        }
        return null;
    }

    private static final java.util.Map<Long, Long> LUAU_STARTED = new java.util.HashMap<>();

    private static void startLuau(ServerLevel level, BlockPos fire) {
        long day = level.getOverworldClockTime() / 24000;
        Long was = LUAU_STARTED.get(fire.asLong());
        if (was != null && was == day) return;
        LUAU_STARTED.put(fire.asLong(), day);
        setFires(level, fire, true);
        // chega mais gente para o luau
        for (int i = 0; i < 3; i++) {
            Raver g = ModEntities.RAVER.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (g == null) continue;
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            g.snapTo(fire.getX() + 0.5 + Math.cos(a) * 9, fire.getY(), fire.getZ() + 0.5 + Math.sin(a) * 9, 0, 0);
            g.setRole(LUAU);
            g.setGlow(level.getRandom().nextBoolean());
            level.addFreshEntity(g);
        }
        for (var p : level.players()) {
            if (p.blockPosition().distSqr(fire) < 80 * 80) {
                p.sendSystemMessage(Component.literal("§b🌕 Noite de lua cheia: §fluau na praia! Fogueira acesa, violão e a turma toda na areia."));
            }
        }
    }

    private static void endLuau(ServerLevel level, BlockPos fire) {
        if (LUAU_STARTED.remove(fire.asLong()) != null) setFires(level, fire, false);
    }

    private static void setFires(ServerLevel level, BlockPos fire, boolean lit) {
        for (BlockPos p : BlockPos.betweenClosed(fire.offset(-8, -2, -8), fire.offset(8, 3, 8))) {
            var st = level.getBlockState(p);
            if (st.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock || st.is(com.boombox.bar.Blocks34.TIKI_TORCH)) {
                if (st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) != lit) {
                    level.setBlock(p, st.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, lit), 3);
                }
            }
        }
    }

}
