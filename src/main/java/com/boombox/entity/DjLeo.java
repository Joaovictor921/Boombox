package com.boombox.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * DJ Leo: mora numa casinha de rave em cada vila, comandando o som estourado.
 * Fica dentro de casa (perto de onde nasceu), dança quando tem música e conversa com quem clica nele.
 */
public class DjLeo extends PathfinderMob {
    private static final String[] LINES = {
            "Aumenta esse som aí!",
            "Aqui é rave de verdade, parceiro!",
            "Essa caixa tá estourada mesmo, faz parte.",
            "Chega mais, a pista é sua!",
            "Pode pedir música não, quem manda no som sou eu!",
            "O grave tá batendo no peito? Então tá certo.",
            "Rave raiz: luz de LED e caixa no talo.",
            "Se o vizinho reclamar, a gente aumenta."
    };

    /** Dormindo (de dia a balada está fechada). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> SLEEPING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(DjLeo.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private Boolean wasOpen;
    /** Onde fica a cabine do DJ (a casa dele); no baile à fantasia ele desce para a pista e depois volta. */
    private net.minecraft.core.BlockPos booth;
    private boolean partying;

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLEEPING, false);
    }

    public boolean sleepingDay() {
        return entityData.get(SLEEPING);
    }

    public DjLeo(EntityType<? extends DjLeo> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("DJ Leo"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 10f, 0.6f));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.4, 0.001f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        // não sai de casa: fica num raio pequeno de onde nasceu
        if (booth == null) booth = hasHome() ? getHomePosition() : blockPosition();
        if (!hasHome()) setHomeTo(booth, 3);
        if (tickCount % 40 == 0 && level() instanceof net.minecraft.server.level.ServerLevel sl) partyTick(sl);
        // de dia a balada fecha: DJ Leo dorme e as portas fecham; de noite abre tudo
        if (tickCount % 20 == 0 && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            boolean open = com.boombox.server.ServerBoomboxManager.raveOpen(sl);
            entityData.set(SLEEPING, !open);
            setCustomName(Component.literal(open ? "DJ Leo" : "DJ Leo (zZz)"));
            if (!open) getNavigation().stop();
            if (wasOpen == null || wasOpen != open) {
                wasOpen = open;
                setDoors(sl, open);
            }
        }
        // seguranças da rave: monstro que aparecer lá dentro é posto para fora
        if (tickCount % 40 == 0 && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            var area = new net.minecraft.world.phys.AABB(getHomePosition()).inflate(20, 8, 20);
            for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, area,
                    m -> !m.isPersistenceRequired() && !m.hasCustomName())) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, m.getX(), m.getY() + 0.8, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                m.discard();
            }
        }
    }

    /** Baile à fantasia está acontecendo (o DJ Leo está fantasiado na pista)? */
    public boolean partying() {
        return partying;
    }

    /**
     * Baile à fantasia: o DJ Leo desce da cabine e vai dançar no meio da galera (onde tem mais aldeão perto), de
     * fantasia também. Avisa quem está perto quando o baile começa. No fim da noite volta para a cabine.
     */
    private void partyTick(net.minecraft.server.level.ServerLevel sl) {
        boolean party = com.boombox.bar.Fantasia.tonight(sl) && !sleepingDay();
        if (party && !partying) {
            partying = true;
            for (var p : sl.players()) {
                if (p.distanceTo(this) < 96) p.sendSystemMessage(Component.literal(
                        "§5§l🎭 Hoje é BAILE À FANTASIA §r§dna balada do DJ Leo! §fA galera chegou fantasiada e até o DJ Leo desceu do palco pra dançar."));
            }
        } else if (!party && partying) {
            partying = false;
            clearHome();
            setHomeTo(booth, 3);
            getNavigation().moveTo(booth.getX() + 0.5, booth.getY(), booth.getZ() + 0.5, 0.6);
        }
        if (!partying) return;
        // o meio da galera
        var crowd = sl.getEntitiesOfClass(Raver.class, getBoundingBox().inflate(20, 4, 20), r -> r.role() == 0 && !r.atBar() && r.queueSpot == null);
        if (!crowd.isEmpty()) {
            double x = 0, y = 0, z = 0;
            for (Raver r : crowd) { x += r.getX(); y += r.getY(); z += r.getZ(); }
            var mid = net.minecraft.core.BlockPos.containing(x / crowd.size(), y / crowd.size(), z / crowd.size());
            if (!hasHome() || getHomePosition().distSqr(mid) > 9) {
                clearHome();
                setHomeTo(mid, 4);
                getNavigation().moveTo(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5, 0.6);
            }
        }
        if (getRandom().nextInt(3) == 0) cheer();
        for (var p : sl.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, getBoundingBox().inflate(16))) {
            com.boombox.Achievements.award(p, "baile_a_fantasia");
        }
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (booth != null) out.putLong("Booth", booth.asLong());
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        long b = in.getLongOr("Booth", Long.MIN_VALUE);
        if (b != Long.MIN_VALUE) booth = net.minecraft.core.BlockPos.of(b);
    }

    /** Preenchido pelo cliente: abre a tela de pedir música (id da entidade). */
    public static java.util.function.IntConsumer openRequestHook;

    /** Comemora um pedido: pulo e som de festa. */
    public void cheer() {
        if (onGround()) jumpFromGround();
        playSound(net.minecraft.sounds.SoundEvents.VILLAGER_CELEBRATE, 1f, 1.1f);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (sleepingDay()) {
            if (!level().isClientSide()) player.sendSystemMessage(Component.literal("§d<DJ Leo>§r Zzz... a balada só abre de noite... zzz"));
            return InteractionResult.SUCCESS;
        }
        // com uma barra de ouro na mão: pedir música
        if (player.getItemInHand(hand).is(net.minecraft.world.item.Items.GOLD_INGOT)) {
            if (level().isClientSide() && openRequestHook != null) openRequestHook.accept(getId());
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide()) {
            if (getRandom().nextInt(3) == 0) {
                player.sendSystemMessage(Component.literal("§d<DJ Leo>§r Quer pedir música? Me traz uma §6barra de ouro§r e clica em mim com ela!"));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.literal("§d<DJ Leo>§r " + LINES[getRandom().nextInt(LINES.length)]));
            getLookControl().setLookAt(player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Abre (noite) ou fecha (dia) as portas da casa dele. */
    private void setDoors(net.minecraft.server.level.ServerLevel sl, boolean open) {
        net.minecraft.core.BlockPos home = getHomePosition();
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(home.offset(-34, -3, -34), home.offset(34, 4, 34))) {
            var st = sl.getBlockState(p);
            if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door
                    && st.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    && st.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) != open) {
                door.setOpen(this, sl, st, p.immutable(), open);
            }
        }
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || sleepingDay();
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
