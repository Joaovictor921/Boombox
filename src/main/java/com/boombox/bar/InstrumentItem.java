package com.boombox.bar;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Pandeiro e cavaquinho: clique para tocar (cada clique uma nota um pouco diferente). */
public class InstrumentItem extends Item {
    private final Holder<SoundEvent> sound;

    public InstrumentItem(Properties properties, Holder<SoundEvent> sound) {
        super(properties);
        this.sound = sound;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            float[] scale = {0.5f, 0.6f, 0.67f, 0.75f, 0.84f, 1.0f, 1.12f, 1.26f, 1.33f};
            level.playSound(null, player.getX(), player.getY(), player.getZ(), sound.value(), SoundSource.PLAYERS, 1f,
                    scale[level.getRandom().nextInt(scale.length)]);
        }
        return InteractionResult.SUCCESS;
    }
}
