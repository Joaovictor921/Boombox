package com.boombox.client;

import com.boombox.BoomboxItem;
import com.boombox.BoomboxMod;
import com.boombox.Target;
import com.boombox.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.boombox.ModBlocks;
import com.boombox.client.render.BoomboxBlockRenderer;
import com.boombox.client.render.BoomboxItemRenderer;
import com.boombox.client.render.HeldBoomboxLayer;
import com.boombox.mixin.client.SpecialModelRenderersAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

public class BoomboxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BoomboxMod.openScreenHook = pos -> Minecraft.getInstance().gui.setScreen(new BoomboxScreen(Target.block(pos)));
        BoomboxItem.openHeldScreenHook = hand -> Minecraft.getInstance().gui.setScreen(new BoomboxScreen(Target.held(hand)));

        // modelo 3D animado: bloco, item e boombox na mão
        BlockEntityRendererRegistry.register(ModBlocks.BOOMBOX_BE, BoomboxBlockRenderer::new);
        BlockEntityRendererRegistry.register(ModBlocks.RAVE_LED_BE, com.boombox.client.render.RaveLedRenderer::new);
        BlockEntityRendererRegistry.register(ModBlocks.RAVE_SCREEN_BE, com.boombox.client.render.RaveScreenRenderer::new);
        BlockEntityRendererRegistry.register(ModBlocks.DISCO_BALL_BE, com.boombox.client.render.DiscoBallRenderer::new);
        com.boombox.SmokeMachineBlock.clientTicker = SmokeMachines::tick;
        BarClient.register();
        PartyClient33.register();
        PartyClient34.register();
        com.boombox.entity.DjLeo.openRequestHook = id -> Minecraft.getInstance().gui.setScreen(new DjRequestScreen(id));
        com.boombox.entity.Raver.openTrucoHook = id -> Minecraft.getInstance().gui.setScreen(new TrucoScreen(id));
        com.boombox.entity.MountedSpeaker.openMenuHook = id -> Minecraft.getInstance().gui.setScreen(new BoomboxScreen(Target.entity(id)));
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.boombox.entity.ModEntities.MOUNTED_SPEAKER, com.boombox.client.render.MountedSpeakerRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.boombox.entity.ModEntities.DJ_LEO, com.boombox.client.render.DjLeoRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.boombox.entity.ModEntities.RAVER, com.boombox.client.render.RaverRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.boombox.entity.ModEntities.SEU_ZE, com.boombox.client.render.SeuZeRenderer::new);
        SpecialModelRenderersAccessor.boombox$idMapper().put(BoomboxMod.id("boombox"), BoomboxItemRenderer.Unbaked.MAP_CODEC);
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new HeldBoomboxLayer(avatar));
                helper.register(new com.boombox.client.render.Renderers33.BraceletLayer(avatar));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(Payloads.BoomboxState.TYPE,
                (payload, ctx) -> ClientBoomboxManager.onState(payload));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.UploadNeeded.TYPE,
                (payload, ctx) -> ClientFiles.onUploadNeeded(payload.sha1()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.FileReady.TYPE,
                (payload, ctx) -> ClientFiles.onFileReady(payload.sha1()));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.FileChunk.TYPE,
                (payload, ctx) -> ClientFiles.onFileChunk(payload));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientBoomboxManager.tick(mc);
            VideoManager.tick();
            BarClient.tick(mc);
            PartyClient33.tick(mc);
            ClientFiles.tick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
            ClientBoomboxManager.clear();
            VideoManager.clear();
            SmokeMachines.clear();
            ClientFiles.clear();
        }));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) ->
                Tools.ensureReady().exceptionally(e -> {
                    Tools.chat("§cNão foi possível preparar as ferramentas: " + e.getMessage());
                    return null;
                }));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> ClientBoomboxManager.clear());
        // playlists salvas em .minecraft/boombox_playlists/*.txt são lidas ao iniciar
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> Playlists.load());
    }
}
