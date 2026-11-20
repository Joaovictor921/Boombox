package com.boombox.entity;

import com.boombox.Achievements;
import com.boombox.bar.Blocks34;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Boia de flamingo: boia na piscina, balançando de leve. Clique para sentar (aldeões também sentam); andando para a
 * frente você "rema" devagar para onde estiver olhando. Bata nela para guardar de volta.
 */
public class PoolFloat extends Entity {
    public PoolFloat(EntityType<? extends PoolFloat> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    /** Altura da água no bloco onde a boia está (ou NaN se não tem água). */
    private double waterSurface() {
        var pos = blockPosition();
        for (int dy = 1; dy >= -1; dy--) {
            var p = pos.above(dy);
            var fs = level().getFluidState(p);
            if (fs.is(FluidTags.WATER)) return p.getY() + fs.getHeight(level(), p);
        }
        return Double.NaN;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        double surface = waterSurface();
        if (!Double.isNaN(surface)) {
            // boia: sobe até a linha d'água e fica balançando
            double target = surface - 0.12 + Math.sin((tickCount + getId() * 13) * 0.08) * 0.03;
            v = new Vec3(v.x * 0.94, (target - getY()) * 0.25, v.z * 0.94);
            // correnteza fraquinha: vai rodando devagar
            if (getPassengers().isEmpty()) setYRot(getYRot() + 0.15f);
        } else {
            v = new Vec3(v.x * 0.7, v.y - 0.04, v.z * 0.7);
        }
        // quem está sentado e anda para a frente rema na direção que olha
        if (!level().isClientSide() && getFirstPassenger() instanceof ServerPlayer sp && !Double.isNaN(surface)) {
            var in = sp.getLastClientInput();
            float yaw = sp.getYRot() * ((float) Math.PI / 180f);
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            if (in.forward()) v = v.add(fwd.scale(0.012));
            if (in.backward()) v = v.add(fwd.scale(-0.008));
            setYRot(sp.getYRot());
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
    }

    public String debug() {
        return String.format(java.util.Locale.ROOT, "y=%.2f t=%d vy=%.3f água=%.2f", getY(), tickCount, getDeltaMovement().y, waterSurface());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 at) {
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            if (player.startRiding(this)) {
                playSound(SoundEvents.GENERIC_SPLASH, 0.6f, 1.3f);
                if (player instanceof ServerPlayer sp) Achievements.award(sp, "festa_na_piscina");
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player p) {
            ejectPassengers();
            if (!p.getAbilities().instabuild) spawnAtLocation(level, new ItemStack(Blocks34.FLAMINGO));
            playSound(SoundEvents.ITEM_PICKUP, 0.8f, 0.8f);
            discard();
        }
        return true;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dims, float scale) {
        return new Vec3(0, 0.15, 0);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
    }
}
