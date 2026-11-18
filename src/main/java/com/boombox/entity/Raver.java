package com.boombox.entity;

import com.boombox.server.ServerBoomboxManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Aldeão festeiro: mora na rave do DJ Leo. Fica pela pista de dança e, quando tem música tocando,
 * pula no ritmo e levanta os braços (a dança é desenhada no cliente).
 * A roupa (bioma e profissão) é sorteada para cada um ficar diferente.
 */
public class Raver extends PathfinderMob {
    /** Roupa: tipo de bioma * 16 + profissão (15 = sem profissão). -1 = ainda não sorteada. */
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.INT);
    /** Freguês do bar do Seu Zé: não dança, fica com a cerveja na mão. */
    private static final EntityDataAccessor<Boolean> BAR = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.BOOLEAN);
    public static final String[] TYPES = {"plains", "desert", "savanna", "snow", "taiga", "jungle", "swamp"};
    public static final String[] PROFESSIONS = {"farmer", "librarian", "cleric", "butcher", "shepherd", "fisherman",
            "fletcher", "leatherworker", "mason", "cartographer", "toolsmith", "weaponsmith", "armorer", "nitwit"};

    public Raver(EntityType<? extends Raver> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LOOK, -1);
        builder.define(BAR, false);
    }

    public boolean atBar() {
        return entityData.get(BAR);
    }

    public int look() {
        return entityData.get(LOOK);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8f, 0.3f));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.45, 0.02f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (look() < 0) {
            int type = getRandom().nextInt(TYPES.length);
            int prof = getRandom().nextInt(4) == 0 ? 15 : getRandom().nextInt(PROFESSIONS.length);
            entityData.set(LOOK, type * 16 + prof);
        }
        if (!hasHome()) setHomeTo(blockPosition(), atBar() ? 3 : 6);
        if (atBar()) {
            // no bar: cerveja na mão (garrafa de mel) e um gole de vez em quando
            if (getMainHandItem().isEmpty()) setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE));
            if (getRandom().nextInt(260) == 0) playSound(SoundEvents.GENERIC_DRINK.value(), 0.6f, 0.9f + getRandom().nextFloat() * 0.2f);
            return;
        }
        // música perto: pula no ritmo de vez em quando e comemora
        if (tickCount % 5 == 0 && onGround() && ServerBoomboxManager.isPlayingNear(level, position(), 24)) {
            if (getRandom().nextInt(3) == 0) {
                jumpFromGround();
                if (getRandom().nextInt(12) == 0) playSound(SoundEvents.VILLAGER_CELEBRATE, 0.8f, 0.9f + getRandom().nextFloat() * 0.3f);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Look", look());
        out.putBoolean("Bar", atBar());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(LOOK, in.getIntOr("Look", -1));
        entityData.set(BAR, in.getBooleanOr("Bar", false));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
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
