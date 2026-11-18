package com.boombox;

import com.boombox.net.Payloads;
import com.boombox.server.ServerBoomboxManager;
import com.boombox.server.ServerFiles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class BoomboxMod implements ModInitializer {
    public static final String MOD_ID = "boombox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Preenchido pelo lado cliente: abre a tela da boombox. Nulo em servidor dedicado. */
    public static Consumer<BlockPos> openScreenHook;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.init();
        com.boombox.entity.ModEntities.init();
        Payloads.register();
        ServerBoomboxManager.registerReceivers();
        ServerFiles.registerReceivers();

        // aba própria do mod no modo criativo
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("speakers"), FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.boombox.speakers"))
                .icon(() -> new ItemStack(ModBlocks.BOOMBOX_ITEM))
                .displayItems((params, output) -> ModBlocks.ALL_ITEMS.forEach(output::accept))
                .build());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerBoomboxManager.sendAllTo(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerFiles.onDisconnect(handler.getPlayer());
            ServerBoomboxManager.onDisconnect(handler.getPlayer());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 5 == 0) ServerBoomboxManager.tickPlayers(server);
            if (server.getTickCount() % 20 == 0) ServerBoomboxManager.tickDancers(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerBoomboxManager.clear());
        // casa do DJ Leo em todas as vilas
        ServerLifecycleEvents.SERVER_STARTING.register(com.boombox.village.DjLeoHouse::onServerStarting);

        LOGGER.info("Boombox carregada!");
    }
}
