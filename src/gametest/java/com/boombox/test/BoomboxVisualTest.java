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

    static volatile long lastServerTick = System.currentTimeMillis(), lastClientTick = System.currentTimeMillis();

    /** Reproduz o travamento: mundo normal, campo de uma vila de verdade, copa, vários gols seguidos. */
    private void trava(ClientGameTestContext ctx) {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(sv -> lastServerTick = System.currentTimeMillis());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> lastClientTick = System.currentTimeMillis());
        Thread dog = new Thread(() -> {
            boolean dumped = false;
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                long now = System.currentTimeMillis();
                long ss = now - lastServerTick, cs = now - lastClientTick;
                if ((ss > 6000 || cs > 6000) && !dumped) {
                    dumped = true;
                    System.out.println("[BoomboxTest] TRAVOU servidor parado " + ss + " ms, cliente parado " + cs + " ms");
                    for (var e : Thread.getAllStackTraces().entrySet()) {
                        String n = e.getKey().getName();
                        if (!n.contains("Server thread") && !n.contains("Render thread")) continue;
                        StringBuilder sb = new StringBuilder("[BoomboxTest] pilha " + n + ":\n");
                        for (var f : e.getValue()) sb.append("    at ").append(f).append('\n');
                        System.out.println(sb);
                    }
                }
                if (ss < 2000 && cs < 2000) dumped = false;
            }
        }, "boombox-watchdog");
        dog.setDaemon(true);
        dog.start();
        try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(st -> st.setSeed("3720")).create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("gamerule spawn_monsters false");
            ctx.runOnClient(mc -> mc.options.renderDistance().set(16));
            String at = server.computeOnServer(s -> {
                var sl = s.overworld();
                var hit = sl.getChunkSource().getGenerator().findNearestMapStructure(sl,
                        sl.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrThrow(net.minecraft.tags.StructureTags.VILLAGE),
                        new BlockPos(-1248, 64, 320), 20, false);
                var chunk = sl.getChunk(hit.getFirst().getX() >> 4, hit.getFirst().getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
                for (var st : chunk.getAllStarts().values()) for (var pc : st.getPieces()) {
                    if (pc instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pe && pe.getElement().toString().contains("boombox:varzea")) {
                        var c = pc.getBoundingBox().getCenter();
                        return c.getX() + " " + c.getZ();
                    }
                }
                return null;
            });
            System.out.println("[BoomboxTest] trava campo em " + at);
            String[] xz = at.split(" ");
            server.runCommand("tp @p " + xz[0] + " 200 " + xz[1]);
            server.runCommand("effect give @p slow_falling 30");
            ctx.waitTicks(200);
            ctx.waitTicks(100);
            String setup = server.computeOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                var bs = s.overworld().getEntities(ModEntities.BALL, e -> e.distanceTo(pl) < 300);
                if (bs.isEmpty()) return "sem bola";
                var b = bs.get(0);
                pl.teleportTo(b.getX(), b.getY() + 1, b.getZ());
                b.setup(pl, com.boombox.entity.Sports.CAMPEONATO, "pix", 0xF0D020, 3);
                return "ok " + b.info();
            });
            System.out.println("[BoomboxTest] trava setup: " + setup);
            for (int g = 0; g < 8; g++) {
                long t0 = System.currentTimeMillis();
                ctx.waitTicks(200);
                long dt = System.currentTimeMillis() - t0;
                String st = server.computeOnServer(s -> {
                    var pl = s.getPlayerList().getPlayers().get(0);
                    var bs = s.overworld().getEntities(ModEntities.BALL, e -> e.distanceTo(pl) < 300);
                    if (bs.isEmpty()) return "sem bola";
                    var b = bs.get(0);
                    String r = b.minute() + " " + b.scoreA() + "x" + b.scoreB() + String.format(Locale.ROOT, " tick médio %.1f ms", s.getAverageTickTimeNanos() / 1e6);
                    // gol do pix: a pessoa chuta de perto
                    b.forcePlay();
                    b.loseOwner();
                    Vec3 gl = b.goal(1);
                    Vec3 from = b.teamPoint(0, b.length() - 6, 0);
                    pl.teleportTo(from.x, from.y, from.z);
                    b.setPos(from.x, from.y + 0.3, from.z);
                    b.setDeltaMovement(new Vec3(gl.x - from.x, 0, gl.z - from.z).normalize().scale(1.4).add(0, 0.12, 0));
                    return r;
                });
                System.out.println("[BoomboxTest] trava " + g + " (" + dt + " ms para 200 ticks): " + st);
            }
            ctx.waitTicks(200);
            ctx.runOnClient(mc -> mc.options.renderDistance().set(12));
        }
    }

    /** 3.7.2: num mundo normal, quantas vilas ganharam o campo de futebol. */
    private void vila(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(st -> st.setSeed("3720")).create()) {
            TestServerContext server = sp.getServer();
            for (int seed = 0; seed < 1; seed++) {
                String r = server.computeOnServer(s -> {
                    var sl = s.overworld();
                    java.util.Set<Long> seen = new java.util.HashSet<>();
                    int villages = 0, fields = 0;
                    StringBuilder where = new StringBuilder();
                    for (int gx = -3; gx <= 3; gx++) for (int gz = -3; gz <= 3; gz++) {
                        var hit = sl.getChunkSource().getGenerator().findNearestMapStructure(sl,
                                sl.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrThrow(net.minecraft.tags.StructureTags.VILLAGE),
                                new BlockPos(gx * 1500, 64, gz * 1500), 20, false);
                        if (hit == null) continue;
                        BlockPos p = hit.getFirst();
                        if (!seen.add(net.minecraft.world.level.ChunkPos.containing(p).pack())) continue;
                        var chunk = sl.getChunk(p.getX() >> 4, p.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
                        for (var st : chunk.getAllStarts().values()) {
                            if (!st.isValid()) continue;
                            villages++;
                            boolean f = false;
                            double d = 0;
                            for (var pc : st.getPieces()) {
                                if (pc instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pe && pe.getElement().toString().contains("boombox:varzea")) {
                                    f = true;
                                    var bb = pc.getBoundingBox();
                                    d = Math.hypot((bb.minX() + bb.maxX()) / 2.0 - p.getX(), (bb.minZ() + bb.maxZ()) / 2.0 - p.getZ());
                                }
                            }
                            if (f) fields++;
                            where.append(String.format(Locale.ROOT, "(%d,%d %s) ", p.getX(), p.getZ(), f ? String.format(Locale.ROOT, "campo a %.0f", d) : "sem campo"));
                        }
                    }
                    return "vilas=" + villages + " com campo=" + fields + " " + where;
                });
                System.out.println("[BoomboxTest] vila: " + r);
            }
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        HttpServer http = startBeatServer();
        String url = "http://127.0.0.1:" + http.getAddress().getPort() + "/batida.wav";
        if ("mapa".equals(System.getenv("BOOMBOX_QUICK"))) {
            mapa(ctx);
            return;
        }
        if ("vigia".equals(System.getenv("BOOMBOX_QUICK"))) {
            try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
                TestServerContext server = sp.getServer();
                ctx.waitTicks(40);
                System.out.println("[BoomboxTest] vigia: travando o mundo por 9 s");
                server.runOnServer(s -> {
                    try { Thread.sleep(9000); } catch (InterruptedException e) { }
                });
                ctx.waitTicks(60);
                System.out.println("[BoomboxTest] vigia: pausando o jogo por 9 s (não pode avisar)");
                ctx.runOnClient(mc -> mc.pauseGame(false));
                ctx.waitTicks(200);
                ctx.runOnClient(mc -> mc.gui.setScreen(null));
                ctx.waitTicks(40);
            }
            return;
        }
        if ("trava".equals(System.getenv("BOOMBOX_QUICK"))) {
            trava(ctx);
            return;
        }
        if ("vila".equals(System.getenv("BOOMBOX_QUICK"))) {
            vila(ctx);
            return;
        }
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            ctx.waitTicks(100);
            TestServerContext server = sp.getServer();
            server.runCommand("time set midnight");
            server.runCommand("weather clear");
            if ("vip".equals(System.getenv("BOOMBOX_QUICK"))) {
                vip381(ctx, server);
                return;
            }
            if ("v38".equals(System.getenv("BOOMBOX_QUICK"))) {
                v38(ctx, server);
                return;
            }
            if ("v37".equals(System.getenv("BOOMBOX_QUICK"))) {
                v37(ctx, server);
                return;
            }
            if ("copa".equals(System.getenv("BOOMBOX_QUICK"))) {
                copa(ctx, server);
                return;
            }
            if ("fut".equals(System.getenv("BOOMBOX_QUICK"))) {
                server.runCommand("time set midnight"); // 3.5.3: tem que jogar de noite também
                server.runCommand("forceload add 70 30 150 90");
                look(ctx, server, new Vec3(110.5, -40, 30.5), 0f, 45f);
                ctx.waitTicks(60);
                server.runCommand("place template boombox:varzea 80 -61 40");
                ctx.waitTicks(60);
                String setup = server.computeOnServer(s -> {
                    var bs = s.overworld().getEntities(ModEntities.BALL, e -> true);
                    if (bs.isEmpty()) return "sem bola";
                    var b = bs.get(0);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    b.setup(pl, com.boombox.entity.Sports.CAMPEONATO, "Bonde do Teste", 0xF0D020, 2);
                    long org = s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.ORG_FUT).size();
                    long jog = s.overworld().getEntities(ModEntities.RAVER, r -> b.teamOf(r) >= 0).size();
                    return "time A=" + b.teamName(0) + " (cor " + Integer.toHexString(b.teamColor(0)) + ") x " + b.teamName(1) + " (cor " + Integer.toHexString(b.teamColor(1))
                            + ") jogador no time=" + b.teamOf(pl) + " posição=" + com.boombox.entity.Sports.POSITIONS[b.positionOf(pl)]
                            + " banco=" + s.overworld().getEntities(ModEntities.RAVER, r -> b.isBenched(r)).stream().map(r -> r.stall).toList()
                            + " organizadores=" + org + " aldeões jogando=" + jog + " " + b.info();
                });
                System.out.println("[BoomboxTest] fut setup: " + setup);
                java.util.Map<Integer, Vec3> last = new java.util.HashMap<>();
                for (int i = 0; i < 45; i++) {
                    ctx.waitTicks(20);
                    String st = server.computeOnServer(s -> {
                        var bs = s.overworld().getEntities(ModEntities.BALL, e -> true);
                        if (bs.isEmpty()) return "sem bola";
                        var b = bs.get(0);
                        var rs = s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.TEAM_A || r.role() == com.boombox.entity.RaverRoles.TEAM_B);
                        StringBuilder sb = new StringBuilder();
                        int still = 0;
                        for (var r : rs) {
                            Vec3 o = last.put(r.getId(), r.position());
                            double d = o == null ? 0 : o.distanceTo(r.position());
                            if (d < 0.3) still++;
                            sb.append(String.format(Locale.ROOT, "%.1f ", d));
                        }
                        return b.minute() + " " + b.scoreA() + "x" + b.scoreB() + " parada=" + b.restarting() + " tipo=" + b.restartKind()
                                + String.format(Locale.ROOT, " bola=(%.1f,%.1f) altura=%.1f v=%.2f", b.getX(), b.getZ(), b.getY() + 60, b.getDeltaMovement().horizontalDistance())
                                + " parados=" + still + "/" + rs.size() + "";
                    });
                    System.out.println("[BoomboxTest] fut " + i + ": " + st);
                    server.runOnServer(s -> {
                        var bs = s.overworld().getEntities(ModEntities.BALL, e -> true);
                        if (bs.isEmpty() || !bs.get(0).hasGoals()) return;
                        var b = bs.get(0);
                        for (var r : s.overworld().getEntities(ModEntities.RAVER, r -> b.teamOf(r) >= 0)) {
                            double f = b.uFor(b.teamOf(r), r.position()) / b.length();
                            if (r.stall == 12) ZAG_MAX[0] = Math.max(ZAG_MAX[0], f);
                            if (r.stall == 15) ATA_MIN[0] = Math.min(ATA_MIN[0], f);
                        }
                    });
                    if (i == 20) {
                        // a pessoa encosta na bola (gruda no pé), anda um pouco e chuta
                        String pe = server.computeOnServer(s -> {
                            var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                            var pl = s.getPlayerList().getPlayers().get(0);
                            b.forcePlay();
                            pl.teleportTo(b.getX(), b.getY(), b.getZ() - 0.3);
                            return "";
                        });
                        ctx.waitTicks(4);
                        String pe2 = server.computeOnServer(s -> {
                            var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                            var pl = s.getPlayerList().getPlayers().get(0);
                            boolean grudou = b.owner() == pl;
                            pl.teleportTo(pl.getX() + 1.5, pl.getY(), pl.getZ());
                            return "grudou=" + grudou;
                        });
                        ctx.waitTicks(3);
                        String pe3 = server.computeOnServer(s -> {
                            var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                            var pl = s.getPlayerList().getPlayers().get(0);
                            boolean junto = b.owner() == pl && b.distanceTo(pl) < 1.5;
                            b.playerAction(pl, com.boombox.entity.Sports.FUT_SHOOT);
                            return " foi junto=" + junto + " chutou: velocidade=" + String.format(Locale.ROOT, "%.2f", b.getDeltaMovement().horizontalDistance()) + " dono depois=" + (b.owner() != null);
                        });
                        System.out.println("[BoomboxTest] fut bola no pé: " + pe2 + pe3);
                    }
                }
                String zonas = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                    return String.format(Locale.ROOT, "zagueiro mais avançado=%.2f atacante mais recuado=%.2f (fração do campo)", ZAG_MAX[0], ATA_MIN[0]);
                });
                System.out.println("[BoomboxTest] fut zonas: " + zonas);
                String falta = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                    var vit = s.overworld().getEntities(ModEntities.RAVER, r -> b.teamOf(r) == 1 && r.stall == 15);
                    if (vit.isEmpty()) return "sem atacante";
                    b.forcePlay();
                    b.foul(1, vit.get(0));
                    return "cobrador = vítima? " + vit.get(0).getUUID().equals(b.restartTaker());
                });
                System.out.println("[BoomboxTest] fut falta: " + falta);
                ctx.waitTicks(140);
                String cobrou = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                    return "parada=" + b.restartKind() + " (−1 = já cobrou)";
                });
                System.out.println("[BoomboxTest] fut falta cobrada: " + cobrou);
                String goleiro = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    b.setup(pl, com.boombox.entity.Sports.AMISTOSO, "", 0, 0);
                    b.forcePlay();
                    Vec3 g = b.goal(0);
                    pl.getAbilities().flying = false;
                    pl.onUpdateAbilities();
                    pl.teleportTo(g.x + 1.5, g.y, g.z);
                    // bola chutada no canto, de frente para ele
                    b.setPos(g.x + 12, g.y + 0.3, g.z + 1.8);
                    b.setDeltaMovement(new Vec3(-0.9, 0.05, 0));
                    return "posição=" + com.boombox.entity.Sports.POSITIONS[b.positionOf(pl)];
                });
                String gpos = server.computeOnServer(s -> {
                    var g = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).goal(0);
                    return String.format(Locale.ROOT, "%.2f %.2f %.2f", g.x + 1.5, g.y, g.z);
                });
                server.runCommand("tp @a " + gpos + " 0 0"); // olhando para o canto (+z)
                ctx.waitTicks(4);
                String voou = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    b.playerAction(pl, com.boombox.entity.Sports.GK_DIVE, 0f);
                    return "";
                });
                ctx.waitTicks(12);
                String pegou = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    return "pegou=" + (b.owner() == pl) + " placar " + b.scoreA() + "x" + b.scoreB();
                });
                System.out.println("[BoomboxTest] fut goleiro: " + goleiro + " " + pegou);
                String rede = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    b.loseOwner();
                    b.forcePlay();
                    Vec3 g = b.goal(1);
                    int antes = b.scoreA();
                    // tira o goleiro do caminho para ver só a rede
                    for (var k : s.overworld().getEntities(ModEntities.RAVER, r -> r.stall == 1 && b.teamOf(r) == 1)) k.teleportTo(g.x, g.y, g.z + 8);
                    b.setPos(g.x - 3.5, g.y + 0.3, g.z + 1.0);
                    b.setDeltaMovement(new Vec3(0.8, 0.02, 0));
                    return "antes=" + antes;
                });
                ctx.waitTicks(10);
                String rede2 = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                    return rede + " depois=" + b.scoreA() + String.format(Locale.ROOT, " bola parou em u=%.1f (comprimento %.1f)", b.u(b.position()), b.length());
                });
                System.out.println("[BoomboxTest] fut gol na rede funda: " + rede2);
                String dives = server.computeOnServer(s -> "posições: " + s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.TEAM_A)
                        .stream().map(r -> r.stall + "@" + String.format(Locale.ROOT, "%.0f,%.0f", r.getX(), r.getZ())).toList());
                System.out.println("[BoomboxTest] fut " + dives);
                look(ctx, server, new Vec3(105.5, -57.5, 47.5), 180f, -5f);
                ctx.waitTicks(30);
                ctx.takeScreenshot("fut_placar");
                look(ctx, server, new Vec3(105.5, -42, 76.5), 180f, 45f);
                ctx.waitTicks(30);
                ctx.takeScreenshot("fut_quick");
                return;
            }
            if ("vol".equals(System.getenv("BOOMBOX_QUICK"))) {
                server.runCommand("time set noon");
                server.runCommand("forceload add -60 40 0 100");
                look(ctx, server, new Vec3(-29.5, -55, 80.5), 160f, 30f);
                ctx.waitTicks(60);
                server.runCommand("place template boombox:volei_praia -40 -61 60");
                ctx.waitTicks(60);
                String antes = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                    long org = s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.ORG_VOLEI).size();
                    return "quadra=" + b.hasCourt() + " ativo=" + b.active() + " jogadores=" + b.playerCount() + " organizadores=" + org;
                });
                System.out.println("[BoomboxTest] vol antes: " + antes);
                look(ctx, server, new Vec3(-29.5, -55, 72.5), 160f, 30f);
                ctx.waitTicks(20);
                ctx.takeScreenshot("vol_vazia");
                String setup = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    pl.teleportTo(-35.5, -60, 66.5);
                    b.setup(pl, com.boombox.entity.Sports.CAMPEONATO, "Areia Teste", 0x8E24AA);
                    return b.teamName(0) + " x " + b.teamName(1) + " jogadores=" + b.playerCount() + " lado da pessoa=" + b.sideOf(pl);
                });
                System.out.println("[BoomboxTest] vol setup: " + setup);
                // 3.5.5: o amigo entra na dupla, um terceiro não consegue atropelar o jogo, e o amigo sai
                String amigoVol = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                    var amigo = net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Amigo"));
                    var intruso = net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Intruso"));
                    b.setup(amigo, com.boombox.entity.Sports.JUNTAR, "", 0);
                    String entrou = "entrou: lado do amigo=" + b.sideOf(amigo) + " pessoas=" + b.humanCount() + " aldeões=" + b.playerCount();
                    b.setup(intruso, com.boombox.entity.Sports.AMISTOSO, "", 0);
                    String intr = " | intruso tentou amistoso: time ainda=" + b.teamName(0) + " pessoas=" + b.humanCount();
                    b.leave(amigo);
                    return entrou + intr + " | amigo saiu: ativo=" + b.active() + " pessoas=" + b.humanCount() + " aldeões=" + b.playerCount();
                });
                System.out.println("[BoomboxTest] vol amigo: " + amigoVol);
                // 3.5.1: aldeão e zumbi dentro da quadra, carnaval do lado e pedido de música ao organizador
                server.runCommand("summon minecraft:villager -32.5 -60 64.5");
                server.runCommand("summon minecraft:zombie -27.5 -60 68.5");
                ctx.waitTicks(30);
                String guard = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                    long dentro = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(-60, -70, 40, 0, -40, 100),
                            m -> b.inArea(m.position(), 0) && !(m instanceof com.boombox.entity.Raver)).size();
                    long zumbis = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new net.minecraft.world.phys.AABB(-60, -70, 40, 0, -40, 100)).size();
                    boolean carnaval = com.boombox.entity.Trio.start(s.overworld(), new Vec3(-30, -60, 66));
                    var org = s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.ORG_VOLEI).get(0);
                    boolean musica = com.boombox.server.ServerBoomboxManager.playNearest(s.overworld(), org.position(),
                            new com.boombox.QueueItem("https://www.youtube.com/watch?v=jNQXAC9IVRw", "Pedido de teste", 19));
                    return "de fora ainda dentro=" + dentro + " zumbis=" + zumbis + " carnaval começou do lado=" + carnaval + " música tocou=" + musica;
                });
                System.out.println("[BoomboxTest] vol 3.5.1: " + guard);
                for (int i = 0; i < 30; i++) {
                    ctx.waitTicks(20);
                    String st = server.computeOnServer(s -> {
                        var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                        var pl = s.getPlayerList().getPlayers().get(0);
                        // a pessoa tenta uma manchete quando a bola chega perto
                        if (b.distanceTo(pl) < 3) {
                            b.playerAction(pl, com.boombox.entity.Sports.VOL_SPIKE, 0.8f);
                            b.playerAction(pl, com.boombox.entity.Sports.VOL_SPIKE, 0.8f); // clique repetido: tem que ser ignorado
                        }
                        return b.debug() + " placar " + b.score(0) + "x" + b.score(1) + " " + b.info();
                    });
                    System.out.println("[BoomboxTest] vol " + i + ": " + st);
                }
                look(ctx, server, new Vec3(-29.5, -55, 72.5), 160f, 30f);
                ctx.waitTicks(10);
                ctx.takeScreenshot("vol_jogo");
                String corte = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    b.forTestRally(pl);
                    b.playerAction(pl, com.boombox.entity.Sports.VOL_SPIKE, 0.9f);
                    Vec3 v1 = b.getDeltaMovement();
                    b.playerAction(pl, com.boombox.entity.Sports.VOL_SPIKE, 0.9f);
                    return String.format(Locale.ROOT, "velocidade depois do corte=%.2f (para baixo %.2f) placar %dx%d %s", v1.horizontalDistance(), v1.y, b.score(0), b.score(1), b.debug());
                });
                System.out.println("[BoomboxTest] vol corte: " + corte);
                ctx.waitTicks(25);
                System.out.println("[BoomboxTest] vol corte depois: " + server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                    return b.debug() + " placar " + b.score(0) + "x" + b.score(1);
                }));
                // 3.5.2: sair do campeonato de vôlei e de um amistoso de futebol
                String sairVol = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    com.boombox.entity.Sports.action(pl, com.boombox.entity.Sports.LEAVE);
                    return "ativo=" + b.active() + " jogadores=" + b.playerCount() + " lado da pessoa=" + b.sideOf(pl) + " " + b.info();
                });
                System.out.println("[BoomboxTest] vol saiu: " + sairVol);
                server.runCommand("place template boombox:varzea_small -40 -61 0");
                ctx.waitTicks(80);
                String sairFut = server.computeOnServer(s -> {
                    var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    b.setup(pl, com.boombox.entity.Sports.CAMPEONATO, "Desistentes FC", 0x2E8B3A);
                    String antes2 = "antes: time=" + b.teamOf(pl) + " modo=" + b.mode() + " " + b.teamName(0);
                    // 3.5.5: amigo entra de goleiro no mesmo time
                    var amigo = net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Amigo"));
                    var intruso = net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Intruso"));
                    int benchAntes = b.benchedCount();
                    b.setup(amigo, com.boombox.entity.Sports.JUNTAR, "", 0, 0);
                    String amigoFut = " | amigo: time=" + b.teamOf(amigo) + " posição=" + com.boombox.entity.Sports.POSITIONS[b.positionOf(amigo)]
                            + " banco " + benchAntes + "->" + b.benchedCount();
                    b.setup(intruso, com.boombox.entity.Sports.AMISTOSO, "", 0, 6);
                    amigoFut += " | intruso: modo=" + b.mode() + " time=" + b.teamName(0) + " intruso no jogo=" + b.teamOf(intruso);
                    b.leave(amigo);
                    amigoFut += " | amigo saiu: banco=" + b.benchedCount() + " modo=" + b.mode();
                    System.out.println("[BoomboxTest] fut amigo:" + amigoFut);
                    com.boombox.entity.Sports.action(pl, com.boombox.entity.Sports.LEAVE);
                    return antes2 + " | depois: time=" + b.teamOf(pl) + " modo=" + b.mode() + " " + b.teamName(0) + " x " + b.teamName(1);
                });
                System.out.println("[BoomboxTest] fut saiu: " + sairFut);
                return;
            }
            if (System.getenv("BOOMBOX_QUICK") != null) {
                server.runCommand("place template boombox:djleo_house " + HX + " " + HY + " " + HZ);
                ctx.waitTicks(40);
                look(ctx, server, new Vec3(HX + 21.5, HY + 6.5, HZ + 25.5), -130f, 32f);
                ctx.waitTicks(40);
                ctx.takeScreenshot("area_vip");
                server.runCommand("time set noon");
                server.runCommand("place template boombox:volei_praia -40 -61 60");
                server.runCommand("place template boombox:casa_piscina -40 -61 90");
                look(ctx, server, new Vec3(-29.5, -55, 80.5), 160f, 30f);
                for (int i = 0; i < 30; i++) {
                    ctx.waitTicks(20);
                    String st = server.computeOnServer(s -> {
                        var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true);
                        var f = s.overworld().getEntities(ModEntities.POOL_FLOAT, e -> true);
                        return (b.isEmpty() ? "sem bola" : b.get(0).debug() + " placar " + b.get(0).score(0) + "x" + b.get(0).score(1))
                                + " | boias " + f.stream().map(x -> x.debug()).toList();
                    });
                    System.out.println("[BoomboxTest] quick " + i + ": " + st);
                }
                return;
            }
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
            int vt = -1;
            try {
                vt = ctx.waitFor((Minecraft mc) -> com.boombox.client.VideoManager.anyFrame(), 20 * 90);
            } catch (AssertionError e) {
                System.out.println("[BoomboxTest] telão: o YouTube não respondeu a tempo (internet), seguindo o teste");
            }
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
            // ---- 3.3: sinuca com câmera na mesa e regras de bola 8 (parte 0 em 38,-60,46)
            BlockPos mesa = new BlockPos(38, -60, 46);
            look(ctx, server, new Vec3(39.2, -60, 46.6), 90f, 25f);
            server.runOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                pl.getAbilities().flying = false;
                pl.onUpdateAbilities();
            });
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> com.boombox.client.PoolClient.click(mesa));
            ctx.waitTicks(30);
            ctx.takeScreenshot("sinuca_camera");
            String mira = ctx.computeOnClient(mc -> String.format(Locale.ROOT, "modo=%b ângulo=%.2f",
                    com.boombox.client.PoolClient.active(), com.boombox.client.PoolClient.aimAngle(mesa)));
            // saída forte em direção ao triângulo (pelo servidor, como se o jogador tacasse)
            server.runOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                if (s.overworld().getBlockEntity(mesa) instanceof com.boombox.bar.SinucaBlockEntity be) be.playerShot(pl, 0.0, 1.0);
            });
            ctx.waitTicks(12);
            ctx.takeScreenshot("sinuca_vista_cima");
            ctx.waitTicks(260);
            String sinuca = server.computeOnServer(s -> {
                if (!(s.overworld().getBlockEntity(mesa) instanceof com.boombox.bar.SinucaBlockEntity be)) return "sem mesa";
                int in = 0;
                for (int i = 1; i < com.boombox.bar.SinucaBlockEntity.N; i++) if (be.in[i]) in++;
                return "andando=" + be.moving + " encaçapadas=" + in + " grupos=" + be.group[0] + "/" + be.group[1]
                        + " vez=" + be.turnName() + " jogadores=" + be.names[0] + "/" + be.names[1] + " msg=" + be.message;
            });
            System.out.println("[BoomboxTest] sinuca 3.3: " + mira + " | " + sinuca);
            ctx.takeScreenshot("sinuca_depois");
            // regra: tocar primeiro na bola do outro é falta (testa direto na lógica, mirando na bola mais perto)
            String regra = server.computeOnServer(s -> {
                if (!(s.overworld().getBlockEntity(mesa) instanceof com.boombox.bar.SinucaBlockEntity be)) return "";
                be.rack();
                be.names[0] = "Player0"; be.names[1] = "Outro"; be.breakShot = false; be.group[0] = 1; be.group[1] = 2;
                // tira todas as bolas do caminho menos a 9 (listrada, do outro) bem na frente da branca
                for (int i = 1; i < com.boombox.bar.SinucaBlockEntity.N; i++) { be.in[i] = i != 9; }
                be.in[1] = false; be.x[1] = 2.6; be.z[1] = 1.7;
                be.x[9] = be.x[0] + 0.5; be.z[9] = be.z[0];
                be.shoot(0.0, 0.3);
                return "";
            });
            ctx.waitTicks(200);
            String falta = server.computeOnServer(s -> s.overworld().getBlockEntity(mesa) instanceof com.boombox.bar.SinucaBlockEntity be
                    ? "vez=" + be.turnName() + " msg=" + be.message : "");
            System.out.println("[BoomboxTest] sinuca falta: " + falta);
            ctx.runOnClient(mc -> com.boombox.client.PoolClient.stop());
            server.runOnServer(s -> {
                if (s.overworld().getBlockEntity(mesa) instanceof com.boombox.bar.SinucaBlockEntity be) be.rack();
            });

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

            // ---- 3.3: freguês fiel (7 dias = a cada 3 pedidos um é por conta da casa)
            String fiel = server.computeOnServer(s -> {
                var level = s.overworld();
                var pl = s.getPlayerList().getPlayers().get(0);
                var zeh = (com.boombox.entity.SeuZe) level.getEntities(ModEntities.SEU_ZE, e -> true).get(0);
                pl.teleportTo(zeh.getX() - 2, zeh.getY(), zeh.getZ());
                zeh.setVisitDays(pl.getUUID(), 7);
                int before = zeh.debtsForDisplay().getOrDefault(pl.getName().getString(), 0);
                for (int i = 0; i < 3; i++) zeh.order(pl, 0, 1);
                int after = zeh.debtsForDisplay().getOrDefault(pl.getName().getString(), 0);
                zeh.order(pl, 0, 2);
                return "pediu 3, pendurou " + (after - before) + " (1 foi de graça)";
            });
            System.out.println("[BoomboxTest] freguês fiel: " + fiel);
            look(ctx, server, new Vec3(31.5, -58.6, 44.5), -110f, 30f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("mesa_bebidas");
            // bêbado: fim da noite, vai embora cambaleando
            server.runCommand("time set 22500");
            Vec3 bebIni = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.RAVER, e -> e.role() == 1).stream().findFirst().map(e -> e.position()).orElse(Vec3.ZERO));
            ctx.waitTicks(300);
            Vec3 bebFim = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.RAVER, e -> e.role() == 1).stream().findFirst().map(e -> e.position()).orElse(Vec3.ZERO));
            System.out.println("[BoomboxTest] bêbado: andou " + String.format(Locale.ROOT, "%.1f", bebIni.distanceTo(bebFim)) + " blocos para casa");
            look(ctx, server, bebFim.add(-4, 3, -4), -45f, 30f);
            ctx.waitTicks(10);
            ctx.takeScreenshot("bebado");
            server.runCommand("time set 14000");

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

            // ---- 3.3: fila na porta, lounge com piscina, pulseira de luz e drink neon
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a " + (HX - 8) + " " + (HY + 3) + " " + (HZ + 7.5) + " -90 10");
            ctx.waitTicks(500);
            long fila = server.computeOnServer(s -> (long) s.overworld().getEntities(ModEntities.RAVER, r -> r.queueSpot != null).size());
            System.out.println("[BoomboxTest] fila na porta: " + fila + " aldeões");
            look(ctx, server, new Vec3(HX - 9.5, HY + 3.5, HZ + 3.5), -60f, 12f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("fila");
            server.runCommand("gamemode creative @a");
            look(ctx, server, new Vec3(HX + 15.5, HY + 7, HZ + 27.5), 180f, 35f);
            ctx.waitTicks(40);
            ctx.takeScreenshot("lounge");
            server.runCommand("item replace entity @p weapon.offhand with boombox:glow_bracelet");
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
            look(ctx, server, new Vec3(HX + 14.5, HY + 1, HZ + 7.5), 0f, 0f);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(20);
            ctx.takeScreenshot("pulseira");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("item replace entity @p weapon.offhand with minecraft:air");
            server.runOnServer(s -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(s.getPlayerList().getPlayers().get(0),
                    new com.boombox.net.Payloads.NeonVision(200)));
            look(ctx, server, new Vec3(HX + 12.5, HY + 2.5, HZ + 7.5), -90f, 5f);
            ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
            ctx.waitTicks(40);
            ctx.takeScreenshot("drink_neon");
            ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });

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
            ctx.waitTicks(10);
            String barraqueiro = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
                    new net.minecraft.world.phys.AABB(-45, -65, 25, -25, -50, 45)).stream().filter(v -> v.hasCustomName())
                    .map(v -> v.getCustomName().getString() + " com " + v.getOffers().size() + " trocas: "
                            + v.getOffers().stream().map(o -> o.getResult().getHoverName().getString()).toList()).toList().toString());
            System.out.println("[BoomboxTest] praia: " + barraqueiro);
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

            // ---- 3.3: aniversário de aldeão, quermesse, feira, várzea e copos
            server.runCommand("time set noon");
            server.runCommand("summon minecraft:villager 60.5 -60 -30.5");
            server.runCommand("summon minecraft:villager 62.5 -60 -28.5");
            ctx.waitTicks(10);
            boolean festa = server.computeOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                pl.teleportTo(58.5, -60, -34.5);
                return com.boombox.bar.Birthday.start(s.overworld(), pl);
            });
            ctx.waitTicks(80);
            System.out.println("[BoomboxTest] aniversário começou=" + festa + " balões=" + server.computeOnServer(s ->
                    (long) s.overworld().getEntities(ModEntities.BALLOON, e -> true).size()));
            look(ctx, server, new Vec3(56.5, -57, -35.5), -40f, 25f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("aniversario");
            server.runCommand("place template boombox:quermesse 80 -61 0");
            server.runCommand("place template boombox:feira 80 -61 20");
            server.runCommand("place template boombox:varzea 80 -61 40");
            ctx.waitTicks(20);
            look(ctx, server, new Vec3(77.5, -55, -4.5), -40f, 28f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("quermesse");
            String barraca = server.computeOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                var st = s.overworld().getBlockState(new BlockPos(84, -60, 1));
                st.useWithoutItem(s.overworld(), pl, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(new BlockPos(84, -60, 1)),
                        net.minecraft.core.Direction.UP, new BlockPos(84, -60, 1), false));
                return st.getBlock().getName().getString() + " kind=" + (st.hasProperty(com.boombox.bar.StallBlock.KIND) ? st.getValue(com.boombox.bar.StallBlock.KIND) : -1);
            });
            System.out.println("[BoomboxTest] quermesse: " + barraca);
            look(ctx, server, new Vec3(77.5, -56, 15.5), -40f, 28f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("feira");
            ctx.waitTicks(200);
            // várzea: entra no time azul, testa lateral, gol e chute do jogador
            String varzea = server.computeOnServer(s -> {
                var balls = s.overworld().getEntities(ModEntities.BALL, e -> true);
                if (balls.isEmpty()) return "sem bola";
                var b = balls.get(0);
                var pl = s.getPlayerList().getPlayers().get(0);
                b.setup(pl, com.boombox.entity.Sports.AMISTOSO, "", 0);
                String camisa = pl.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getHoverName().getString();
                return "campo achado=" + b.hasGoals() + " time do jogador=" + b.teamOf(pl) + " camisa=" + camisa
                        + " placar " + b.scoreA() + "x" + b.scoreB() + " bola parada=" + b.restarting();
            });
            System.out.println("[BoomboxTest] várzea: " + varzea);
            look(ctx, server, new Vec3(100.5, -44, 33.5), 0f, 50f);
            ctx.waitTicks(60);
            ctx.takeScreenshot("varzea");
            ctx.waitTicks(120);
            // bola pela lateral: tem que virar lateral (bola parada) do outro time
            String lateral = server.computeOnServer(s -> {
                var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                b.forcePlay();
                Vec3 out = b.fieldPoint(10, 12);
                b.setPos(out.x, b.getY(), out.z);
                return "";
            });
            ctx.waitTicks(3);
            String lat2 = server.computeOnServer(s -> {
                var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                return lateral + " depois de sair: tipo=" + b.restartKind() + " time=" + b.restartTeam();
            });
            System.out.println("[BoomboxTest] várzea lateral: " + lat2);
            // gol: bola dentro do gol do vermelho (gol 1) → ponto do azul
            ctx.waitTicks(80);
            String gol = server.computeOnServer(s -> {
                var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                int antes = b.scoreA();
                Vec3 g = b.goal(1);
                b.forcePlay();
                b.setPos(g.x, g.y + 0.1, g.z);
                return "azul antes=" + antes;
            });
            ctx.waitTicks(5);
            String gol2 = server.computeOnServer(s -> {
                var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                return gol + " depois=" + b.scoreA() + " (bola parada=" + b.restarting() + ")";
            });
            System.out.println("[BoomboxTest] várzea gol: " + gol2);
            // jogador chuta
            ctx.waitTicks(200);
            look(ctx, server, new Vec3(96.5, -60, 52.5), -90f, 15f);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(30);
            ctx.takeScreenshot("varzea_jogando");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            look(ctx, server, new Vec3(112.5, -59, 52.5), -90f, 10f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("varzea_gol");
            // copos: dá alguns e põe na prateleira
            server.runCommand("setblock 59 -59 -10 boombox:cup_shelf[facing=north]");
            String copos = server.computeOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                int n = 0;
                for (int i = 0; i < 5; i++) {
                    com.boombox.bar.Cups.give(pl, i);
                    if (s.overworld().getBlockEntity(new BlockPos(59, -59, -10)) instanceof com.boombox.bar.CupShelfBlockEntity be
                            && be.put(new net.minecraft.world.item.ItemStack(com.boombox.bar.BarBlocks.CUPS[i]))) n++;
                }
                return n + " copos na prateleira";
            });
            System.out.println("[BoomboxTest] copos: " + copos);
            look(ctx, server, new Vec3(59.5, -58.4, -12.2), 0f, 10f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("copos");
            server.runCommand("time set midnight");

            // =============================================================== 3.4
            // ---- petiscos na mesa
            server.runCommand("setblock 64 -60 -10 boombox:plastic_table[facing=north]");
            server.runCommand("fill 63 -61 -12 69 -61 -8 minecraft:stone");
            String petiscos = server.computeOnServer(s -> {
                if (!(s.overworld().getBlockEntity(new BlockPos(64, -60, -10)) instanceof com.boombox.bar.PlasticTableBlockEntity t)) return "sem mesa";
                t.put(new net.minecraft.world.item.ItemStack(com.boombox.bar.Blocks34.FRITAS));
                t.put(new net.minecraft.world.item.ItemStack(com.boombox.ModBlocks.BEER));
                t.put(new net.minecraft.world.item.ItemStack(com.boombox.ModBlocks.BEER));
                return "mesa com " + t.items.stream().filter(i -> !i.isEmpty()).count() + " coisas";
            });
            server.runCommand("setblock 66 -60 -10 boombox:plastic_table[facing=north]");
            server.computeOnServer(s -> s.overworld().getBlockEntity(new BlockPos(66, -60, -10)) instanceof com.boombox.bar.PlasticTableBlockEntity t
                    && t.put(new net.minecraft.world.item.ItemStack(com.boombox.bar.Blocks34.CALABRESA)));
            server.runCommand("setblock 68 -60 -10 boombox:plastic_table[facing=north]");
            server.computeOnServer(s -> s.overworld().getBlockEntity(new BlockPos(68, -60, -10)) instanceof com.boombox.bar.PlasticTableBlockEntity t
                    && t.put(new net.minecraft.world.item.ItemStack(com.boombox.bar.Blocks34.FRANGO)));
            System.out.println("[BoomboxTest] petiscos: " + petiscos);
            server.runCommand("time set noon");
            look(ctx, server, new Vec3(66.5, -58.2, -12.6), 0f, 35f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("petiscos");

            // ---- neon personalizado e pôster
            server.runCommand("fill 72 -60 -8 78 -57 -8 minecraft:dark_oak_planks");
            server.runCommand("setblock 73 -59 -9 boombox:neon_sign[facing=north]");
            server.runCommand("setblock 77 -59 -9 boombox:neon_sign[facing=north]");
            server.runOnServer(s -> {
                if (s.overworld().getBlockEntity(new BlockPos(73, -59, -9)) instanceof com.boombox.bar.NeonSignBlockEntity be) be.set("BAR DO ZÉ", 0, false);
                if (s.overworld().getBlockEntity(new BlockPos(77, -59, -9)) instanceof com.boombox.bar.NeonSignBlockEntity be) be.set("ABERTO", com.boombox.bar.NeonSignBlockEntity.RAINBOW, true);
            });
            server.runCommand("summon minecraft:painting 75 -59 -9 {variant:\"boombox:poster_luau\",facing:2b}");
            long posters = server.computeOnServer(s -> (long) s.overworld().getEntitiesOfClass(net.minecraft.world.entity.decoration.painting.Painting.class,
                    new net.minecraft.world.phys.AABB(70, -62, -12, 80, -55, -6)).size());
            System.out.println("[BoomboxTest] neon e pôster: pôsteres=" + posters);
            server.runCommand("time set midnight");
            look(ctx, server, new Vec3(75.5, -58.6, -14.5), 0f, 5f);
            ctx.waitTicks(20);
            ctx.takeScreenshot("neon");

            // ---- área VIP: corda barra quem não está na lista; com a pulseira no nome, passa
            String vip = server.computeOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                pl.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                BlockPos rope = new BlockPos(HX + 25, HY + 1, HZ + 19);
                var st = s.overworld().getBlockState(rope);
                boolean blocked = !st.getCollisionShape(s.overworld(), rope, net.minecraft.world.phys.shapes.CollisionContext.of(pl)).isEmpty();
                pl.getInventory().add(com.boombox.bar.Vip.braceletFor("Outro"));
                boolean other = !st.getCollisionShape(s.overworld(), rope, net.minecraft.world.phys.shapes.CollisionContext.of(pl)).isEmpty();
                pl.getInventory().add(com.boombox.bar.Vip.braceletFor(pl.getName().getString()));
                boolean mine = st.getCollisionShape(s.overworld(), rope, net.minecraft.world.phys.shapes.CollisionContext.of(pl)).isEmpty();
                pl.getInventory().clearContent();
                pl.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                return "corda=" + st.getBlock().getName().getString() + " sem pulseira barra=" + blocked + " pulseira de outro barra=" + other + " com a minha passa=" + mine;
            });
            System.out.println("[BoomboxTest] área VIP: " + vip);
            look(ctx, server, new Vec3(HX + 21.5, HY + 6.5, HZ + 25.5), -130f, 32f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("area_vip");

            // ---- baile à fantasia (dia 2 do ciclo, de noite): aldeões fantasiados e DJ Leo na pista
            server.runCommand("time set 61000");
            ctx.waitTicks(140);
            String baile = server.computeOnServer(s -> {
                var dj = s.overworld().getEntities(ModEntities.DJ_LEO, e -> e.position().x > HX && e.position().x < HX + 34 && Math.abs(e.position().z - HZ) < 30);
                if (dj.isEmpty()) return "sem DJ";
                return "fantasia hoje=" + com.boombox.bar.Fantasia.tonight(s.overworld()) + " DJ na festa=" + dj.get(0).partying()
                        + " DJ em x=" + String.format(Locale.ROOT, "%.1f", dj.get(0).getX() - HX);
            });
            System.out.println("[BoomboxTest] baile à fantasia: " + baile);
            look(ctx, server, new Vec3(HX + 6.5, HY + 3.5, HZ + 7.5), -90f, 15f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("baile_fantasia");

            // ---- luau (lua cheia, dia 0 do ciclo, de noite) na barraca de praia
            server.runCommand("time set 14000");
            ctx.waitTicks(200);
            String luau = server.computeOnServer(s -> {
                var fire = s.overworld().getBlockState(new BlockPos(-38, -60, 35));
                var torch = s.overworld().getBlockState(new BlockPos(-39, -60, 33));
                long roda = s.overworld().getEntitiesOfClass(com.boombox.entity.Raver.class, new net.minecraft.world.phys.AABB(-46, -62, 29, -30, -56, 41),
                        r -> r.position().distanceTo(new Vec3(-37.5, -60, 35.5)) < 4).size();
                return "noite de luau=" + com.boombox.entity.RaverRoles.luauNight(s.overworld()) + " fogueira=" + fire + " tocha=" + torch + " na roda=" + roda;
            });
            System.out.println("[BoomboxTest] luau: " + luau);
            look(ctx, server, new Vec3(-31.5, -57, 40.5), 130f, 25f);
            ctx.waitTicks(30);
            ctx.takeScreenshot("luau");

            // ---- vôlei de praia
            server.runCommand("time set noon");
            server.runCommand("place template boombox:volei_praia -40 -61 60");
            ctx.waitTicks(60);
            server.runOnServer(s -> {
                var bs = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true);
                if (!bs.isEmpty()) bs.get(0).setup(s.getPlayerList().getPlayers().get(0), com.boombox.entity.Sports.AMISTOSO, "", 0);
            });
            look(ctx, server, new Vec3(-29.5, -55, 80.5), 160f, 30f);
            ctx.waitTicks(400);
            String volei = server.computeOnServer(s -> {
                var bs = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true);
                if (bs.isEmpty()) return "sem bola";
                var b = bs.get(0);
                return "quadra=" + b.hasCourt() + " placar Sol " + b.score(0) + " x " + b.score(1) + " Mar, sacando=" + b.serving();
            });
            System.out.println("[BoomboxTest] vôlei: " + volei);
            ctx.takeScreenshot("volei");
            ctx.waitTicks(600);
            String volei2 = server.computeOnServer(s -> {
                var b = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                return "placar depois de 30 s: Sol " + b.score(0) + " x " + b.score(1) + " Mar";
            });
            System.out.println("[BoomboxTest] vôlei: " + volei2);

            // ---- casa com piscina e boia de flamingo
            server.runCommand("place template boombox:casa_piscina -40 -61 90");
            ctx.waitTicks(60);
            String piscina = server.computeOnServer(s -> {
                var floats = s.overworld().getEntities(ModEntities.POOL_FLOAT, e -> true);
                if (floats.isEmpty()) return "sem boia";
                var pl = s.getPlayerList().getPlayers().get(0);
                var f = floats.get(0);
                f.interact(pl, net.minecraft.world.InteractionHand.MAIN_HAND, f.position());
                return floats.size() + " boias, y da boia=" + String.format(Locale.ROOT, "%.2f", f.getY()) + " montou=" + (pl.getVehicle() == f);
            });
            System.out.println("[BoomboxTest] piscina: " + piscina);
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).stopRiding());
            look(ctx, server, new Vec3(-43.5, -55, 84.5), -40f, 30f);
            ctx.waitTicks(40);
            ctx.takeScreenshot("piscina");

            // ---- futebol: partida com relógio, campeonato e times
            String fut = server.computeOnServer(s -> {
                var bs = s.overworld().getEntities(ModEntities.BALL, e -> true);
                if (bs.isEmpty()) return "sem bola";
                var b = bs.get(0);
                return b.teamName(0) + " " + b.scoreA() + " x " + b.scoreB() + " " + b.teamName(1) + " — " + b.minute();
            });
            System.out.println("[BoomboxTest] futebol 3.4: " + fut);
            server.runCommand("time set noon");
            look(ctx, server, new Vec3(100.5, -48, 33.5), 0f, 50f);
            ctx.waitTicks(300);
            String fut2 = server.computeOnServer(s -> {
                var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                return b.teamName(0) + " " + b.scoreA() + " x " + b.scoreB() + " " + b.teamName(1) + " — " + b.minute() + " parada=" + b.restarting();
            });
            System.out.println("[BoomboxTest] futebol 3.4 (15 s depois): " + fut2);
            ctx.takeScreenshot("futebol34");

            // ---- torneio de sinuca: inscrição com o Seu Zé
            String torneio = server.computeOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                for (var seu : s.overworld().getEntities(ModEntities.SEU_ZE, e -> true)) {
                    pl.teleportTo(seu.getX() + 1, seu.getY(), seu.getZ());
                    seu.order(pl, 0, 3);
                    for (BlockPos q : BlockPos.betweenClosed(seu.blockPosition().offset(-14, -3, -14), seu.blockPosition().offset(14, 3, 14))) {
                        if (s.overworld().getBlockEntity(q) instanceof com.boombox.bar.SinucaBlockEntity t && t.tournamentRunning()) {
                            return "torneio de " + String.join(",", t.tourHumans) + " fase=" + t.tourStage + " adversário=" + t.botName;
                        }
                    }
                }
                return "sem torneio";
            });
            System.out.println("[BoomboxTest] torneio: " + torneio);
            server.runCommand("time set midnight");

            // ---- vilas de todos os tipos: cada uma deve ter um DJ Leo
            String[] types = {"plains", "desert", "savanna", "snowy", "taiga", "plains"};
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
                String novas = server.computeOnServer(s -> {
                    int st = 0, gols = 0, redes = 0, neons = 0, cordas = 0;
                    for (BlockPos q : BlockPos.betweenClosed(-100, -64, zc - 100, 100, -40, zc + 100)) {
                        var b = s.overworld().getBlockState(q);
                        if (b.is(com.boombox.bar.BarBlocks.STALL)) st++;
                        if (b.is(com.boombox.bar.BarBlocks.GOAL)) gols++;
                        if (b.is(com.boombox.bar.Blocks34.VOLLEY_NET)) redes++;
                        if (b.is(com.boombox.bar.Blocks34.NEON_SIGN)) neons++;
                        if (b.is(com.boombox.bar.Blocks34.VELVET_ROPE)) cordas++;
                    }
                    return "barraquinhas=" + st + " blocos de gol=" + gols + " rede de vôlei=" + redes + " neons=" + neons + " cordas VIP=" + cordas;
                });
                System.out.println("[BoomboxTest] vila " + types[i] + ": " + novas);
                long aldeoes = server.computeOnServer(s -> (long) s.overworld().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
                        new net.minecraft.world.phys.AABB(-150, -70, zc - 150, 150, 0, zc + 150)).size());
                System.out.println("[BoomboxTest] vila " + types[i] + ": aldeões normais=" + aldeoes);
                if (i == 0 && !leos.isEmpty()) {
                    Vec3 p = leos.get(0);
                    server.runCommand("time set noon");
                    look(ctx, server, p.add(-22, 18, -22), -45f, 30f);
                    ctx.waitTicks(100);
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

    private static final double[] ZAG_MAX = {0}, ATA_MIN = {1};

    private static void say(String s) {
        System.out.println("[BoomboxTest] " + s);
    }

    /** 3.6: copa (grupos e mata-mata) no futebol e no vôlei, marcação dos aldeões e torneio de sinuca com o quadro. */
    private static void copa(ClientGameTestContext ctx, TestServerContext server) {
        // ---- 1) a lógica da copa sozinha: 400 copas com resultados sorteados
        String logic = server.computeOnServer(s -> {
            var rnd = net.minecraft.util.RandomSource.create(7);
            int champs = 0, outGroups = 0, bad = 0, maxGames = 0;
            for (int n = 0; n < 400; n++) {
                var c = new com.boombox.entity.Cup(n % 2 == 1);
                c.init(rnd, "Meu Time", 0xF0D020);
                int games = 0;
                while (!c.finished() && games < 10) {
                    int a = rnd.nextInt(4), b = rnd.nextInt(4);
                    if (c.volley) { if (rnd.nextBoolean()) { a = 12; b = rnd.nextInt(10); } else { b = 12; a = rnd.nextInt(10); } }
                    c.playerResult(a, b, rnd.nextInt(2), rnd);
                    games++;
                }
                maxGames = Math.max(maxGames, games);
                if (c.champion() < 0 || c.champion() > 15) bad++;
                if (c.champion() == 0) champs++;
                if (c.out && c.ko[8] != 0 && java.util.Arrays.stream(c.ko, 0, 8).noneMatch(x -> x == 0)) outGroups++;
                var d = com.boombox.entity.Cup.decode(c.encode());
                if (d.champion() != c.champion() || d.stage != c.stage || !d.names[5].equals(c.names[5])) bad++;
            }
            return "copas=400 sem campeão ou erro=" + bad + " seu time campeão=" + champs + " caiu nos grupos=" + outGroups + " máx de jogos=" + maxGames;
        });
        say("copa lógica: " + logic);

        // ---- 2) futebol: campo, marcação e a copa jogada de verdade
        server.runCommand("time set noon");
        server.runCommand("forceload add 70 30 150 90");
        look(ctx, server, new Vec3(110.5, -40, 30.5), 0f, 45f);
        ctx.waitTicks(60);
        server.runCommand("place template boombox:varzea 80 -61 40");
        ctx.waitTicks(60);
        say("copa fut setup: " + server.computeOnServer(s -> {
            var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
            var pl = s.getPlayerList().getPlayers().get(0);
            b.setup(pl, com.boombox.entity.Sports.CAMPEONATO, "Bonde do Teste", 0xF0D020, 6);
            var c = b.cup();
            return "grupo A: " + c.names[0] + ", " + c.names[1] + ", " + c.names[2] + ", " + c.names[3] + " | jogo: " + b.teamName(0) + " x " + b.teamName(1) + " " + b.info();
        }));
        // a pessoa sai do campo para os aldeões jogarem 5 contra 5 (com o aldeão dela de volta)
        server.runOnServer(s -> {
            var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
            var pl = s.getPlayerList().getPlayers().get(0);
            pl.teleportTo(b.center().x, b.center().y + 12, b.center().z);
        });
        double[] zag = {0}, crowd = {0, 0}, marked = {0, 0};
        int[] crowdMax = {0};
        for (int i = 0; i < 60; i++) {
            ctx.waitTicks(10);
            server.runOnServer(s -> {
                var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                if (b.restarting()) return;
                var rs = s.overworld().getEntities(ModEntities.RAVER, r -> b.teamOf(r) >= 0);
                for (int team = 0; team < 2; team++) {
                    int t = team;
                    long near = rs.stream().filter(r -> b.teamOf(r) == t && r.stall != 1 && r.distanceTo(b) < 3.5).count();
                    crowd[0] += near;
                    crowd[1]++;
                    crowdMax[0] = Math.max(crowdMax[0], (int) near);
                }
                for (var r : rs) {
                    if (r.stall == 12) zag[0] = Math.max(zag[0], b.uFor(b.teamOf(r), r.position()) / b.length());
                }
                // marcação: atacantes do time que ataca têm alguém do outro time a menos de 3 blocos?
                int att = b.lastTeam();
                if (att < 0) return;
                for (var a : rs) {
                    if (b.teamOf(a) != att || a.stall == 1 || a.distanceTo(b) < 3) continue;
                    if (b.uFor(att, a.position()) < b.length() * 0.5) continue;
                    boolean m = rs.stream().anyMatch(d -> b.teamOf(d) == 1 - att && d.stall != 1 && d.distanceTo(a) < 3);
                    marked[0] += m ? 1 : 0;
                    marked[1]++;
                }
            });
        }
        say(String.format(Locale.ROOT, "copa fut IA: zagueiro mais avançado=%.2f do campo | perto da bola (3,5 blocos) por time: média %.2f, máximo %d | atacantes marcados no campo de ataque: %.0f%% (%d casos)",
                zag[0], crowd[0] / Math.max(1, crowd[1]), crowdMax[0], 100 * marked[0] / Math.max(1, marked[1]), (int) marked[1]));
        look(ctx, server, new Vec3(105.5, -42, 76.5), 180f, 45f);
        ctx.waitTicks(20);
        ctx.takeScreenshot("copa_fut_marcacao");
        // grupos: 3 vitórias
        for (int g = 0; g < 3; g++) {
            int gg = g;
            server.runOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).testEnd(2 + gg % 2, 1));
            ctx.waitTicks(5);
            say("copa fut rodada " + (g + 1) + ": " + server.computeOnServer(s -> {
                var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
                var c = b.cup();
                return c.stageName() + " pts=" + c.pts[0] + " fora=" + c.out + " fase do jogo=" + b.phase();
            }));
            if (g == 0) {
                ctx.waitTicks(10);
                ctx.takeScreenshot("copa_tela_grupos");
            }
            server.runOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).testSkipAfter());
            ctx.waitTicks(5);
        }
        ctx.takeScreenshot("copa_tela_chave");
        say("copa fut chave: " + server.computeOnServer(s -> {
            var c = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).cup();
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 8; k++) sb.append(c.names[c.ko[k]]).append(k % 2 == 0 ? " x " : " | ");
            return sb + "adversário=" + (c.opponent() < 0 ? "-" : c.names[c.opponent()]);
        }));
        // quartas: empate -> prorrogação, gol de ouro do time A
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).testEnd(1, 1));
        ctx.waitTicks(5);
        say("copa fut quartas empate: fase do jogo=" + server.computeOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).phase() + " "
                + s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).minute()));
        server.runOnServer(s -> {
            var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
            b.forcePlay();
            Vec3 g = b.goal(1);
            b.setPos(g.x + 2.5, g.y + 0.3, g.z + 1.0);
            b.setDeltaMovement(new Vec3(0.8, 0.02, 0));
        });
        ctx.waitTicks(15);
        say("copa fut gol de ouro: " + server.computeOnServer(s -> {
            var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
            return "placar " + b.scoreA() + "x" + b.scoreB() + " fase do jogo=" + b.phase() + " copa=" + b.cup().stageName();
        }));
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).testSkipAfter());
        ctx.waitTicks(5);
        // semi: vitória; final: empate, prorrogação sem gol e pênaltis
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).testEnd(3, 0));
        ctx.waitTicks(5);
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).testSkipAfter());
        ctx.waitTicks(5);
        say("copa fut antes da final: " + server.computeOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).cup().stageName()
                + " " + s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).teamName(1)));
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).testEnd(0, 0));
        ctx.waitTicks(5);
        server.runOnServer(s -> s.overworld().getEntities(ModEntities.BALL, e -> true).get(0).testEndExtra());
        ctx.waitTicks(5);
        say("copa fut final: " + server.computeOnServer(s -> {
            var b = s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
            var c = b.cup();
            var pl = s.getPlayerList().getPlayers().get(0);
            int tacas = pl.getInventory().countItem(com.boombox.bar.Blocks34.TACA_VARZEA.asItem());
            return "copa=" + c.stageName() + " acabou=" + c.finished() + " campeão=" + (c.champion() < 0 ? "?" : c.names[c.champion()]) + " taças no inventário=" + tacas;
        }));
        ctx.waitTicks(10);
        ctx.takeScreenshot("copa_tela_final");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));

        // ---- 3) vôlei: grupos e eliminação nas quartas
        server.runCommand("forceload add -60 40 0 100");
        look(ctx, server, new Vec3(-29.5, -55, 72.5), 160f, 30f);
        ctx.waitTicks(40);
        server.runCommand("place template boombox:volei_praia -40 -61 60");
        ctx.waitTicks(60);
        server.runOnServer(s -> {
            var v = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
            var pl = s.getPlayerList().getPlayers().get(0);
            pl.teleportTo(-35.5, -60, 66.5);
            v.setup(pl, com.boombox.entity.Sports.CAMPEONATO, "Areia Teste", 0x8E24AA);
        });
        for (int g = 0; g < 5; g++) {
            int gg = g;
            server.runOnServer(s -> {
                var v = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                if (v.cup() == null || v.cup().finished()) return;
                if (v.cup().knockout()) v.testEnd(6, 12); // perde nas quartas
                else v.testEnd(12, 4 + gg);
            });
            ctx.waitTicks(5);
            say("copa vôlei jogo " + (g + 1) + ": " + server.computeOnServer(s -> {
                var v = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
                var c = v.cup();
                return c.stageName() + " pts=" + c.pts[0] + " fora=" + c.out + " campeão=" + (c.champion() < 0 ? "?" : c.names[c.champion()]) + " quadra ativa=" + v.active() + " " + v.info();
            }));
        }
        ctx.takeScreenshot("copa_volei_tela");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));

        // ---- 4) sinuca: chave de 8 e o quadro na parede
        server.runCommand("forceload add 20 30 70 90");
        server.runCommand("place template boombox:ze_bar 30 -61 40");
        ctx.waitTicks(60);
        String sin = server.computeOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().get(0);
            com.boombox.bar.SinucaBlockEntity t = null;
            for (var p : net.minecraft.core.BlockPos.betweenClosed(new net.minecraft.core.BlockPos(20, -64, 30), new net.minecraft.core.BlockPos(70, -50, 90))) {
                if (s.overworld().getBlockEntity(p) instanceof com.boombox.bar.SinucaBlockEntity st) { t = st; break; }
            }
            if (t == null) return "sem mesa";
            pl.teleportTo(t.getBlockPos().getX() + 0.5, t.getBlockPos().getY(), t.getBlockPos().getZ() + 2.5);
            t.startTournament(pl);
            String quadro = "nenhum";
            for (var p : net.minecraft.core.BlockPos.betweenClosed(t.getBlockPos().offset(-10, -3, -10), t.getBlockPos().offset(10, 3, 10))) {
                var st = s.overworld().getBlockState(p);
                if (st.is(com.boombox.bar.Blocks34.BRACKET_BOARD)) { quadro = p.toShortString() + " virado para " + st.getValue(com.boombox.bar.FacingBlock.FACING); BOARD[0] = p.immutable(); BOARD_DIR[0] = st.getValue(com.boombox.bar.FacingBlock.FACING); }
            }
            String q = "quartas: " + String.join(", ", java.util.Arrays.copyOf(t.bracket, 8));
            t.testResult(s.overworld(), true);
            String f1 = " | depois das quartas: fase=" + t.tourStage + " adversário=" + t.botName;
            t.testResult(s.overworld(), true);
            String f2 = " | depois da semi: fase=" + t.tourStage + " adversário=" + t.botName;
            t.testResult(s.overworld(), false);
            String f3 = " | final perdida: torneio rolando=" + t.tournamentRunning() + " campeão=" + t.bracket[14] + " semis=" + String.join(", ", java.util.Arrays.copyOfRange(t.bracket, 8, 14));
            return "quadro=" + quadro + " " + q + f1 + f2 + f3;
        });
        say("copa sinuca: " + sin);
        if (BOARD[0] != null) {
            Vec3 front = Vec3.atCenterOf(BOARD[0]).add(BOARD_DIR[0].getStepX() * 2.6, -0.1, BOARD_DIR[0].getStepZ() * 2.6);
            look(ctx, server, front.subtract(0, 1.62, 0), BOARD_DIR[0].getOpposite().toYRot(), 5f);
            server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(pl -> { pl.getAbilities().flying = true; pl.onUpdateAbilities(); }));
            ctx.waitTicks(30);
            ctx.takeScreenshot("copa_sinuca_quadro");
        }
    }

    /** 3.8: narrador, torcida, Liga dos Amigos, apostas da copa, ranking, desafio de pênaltis e as telas. */
    private static void v38(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("time set noon");
        server.runCommand("gamerule spawn_monsters false");
        server.runCommand("forceload add 40 0 170 110");
        look(ctx, server, new Vec3(110.5, -40, 30.5), 0f, 45f);
        ctx.waitTicks(60);
        server.runCommand("place template boombox:varzea 80 -61 40");
        ctx.waitTicks(260); // o organizador e o narrador aparecem
        say("v38 narrador: " + server.computeOnServer(s -> {
            var narr = s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.NARRADOR);
            if (narr.isEmpty()) return "SEM NARRADOR";
            var n = narr.get(0);
            return "narradores=" + narr.size() + " nome=" + (n.getCustomName() == null ? "?" : n.getCustomName().getString())
                    + " microfone=" + n.getMainHandItem().is(com.boombox.ModBlocks.MICROPHONE);
        }));
        // ---- amistoso: torcida e narração
        server.runOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().get(0);
            pl.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            pl.getInventory().clearContent();
            pl.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 20));
            ball(s).setup(pl, com.boombox.entity.Sports.AMISTOSO, "", 0, 6);
            pl.getAbilities().flying = false;
            pl.onUpdateAbilities();
        });
        ctx.waitTicks(40);
        say("v38 torcida: " + server.computeOnServer(s -> {
            var b = ball(s);
            var fans = s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.TORCIDA);
            long banners = fans.stream().filter(f -> f.getMainHandItem().getItem().toString().contains("banner")).count();
            long shirtA = fans.stream().filter(f -> (f.getShirt() & 0xFFFFFF) == b.teamColor(0)).count();
            double minOut = 99;
            for (var f : fans) minOut = Math.min(minOut, Math.abs(b.v(f.position())) - b.halfWidth());
            return "torcedores=" + fans.size() + " com bandeira=" + banners + " camisa do time A=" + shirtA
                    + String.format(Locale.ROOT, " (fora do campo, a %.1f blocos da linha)", minOut) + " falas do narrador=" + b.narrator.lines;
        }));
        // foto: torcida na beira do campo
        server.runOnServer(s -> {
            var b = ball(s);
            Vec3 at = b.fieldPoint(b.length() * 0.5, b.halfWidth() + 9);
            var pl = s.getPlayerList().getPlayers().get(0);
            pl.teleportTo(at.x, at.y + 4, at.z);
        });
        server.runOnServer(s -> {
            var b = ball(s);
            Vec3 at = b.fieldPoint(b.length() * 0.5, b.halfWidth() + 9);
            Vec3 c = b.fieldPoint(b.length() * 0.5, 0);
            float yaw = (float) (Math.toDegrees(Math.atan2(c.z - at.z, c.x - at.x)) - 90);
            var pl = s.getPlayerList().getPlayers().get(0);
            pl.teleportTo(s.overworld(), at.x, at.y + 4, at.z, java.util.Set.of(), yaw, 25f, true);
            pl.getAbilities().flying = true;
            pl.onUpdateAbilities();
        });
        ctx.waitTicks(10);
        ctx.takeScreenshot("v38_torcida");
        // gol da pessoa: narrador, sinalizador da torcida e ranking de artilheiros
        server.runOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            b.forcePlay();
            b.loseOwner();
            Vec3 from = b.teamPoint(0, b.length() - 5, 0);
            Vec3 g = b.goal(1);
            b.setPos(from.x, from.y + 0.3, from.z);
            b.kick(pl, new Vec3(g.x - from.x, 0, g.z - from.z).normalize().scale(1.3).add(0, 0.05, 0));
        });
        ctx.waitTicks(12);
        say("v38 gol: " + server.computeOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            long flares = s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.TORCIDA && r.flare > 0).size();
            return "placar " + b.scoreA() + "x" + b.scoreB() + " torcedores com sinalizador=" + flares + " gols no ranking="
                    + com.boombox.WorldStats.get(s).stat(pl.getName().getString(), com.boombox.WorldStats.GOALS) + " falas=" + b.narrator.lines;
        }));
        ctx.takeScreenshot("v38_sinalizador");
        // um pouco de jogo: narração dos lances e a torcida cantando
        server.runOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().get(0);
            var b = ball(s);
            Vec3 at = b.fieldPoint(b.length() * 0.5, -(b.halfWidth() + 6));
            pl.teleportTo(at.x, at.y + 3, at.z);
        });
        ctx.waitTicks(1200);
        say("v38 jogo: " + server.computeOnServer(s -> {
            var b = ball(s);
            return "falas do narrador=" + b.narrator.lines + " cantos da torcida=" + b.chants + " " + b.minute() + " " + b.scoreA() + "x" + b.scoreB();
        }));
        // ---- Liga dos Amigos: os dois têm time, o jogo contra vale pontos
        say("v38 liga: " + server.computeOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            var rival = net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Rival"));
            var ws = com.boombox.WorldStats.get(s);
            ws.setTeam(pl, "Bonde do Teste", 0xF0D020);
            ws.setTeam(rival, "Rival FC", 0x8E24AA);
            b.setup(rival, com.boombox.entity.Sports.CONTRA, "", 0, 4);
            return "vale para a liga=" + b.leagueGameRunning() + " times: " + b.teamName(0) + " x " + b.teamName(1) + " placar " + b.scoreA() + "x" + b.scoreB();
        }));
        server.runOnServer(s -> ball(s).testEnd(2, 1));
        ctx.waitTicks(5);
        say("v38 tabela da liga: " + server.computeOnServer(s -> {
            StringBuilder sb = new StringBuilder();
            for (var t : com.boombox.WorldStats.get(s).table()) sb.append(t.name).append(" P=").append(t.points()).append(" J=").append(t.j)
                    .append(" GP=").append(t.gp).append(" GC=").append(t.gc).append("; ");
            return sb.toString();
        }));
        server.runOnServer(s -> com.boombox.server.Features38.openLeague(s.getPlayerList().getPlayers().get(0)));
        ctx.waitTicks(10);
        ctx.takeScreenshot("v38_tela_liga");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(420); // acabou o amistoso: volta a pelada
        // ---- copa com apostas: a pessoa aposta no próprio time, o amigo no outro
        say("v38 apostas: " + server.computeOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            b.setup(pl, com.boombox.entity.Sports.CAMPEONATO, "Bonde do Teste", 0xF0D020, 6);
            var amigo = net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Apostador"));
            amigo.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 10));
            int antes = com.boombox.WorldStats.countEmeralds(pl);
            b.placeBet(pl, 0, 3);
            b.placeBet(amigo, 1, 5);
            b.placeBet(pl, 1, 2); // não pode trocar de lado
            return "aberta=" + b.betsOpen() + " esmeraldas da pessoa " + antes + "->" + com.boombox.WorldStats.countEmeralds(pl)
                    + " do amigo 10->" + com.boombox.WorldStats.countEmeralds(amigo) + " dados=" + b.betData(pl).replace('\t', '|');
        }));
        server.runOnServer(s -> com.boombox.server.Features38.send(s.getPlayerList().getPlayers().get(0), com.boombox.server.Features38.UI_BET,
                "0\t" + ball(s).betData(s.getPlayerList().getPlayers().get(0))));
        ctx.waitTicks(10);
        ctx.takeScreenshot("v38_tela_apostas");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        server.runOnServer(s -> ball(s).testEnd(2, 0));
        ctx.waitTicks(5);
        say("v38 apostas depois: " + server.computeOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().get(0);
            return "esmeraldas da pessoa=" + com.boombox.WorldStats.countEmeralds(pl) + " (apostou 3, o pote era 8)";
        }));
        ctx.waitTicks(420);
        // desiste da copa
        server.runOnServer(s -> ball(s).leave(s.getPlayerList().getPlayers().get(0)));
        ctx.waitTicks(20);
        // ---- ranking: quadro na parede
        server.runCommand("fill 60 -60 60 66 -56 60 minecraft:stone_bricks");
        server.runOnServer(s -> {
            var ws = com.boombox.WorldStats.get(s);
            ws.add("Zezinho", com.boombox.WorldStats.SINUCA, 3);
            ws.add("Mariazinha", com.boombox.WorldStats.CUPS, 2);
            ws.add("Mariazinha", com.boombox.WorldStats.GOALS, 7);
            ws.add("Tonhão", com.boombox.WorldStats.GOALS, 4);
            com.boombox.bar.RankingBoardBlock.hangNear(s.overworld(), new BlockPos(63, -61, 58));
        });
        ctx.waitTicks(120);
        say("v38 quadro: " + server.computeOnServer(s -> {
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(55, -62, 50), new BlockPos(72, -54, 66))) {
                if (s.overworld().getBlockEntity(p) instanceof com.boombox.bar.RankingBoardBlock.Entity be) return "em " + p.toShortString() + " dados=" + be.data.replace('\n', '/').replace('\t', ',');
            }
            return "SEM QUADRO";
        }));
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        look(ctx, server, new Vec3(63.5, -60.0, 56.6), 0f, 0f);
        ctx.waitTicks(15);
        ctx.runOnClient(mc -> { mc.gui.setScreen(null); if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
        ctx.waitTicks(3);
        ctx.takeScreenshot("v38_quadro_ranking");
        ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
        server.runOnServer(s -> com.boombox.server.Features38.openRanking(s.getPlayerList().getPlayers().get(0)));
        ctx.waitTicks(10);
        ctx.takeScreenshot("v38_tela_ranking");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        // ---- desafio de pênaltis com o Seu Zé
        server.runOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().get(0);
            pl.getAbilities().flying = false;
            pl.onUpdateAbilities();
            var ze = ModEntities.SEU_ZE.create(s.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            ze.snapTo(70.5, -61, 30.5, 0, 0);
            s.overworld().addFreshEntity(ze);
            pl.teleportTo(71.5, -61, 31.5);
        });
        ctx.waitTicks(5);
        say("v38 desafio: " + server.computeOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().get(0);
            var ze = (com.boombox.entity.SeuZe) s.overworld().getEntities(ModEntities.SEU_ZE, e -> true).get(0);
            int antes = com.boombox.WorldStats.countEmeralds(pl);
            ze.order(pl, 3, 4);
            return "esmeraldas " + antes + "->" + com.boombox.WorldStats.countEmeralds(pl) + " desafio=" + ball(s).challengeRunning() + " " + ball(s).challengeInfo();
        }));
        ctx.waitTicks(10);
        ctx.takeScreenshot("v38_seta");
        server.runOnServer(s -> {
            var b = ball(s);
            Vec3 at = b.fieldPoint(b.length() - 9, 0);
            s.getPlayerList().getPlayers().get(0).teleportTo(at.x, at.y + 0.2, at.z);
        });
        int[] meus = {0};
        for (int k = 0; k < 10; k++) {
            int kk = k;
            // espera a bola ficar na marca
            for (int w = 0; w < 30; w++) {
                ctx.waitTicks(10);
                boolean ready = server.computeOnServer(s -> ball(s).challengeState() == 1 && ball(s).challengeRound() == kk);
                if (ready) break;
            }
            if (k < 5) {
                // a pessoa bate: chega na bola e chuta num canto (às vezes fraco no meio, para o goleiro pegar)
                ctx.waitTicks(20);
                server.runOnServer(s -> {
                    var b = ball(s);
                    var pl = s.getPlayerList().getPlayers().get(0);
                    Vec3 spot = b.challengeSpot();
                    Vec3 g = b.goal(1);
                    Vec3 dir = new Vec3(g.x - spot.x, 0, g.z - spot.z).normalize();
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    Vec3 aim = kk % 2 == 0 ? g.add(side.scale(1.2)) : g;
                    Vec3 stand = spot.subtract(dir.scale(0.8));
                    float yaw = (float) (Math.toDegrees(Math.atan2(aim.z - spot.z, aim.x - spot.x)) - 90);
                    pl.teleportTo(s.overworld(), stand.x, b.getY(), stand.z, java.util.Set.of(), yaw, 0f, true);
                    b.playerAction(pl, com.boombox.entity.Sports.FUT_SHOOT, kk % 2 == 0 ? 0.35f : 0.1f);
                });
            } else {
                // a pessoa no gol: pula na direção da bola quando ela vem
                for (int w = 0; w < 140; w += 2) {
                    ctx.waitTicks(2);
                    boolean dove = server.computeOnServer(s -> {
                        var b = ball(s);
                        if (b.challengeState() != 2) return false;
                        var pl = s.getPlayerList().getPlayers().get(0);
                        if (kk % 2 == 1) return true; // nessas não pula
                        Vec3 v = b.getDeltaMovement();
                        Vec3 to = new Vec3(b.getX() + v.x * 6 - pl.getX(), 0, b.getZ() + v.z * 6 - pl.getZ());
                        float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
                        pl.setYRot(yaw);
                        pl.setXRot(0);
                        b.playerAction(pl, com.boombox.entity.Sports.GK_DIVE);
                        return true;
                    });
                    if (dove) break;
                }
            }
            ctx.waitTicks(60);
            say("v38 cobrança " + (k + 1) + ": " + server.computeOnServer(s -> ball(s).challengeInfo()));
            if (k == 2) ctx.takeScreenshot("v38_penalti_batendo");
            if (k == 6) ctx.takeScreenshot("v38_penalti_no_gol");
        }
        ctx.waitTicks(80);
        say("v38 desafio fim: " + server.computeOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().get(0);
            return "desafio=" + ball(s).challengeRunning() + " modo=" + ball(s).mode() + " esmeraldas=" + com.boombox.WorldStats.countEmeralds(pl) + " " + ball(s).info();
        }));
    }

    /** 3.8: mapa da vila num mundo normal: lugares da vila mais perto e a tela, com a seta. */
    private void mapa(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(st -> st.setSeed("3720")).create()) {
            TestServerContext server = sp.getServer();
            server.runCommand("gamerule spawn_monsters false");
            server.runCommand("time set noon");
            server.runCommand("tp @p -1290 150 330");
            server.runCommand("effect give @p slow_falling 60");
            ctx.waitTicks(200);
            String r = server.computeOnServer(s -> {
                var pl = s.getPlayerList().getPlayers().get(0);
                var places = com.boombox.server.Features38.villagePlaces(s.overworld(), pl.blockPosition());
                if (places == null) return "SEM VILA";
                StringBuilder sb = new StringBuilder();
                for (String[] p : places) sb.append(p[0]).append(" (").append(p[1]).append(",").append(p[2]).append(") ");
                pl.getInventory().add(new net.minecraft.world.item.ItemStack(com.boombox.bar.Blocks34.MAPA_VILA));
                com.boombox.server.Features38.openMap(pl);
                return sb.toString();
            });
            System.out.println("[BoomboxTest] mapa: " + r);
            ctx.waitTicks(20);
            ctx.takeScreenshot("v38_tela_mapa");
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() != null) mc.gui.setScreen(null);
                com.boombox.client.Client38.navigate("Campo de futebol", -1301, 330);
            });
            ctx.waitTicks(10);
            ctx.takeScreenshot("v38_seta_mapa");
            System.out.println("[BoomboxTest] mapa seta: " + ctx.computeOnClient(mc -> com.boombox.client.Client38.navLine(mc)));
        }
    }

    private static com.boombox.entity.Ball ball(net.minecraft.server.MinecraftServer s) {
        return s.overworld().getEntities(ModEntities.BALL, e -> true).get(0);
    }

    /** 3.7: saída depois do gol, barreira na falta, jogador contra jogador (futebol, vôlei, sinuca) e tela da sinuca. */
    private static void v37(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("time set noon");
        server.runCommand("forceload add 70 30 150 90");
        look(ctx, server, new Vec3(110.5, -40, 30.5), 0f, 45f);
        ctx.waitTicks(60);
        server.runCommand("place template boombox:varzea 80 -61 40");
        ctx.waitTicks(60);
        server.runOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().get(0);
            ball(s).setup(pl, com.boombox.entity.Sports.AMISTOSO, "", 0, 6);
            pl.getAbilities().flying = false;
            pl.onUpdateAbilities();
        });
        ctx.waitTicks(20);
        // ---- jogador contra jogador no futebol
        say("v37 fut contra: " + server.computeOnServer(s -> {
            var b = ball(s);
            var amigo = net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Rival"));
            int antes = b.benchedCount();
            b.setup(amigo, com.boombox.entity.Sports.CONTRA, "", 0, 0);
            long goleirosB = s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.TEAM_B && r.stall == 1 && b.isBenched(r)).size();
            return "time do rival=" + b.teamOf(amigo) + " (1 = adversário) banco " + antes + "->" + b.benchedCount() + " goleiro do time B no banco=" + goleirosB;
        }));
        // ---- gol do time A: o time A fica no seu campo até o B dar a saída
        server.runOnServer(s -> {
            var b = ball(s);
            b.forcePlay();
            Vec3 g = b.goal(1);
            b.setPos(g.x + 2.5, g.y + 0.3, g.z + 1.0);
            b.setDeltaMovement(new Vec3(0.8, 0.02, 0));
        });
        ctx.waitTicks(15);
        say("v37 gol: " + server.computeOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            Vec3 other = b.teamPoint(0, b.length() * 0.75, 0);
            pl.teleportTo(other.x, other.y + 0.5, other.z); // tenta ficar no campo do adversário
            return "placar " + b.scoreA() + "x" + b.scoreB() + " trava (time que marcou)=" + b.kickoffLock();
        }));
        ctx.waitTicks(10);
        say("v37 trava: " + server.computeOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            return String.format(Locale.ROOT, "pessoa do time A voltou para u=%.2f do campo (meio = 0.50)", b.uFor(0, pl.position()) / b.length());
        }));
        int[] lockedUntil = {-1};
        for (int i = 0; i < 20; i++) {
            ctx.waitTicks(10);
            int ii = i;
            server.runOnServer(s -> { if (ball(s).kickoffLock() < 0 && lockedUntil[0] < 0) lockedUntil[0] = ii; });
        }
        say("v37 saída: " + server.computeOnServer(s -> {
            var b = ball(s);
            return "trava depois da saída=" + b.kickoffLock() + " (liberou em ~" + (lockedUntil[0] * 10) + " tiques) último a tocar=time " + b.lastTeam();
        }));
        // ---- barreira: tamanho conforme a distância do gol
        StringBuilder walls = new StringBuilder();
        double[] fr = {0.3, 0.55, 0.68, 0.82};
        for (double f : fr) {
            server.runOnServer(s -> {
                var b = ball(s);
                b.forcePlay();
                Vec3 p = b.teamPoint(0, b.length() * f, 2.0);
                b.setPos(p.x, p.y, p.z);
                b.setDeltaMovement(Vec3.ZERO);
                b.foul(0);
            });
            ctx.waitTicks(2);
            walls.append((String) server.computeOnServer(s -> {
                var b = ball(s);
                return String.format(Locale.ROOT, "%.2f do campo (%.0f blocos do gol): barreira=%d; ", f, b.restartSpot().distanceTo(b.goal(1)), b.wallCount());
            }));
        }
        say("v37 barreira: " + walls);
        ctx.waitTicks(55);
        say("v37 barreira no lugar: " + server.computeOnServer(s -> {
            var b = ball(s);
            StringBuilder sb = new StringBuilder();
            for (var r : s.overworld().getEntities(ModEntities.RAVER, r -> b.wallIndex(r) >= 0)) {
                sb.append(String.format(Locale.ROOT, "%.1f ", r.position().distanceTo(b.wallSpot(b.wallIndex(r)))));
            }
            return "distância de cada um até o lugar dele na barreira: " + sb;
        }));
        double[] cam = server.computeOnServer(s -> {
            var b = ball(s);
            Vec3 at = b.restartSpot();
            Vec3 g = b.goal(1);
            Vec3 back = at.subtract(new Vec3(g.x - at.x, 0, g.z - at.z).normalize().scale(4)).add(0, 1.2, 0);
            return new double[]{back.x, back.y, back.z, Math.toDegrees(Math.atan2(-(g.x - at.x), g.z - at.z))};
        });
        look(ctx, server, new Vec3(cam[0], cam[1], cam[2]), (float) cam[3], 12f);
        ctx.waitTicks(10);
        ctx.takeScreenshot("v37_barreira");
        // cobranças perto do gol: barreira, gol ou fora
        int[] res = {0, 0, 0};
        for (int k = 0; k < 10; k++) {
            int kk = k;
            int[] before = new int[2];
            server.runOnServer(s -> {
                var b = ball(s);
                var pl = s.getPlayerList().getPlayers().get(0);
                pl.teleportTo(b.center().x, b.center().y + 20, b.center().z);
                b.forcePlay();
                Vec3 p = b.teamPoint(0, b.length() - 10, (kk % 3 - 1) * 2.5);
                b.setPos(p.x, p.y, p.z);
                b.setDeltaMovement(Vec3.ZERO);
                b.foul(0);
                before[0] = b.scoreA();
            });
            int hitsBefore = server.computeOnServer(s -> ball(s).wallHits);
            ctx.waitTicks(170);
            int after = server.computeOnServer(s -> ball(s).scoreA());
            int hits = server.computeOnServer(s -> ball(s).wallHits) - hitsBefore;
            if (after > before[0]) res[0]++;
            else if (hits > 0) res[1]++;
            else res[2]++;
        }
        say("v37 cobranças com barreira de 3 (10 faltas): gols=" + res[0] + " na barreira=" + res[1] + " por cima, fora ou goleiro=" + res[2]);
        // ---- 3.7.1: chute com mais força sobe mais
        StringBuilder chutes = new StringBuilder();
        for (float pw : new float[]{0.25f, 0.5f, 0.75f, 1f}) {
            chutes.append((String) server.computeOnServer(s -> {
                var b = ball(s);
                var pl = s.getPlayerList().getPlayers().get(0);
                b.forcePlay();
                b.loseOwner();
                pl.getAbilities().flying = false;
                pl.onUpdateAbilities();
                Vec3 at = b.teamPoint(0, b.length() * 0.6, 0);
                pl.teleportTo(at.x, at.y, at.z);
                pl.setYRot(b.goal(1).x > at.x ? -90f : 90f);
                pl.setXRot(0f);
                b.setPos(at.x, at.y, at.z);
                b.setDeltaMovement(Vec3.ZERO);
                b.playerAction(pl, com.boombox.entity.Sports.FUT_SHOOT, pw);
                Vec3 v = b.getDeltaMovement();
                return String.format(Locale.ROOT, "força %.2f: velocidade %.2f, subida %.2f; ", pw, v.horizontalDistance(), v.y);
            }));
            ctx.waitTicks(15);
        }
        say("v371 chutes: " + chutes);
        // ---- 3.7.1: bola do goleiro: o adversário sai da área
        say("v371 goleiro: " + server.computeOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            b.forcePlay();
            var gk = s.overworld().getEntities(ModEntities.RAVER, r -> r.role() == com.boombox.entity.RaverRoles.TEAM_B && r.stall == 1).get(0);
            b.catchBall(gk);
            Vec3 in = b.fieldPoint(b.length() - 3, 1);
            pl.teleportTo(in.x, in.y, in.z);
            return "área protegida=" + b.protectedGoal() + " (1 = gol do time B)";
        }));
        ctx.waitTicks(4);
        say("v371 goleiro depois: " + server.computeOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            return "pessoa do time A dentro da área=" + b.inBox(1, pl.position());
        }));
        ctx.waitTicks(30);
        say("v371 aldeões do time A na área do goleiro: " + server.computeOnServer(s -> {
            var b = ball(s);
            return s.overworld().getEntities(ModEntities.RAVER, r -> b.teamOf(r) == 0 && b.inBox(1, r.position())).size() + " (goleiro ainda com a bola=" + (b.keeperHold() != null) + ")";
        }));
        // ---- 3.7.1: lateral: o adversário fica a 4 blocos
        say("v371 lateral: " + server.computeOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            b.forcePlay();
            Vec3 spot = b.fieldPoint(b.length() * 0.5, b.halfWidth() - 0.4);
            b.setRestart(s.overworld(), com.boombox.entity.Ball.THROW_IN, 1, spot, false);
            pl.teleportTo(spot.x + 1, spot.y, spot.z);
            return "lateral do time B";
        }));
        ctx.waitTicks(4);
        say("v371 lateral depois: " + server.computeOnServer(s -> {
            var b = ball(s);
            var pl = s.getPlayerList().getPlayers().get(0);
            return String.format(Locale.ROOT, "pessoa do time A a %.1f blocos da bola", Math.hypot(pl.getX() - b.restartSpot().x, pl.getZ() - b.restartSpot().z));
        }));
        ctx.waitTicks(40);
        say("v371 lateral aldeões: " + server.computeOnServer(s -> {
            var b = ball(s);
            double min = 99;
            for (var r : s.overworld().getEntities(ModEntities.RAVER, r -> b.teamOf(r) == 0)) min = Math.min(min, r.position().distanceTo(b.restartSpot()));
            return String.format(Locale.ROOT, "aldeão do time A mais perto da cobrança: %.1f blocos (parada=%d)", min, b.restartKind());
        }));
        // ---- vôlei: contra o amigo
        server.runCommand("forceload add -60 40 0 100");
        look(ctx, server, new Vec3(-29.5, -55, 72.5), 160f, 30f);
        ctx.waitTicks(40);
        server.runCommand("place template boombox:volei_praia -40 -61 60");
        ctx.waitTicks(60);
        say("v37 vôlei contra: " + server.computeOnServer(s -> {
            var v = s.overworld().getEntities(ModEntities.VOLLEYBALL, e -> true).get(0);
            var pl = s.getPlayerList().getPlayers().get(0);
            pl.teleportTo(-35.5, -60, 66.5);
            v.setup(pl, com.boombox.entity.Sports.AMISTOSO, "", 0);
            var rival = net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Rival"));
            v.setup(rival, com.boombox.entity.Sports.CONTRA, "", 0);
            long ladoB = s.overworld().getEntities(ModEntities.RAVER, r -> v.sideOf(r) == 1).size();
            long ladoA = s.overworld().getEntities(ModEntities.RAVER, r -> v.sideOf(r) == 0).size();
            return "lado do rival=" + v.sideOf(rival) + " aldeões do lado dele=" + ladoB + " do seu lado=" + ladoA + " pessoas=" + v.humanCount();
        }));
        // ---- sinuca: duas pessoas na chave, jogador contra jogador na semi
        server.runCommand("forceload add 20 30 70 90");
        server.runCommand("place template boombox:ze_bar 30 -61 40");
        ctx.waitTicks(60);
        say("v37 sinuca: " + server.computeOnServer(s -> {
            var pl = s.getPlayerList().getPlayers().get(0);
            com.boombox.bar.SinucaBlockEntity t = null;
            for (var p : net.minecraft.core.BlockPos.betweenClosed(new net.minecraft.core.BlockPos(20, -64, 30), new net.minecraft.core.BlockPos(70, -50, 90))) {
                if (s.overworld().getBlockEntity(p) instanceof com.boombox.bar.SinucaBlockEntity st) { t = st; break; }
            }
            if (t == null) return "sem mesa";
            pl.teleportTo(t.getBlockPos().getX() + 0.5, t.getBlockPos().getY(), t.getBlockPos().getZ() + 2.5);
            var rival = net.fabricmc.fabric.api.entity.FakePlayer.get(s.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Rival"));
            t.startTournament(pl);
            boolean entrou = t.startTournament(rival);
            String q = "rival entrou=" + entrou + " quartas: " + String.join(", ", java.util.Arrays.copyOf(t.bracket, 8));
            String m1 = " | mesa 1: " + t.tableLabel();
            t.testResult(s.overworld(), true);
            String m2 = " | mesa 2: " + t.tableLabel();
            t.testResult(s.overworld(), true);
            String m3 = " | mesa 3 (" + com.boombox.bar.SinucaBlockEntity.stageName(t.tourStage) + "): " + t.tableLabel() + " contra aldeão=" + t.againstBot;
            t.testResult(s.overworld(), false);
            String m4 = " | mesa 4 (" + com.boombox.bar.SinucaBlockEntity.stageName(t.tourStage) + "): " + t.tableLabel();
            t.testResult(s.overworld(), true);
            String fim = " | rolando=" + t.tournamentRunning() + " campeão=" + t.bracket[14];
            t.sendBracket(pl);
            return q + m1 + m2 + m3 + m4 + fim;
        }));
        ctx.waitTicks(20);
        ctx.takeScreenshot("v37_sinuca_tela");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        // tecla da tabela (J) perto da mesa: abre a mesma tela
        server.runOnServer(s -> com.boombox.entity.Sports.openTable(s.getPlayerList().getPlayers().get(0), s.overworld()));
        ctx.waitTicks(10);
        say("v37 tecla J: tela aberta=" + ctx.computeOnClient(mc -> mc.gui.screen() == null ? "nenhuma" : mc.gui.screen().getClass().getSimpleName()));
    }

    private static Vec3 ball0(TestServerContext server) {
        return server.computeOnServer(s -> ball(s).position());
    }

    private static final net.minecraft.core.BlockPos[] BOARD = {null};
    private static final net.minecraft.core.Direction[] BOARD_DIR = {null};

    /** 3.8.1: segurança do camarote na frente da corda VIP; baladas antigas mudam sozinhas. */
    private static void vip381(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("place template boombox:djleo_house " + HX + " " + HY + " " + HZ);
        server.runCommand("gamemode creative @a");
        ctx.waitTicks(60);
        String novo = server.computeOnServer(s -> {
            var l = s.overworld();
            StringBuilder sb = new StringBuilder();
            for (var b : l.getEntities(ModEntities.BOUNCER, e -> true))
                sb.append(String.format(Locale.ROOT, "(%.1f, %.1f yaw %.0f %s) ", b.getX() - HX, b.getZ() - HZ, b.getYRot(), String.valueOf(b.getCustomName() == null ? "-" : b.getCustomName().getString())));
            int ropes = 0;
            for (int x = 0; x < 33; x++) for (int z = 0; z < 24; z++)
                if (l.getBlockState(new net.minecraft.core.BlockPos(HX + x, HY + 1, HZ + z)).is(com.boombox.bar.Blocks34.VELVET_ROPE)) ropes++;
            return sb + "cordas=" + ropes;
        });
        System.out.println("[BoomboxTest] vip novo: " + novo);
        server.runCommand("time set 14000");
        look(ctx, server, new Vec3(HX + 13.5, HY + 2.6, HZ + 12.0), 90f, 12f);
        ctx.waitTicks(60);
        ctx.takeScreenshot("vip_corda");
        look(ctx, server, new Vec3(HX + 10.5, HY + 2.6, HZ + 3.5), 125f, 18f);
        ctx.waitTicks(30);
        ctx.takeScreenshot("vip_escada");
        // balada antiga: segurança no pé da escada, sem as cordas
        server.runCommand("kill @e[type=boombox:bouncer,name=\"Segurança do Camarote\",x=" + (HX + 7) + ",y=" + (HY + 1) + ",z=" + (HZ + 11) + ",distance=..2]");
        server.runCommand("fill " + HX + " " + (HY + 1) + " " + HZ + " " + (HX + 8) + " " + (HY + 1) + " " + (HZ + 14) + " air replace boombox:velvet_rope");
        server.runCommand(String.format(Locale.ROOT, "summon boombox:bouncer %.1f %d %.1f {VipOnly:1b,Rotation:[180f,0f]}", HX + 7.5, HY + 1, HZ + 7.5));
        ctx.waitTicks(20);
        String velho = server.computeOnServer(s -> {
            var l = s.overworld();
            StringBuilder sb = new StringBuilder();
            for (var b : l.getEntities(ModEntities.BOUNCER, e -> e.getX() < HX + 12 && e.getX() > HX + 3))
                sb.append(String.format(Locale.ROOT, "(%.1f, %.1f yaw %.0f) ", b.getX() - HX, b.getZ() - HZ, b.getYRot()));
            int ropes = 0;
            for (int x = 0; x < 12; x++) for (int z = 0; z < 16; z++)
                if (l.getBlockState(new net.minecraft.core.BlockPos(HX + x, HY + 1, HZ + z)).is(com.boombox.bar.Blocks34.VELVET_ROPE)) ropes++;
            return sb + "cordas do camarote=" + ropes;
        });
        System.out.println("[BoomboxTest] vip antigo mudou: " + velho);
        // sem pulseira: a corda do pé da escada barra
        server.runCommand("clear @a boombox:vip_bracelet");
        server.runCommand("gamemode survival @a");
        server.runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f 0 0", HX + 6.5, HY + 1, HZ + 4.5));
        ctx.waitTicks(10);
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setDeltaMovement(0, 0, 0.6));
        ctx.waitTicks(30);
        double zSem = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getZ() - HZ);
        System.out.println("[BoomboxTest] vip corda: sem pulseira parou em z=" + String.format(Locale.ROOT, "%.2f", zSem) + " (corda em z=6)");
        server.runCommand("gamemode creative @a");
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
