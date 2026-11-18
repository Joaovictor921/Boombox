package com.boombox;

import com.boombox.net.Payloads;
import com.boombox.server.ServerBoomboxManager;
import com.boombox.server.ServerFiles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
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
        Payloads.register();
        ServerBoomboxManager.registerReceivers();
        ServerFiles.registerReceivers();

        ResourceKey<CreativeModeTab> functional = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("functional_blocks"));
        CreativeModeTabEvents.modifyOutputEvent(functional).register(output ->
                output.accept(new ItemStack(ModBlocks.BOOMBOX_ITEM), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerBoomboxManager.sendAllTo(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerFiles.onDisconnect(handler.getPlayer());
            ServerBoomboxManager.onDisconnect(handler.getPlayer());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 5 == 0) ServerBoomboxManager.tickPlayers(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerBoomboxManager.clear());

        LOGGER.info("Boombox carregada!");
    }
}
