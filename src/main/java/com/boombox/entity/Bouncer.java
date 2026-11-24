package com.boombox.entity;

import com.boombox.bar.BarBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Segurança da balada do DJ Leo: fica parado na porta, olhando para fora. Antes da meia-noite só entra quem tem
 * a pulseira VIP (ele vende por 2 barras de ouro); depois da meia-noite a entrada é liberada.
 */
public class Bouncer extends PathfinderMob {
    private final Map<UUID, Long> warned = new HashMap<>();
    /** Direção da rua ("fora"), guardada quando ele aparece (o corpo dele vira para olhar as pessoas). */
    private float outYaw = Float.NaN;
    /** Segurança do camarote: sem pulseira não sobe nunca (nem depois da meia-noite). */
    private boolean vipOnly;
    /** Este segurança organiza a fila de aldeões na porta (só um por balada). */
    private boolean queueBoss;
    private final java.util.List<UUID> line = new java.util.ArrayList<>();
    private int nextAdmit;
    /** 3.8.1: 1 = já está no lugar novo (o do camarote fica na frente da corda VIP, virado para a pista). */
    private int layout;

    /** Cordas do camarote no molde da balada (x, z), com o segurança antigo em (7,5; 7,5) olhando para o norte. */
    private static final int[][] CAMAROTE_ROPES = {{6, 11}, {6, 12}, {6, 13}, {3, 10}, {4, 10}, {5, 10}, {5, 6}, {6, 6}, {7, 6}, {5, 7}, {7, 7}};

