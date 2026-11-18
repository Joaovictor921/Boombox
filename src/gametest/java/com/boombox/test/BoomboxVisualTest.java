package com.boombox.test;

import com.boombox.BoomboxBlockEntity;
import com.boombox.SongData;
import com.boombox.client.ClientBoomboxManager;
import com.boombox.entity.DjLeo;
import com.boombox.entity.ModEntities;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;

/**
 * Teste visual da 2.1: casa de rave do DJ Leo com música tocando de verdade (uma batida eletrônica gerada
 * aqui e servida por um servidor HTTP local), medição do abafamento do som fora da casa e vilas de todos
 * os tipos (cada uma deve ganhar uma casa do DJ Leo).
 * Rodar com: ./gradlew runClientGameTest — as imagens ficam em build/run/clientGameTest/screenshots.
 */
public class BoomboxVisualTest implements FabricClientGameTest {
    private static final int HX = 30, HY = -61, HZ = 0; // canto da casa

    @Override
    public void runTest(ClientGameTestContext ctx) {
        HttpServer http = startBeatServer();
        String url = "http://127.0.0.1:" + http.getAddress().getPort() + "/batida.wav";
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext server = sp.getServer();
            server.runCommand("time set midnight");
            server.runCommand("weather clear");
            server.runCommand("gamemode creative @a");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.fov().set(70);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            });

            // ---- casa do DJ Leo com a batida tocando em todas as caixas dela
            server.runCommand("place template boombox:djleo_house " + HX + " " + HY + " " + HZ);
            ctx.waitTicks(60);
            int speakers = server.computeOnServer(s -> {
                var level = s.overworld();
                SongData song = SongData.EMPTY.withSong(url, "Batida de teste", 120, System.currentTimeMillis() + 1500);
                int n = 0;
                for (BlockPos p : BlockPos.betweenClosed(HX, HY, HZ, HX + 33, HY + 11, HZ + 16)) {
                    if (level.getBlockEntity(p) instanceof BoomboxBlockEntity be) {
                        be.setSong(song);
                        n++;
                    }
                }
                return n;
            });
            System.out.println("[BoomboxTest] caixas na casa: " + speakers);
            look(ctx, server, new Vec3(HX + 4.5, HY + 1, HZ + 7.5), -90f, 0f);
            int waited = ctx.waitFor((Minecraft mc) -> mc.player != null
                    && ClientBoomboxManager.playingNear(mc, mc.player.position(), 40) != null, 20 * 240);
            System.out.println("[BoomboxTest] música começou depois de " + waited + " ticks");
            ctx.waitTicks(60);
            ctx.takeScreenshot("rave_entrance");
            look(ctx, server, new Vec3(HX + 12.5, HY + 2.5, HZ + 2.5), -65f, 2f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("rave_floor");
            look(ctx, server, new Vec3(HX + 20.5, HY + 2.2, HZ + 7.5), -90f, -8f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("rave_stage");
            look(ctx, server, new Vec3(HX + 23.5, HY + 1, HZ + 12.5), -120f, -5f);
            ctx.waitTicks(17);
            ctx.takeScreenshot("rave_side");

            // ---- abafamento: quanto passa pelas paredes, de dentro e de fora
            Vec3 src = new Vec3(HX + 31.5, HY + 2.4, HZ + 7.5);   // paredão do fundo
            Vec3 side = new Vec3(HX + 16.5, HY + 1.4, HZ + 1.5);  // caixa da lateral
            String occ = ctx.computeOnClient(mc -> {
                StringBuilder b = new StringBuilder();
                Object[][] ears = {
                        {"pista", new Vec3(HX + 14.5, HY + 2.6, HZ + 7.5)},
                        {"colado na parede de fora", new Vec3(HX + 16.5, HY + 2.6, HZ - 1.5)},
                        {"12 blocos fora", new Vec3(HX + 16.5, HY + 2.6, HZ - 12.5)},
                        {"porta (fora)", new Vec3(HX - 2.5, HY + 2.6, HZ + 7.5)}};
                for (Object[] e : ears) {
                    Vec3 ear = (Vec3) e[1];
                    float o1 = ClientBoomboxManager.occlusion(mc, src, ear), o2 = ClientBoomboxManager.occlusion(mc, side, ear);
                    b.append(String.format(Locale.ROOT, "%s: fundo=%.2f (volume x%.2f) lateral=%.2f (x%.2f); ", e[0],
                            o1, ClientBoomboxManager.occlusionGain(o1), o2, ClientBoomboxManager.occlusionGain(o2)));
                }
                return b.toString();
            });
            System.out.println("[BoomboxTest] abafamento: " + occ);
            String dancers = server.computeOnServer(s -> {
                var level = s.overworld();
                long ravers = level.getEntities(ModEntities.RAVER, e -> true).size();
                long leos = level.getEntities(ModEntities.DJ_LEO, e -> true).size();
                return "aldeões festeiros=" + ravers + " djleo=" + leos;
            });
            System.out.println("[BoomboxTest] " + dancers);

            server.runCommand("time set noon");
            look(ctx, server, new Vec3(HX - 9.5, HY + 9, HZ - 9.5), -45f, 22f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("house_outside");
            server.runCommand("time set midnight");

            // ---- vilas de todos os tipos: cada uma deve ter um DJ Leo
            String[] types = {"plains", "desert", "savanna", "snowy", "taiga"};
            for (int i = 0; i < types.length; i++) {
                int cz = 400 + i * 300;
                server.runCommand("forceload add -100 " + (cz - 100) + " 100 " + (cz + 100));
                ctx.waitTicks(30);
                server.runCommand("place structure minecraft:village_" + types[i] + " 0 -60 " + cz);
                ctx.waitTicks(40);
                final int zc = cz;
                List<Vec3> leos = server.computeOnServer(s -> s.overworld()
                        .getEntities(ModEntities.DJ_LEO, e -> Math.abs(e.position().z - zc) < 150).stream()
                        .map(DjLeo::position).toList());
                System.out.println("[BoomboxTest] vila " + types[i] + ": " + leos.size() + " DJ Leo " + leos);
                if (i == 0 && !leos.isEmpty()) {
                    Vec3 p = leos.get(0);
                    server.runCommand("time set noon");
                    look(ctx, server, p.add(-22, 18, -22), -45f, 30f);
                    sp.getConnection().waitForChunksRender();
                    ctx.waitTicks(40);
                    ctx.takeScreenshot("village_djleo");
                    server.runCommand("time set midnight");
                }
                server.runCommand("forceload remove -100 " + (cz - 100) + " 100 " + (cz + 100));
            }
        } finally {
            http.stop(0);
        }
    }

    private static void look(ClientGameTestContext ctx, TestServerContext server, Vec3 at, float yaw, float pitch) {
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", at.x, at.y, at.z, yaw, pitch));
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(pl -> {
            pl.getAbilities().flying = true; // não cai durante a foto
            pl.onUpdateAbilities();
        }));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(5);
    }

    /** Servidor HTTP local com uma batida eletrônica (bumbo forte a 125 BPM, baixo e chimbal) em WAV. */
    private static HttpServer startBeatServer() {
        try {
            byte[] wav = beatWav(120);
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/batida.wav", ex -> {
                ex.getResponseHeaders().add("Content-Type", "audio/wav");
                ex.sendResponseHeaders(200, wav.length);
                try (var out = ex.getResponseBody()) {
                    out.write(wav);
                }
            });
            http.start();
            return http;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] beatWav(int seconds) {
        int rate = 44100, n = rate * seconds;
        ByteBuffer pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN);
        double beat = 60.0 / 125.0;
        java.util.Random rnd = new java.util.Random(1);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) rate;
            double tb = t % beat;                                   // tempo desde o último bumbo
            double f = 45 + 110 * Math.exp(-tb * 30);               // bumbo: queda de frequência
            phase += 2 * Math.PI * f / rate;
            double kick = Math.sin(phase) * Math.exp(-tb * 7);
            double off = (t + beat / 2) % beat;                     // contratempo
            double hat = (rnd.nextDouble() * 2 - 1) * Math.exp(-off * 60) * 0.25;
            double bass = Math.sin(2 * Math.PI * 55 * t) * 0.3 * (off < beat * 0.45 ? 1 : 0.2);
            double v = Math.max(-1, Math.min(1, kick * 0.8 + hat + bass));
            pcm.putShort((short) (v * 30000));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes()).put("fmt ".getBytes())
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(n * 2);
        out.writeBytes(h.array());
        out.writeBytes(pcm.array());
        return out.toByteArray();
    }
}
