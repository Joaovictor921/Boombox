package com.boombox.bar;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Correio elegante da quermesse: clique num jogador (ou aldeão) para mandar um bilhetinho anônimo. */
public class CorreioItem extends Item {
    private static final String[] NOTES = {
            "Alguém aqui na festa acha você uma graça! 💌", "Um admirador secreto mandou dizer: que sorriso bonito!",
            "Tem gente de olho em você desde o arraiá... 😳", "Quer dançar a quadrilha comigo? Assinado: segredo.",
            "Você é mais doce que paçoca! 💕", "Me encontra na barraca do quentão? 😉"};

    public CorreioItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        String note = NOTES[level.getRandom().nextInt(NOTES.length)];
        if (target instanceof ServerPlayer other) {
            other.sendSystemMessage(Component.literal("§d💌 Correio elegante pra você: §f" + note));
            player.sendSystemMessage(Component.literal("§dCorreio elegante entregue para " + other.getName().getString() + " (anônimo!)"));
        } else {
            player.sendSystemMessage(Component.literal("§dO aldeão leu o bilhete e ficou todo vermelho!"));
        }
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getEyeY() + 0.4, target.getZ(), 5, 0.3, 0.2, 0.3, 0);
        level.playSound(null, target.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.3f);
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
