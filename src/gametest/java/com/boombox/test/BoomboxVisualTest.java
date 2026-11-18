package com.boombox.test;

import com.boombox.BoomboxBlockEntity;
import com.boombox.entity.DjLeo;
import com.boombox.entity.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Teste visual da 2.0: caixas tingidas com modos de luz, LEDs de rave, a casa do DJ Leo
 * e uma vila gerada (confere se ela ganhou exatamente uma casa do DJ Leo).
 * Rodar com: ./gradlew runClientGameTest — as imagens ficam em build/run/clientGameTest/screenshots.
 */
public class BoomboxVisualTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext server = sp.getServer();
            server.runCommand("time set midnight");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.fov().set(50);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            });

            // ---- 1) caixas tingidas + modos de luz + parede de LEDs
            server.runCommand("fill -5 -60 6 5 -56 6 minecraft:stone_bricks");
            String[] speakers = {
                    "party_speaker_small{Settings:{dye:14,light:1,color:6}}",   // vermelha, luz azul-clara fixa
                    "party_speaker_medium{Settings:{dye:0}}",                    // branca, arco-íris
                    "party_speaker_large{Settings:{dye:11,light:2}}",            // azul, na batida
                    "party_speaker_medium{Settings:{dye:4,light:3}}"};           // amarela, luz desligada
            for (int i = 0; i < speakers.length; i++) {
                String[] p = speakers[i].split("\\{", 2);
                server.runCommand("setblock " + (i * 3 - 4) + " -60 3 boombox:" + p[0] + "[facing=north]{" + p[1]);
            }
            for (int x = -4; x <= 4; x += 2) {
                server.runCommand("setblock " + x + " -57 5 boombox:rave_led[facing=north]");
            }
            server.runCommand("setblock 0 -55 4 boombox:rave_led[facing=down]");
            look(ctx, server, new Vec3(0.5, -60, -4.5), 0f, 12f, CameraType.FIRST_PERSON);
            ctx.waitTicks(30);
            ctx.takeScreenshot("speakers_dyed");

            // ---- 2) casa do DJ Leo (colocada direto com /place template)
            server.runCommand("place template boombox:djleo_house 30 -61 0");
            ctx.waitTicks(40); // a caixa sorteia o tamanho no 1º tick
            server.runCommand("time set noon");
            look(ctx, server, new Vec3(17.5, -56, -11.5), -45f, 12f, CameraType.FIRST_PERSON);
            ctx.waitTicks(20);
            ctx.takeScreenshot("house_outside");
            server.runCommand("time set midnight");
            look(ctx, server, new Vec3(32.5, -60, 4.5), -90f, 8f, CameraType.FIRST_PERSON);
            ctx.waitTicks(20);
            ctx.takeScreenshot("house_inside");
            look(ctx, server, new Vec3(34.5, -60, 2.5), -56f, 6f, CameraType.FIRST_PERSON);
            ctx.waitTicks(10);
            ctx.takeScreenshot("house_dj");
            String houseInfo = server.computeOnServer(s -> {
                var level = s.overworld();
                BlockPos sp1 = new BlockPos(38, -60, 4);
                String block = level.getBlockState(sp1).getBlock().getDescriptionId();
                String settings = level.getBlockEntity(sp1) instanceof BoomboxBlockEntity be ? be.settings().toString() : "sem caixa";
                long leos = level.getEntities(ModEntities.DJ_LEO, e -> true).size();
                return "caixa=" + block + " " + settings + " djleos=" + leos;
            });
            System.out.println("[BoomboxTest] casa: " + houseInfo);
            // menu da caixa do DJ Leo (travada no Modo DJ dele)
            ctx.runOnClient(mc -> mc.gui.setScreen(new com.boombox.client.BoomboxScreen(com.boombox.Target.block(new BlockPos(38, -60, 4)))));
            ctx.waitTicks(10);
            ctx.takeScreenshot("menu_djleo_locked");
            String widgets = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
                    .filter(w -> w instanceof net.minecraft.client.gui.components.AbstractWidget)
                    .map(w -> ((net.minecraft.client.gui.components.AbstractWidget) w).getMessage().getString()
                            + "=" + ((net.minecraft.client.gui.components.AbstractWidget) w).active)
                    .toList().toString());
            System.out.println("[BoomboxTest] botões travados: " + widgets);
            ctx.runOnClient(mc -> mc.gui.setScreen(new com.boombox.client.BoomboxScreen(com.boombox.Target.block(new BlockPos(-4, -60, 3)))));
            ctx.waitTicks(10);
            ctx.takeScreenshot("menu_small_speaker");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));

            // ---- 3) vila gerada de verdade: deve ter exatamente uma casa do DJ Leo
            server.runCommand("forceload add -100 200 100 400");
            ctx.waitTicks(40);
            server.runCommand("place structure minecraft:village_plains 0 -60 300");
            ctx.waitTicks(60);
            List<Vec3> leos = server.computeOnServer(s -> s.overworld()
                    .getEntities(ModEntities.DJ_LEO, e -> e.position().z > 150).stream()
                    .map(DjLeo::position).toList());
            System.out.println("[BoomboxTest] DJ Leos na vila: " + leos.size() + " " + leos);
            if (!leos.isEmpty()) {
                Vec3 p = leos.get(0);
                String around = server.computeOnServer(s -> {
                    var level = s.overworld();
                    BlockPos c = BlockPos.containing(p);
                    int leds = 0, boxes = 0;
                    for (BlockPos q : BlockPos.betweenClosed(c.offset(-10, -3, -10), c.offset(10, 6, 10))) {
                        var b = level.getBlockState(q).getBlock();
                        if (b == com.boombox.ModBlocks.RAVE_LED) leds++;
                        if (b instanceof com.boombox.BoomboxBlock) boxes++;
                    }
                    return "leds=" + leds + " caixas=" + boxes + " embaixo=" + level.getBlockState(c.below()).getBlock().getDescriptionId();
                });
                System.out.println("[BoomboxTest] perto do DJ Leo da vila: " + around);
                server.runCommand("time set noon");
                look(ctx, server, p.add(-14, 12, -14), -45f, 32f, CameraType.FIRST_PERSON);
                sp.getConnection().waitForChunksRender();
                ctx.waitTicks(40);
                ctx.takeScreenshot("village_djleo");
                look(ctx, server, p.add(14, 12, 14), 135f, 32f, CameraType.FIRST_PERSON);
                sp.getConnection().waitForChunksRender();
                ctx.waitTicks(30);
                ctx.takeScreenshot("village_djleo_2");
            }
        }
    }

    private static void look(ClientGameTestContext ctx, TestServerContext server, Vec3 at, float yaw, float pitch, CameraType cam) {
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", at.x, at.y, at.z, yaw, pitch));
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(pl -> {
            pl.getAbilities().flying = true; // não cai durante a foto
            pl.onUpdateAbilities();
        }));
        ctx.runOnClient(mc -> mc.options.setCameraType(cam));
        ctx.waitTicks(5);
    }
}
