package com.boombox.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Balão de festa de aniversário: fica amarrado (balançando) e some quando a festa acaba. Dá para estourar. */
public class Balloon extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Balloon.class, EntityDataSerializers.INT);
    private static final int[] COLORS = {0xFFE83040, 0xFFF0D020, 0xFF3070F0, 0xFF30C060, 0xFFE060C0, 0xFFF08020, 0xFFFFFFFF};
    /** Ticks até sumir (só no servidor). */
    public int life = 20 * 60 * 3;

    public Balloon(EntityType<? extends Balloon> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public void randomColor() {
        entityData.set(COLOR, COLORS[random.nextInt(COLORS.length)]);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, COLORS[0]);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && --life <= 0) discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // ploc!
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.6, getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 0.6f, 1.8f);
        discard();
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(COLOR, in.getIntOr("Color", COLORS[0]));
        life = in.getIntOr("Life", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("Color", color());
        out.putInt("Life", life);
    }
}
