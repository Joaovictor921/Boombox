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
                    && ClientBoomboxManager.playingNear(mc, mc.player.position(), 40) != null, 20 * 600);
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
            look(ctx, server, new Vec3(HX + 10.5, HY + 1.2, HZ + 7.5), -90f, -14f);
            ctx.waitTicks(60);
            ctx.takeScreenshot("rave_fumaca_globo");

            // ---- tremor da câmera: colado numa caixa vs. longe (maior valor em 3 segundos)
            for (Object[] spot : new Object[][]{{"colado na caixa", new Vec3(HX + 16.5, HY + 1, HZ + 2.6)},
                    {"meio da pista", new Vec3(HX + 12.5, HY + 1, HZ + 7.5)}, {"lá fora", new Vec3(HX - 12.5, HY + 1, HZ + 7.5)}}) {
                look(ctx, server, (Vec3) spot[1], 180f, 0f);
                float[] max = {0};
                for (int i = 0; i < 60; i++) {
                    ctx.runOnClient(mc -> max[0] = Math.max(max[0], ClientBoomboxManager.cameraShake(mc)));
                    ctx.waitTicks(1);
                }
                System.out.println("[BoomboxTest] tremor " + spot[0] + ": " + String.format(Locale.ROOT, "%.2f", max[0]));
            }

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

            // ---- luz: nenhum lugar do chão da casa pode ficar com luz 0 (é onde monstros nascem)
            String light = server.computeOnServer(s -> {
                var level = s.overworld();
                int min = 99, dark = 0, total = 0;
                for (BlockPos p : BlockPos.betweenClosed(HX + 2, HY + 1, HZ + 1, HX + 31, HY + 1, HZ + 14)) {
                    if (!level.getBlockState(p).isAir() || !level.getBlockState(p.below()).isSolidRender()) continue;
                    int l = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p);
                    min = Math.min(min, l);
                    total++;
                    if (l == 0) dark++;
                }
                return "luz mínima no chão=" + min + ", lugares escuros=" + dark + " de " + total;
            });
            System.out.println("[BoomboxTest] " + light);

            // ---- pedir música pro DJ Leo com uma barra de ouro
            var djPos = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.DJ_LEO, e -> true).get(0).position());
            look(ctx, server, djPos.add(-3, 0, 0), -90f, 0f);
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:gold_ingot 3");
            ctx.waitTicks(10);
            int djId = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.DJ_LEO, e -> true).get(0).getId());
            String reqUrl = url + "?pedido=1";
            ctx.runOnClient(mc -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.boombox.net.Payloads.DjRequest(djId, reqUrl, "Pedido de teste", 120)));
            ctx.waitTicks(20);
            String req = server.computeOnServer(s -> {
                var level = s.overworld();
                int gold = s.getPlayerList().getPlayers().get(0).getMainHandItem().getCount();
                int playing = 0;
                for (BlockPos p : BlockPos.betweenClosed(HX, HY, HZ, HX + 33, HY + 11, HZ + 16)) {
                    if (level.getBlockEntity(p) instanceof BoomboxBlockEntity be && be.song().url().equals(reqUrl)) playing++;
                }
                return "ouro na mão=" + gold + " (era 3), caixas tocando o pedido=" + playing;
            });
            System.out.println("[BoomboxTest] pedido pro DJ Leo: " + req);

            // ---- telão com o vídeo de verdade (YouTube) em todas as caixas da casa
            String yt = "https://www.youtube.com/watch?v=jNQXAC9IVRw";
            server.runOnServer(s -> {
                var level = s.overworld();
                SongData song = SongData.EMPTY.withSong(yt, "Me at the zoo", 19, System.currentTimeMillis() + 3000);
                for (BlockPos p : BlockPos.betweenClosed(HX, HY, HZ, HX + 33, HY + 11, HZ + 16)) {
                    if (level.getBlockEntity(p) instanceof BoomboxBlockEntity be) be.setSong(song);
                }
            });
            look(ctx, server, new Vec3(HX + 16.5, HY + 2.6, HZ + 7.5), -90f, -6f);
            int vt = ctx.waitFor((Minecraft mc) -> com.boombox.client.VideoManager.anyFrame(), 20 * 90);
            System.out.println("[BoomboxTest] telão mostrou vídeo depois de " + vt + " ticks");
            ctx.waitTicks(60);
            ctx.takeScreenshot("telao_video");

            // ---- bar do Seu Zé: a caixinha tem que começar sozinha com uma música da playlist do bar
            server.runCommand("place template boombox:ze_bar 30 -61 40");
            look(ctx, server, new Vec3(33.5, -60, 44.5), -90f, 4f);
            BlockPos barSpeaker = new BlockPos(40, -59, 42);
            int bw = server.waitFor(s -> s.overworld().getBlockEntity(barSpeaker) instanceof BoomboxBlockEntity be && be.song().hasSong(), 20 * 180);
            String barSong = server.computeOnServer(s -> s.overworld().getBlockEntity(barSpeaker) instanceof BoomboxBlockEntity be
                    ? be.song().title() + " | fila=" + be.song().queue().stream().map(q -> q.title()).toList() : "sem caixa");
            System.out.println("[BoomboxTest] bar do Seu Zé tocou depois de " + bw + " ticks: " + barSong);
            ctx.waitTicks(200);
            ctx.takeScreenshot("ze_bar_inside");
            server.runCommand("time set noon");
            look(ctx, server, new Vec3(22.5, -57, 35.5), -60f, 15f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("ze_bar_outside");

            // ---- 3.0: Seu Zé vende cerveja por uma esmeralda; beber deixa tonto e dá conquista
            String ze = server.computeOnServer(s -> {
                var level = s.overworld();
                var pl = s.getPlayerList().getPlayers().get(0);
                var zes = level.getEntities(ModEntities.SEU_ZE, e -> true);
                if (zes.isEmpty()) return "sem Seu Zé";
                var z = zes.get(0);
                pl.teleportTo(z.getX() - 2, z.getY(), z.getZ());
                pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));
                ((com.boombox.entity.SeuZe) z).order(pl, 0, 0); // compra uma cerveja pelo cardápio
                int slot = -1;
                for (int i = 0; i < pl.getInventory().getContainerSize(); i++) if (pl.getInventory().getItem(i).is(com.boombox.ModBlocks.BEER)) slot = i;
                if (slot < 0) return "Seu Zé não deu a cerveja";
                var beer = pl.getInventory().getItem(slot);
                beer.finishUsingItem(level, pl);
                boolean tonto = pl.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA);
                return "cerveja comprada, tonto=" + tonto + ", fregueses jogando truco="
                        + level.getEntities(ModEntities.RAVER, e -> e.playingCards()).size();
            });
            System.out.println("[BoomboxTest] bar 3.0: " + ze);
            look(ctx, server, new Vec3(33.5, -59.5, 44.5), -90f, 10f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("ze_bar_seuze");

            // ---- truco: uma partida inteira jogada no automático tem que terminar
            String truco = ctx.computeOnClient(mc -> {
                int games = 0, wins = 0;
                for (int g = 0; g < 20; g++) {
                    com.boombox.client.TrucoGame t = new com.boombox.client.TrucoGame();
                    int steps = 0;
                    while (t.phase != com.boombox.client.TrucoGame.Phase.GAME_OVER && steps++ < 5000) {
                        switch (t.phase) {
                            case PLAYER_TURN -> { if (steps % 7 == 0) t.playerRaises(); else t.playerPlays(0); }
                            case PLAYER_ANSWER -> t.playerAnswers(steps % 3);
                            default -> t.botStep();
                        }
                    }
                    if (t.phase == com.boombox.client.TrucoGame.Phase.GAME_OVER) games++;
                    if (t.playerWon()) wins++;
                }
                return games + " de 20 partidas terminaram (você ganhou " + wins + ")";
            });
            System.out.println("[BoomboxTest] truco: " + truco);
            int raverId = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.RAVER, e -> e.playingCards()).get(0).getId());
            ctx.runOnClient(mc -> mc.gui.setScreen(new com.boombox.client.TrucoScreen(raverId)));
            ctx.waitTicks(30);
            ctx.takeScreenshot("truco");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));

            // ---- de dia a balada fecha: DJ Leo dorme e as caixas param
            ctx.waitTicks(80);
            String day = server.computeOnServer(s -> {
                var level = s.overworld();
                var dj = level.getEntities(ModEntities.DJ_LEO, e -> e.getZ() < 30).get(0);
                int playing = 0;
                for (BlockPos p : BlockPos.betweenClosed(HX, HY, HZ, HX + 33, HY + 11, HZ + 16)) {
                    if (level.getBlockEntity(p) instanceof BoomboxBlockEntity be && be.song().hasSong()) playing++;
                }
                return "DJ dormindo=" + dj.sleepingDay() + " nome=" + dj.getCustomName().getString() + " caixas tocando=" + playing;
            });
            System.out.println("[BoomboxTest] de dia: " + day);
            look(ctx, server, new Vec3(HX + 24.5, HY + 2.6, HZ + 7.5), -90f, 15f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("djleo_dormindo");

            // ---- paredão no barco, tocando
            server.runCommand("summon minecraft:oak_boat 10 -60 -12");
            ctx.waitTicks(10);
            String boat = server.computeOnServer(s -> {
                var level = s.overworld();
                var b = level.getEntitiesOfClass(net.minecraft.world.entity.vehicle.boat.AbstractBoat.class,
                        new net.minecraft.world.phys.AABB(new BlockPos(10, -60, -12)).inflate(4)).get(0);
                var ms = com.boombox.entity.MountedSpeaker.fromItem(level,
                        new net.minecraft.world.item.ItemStack(com.boombox.ModBlocks.ITEMS.get(com.boombox.SpeakerType.PARTY_LARGE)),
                        com.boombox.SpeakerType.PARTY_LARGE, b);
                if (ms == null) return "não montou";
                ms.setSong(SongData.EMPTY.withSong(url, "Paredão de teste", 120, System.currentTimeMillis() + 1500));
                return "montado no barco: " + ms.isPassenger();
            });
            System.out.println("[BoomboxTest] paredão: " + boat);
            look(ctx, server, new Vec3(7.5, -59, -15.5), -40f, 12f);
            int bt = ctx.waitFor((Minecraft mc) -> ClientBoomboxManager.playingNear(mc, new Vec3(10, -59, -12), 6) != null, 20 * 60);
            System.out.println("[BoomboxTest] paredão tocando depois de " + bt + " ticks");
            ctx.waitTicks(30);
            ctx.takeScreenshot("paredao_barco");

            // ---- conquistas ganhas no caminho
            String adv = server.computeOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                StringBuilder b = new StringBuilder();
                for (String n : new String[]{"pediu_musica", "colado_no_paredao", "bebeu_no_bar", "ganhou_no_truco"}) {
                    var h = s.getAdvancements().get(com.boombox.BoomboxMod.id(n));
                    b.append(n).append('=').append(h != null && pl.getAdvancements().getOrStartProgress(h).isDone()).append(' ');
                }
                return b.toString();
            });
            System.out.println("[BoomboxTest] conquistas: " + adv);

            // ======================= 3.1 =======================
            // fiado: pendura duas coisas, vê na lousa, paga
            String fiado = server.computeOnServer(s -> {
                var level = s.overworld();
                var pl = s.getPlayerList().getPlayers().get(0);
                var zeh = (com.boombox.entity.SeuZe) level.getEntities(ModEntities.SEU_ZE, e -> true).get(0);
                pl.teleportTo(zeh.getX() - 2, zeh.getY(), zeh.getZ());
                pl.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                zeh.order(pl, 2, 1);
                zeh.order(pl, 1, 1);
                String depois = zeh.debtsForDisplay().toString();
                var table = level.getBlockEntity(new BlockPos(33, -60, 42)) instanceof com.boombox.bar.PlasticTableBlockEntity t
                        ? t.items.stream().filter(i -> !i.isEmpty()).count() : -1;
                return "fiado na lousa=" + depois + " coisas na mesa=" + table;
            });
            System.out.println("[BoomboxTest] 3.1 bar: " + fiado);
            server.runCommand("time set midnight");
            look(ctx, server, new Vec3(35.5, -59.4, 47.5), -120f, 8f);
            ctx.waitTicks(40);
            ctx.takeScreenshot("bar31_dentro");
            look(ctx, server, new Vec3(39.5, -59.4, 43.0), -60f, -2f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("bar31_lousa");
            look(ctx, server, new Vec3(36.5, -59.4, 46.0), 0f, 10f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("bar31_tv");
            // sentar na cadeira
            String sit = server.computeOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                com.boombox.entity.Seat.sit(s.overworld(), new BlockPos(32, -60, 42), pl, 0.45, net.minecraft.core.Direction.EAST);
                return "sentado=" + pl.isPassenger();
            });
            System.out.println("[BoomboxTest] cadeira: " + sit);
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(20);
            ctx.takeScreenshot("bar31_sentado");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).stopRiding());
            // ---- 3.2: sinuca 3D na própria mesa (parte 0 em 38,-60,46)
            BlockPos mesa = new BlockPos(38, -60, 46);
            look(ctx, server, new Vec3(37.0, -58.0, 48.6), 180f, 42f);
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> com.boombox.bar.SinucaBlock.openHook.accept(mesa));
            ctx.waitTicks(17);
            ctx.takeScreenshot("sinuca_mira");
            String mira = ctx.computeOnClient(mc -> String.format(Locale.ROOT, "ângulo=%.2f força=%.2f",
                    com.boombox.client.BarClient.aimAngle(mesa), com.boombox.client.BarClient.aimPower()));
            server.runOnServer(s -> {
                if (s.overworld().getBlockEntity(mesa) instanceof com.boombox.bar.SinucaBlockEntity be) be.shoot(0.01, 1.0);
            });
            ctx.waitTicks(6);
            ctx.takeScreenshot("sinuca_tacada");
            ctx.waitTicks(200);
            String sinuca = server.computeOnServer(s -> {
                if (!(s.overworld().getBlockEntity(mesa) instanceof com.boombox.bar.SinucaBlockEntity be)) return "sem mesa";
                int in = 0;
                for (int i = 1; i < com.boombox.bar.SinucaBlockEntity.N; i++) if (be.in[i]) in++;
                return "andando=" + be.moving + " encaçapadas=" + in + " pontos=" + be.myScore + " branca x=" + String.format(Locale.ROOT, "%.2f", be.x[0]) + " msg=" + be.message;
            });
            System.out.println("[BoomboxTest] sinuca 3D: " + mira + " | " + sinuca);
            ctx.takeScreenshot("sinuca_depois");
            ctx.runOnClient(mc -> com.boombox.bar.SinucaBlock.openHook.accept(mesa)); // larga o taco (tacada fora da vez não faz nada)

            // churrasqueira (42,-60,49): espetinho, linguiça e pão de alho
            String churras = server.computeOnServer(s -> {
                if (!(s.overworld().getBlockEntity(new BlockPos(42, -60, 49)) instanceof com.boombox.bar.GrillBlockEntity g)) return "sem churrasqueira";
                g.put(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF));
                g.put(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PORKCHOP));
                g.put(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
                return "assando=" + g.isCooking();
            });
            look(ctx, server, new Vec3(40.6, -58.4, 47.2), -45f, 35f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("churrasco");
            ctx.waitTicks(com.boombox.bar.GrillBlockEntity.COOK_TICKS);
            String pronto = server.computeOnServer(s -> {
                var g = (com.boombox.bar.GrillBlockEntity) s.overworld().getBlockEntity(new BlockPos(42, -60, 49));
                return g.items.stream().filter(i -> !i.isEmpty()).map(i -> i.getItem().toString()).toList().toString();
            });
            System.out.println("[BoomboxTest] churrasco: " + churras + " depois=" + pronto);

            // seguranças: antes da meia-noite, sem pulseira, é empurrado; com pulseira, passa
            server.runCommand("time set 14000");
            String bouncer = server.computeOnServer(s -> {
                var level = s.overworld();
                var bs = level.getEntities(ModEntities.BOUNCER, e -> true);
                return bs.size() + " seguranças";
            });
            server.runCommand("tp @a " + (HX - 1.5) + " " + (HY + 1) + " " + (HZ + 7.5) + " -90 0");
            ctx.waitTicks(30);
            double semPulseira = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getX());
            server.runCommand("give @a boombox:vip_bracelet");
            server.runCommand("tp @a " + (HX - 1.5) + " " + (HY + 1) + " " + (HZ + 7.5) + " -90 0");
            ctx.waitTicks(30);
            double comPulseira = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getX());
            System.out.println("[BoomboxTest] " + bouncer + ": sem pulseira x=" + String.format(Locale.ROOT, "%.1f", semPulseira)
                    + " com pulseira x=" + String.format(Locale.ROOT, "%.1f", comPulseira) + " (começou em " + (HX - 1.5) + ")");
            look(ctx, server, new Vec3(HX - 5.5, HY + 2.2, HZ + 7.5), -90f, 8f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("segurancas");

            // ---- 3.2: camarote VIP (segurança do camarote barra quem não tem pulseira, até depois da meia-noite)
            server.runCommand("clear @a boombox:vip_bracelet");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a " + (HX + 6.5) + " " + (HY + 1) + " " + (HZ + 5.0) + " 0 0");
            ctx.waitTicks(30);
            double camSem = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getZ());
            server.runCommand("give @a boombox:vip_bracelet");
            server.runCommand("tp @a " + (HX + 6.5) + " " + (HY + 1) + " " + (HZ + 6.8) + " 0 0");
            ctx.waitTicks(30);
            double camCom = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getZ());
            System.out.println("[BoomboxTest] camarote: sem pulseira z=" + String.format(Locale.ROOT, "%.1f", camSem)
                    + " com pulseira z=" + String.format(Locale.ROOT, "%.1f", camCom));
            server.runCommand("gamemode creative @a");
            look(ctx, server, new Vec3(HX + 11.5, HY + 6.5, HZ + 7.5), 125f, 22f);
            ctx.waitTicks(60);
            ctx.takeScreenshot("camarote");
            look(ctx, server, new Vec3(HX + 22.5, HY + 3.5, HZ + 6.5), -80f, 5f);
            ctx.waitTicks(40);
            ctx.takeScreenshot("bolhas");

            // fone de ouvido na cabeça (aparece para os outros)
            server.runCommand("item replace entity @p armor.head with boombox:headphones");
            server.runCommand("item replace entity @p weapon.mainhand with boombox:boombox");
            look(ctx, server, new Vec3(HX - 8.5, HY + 1, HZ - 6.5), 0f, 0f);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(20);
            ctx.takeScreenshot("fone");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            // carnaval
            server.runCommand("time set noon");
            String carnaval = server.computeOnServer(s -> {
                var level = s.overworld();
                boolean ok = com.boombox.entity.Trio.start(level, new Vec3(0, -60, -40));
                var trios = level.getEntities(ModEntities.TRIO, e -> true);
                return "começou=" + ok + " trio com paredão=" + (!trios.isEmpty() && !trios.get(0).getPassengers().isEmpty());
            });
            ctx.waitTicks(100);
            long followers = server.computeOnServer(s -> (long) s.overworld().getEntities(ModEntities.RAVER,
                    e -> e.position().distanceTo(new Vec3(0, -60, -40)) < 20).size());
            System.out.println("[BoomboxTest] carnaval: " + carnaval + " foliões=" + followers);
            var trioPos = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.TRIO, e -> true).get(0).position());
            look(ctx, server, trioPos.add(-9, 4, -9), -45f, 14f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("carnaval");

            // arraiá
            server.runCommand("place template boombox:arraia -40 -61 0");
            ctx.waitTicks(20);
            look(ctx, server, new Vec3(-43.5, -57, -4.5), -40f, 22f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("arraia");

            look(ctx, server, new Vec3(HX - 9.5, HY + 9, HZ - 9.5), -45f, 22f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("house_outside");

            // ---- 3.2: praia, pagode na laje e réveillon
            server.runCommand("place template boombox:beach_hut -40 -61 30");
            server.runCommand("place template boombox:pagode_laje -40 -61 60");
            ctx.waitTicks(20);
            look(ctx, server, new Vec3(-44.5, -56.5, 25.5), -35f, 25f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("praia");
            String pagode = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.RAVER, e -> e.instrument() > 0).stream()
                    .map(e -> e.instrument() + "@" + e.getMainHandItem().getItem()).toList().toString());
            System.out.println("[BoomboxTest] pagode: " + pagode);
            look(ctx, server, new Vec3(-44.5, -54.5, 55.5), -35f, 25f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("pagode_laje");
            server.runCommand("time set midnight");
            server.runCommand("reveillon");
            look(ctx, server, new Vec3(-30.5, -59, 20.5), 0f, -35f);
            ctx.waitTicks(36);
            ctx.takeScreenshot("reveillon");
            long fogos = server.computeOnServer(s -> (long) s.overworld().getEntitiesOfClass(net.minecraft.world.entity.projectile.FireworkRocketEntity.class, new net.minecraft.world.phys.AABB(-200, -100, -200, 200, 400, 200)).size());
            System.out.println("[BoomboxTest] réveillon: foguetes no ar=" + fogos);
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
                long bars = server.computeOnServer(s -> (long) s.overworld()
                        .getEntities(ModEntities.RAVER, e -> e.atBar() && Math.abs(e.position().z - zc) < 150).size());
                System.out.println("[BoomboxTest] vila " + types[i] + ": " + leos.size() + " DJ Leo, " + bars + " fregueses de bar " + leos);
                long musicos = server.computeOnServer(s -> (long) s.overworld()
                        .getEntities(ModEntities.RAVER, e -> e.instrument() > 0 && Math.abs(e.position().z - zc) < 150).size());
                long praia = server.computeOnServer(s -> {
                    long n = 0;
                    for (BlockPos q : BlockPos.betweenClosed(-100, -64, zc - 100, 100, -40, zc + 100)) {
                        if (s.overworld().getBlockState(q).is(com.boombox.bar.BarBlocks.PARASOL)) n++;
                    }
                    return n;
                });
                System.out.println("[BoomboxTest] vila " + types[i] + ": músicos do pagode=" + musicos + " guarda-sóis=" + praia);
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
