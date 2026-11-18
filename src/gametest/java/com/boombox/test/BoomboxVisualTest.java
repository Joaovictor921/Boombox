package com.boombox.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Teste visual: cria um mundo plano, coloca as caixas no chão e põe cada uma na mão do jogador,
 * tirando prints de vários ângulos (câmera de terceira pessoa com a cabeça girada em relação ao corpo).
 * Rodar com: ./gradlew runClientGameTest — as imagens ficam em build/run/clientGameTest/screenshots.
 */
public class BoomboxVisualTest implements FabricClientGameTest {
    private static final String[] SPEAKERS = {"boombox", "party_speaker_small", "party_speaker_medium", "party_speaker_large"};

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.fov().set(40);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); // sem barra de itens nas prints
            });

            // caixas no chão, viradas para o jogador
            for (int i = 0; i < SPEAKERS.length; i++) {
                server.runCommand("setblock " + (i * 2 - 3) + " -60 3 boombox:" + SPEAKERS[i] + "[facing=north]");
            }
            server.runCommand("tp @a 0.5 -60 -3.5 0 15");
            ctx.waitTicks(40);
            ctx.takeScreenshot("blocks");

            server.runCommand("tp @a 0.5 -60 -10.5 0 0");
            ctx.waitTicks(10);
            for (String s : SPEAKERS) {
                server.runCommand("item replace entity @p weapon.mainhand with boombox:" + s);
                ctx.waitTicks(5);
                shot(ctx, s + "_front_right", CameraType.THIRD_PERSON_FRONT, -45f);
                shot(ctx, s + "_front_left", CameraType.THIRD_PERSON_FRONT, 45f);
                shot(ctx, s + "_back_right", CameraType.THIRD_PERSON_BACK, 45f);
                shot(ctx, s + "_back_left", CameraType.THIRD_PERSON_BACK, -45f);
            }
        }
    }

    /** Corpo virado para +Z (yaw 0); a cabeça/câmera gira headYaw graus. */
    private static void shot(ClientGameTestContext ctx, String name, CameraType cam, float headYaw) {
        ctx.runOnClient(mc -> mc.options.setCameraType(cam));
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> {
            mc.player.setYRot(headYaw);
            mc.player.yRotO = headYaw;
            mc.player.setYHeadRot(headYaw);
            mc.player.yHeadRotO = headYaw;
            mc.player.setXRot(22f);
            mc.player.xRotO = 22f;
            mc.player.yBodyRot = 0f;
            mc.player.yBodyRotO = 0f;
        });
        ctx.takeScreenshot("held_" + name);
    }
}
