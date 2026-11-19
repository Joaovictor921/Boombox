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
        com.boombox.bar.BarBlocks.init();
        Achievements.registerStats();
        Payloads.register();
        ServerBoomboxManager.registerReceivers();
        ServerFiles.registerReceivers();

        // aba própria do mod no modo criativo
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("speakers"), FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.boombox.speakers"))
                .icon(() -> new ItemStack(ModBlocks.BOOMBOX_ITEM))
                .displayItems((params, output) -> ModBlocks.ALL_ITEMS.forEach(output::accept))
                .build());

        // paredão de carro: clique num barco ou carrinho de mina com uma caixa na mão
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            var stack = player.getItemInHand(hand);
            SpeakerType type = BoomboxItem.type(stack);
            if (type == null || !(entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat
                    || entity instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart)) {
                return net.minecraft.world.InteractionResult.PASS;
            }
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                if (com.boombox.entity.MountedSpeaker.fromItem(sl, stack, type, entity) == null) {
                    player.sendOverlayMessage(Component.literal("Não cabe mais nada nesse veículo"));
                    return net.minecraft.world.InteractionResult.FAIL;
                }
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.sendOverlayMessage(Component.literal("Paredão montado! Clique nele para abrir o menu (agachado para tirar)"));
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        });

        // /carnaval: chama um trio elétrico na hora, perto de você
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("carnaval").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    boolean ok = com.boombox.entity.Trio.start(p.level(), p.position());
                    if (!ok) c.getSource().sendFailure(Component.literal("Já tem um carnaval passando por perto!"));
                    return ok ? 1 : 0;
                })));

        // /aniversario: festa de aniversário de aldeão agora, na vila mais perto
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("aniversario").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    boolean ok = com.boombox.bar.Birthday.start(p.level(), p);
                    if (!ok) c.getSource().sendFailure(Component.literal("Nenhum aldeão por perto (ou já tem uma festa acontecendo)."));
                    return ok ? 1 : 0;
                })));

        // /reveillon: solta os fogos de ano novo agora (para testar ou comemorar fora de época)
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("reveillon").executes(c -> {
                    var server = c.getSource().getServer();
                    server.getPlayerList().broadcastSystemMessage(Component.literal("§e§l✦ FELIZ ANO NOVO! ✦"), false);
                    for (int i = 0; i < 12; i++) ServerBoomboxManager.newYearFireworks(server);
                    ServerBoomboxManager.newYearGifts(server);
                    return 1;
                })));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerBoomboxManager.sendAllTo(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerFiles.onDisconnect(handler.getPlayer());
            ServerBoomboxManager.onDisconnect(handler.getPlayer());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 5 == 0) ServerBoomboxManager.tickPlayers(server);
            if (server.getTickCount() % 20 == 0) ServerBoomboxManager.tickDancers(server);
            if (server.getTickCount() % 20 == 0) ServerBoomboxManager.tickShow(server);
            ServerBoomboxManager.tickNewYear(server);
            com.boombox.bar.Birthday.tick(server);
            if (server.getTickCount() % 2400 == 0) com.boombox.entity.Trio.maybeStart(server); // carnaval de vez em quando
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerBoomboxManager.clear());

        LOGGER.info("Boombox carregada!");
    }
}
