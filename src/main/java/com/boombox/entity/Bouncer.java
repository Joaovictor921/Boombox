package com.boombox.entity;

import com.boombox.bar.BarBlocks;
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
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(BarBlocks.VIP)) return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || tickCount % 4 != 0) return;
        if (Float.isNaN(outYaw)) outYaw = getYRot();
        if (queueBoss) tickQueue(sl);
        if (afterMidnight(sl) && !vipOnly) return;
        // para onde é "fora": a direção em que ele estava virado quando apareceu (para a rua)
        float yaw = outYaw * ((float) Math.PI / 180f);
        Vec3 out = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.isCreative() || hasVip(p)) continue;
            Vec3 d = p.position().subtract(position());
            if (d.horizontalDistance() > 3.0 || Math.abs(d.y) > 2) continue;
            if (d.dot(out) < -0.6) continue; // já está lá dentro: pode sair à vontade
            // empurra para fora
            p.setDeltaMovement(out.scale(0.9).add(0, 0.2, 0));
            // o movimento do jogador é do cliente dele: manda o empurrão direto
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
            Long last = warned.get(p.getUUID());
            if (last == null || tickCount - last > 60) {
                warned.put(p.getUUID(), (long) tickCount);
                p.sendSystemMessage(Component.literal((vipOnly ? "§8<Segurança>§r Camarote é só para VIP, parceiro. " : "§8<Segurança>§r Sem pulseira VIP não entra antes da meia-noite. ")
                        + "Quer a pulseira? São §62 barras de ouro§r."));
                playSound(SoundEvents.VILLAGER_NO, 1f, 0.7f);
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.GOLD_INGOT) && (held.getCount() >= 2 || player.getAbilities().instabuild)) {
            if (!player.getAbilities().instabuild) held.shrink(2);
            ItemStack vip = new ItemStack(BarBlocks.VIP);
            if (!player.getInventory().add(vip)) spawnAtLocation((ServerLevel) level(), vip);
            player.sendSystemMessage(Component.literal("§8<Segurança>§r Pulseira VIP do DJ Leo. Pode entrar, chefe."));
            playSound(SoundEvents.VILLAGER_YES, 1f, 0.7f);
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
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        outYaw = in.getFloatOr("OutYaw", Float.NaN);
        vipOnly = in.getBooleanOr("VipOnly", false);
        queueBoss = in.getBooleanOr("QueueBoss", false);
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
