package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Texto e cor do neon personalizado. */
public class NeonSignBlockEntity extends BlockEntity {
    public static final int MAX_LEN = 24;
    /** Cores de neon; o índice COLORS.length = arco-íris. */
    public static final int[] COLORS = {0xFF2D7A, 0xFF3030, 0xFF8A1E, 0xFFE030, 0x5CFF3C, 0x22FFC8, 0x2EC8FF, 0x3C5CFF, 0xA43CFF, 0xFF4CF0, 0xFFFFFF, 0xFFB0D8};
    public static final String[] COLOR_NAMES = {"Rosa-choque", "Vermelho", "Laranja", "Amarelo", "Verde", "Água", "Azul-céu", "Azul", "Roxo", "Magenta", "Branco", "Rosa-bebê"};
    public static final int RAINBOW = COLORS.length;

    public String text = "NEON";
    public int color = 0;
    public boolean blink;

    public NeonSignBlockEntity(BlockPos pos, BlockState state) {
        super(Blocks34.NEON_SIGN_BE, pos, state);
    }

    public void set(String text, int color, boolean blink) {
        String t = text == null ? "" : text.replaceAll("[\\p{Cntrl}§]", "").trim();
        if (t.length() > MAX_LEN) t = t.substring(0, MAX_LEN);
        this.text = t;
        this.color = Math.max(0, Math.min(RAINBOW, color));
        this.blink = blink;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        text = in.getStringOr("Text", "NEON");
        color = in.getIntOr("Color", 0);
        blink = in.getBooleanOr("Blink", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("Text", text);
        out.putInt("Color", color);
        out.putBoolean("Blink", blink);
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
