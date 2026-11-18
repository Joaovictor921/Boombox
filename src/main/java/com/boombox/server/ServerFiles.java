package com.boombox.server;

import com.boombox.BoomboxMod;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.boombox.Target;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Recebe músicas enviadas pelos jogadores (pasta "boombox_uploads" do servidor) e as distribui
 * para os outros clientes pela própria conexão do Minecraft — não precisa abrir porta extra.
 */
public final class ServerFiles {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Boombox-ServerFiles");
        t.setDaemon(true);
        return t;
    });

    /** target nulo = só guardar o arquivo (fila/playlist), sem tocar. */
    private record Expected(String sha1, Target target, String title, long durationSec) {}

    private static final class Upload {
        final Expected expected;
        final long total;
        final Path tmp;
        OutputStream out;
        long written;

        Upload(Expected expected, long total, Path tmp) {
            this.expected = expected;
            this.total = total;
            this.tmp = tmp;
        }
    }

    private static final Map<UUID, Map<String, Expected>> EXPECTED = new ConcurrentHashMap<>();
    private static final Map<UUID, Upload> UPLOADS = new ConcurrentHashMap<>();

    private ServerFiles() {}

    public static Path dir(MinecraftServer server) {
        Path d = server.getServerDirectory().resolve("boombox_uploads");
        try {
            Files.createDirectories(d);
        } catch (IOException ignored) {}
        return d;
    }

    public static boolean exists(MinecraftServer server, String sha1) {
        return Payloads.isSha1(sha1) && Files.isRegularFile(dir(server).resolve(sha1));
    }

    static void expectUpload(ServerPlayer player, String sha1, Target target, String title, long durationSec) {
        Map<String, Expected> m = EXPECTED.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
        if (m.size() > 64) m.clear();
        m.put(sha1, new Expected(sha1, target, title, durationSec));
    }

    public static void onDisconnect(ServerPlayer player) {
        EXPECTED.remove(player.getUUID());
        cancel(player.getUUID());
    }

    private static void cancel(UUID id) {
        Upload u = UPLOADS.remove(id);
        if (u != null) {
            try { if (u.out != null) u.out.close(); } catch (IOException ignored) {}
            try { Files.deleteIfExists(u.tmp); } catch (IOException ignored) {}
        }
    }

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.UploadChunk.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            MinecraftServer server = player.level().getServer();
            UUID id = player.getUUID();
            try {
                Upload u = UPLOADS.get(id);
                if (p.offset() == 0) {
                    Map<String, Expected> m = EXPECTED.get(id);
                    Expected exp = m == null ? null : m.remove(p.sha1());
                    if (exp == null || !exp.sha1().equals(p.sha1())) return;
                    if (p.total() <= 0 || p.total() > Payloads.MAX_UPLOAD_BYTES) {
                        player.sendOverlayMessage(Component.literal("Boombox: arquivo grande demais (máx. 50 MB)"));
                        return;
                    }
                    cancel(id);
                    Path tmp = dir(server).resolve(p.sha1() + "." + id + ".part");
                    u = new Upload(exp, p.total(), tmp);
                    u.out = Files.newOutputStream(tmp);
                    UPLOADS.put(id, u);
                }
                if (u == null || !u.expected.sha1().equals(p.sha1()) || p.offset() != u.written) {
                    cancel(id);
                    return;
                }
                u.out.write(p.data());
                u.written += p.data().length;
                if (u.written > u.total) {
                    cancel(id);
                    return;
                }
                if (u.written == u.total) {
                    u.out.close();
                    UPLOADS.remove(id);
                    String actual = sha1(u.tmp);
                    if (!actual.equals(u.expected.sha1())) {
                        Files.deleteIfExists(u.tmp);
                        player.sendOverlayMessage(Component.literal("Boombox: arquivo corrompido no envio"));
                        return;
                    }
                    Files.move(u.tmp, dir(server).resolve(actual), StandardCopyOption.REPLACE_EXISTING);
                    // play() confere se a boombox ainda está lá (no chão ou na mão)
                    if (u.expected.target() != null) {
                        ServerBoomboxManager.play(player, u.expected.target(), "local:" + actual, u.expected.title(), u.expected.durationSec());
                    }
                }
            } catch (IOException e) {
                BoomboxMod.LOGGER.warn("Falha ao receber música de {}", player.getPlainTextName(), e);
                cancel(id);
            }
        });

        // fila/playlist: o cliente quer que o servidor tenha o arquivo antes de colocá-lo na fila
        ServerPlayNetworking.registerGlobalReceiver(Payloads.PrepareFile.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!Payloads.isSha1(p.sha1())) return;
            if (exists(player.level().getServer(), p.sha1())) {
                ServerPlayNetworking.send(player, new Payloads.FileReady(p.sha1()));
            } else {
                expectUpload(player, p.sha1(), null, p.title(), 0);
                ServerPlayNetworking.send(player, new Payloads.UploadNeeded(p.sha1()));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.FileRequest.TYPE, (p, ctx) -> {
            ServerPlayer player = ctx.player();
            String sha1 = p.sha1();
            MinecraftServer server = player.level().getServer();
            if (!exists(server, sha1)) {
                ServerPlayNetworking.send(player, new Payloads.FileChunk(sha1, 0, -1, new byte[0]));
                return;
            }
            Path file = dir(server).resolve(sha1);
            IO.submit(() -> {
                try (InputStream in = Files.newInputStream(file)) {
                    long total = Files.size(file);
                    long offset = 0;
                    byte[] buf = new byte[Payloads.DOWNLOAD_CHUNK];
                    while (offset < total && !player.hasDisconnected()) {
                        int n = in.readNBytes(buf, 0, buf.length);
                        if (n <= 0) break;
                        byte[] data = java.util.Arrays.copyOf(buf, n);
                        long off = offset;
                        server.execute(() -> ServerPlayNetworking.send(player, new Payloads.FileChunk(sha1, off, total, data)));
                        offset += n;
                        Thread.sleep(5);
                    }
                } catch (Exception e) {
                    BoomboxMod.LOGGER.warn("Falha ao enviar música {}", sha1, e);
                }
            });
        });
    }

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
