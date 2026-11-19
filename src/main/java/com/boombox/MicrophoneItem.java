package com.boombox;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Microfone: segurando ele, a sua voz (Simple Voice Chat) sai também pelas caixas de som perto de você.
 * Clique com o botão direito para ver se está funcionando.
 */
public class MicrophoneItem extends Item {
    public MicrophoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            boolean svc = FabricLoader.getInstance().isModLoaded("voicechat");
            player.sendOverlayMessage(Component.literal(svc
                    ? "§bMicrofone ligado: fale no Simple Voice Chat e a sua voz sai pelas caixas perto (até 32 blocos)"
                    : "§cO microfone precisa do mod Simple Voice Chat instalado"));
        }
        return InteractionResult.SUCCESS;
    }
}
