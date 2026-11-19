package com.boombox.entity;

import com.boombox.ModBlocks;
import com.boombox.SongData;
import com.boombox.SpeakerSettings;
import com.boombox.SpeakerType;
import com.boombox.server.ServerBoomboxManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Paredão de carro: uma caixa de som andando num barco ou carrinho de mina, tocando enquanto vai.
 * Clique para abrir o menu; agachado + clique (ou um soco) para pegar a caixa de volta.
 */
public class MountedSpeaker extends Entity {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(MountedSpeaker.class, EntityDataSerializers.INT);
    /** Preenchido pelo cliente: abre o menu desta caixa (id da entidade). */
    public static java.util.function.IntConsumer openMenuHook;

    private SongData song = SongData.EMPTY;
    private SpeakerSettings settings = SpeakerSettings.DEFAULT;

    public MountedSpeaker(EntityType<? extends MountedSpeaker> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TYPE, SpeakerType.PARTY_LARGE.ordinal());
    }

    public SpeakerType speakerType() {
        return SpeakerType.byOrdinal(entityData.get(TYPE));
    }

    public SongData song() {
        return song;
    }

    public void setSong(SongData s) {
        song = s;
    }

    public SpeakerSettings settings() {
        return settings;
    }

    public void setSettings(SpeakerSettings s) {
        settings = s;
    }

    /** Monta a caixa do item em cima do veículo. */
    public static MountedSpeaker fromItem(ServerLevel level, ItemStack stack, SpeakerType type, Entity vehicle) {
        MountedSpeaker ms = new MountedSpeaker(ModEntities.MOUNTED_SPEAKER, level);
        ms.entityData.set(TYPE, type.ordinal());
        ms.song = stack.getOrDefault(ModBlocks.SONG, SongData.EMPTY);
        ms.settings = stack.getOrDefault(ModBlocks.SETTINGS, SpeakerSettings.DEFAULT);
        ms.setPos(vehicle.position());
        ms.setYRot(vehicle.getYRot());
        level.addFreshEntity(ms);
        if (!ms.startRiding(vehicle, true, true)) {
            ms.discard();
            return null;
        }
        return ms;
    }

    /** Item da caixa, levando a música e as configurações. */
    public ItemStack toItem() {
        ItemStack stack = new ItemStack(ModBlocks.ITEMS.get(speakerType()));
        if (song.hasSong() || !song.queue().isEmpty() || song.volume() != 100) stack.set(ModBlocks.SONG, song);
        if (!settings.isDefault()) stack.set(ModBlocks.SETTINGS, settings);
        return stack;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) {
            if (!isPassenger() && tickCount > 20) {
                // o veículo quebrou: a caixa cai no chão como item
                spawnAtLocation(sl, toItem());
                discard();
                return;
            }
            if (tickCount % 10 == 0) ServerBoomboxManager.tickEntity(sl, this);
        }
        if (getVehicle() != null) setYRot(getVehicle().getYRot());
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl) ServerBoomboxManager.removeEntity(sl, this);
        super.remove(reason);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isShiftKeyDown()) {
            if (level() instanceof ServerLevel sl) {
                ItemStack item = toItem();
                if (!player.getInventory().add(item)) spawnAtLocation(sl, item);
                discard();
            }
            return InteractionResult.SUCCESS;
        }
        if (level().isClientSide() && openMenuHook != null) openMenuHook.accept(getId());
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player) {
            spawnAtLocation(level, toItem());
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(TYPE, in.getIntOr("SpeakerType", SpeakerType.PARTY_LARGE.ordinal()));
        song = in.read("Song", SongData.CODEC).orElse(SongData.EMPTY);
        settings = in.read("Settings", SpeakerSettings.CODEC).orElse(SpeakerSettings.DEFAULT);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("SpeakerType", entityData.get(TYPE));
        out.store("Song", SongData.CODEC, song);
        out.store("Settings", SpeakerSettings.CODEC, settings);
    }
}
