package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** O que está na grelha (até 4 coisas) e quanto falta para cada uma ficar pronta. */
public class GrillBlockEntity extends BlockEntity {
    public static final int COOK_TICKS = 20 * 20;
    public final NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    public final int[] progress = new int[4];
    /** Churrasqueira do bar: no domingo assa espetinho sozinha. */
    private boolean sundayAuto;

    public GrillBlockEntity(BlockPos pos, BlockState state) {
        super(BarBlocks.GRILL_BE, pos, state);
    }

    /** No que cada coisa crua vira na grelha (null = não vai na churrasqueira). */
    public static Item cooked(ItemStack raw) {
        if (raw.is(Items.BEEF) || raw.is(Items.MUTTON) || raw.is(Items.CHICKEN)) return BarBlocks.ESPETINHO;
        if (raw.is(Items.PORKCHOP)) return BarBlocks.LINGUICA;
        if (raw.is(Items.BREAD)) return BarBlocks.PAO_DE_ALHO;
        return null;
    }

    public boolean isCooking() {
        for (int i = 0; i < 4; i++) if (!items.get(i).isEmpty() && progress[i] < COOK_TICKS) return true;
        return false;
    }

    public boolean put(ItemStack raw) {
        for (int i = 0; i < 4; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, raw);
                progress[i] = 0;
                changed();
                return true;
            }
        }
        return false;
    }

    /** Pega uma coisa pronta (assada); se nada estiver pronto, devolve vazio. */
    public ItemStack takeReady() {
        for (int i = 0; i < 4; i++) {
            if (!items.get(i).isEmpty() && progress[i] >= COOK_TICKS) {
                ItemStack out = items.get(i);
                items.set(i, ItemStack.EMPTY);
                progress[i] = 0;
                changed();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public static boolean isSunday() {
        return LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GrillBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        // domingo no bar: o Seu Zé bota espetinho na grelha sozinho
        if (be.sundayAuto && isSunday() && level.getGameTime() % 200 == 0) {
            for (int i = 0; i < 4; i++) if (be.items.get(i).isEmpty()) be.put(new ItemStack(i % 2 == 0 ? Items.BEEF : Items.PORKCHOP));
        }
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            if (be.items.get(i).isEmpty() || be.progress[i] >= COOK_TICKS) continue;
            any = true;
            if (++be.progress[i] >= COOK_TICKS) {
                Item done = cooked(be.items.get(i));
                if (done != null) be.items.set(i, new ItemStack(done)); // ficou pronto: vira espetinho, linguiça ou pão de alho
                be.changed();
                sl.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1f, 1.2f);
            }
        }
        if (!any) return;
        var r = level.getRandom();
        if (level.getGameTime() % 4 == 0) {
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.2, 0, 0.2, 0.01);
            sl.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 2, 0.25, 0, 0.15, 0.01);
        }
        if (r.nextInt(30) == 0) sl.playSound(null, pos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.7f, 1.4f);
        // cheiro de churrasco: aldeões em até 16 blocos vêm para perto da churrasqueira
        if (level.getGameTime() % 100 == 0) {
            for (PathfinderMob v : level.getEntitiesOfClass(PathfinderMob.class, new AABB(pos).inflate(16),
                    m -> m instanceof Villager || m instanceof com.boombox.entity.Raver)) {
                if (v.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 9) {
                    v.getNavigation().moveTo(pos.getX() + 0.5 + r.nextInt(5) - 2, pos.getY(), pos.getZ() + 0.5 + r.nextInt(5) - 2, 0.6);
                }
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, items);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        for (int i = 0; i < 4; i++) items.set(i, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        for (int i = 0; i < 4; i++) progress[i] = in.getIntOr("Progress" + i, 0);
        sundayAuto = in.getBooleanOr("SundayAuto", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out, items, true);
        for (int i = 0; i < 4; i++) out.putInt("Progress" + i, progress[i]);
        if (sundayAuto) out.putBoolean("SundayAuto", true);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
