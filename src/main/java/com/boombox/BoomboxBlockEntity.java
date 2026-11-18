package com.boombox;

import com.boombox.server.ServerBoomboxManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Guarda a música e as configurações (luz, cor, dono) da caixa no chão. */
public class BoomboxBlockEntity extends BlockEntity {
    private SongData song = SongData.EMPTY;
    private SpeakerSettings settings = SpeakerSettings.DEFAULT;
    /** Colocada pela casa do DJ Leo: sorteia o tamanho da caixa no primeiro tick. */
    private boolean randomType;
    private int tickCounter;

    public BoomboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BOOMBOX_BE, pos, state);
    }

    public SongData song() {
        return song;
    }

    public void setSong(SongData song) {
        this.song = song;
        setChanged();
    }

    public SpeakerSettings settings() {
        return settings;
    }

    /** Muda as configurações e avisa os clientes (para o desenho da caixa mudar na hora). */
    public void setSettings(SpeakerSettings s) {
        this.settings = s;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        song = in.read("Song", SongData.CODEC).orElse(SongData.EMPTY);
        settings = in.read("Settings", SpeakerSettings.CODEC).orElse(SpeakerSettings.DEFAULT);
        randomType = in.getBooleanOr("RandomType", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Song", SongData.CODEC, song);
        if (!settings.isDefault()) out.store("Settings", SpeakerSettings.CODEC, settings);
        if (randomType) out.putBoolean("RandomType", true);
    }

    // ---- o cliente recebe só as configurações (a música vai pelos pacotes da boombox)

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        tag.remove("Song");
        tag.remove("RandomType");
        return tag;
    }

    /** Colocar o item no chão: a música continua de onde estava. */
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SongData s = components.get(ModBlocks.SONG);
        if (s != null) song = s;
        SpeakerSettings st = components.get(ModBlocks.SETTINGS);
        if (st != null) settings = st;
    }

    /** Quebrar o bloco: o item leva a música e as configurações junto. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (song.hasSong() || song.volume() != 100 || !song.queue().isEmpty() || !song.autoplay()) builder.set(ModBlocks.SONG, song);
        if (!settings.isDefault()) builder.set(ModBlocks.SETTINGS, settings);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoomboxBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (be.randomType) {
            be.randomType = false;
            pickRandomType(serverLevel, pos, state, be);
            return;
        }
        if (++be.tickCounter % 10 != 0) return;
        ServerBoomboxManager.tickBlock(serverLevel, pos, be);
    }

    /** Casa do DJ Leo: troca a caixa por uma caixa de festa de tamanho sorteado. */
    private static void pickRandomType(ServerLevel level, BlockPos pos, BlockState state, BoomboxBlockEntity be) {
        SpeakerType[] party = {SpeakerType.PARTY_SMALL, SpeakerType.PARTY_MEDIUM, SpeakerType.PARTY_LARGE};
        SpeakerType t = party[level.getRandom().nextInt(party.length)];
        SpeakerSettings s = be.settings;
        BlockState ns = ModBlocks.BLOCKS.get(t).defaultBlockState().setValue(BoomboxBlock.FACING, state.getValue(BoomboxBlock.FACING));
        if (ns.getBlock() != state.getBlock()) level.setBlock(pos, ns, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof BoomboxBlockEntity nbe) nbe.setSettings(s);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            ServerBoomboxManager.removeBlock(serverLevel, worldPosition);
        }
    }
}
