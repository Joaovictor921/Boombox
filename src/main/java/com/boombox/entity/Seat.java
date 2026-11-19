package com.boombox.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Assento invisível das cadeiras: o jogador "monta" nele para sentar. Some quando ninguém está sentado. */
public class Seat extends Entity {
    public Seat(EntityType<? extends Seat> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Senta o jogador na cadeira desse bloco, virado para a frente dela. */
    public static void sit(Level level, BlockPos pos, Player player, double height, Direction facing) {
        Seat seat = new Seat(ModEntities.SEAT, level);
        seat.setPos(pos.getX() + 0.5, pos.getY() + height - 0.35, pos.getZ() + 0.5);
        seat.setYRot(facing.toYRot());
        level.addFreshEntity(seat);
        player.startRiding(seat, true, true);
    }

    @Override
    public void tick() {
        super.tick();
        // levantou da cadeira (ou a cadeira foi quebrada): o assento some
        if (!level().isClientSide() && tickCount > 2 && (getPassengers().isEmpty()
                || !(level().getBlockState(net.minecraft.core.BlockPos.containing(getX(), getY() + 0.4, getZ())).getBlock()
                instanceof com.boombox.bar.SeatMarker))) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {}
}