    public Bouncer(EntityType<? extends Bouncer> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("Segurança"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.SCALE, 1.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8f, 1f));
    }

    /**
     * Fila na porta: de noite (e mais ainda em noite de show) chegam aldeões e fazem fila do lado de fora; o segurança
     * deixa entrar um de cada vez.
     */
    private void tickQueue(ServerLevel sl) {
        float yaw = outYaw * ((float) Math.PI / 180f);
        Vec3 out = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        // meio da porta: entre os dois seguranças da entrada
        Vec3 door = position();
        for (Bouncer b : sl.getEntitiesOfClass(Bouncer.class, getBoundingBox().inflate(6), b -> b != this && !b.vipOnly)) {
            door = position().lerp(b.position(), 0.5);
        }
        line.removeIf(id -> !(sl.getEntity(id) instanceof Raver r) || !r.isAlive() || r.queueSpot == null);
        boolean night = sl.isDarkOutside();
        boolean anyone = sl.players().stream().anyMatch(p -> p.distanceToSqr(this) < 64 * 64);
        if (!night || !anyone) return;
        boolean show = com.boombox.bar.ShowTime.isShowNow();
        // chega gente nova (no máximo 6 na fila)
        if (line.size() < (show ? 6 : 4) && sl.getRandom().nextInt(show ? 6 : 14) == 0) {
            Raver r = ModEntities.RAVER.create(sl, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (r != null) {
                Vec3 from = door.add(out.scale(14 + sl.getRandom().nextInt(4))).add(out.z * (sl.getRandom().nextDouble() - 0.5) * 6, 0, -out.x * (sl.getRandom().nextDouble() - 0.5) * 6);
                int y = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(from.x), (int) Math.floor(from.z));
                r.snapTo(from.x, y, from.z, 0, 0);
                r.setGlow(sl.getRandom().nextInt(3) == 0);
                r.queueSpot = Vec3.ZERO;
                sl.addFreshEntity(r);
                line.add(r.getUUID());
            }
        }
        // cada um no seu lugar da fila
        for (int i = 0; i < line.size(); i++) {
            if (sl.getEntity(line.get(i)) instanceof Raver r) {
                r.queueSpot = door.add(out.scale(2.2 + i * 1.1));
                r.queueLook = door;
            }
        }
        // libera o primeiro de vez em quando (se ainda cabe gente lá dentro)
        if (--nextAdmit <= 0 && !line.isEmpty() && sl.getEntity(line.get(0)) instanceof Raver first && first.position().distanceTo(first.queueSpot) < 1.5) {
            int inside = sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(24), r -> r.queueSpot == null && r.role() == 0).size();
            if (inside < 26) {
                line.remove(0);
                first.queueSpot = null;
                first.settle = 160;
                Vec3 in = door.subtract(out.scale(5 + sl.getRandom().nextInt(6)));
                first.getNavigation().moveTo(in.x, in.y, in.z, 0.6);
                playSound(SoundEvents.VILLAGER_YES, 1f, 0.7f);
                nextAdmit = show ? 30 : 60; // ticks/4: ~6 s em show, ~12 s normal
            } else {
                nextAdmit = 100;
            }
        }
    }

    /** Já passou da meia-noite (entrada liberada para todos)? */
    public static boolean afterMidnight(Level level) {
        long t = level.getOverworldClockTime() % 24000;
        return t >= 18000 && t < 23000;
    }

    private static boolean hasVip(Player p) {
        return com.boombox.bar.Vip.onList(p);
    }

    /** Convidados fixos da lista VIP (além de quem comprou a pulseira). */
    private static final String[] GUESTS = {"DJ Leo", "Dona Neide do Camarote", "Juninho do Grave", "MC Pedrinho", "Tia Lurdes", "Rafa Paredão"};

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || tickCount % 4 != 0) return;
        if (Float.isNaN(outYaw)) outYaw = getYRot();
        if (layout < 1) {
            layout = 1;
            if (vipOnly) moveToRope(sl);
        }
        if (queueBoss) tickQueue(sl);
        if (afterMidnight(sl) && !vipOnly) return;
        // para onde é "fora": a direção em que ele estava virado quando apareceu (para a rua)
        float yaw = outYaw * ((float) Math.PI / 180f);
        Vec3 out = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.isCreative()) continue;
            if (hasVip(p)) {
                com.boombox.bar.Vip.bindUnnamed(p);
                if (vipOnly && p.distanceTo(this) < 3 && !p.entityTags().contains("boombox_vip_" + sl.getOverworldClockTime() / 24000)) {
                    p.addTag("boombox_vip_" + sl.getOverworldClockTime() / 24000);
                    p.sendSystemMessage(Component.literal("§8<Segurança do Camarote>§r " + p.getName().getString() + "... tá aqui na lista. Pode subir, VIP!"));
                    com.boombox.Achievements.award(p, "lista_vip");
                }
                continue;
            }
            Vec3 d = p.position().subtract(position());
            if (vipOnly) {
                // a corda VIP já barra quem não está na lista: ele só avisa quem chega perto
                if (d.horizontalDistance() > 2.0 || Math.abs(d.y) > 2) continue;
                Long last = warned.get(p.getUUID());
                if (last == null || tickCount - last > 200) {
                    warned.put(p.getUUID(), (long) tickCount);
                    String someone = com.boombox.bar.Vip.hasSomeoneElses(p) ? "Essa pulseira não é sua, seu nome não tá na lista! " : "";
                    p.sendSystemMessage(Component.literal("§8<Segurança do Camarote>§r Daqui da corda pra lá é só quem tá na lista VIP. "
                            + someone + "Quer entrar na lista? A pulseira (com seu nome) custa §62 barras de ouro§r."));
                    playSound(SoundEvents.VILLAGER_NO, 1f, 0.7f);
                }
                continue;
            }
            if (d.horizontalDistance() > 3.0 || Math.abs(d.y) > 2) continue;
            if (d.dot(out) < -0.6) continue; // já está lá dentro: pode sair à vontade
            // empurra para fora
            p.setDeltaMovement(out.scale(0.9).add(0, 0.2, 0));
            // o movimento do jogador é do cliente dele: manda o empurrão direto
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
            Long last = warned.get(p.getUUID());
            if (last == null || tickCount - last > 60) {
                warned.put(p.getUUID(), (long) tickCount);
                String someone = com.boombox.bar.Vip.hasSomeoneElses(p) ? "Essa pulseira não é sua, seu nome não tá na lista! " : "";
                p.sendSystemMessage(Component.literal("§8<Segurança>§r Sem pulseira VIP não entra antes da meia-noite. "
                        + someone + "Quer entrar na lista? A pulseira (com seu nome) custa §62 barras de ouro§r."));
                playSound(SoundEvents.VILLAGER_NO, 1f, 0.7f);
            }
        }
    }

    private static Vec3 dir(float yawDeg) {
        float y = yawDeg * ((float) Math.PI / 180f);
        return new Vec3(-Math.sin(y), 0, Math.cos(y));
    }

    /**
     * 3.8.1, baladas que já existiam: o segurança do camarote (o que fica ao lado do pé da escada) vai para a frente
     * das cordas embaixo do camarote, virado para a pista, e as cordas que faltam são postas (no pé da escada também).
     */
    private void moveToRope(ServerLevel sl) {
        Vec3 out = dir(outYaw), east = dir(outYaw + 90f);
        BlockPos stair = BlockPos.containing(position().add(east.scale(-1.0)));
        if (!(sl.getBlockState(stair).getBlock() instanceof net.minecraft.world.level.block.StairBlock)) return; // é o da área VIP
        var rope = (com.boombox.bar.VelvetRopeBlock) com.boombox.bar.Blocks34.VELVET_ROPE;
        java.util.List<BlockPos> spots = new java.util.ArrayList<>();
        for (int[] r : CAMAROTE_ROPES) {
            Vec3 w = position().add(east.scale(r[0] + 0.5 - 7.5)).add(out.scale(-(r[1] + 0.5 - 7.5)));
            BlockPos bp = BlockPos.containing(w.x, getY() + 0.1, w.z);
            if (sl.getBlockState(bp).isAir()) sl.setBlock(bp, rope.defaultBlockState(), 3);
            if (sl.getBlockState(bp).is(rope)) spots.add(bp);
        }
        for (BlockPos bp : spots) sl.setBlock(bp, rope.connectedAt(sl, bp), 3);
        Vec3 to = position().add(out.scale(-4.5));
        outYaw = outYaw + 90f;
        snapTo(to.x, getY(), to.z, outYaw, 0f);
        setYHeadRot(outYaw);
        setYBodyRot(outYaw);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.GOLD_INGOT) && (held.getCount() >= 2 || player.getAbilities().instabuild)) {
            if (!player.getAbilities().instabuild) held.shrink(2);
            ItemStack vip = com.boombox.bar.Vip.braceletFor(player.getName().getString());
            if (!player.getInventory().add(vip)) spawnAtLocation((ServerLevel) level(), vip);
            player.sendSystemMessage(Component.literal("§8<Segurança>§r Anotei seu nome na lista VIP, " + player.getName().getString()
                    + ". Tá aqui a pulseira com seu nome: só vale pra você. Pode entrar, chefe."));
            playSound(SoundEvents.VILLAGER_YES, 1f, 0.7f);
        } else if (vipOnly) {
            // segurança do camarote: mostra a lista VIP de hoje
            java.util.List<String> names = new java.util.ArrayList<>(java.util.List.of(GUESTS));
            for (var p : ((ServerLevel) level()).players()) if (com.boombox.bar.Vip.onList(p) && !p.isCreative()) names.add(p.getName().getString());
            player.sendSystemMessage(Component.literal("§8<Segurança do Camarote>§r 📋 §6Lista VIP de hoje:§f " + String.join(", ", names)
                    + (com.boombox.bar.Vip.onList(player) ? " §a(você tá na lista!)" : " §7(seu nome não tá aqui...)")));
        } else {
            player.sendSystemMessage(Component.literal(afterMidnight(level())
                    ? "§8<Segurança>§r Passou da meia-noite, a casa tá aberta. Bora!"
                    : "§8<Segurança>§r Pulseira VIP: 2 barras de ouro. Ou volta depois da meia-noite."));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (!Float.isNaN(outYaw)) out.putFloat("OutYaw", outYaw);
        out.putBoolean("VipOnly", vipOnly);
        out.putBoolean("QueueBoss", queueBoss);
        out.putInt("Layout", layout);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        outYaw = in.getFloatOr("OutYaw", Float.NaN);
        vipOnly = in.getBooleanOr("VipOnly", false);
        queueBoss = in.getBooleanOr("QueueBoss", false);
        layout = in.getIntOr("Layout", 0);
        if (vipOnly) setCustomName(Component.literal("Segurança do Camarote"));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }
}
