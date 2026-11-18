package com.boombox;

import com.boombox.server.ServerBoomboxManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Guarda a música da boombox no chão. */
public class BoomboxBlockEntity extends BlockEntity {
    private SongData song = SongData.EMPTY;
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

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        song = in.read("Song", SongData.CODEC).orElse(SongData.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Song", SongData.CODEC, song);
    }

    /** Colocar o item no chão: a música continua de onde estava. */
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SongData s = components.get(ModBlocks.SONG);
        if (s != null) song = s;
    }

    /** Quebrar o bloco: o item leva a música junto. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (song.hasSong() || song.volume() != 100 || !song.queue().isEmpty() || !song.autoplay()) builder.set(ModBlocks.SONG, song);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoomboxBlockEntity be) {
        if (++be.tickCounter % 10 != 0 || !(level instanceof ServerLevel serverLevel)) return;
        ServerBoomboxManager.tickBlock(serverLevel, pos, be);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            ServerBoomboxManager.removeBlock(serverLevel, worldPosition);
        }
    }
}
