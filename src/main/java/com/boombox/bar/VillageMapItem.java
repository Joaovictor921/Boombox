package com.boombox.bar;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * 3.8: mapa da vila. Clique direito: abre a tela com os lugares da vila mais perto (campo de futebol, bar do Seu Zé,
 * quadra de vôlei, balada do DJ Leo...), a distância e a direção de cada um, e um botão para seguir a seta até lá.
 */
public class VillageMapItem extends Item {
    /** Preenchido pelo servidor: procura os lugares e manda a tela. */
    public static java.util.function.Consumer<ServerPlayer> openHook;

    public VillageMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && openHook != null) {
            sp.getCooldowns().addCooldown(player.getItemInHand(hand), 40);
            openHook.accept(sp);
        }
        return InteractionResult.SUCCESS;
    }
}
