package com.boombox.client;

import com.boombox.BoomboxMod;
import com.boombox.net.Payloads;
import com.boombox.server.ServerFiles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import com.boombox.Target;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Músicas do próprio jogador (pasta .minecraft/boombox_musicas):
 * envia ao servidor quando necessário e baixa do servidor as músicas enviadas por outros.
 */
public final class ClientFiles {
    public static final List<String> AUDIO_EXT = List.of(".mp3", ".ogg", ".opus", ".wav", ".flac", ".m4a", ".aac", ".webm", ".mp4");

    private record Pending(Target target, Path file, String name) {}

    /** Arquivo local já identificado (hash), com título e duração. */
    public record LocalInfo(String sha1, String title, long durationSec) {}

    private static final class Download {
        final CompletableFuture<Path> future = new CompletableFuture<>();
        Path tmp;
        OutputStream out;
        long written;
    }

    private static final class Upload {
        final String sha1;
        final long total;
        final Runnable onDone;
        RandomAccessFile raf;
        long offset;

        Upload(String sha1, long total, Runnable onDone) {
            this.sha1 = sha1;
            this.total = total;
            this.onDone = onDone;
        }
    }

    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Void>> PREPARING = new ConcurrentHashMap<>();
    private static final Map<String, Download> DOWNLOADS = new ConcurrentHashMap<>();
    private static final ArrayDeque<Upload> UPLOADS = new ArrayDeque<>();

    private ClientFiles() {}

    public static Path musicDir() {
        Path d = Minecraft.getInstance().gameDirectory.toPath().resolve("boombox_musicas");
        try {
            Files.createDirectories(d);
        } catch (IOException ignored) {}
        return d;
    }

    private static Path cacheDir() throws IOException {
        Path d = Tools.baseDir().resolve("cache").resolve("local");
        Files.createDirectories(d);
        return d;
    }

    public static List<Path> listMusic() {
        List<Path> list = new ArrayList<>();
        try (Stream<Path> s = Files.walk(musicDir(), 3)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> AUDIO_EXT.stream().anyMatch(e -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(e)))
                    .sorted()
                    .forEach(list::add);
        } catch (IOException ignored) {}
        return list;
    }

    /** Calcula o hash, copia para o cache e lê a duração (rode fora da thread principal). */
    public static LocalInfo identify(Path file) throws Exception {
        long size = Files.size(file);
        if (size > Payloads.MAX_UPLOAD_BYTES) throw new IOException("arquivo grande demais (máx. 50 MB)");
        String sha1 = ServerFiles.sha1(file);
        Path cached = cacheDir().resolve(sha1);
        if (!Files.exists(cached)) Files.copy(file, cached, StandardCopyOption.REPLACE_EXISTING);
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        return new LocalInfo(sha1, title, Tools.probeDuration(cached));
    }

    /** Toca um arquivo local: calcula o hash, avisa o servidor e envia se ele ainda não tiver. */
    public static void playLocal(Target target, Path file, Consumer<String> status) {
        Tools.EXEC.submit(() -> {
            try {
                LocalInfo info = identify(file);
                PENDING.put(info.sha1(), new Pending(target, file, info.title()));
                Minecraft.getInstance().execute(() -> ClientPlayNetworking.send(
                        new Payloads.PlayRequest(target, "local:" + info.sha1(), info.title(), info.durationSec())));
                status.accept("Tocando: " + info.title());
            } catch (Exception e) {
                status.accept("Erro: " + e.getMessage());
            }
        });
    }

    /**
     * Prepara um arquivo local para a fila/playlist: garante que o servidor tenha o arquivo
     * (enviando se preciso) e devolve o item da fila quando estiver pronto.
     */
    public static CompletableFuture<com.boombox.QueueItem> prepare(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return identify(file);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, Tools.EXEC).thenCompose(info -> {
            CompletableFuture<Void> ready = PREPARING.computeIfAbsent(info.sha1(), k -> new CompletableFuture<>());
            Minecraft.getInstance().execute(() ->
                    ClientPlayNetworking.send(new Payloads.PrepareFile(info.sha1(), info.title())));
            return ready.orTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                    .thenApply(v -> new com.boombox.QueueItem("local:" + info.sha1(), info.title(), info.durationSec()));
        });
    }

    /** O servidor já tinha o arquivo. */
    public static void onFileReady(String sha1) {
        CompletableFuture<Void> f = PREPARING.remove(sha1);
        if (f != null) f.complete(null);
    }

    /** Servidor pediu o arquivo (ele ainda não tem). */
    public static void onUploadNeeded(String sha1) {
        Pending p = PENDING.remove(sha1);
        CompletableFuture<Void> prep = PREPARING.remove(sha1);
        if (p == null && prep == null) return;
        try {
            Path cached = cacheDir().resolve(sha1);
            Path src = Files.exists(cached) ? cached : p.file();
            Upload u = new Upload(sha1, Files.size(src), prep == null ? null : () -> prep.complete(null));
            u.raf = new RandomAccessFile(src.toFile(), "r");
            UPLOADS.add(u);
        } catch (Exception e) {
            BoomboxMod.LOGGER.warn("Falha ao preparar envio", e);
            if (prep != null) prep.completeExceptionally(e);
        }
    }

    /** Envia alguns pedaços por tick para não travar a conexão. */
    public static void tick() {
        Upload u = UPLOADS.peek();
        if (u == null) return;
        try {
            for (int i = 0; i < 12 && u.offset < u.total; i++) {
                int len = (int) Math.min(Payloads.UPLOAD_CHUNK, u.total - u.offset);
                byte[] data = new byte[len];
                u.raf.seek(u.offset);
                u.raf.readFully(data);
                ClientPlayNetworking.send(new Payloads.UploadChunk(u.sha1, u.offset, u.total, data));
                u.offset += len;
            }
            if (u.offset >= u.total) {
                u.raf.close();
                UPLOADS.poll();
                // os pacotes chegam em ordem: o que for enviado depois já encontra o arquivo no servidor
                if (u.onDone != null) u.onDone.run();
            }
        } catch (IOException e) {
            BoomboxMod.LOGGER.warn("Falha no envio", e);
            try { u.raf.close(); } catch (IOException ignored) {}
            UPLOADS.poll();
        }
    }

    /** Devolve o arquivo local para um sha1, baixando do servidor se preciso. */
    public static CompletableFuture<Path> get(String sha1) {
        try {
            Path cached = cacheDir().resolve(sha1);
            if (Files.isRegularFile(cached)) return CompletableFuture.completedFuture(cached);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        Download d = DOWNLOADS.computeIfAbsent(sha1, k -> {
            Download nd = new Download();
            Minecraft.getInstance().execute(() -> ClientPlayNetworking.send(new Payloads.FileRequest(sha1)));
            return nd;
        });
        return d.future;
    }

    /** Pedaço de arquivo vindo do servidor (thread principal). */
    public static void onFileChunk(Payloads.FileChunk c) {
        Download d = DOWNLOADS.get(c.sha1());
        if (d == null) return;
        try {
            if (c.total() < 0) {
                fail(c.sha1(), d, new IOException("o servidor não tem essa música"));
                return;
            }
            if (c.offset() == 0) {
                d.tmp = cacheDir().resolve(c.sha1() + ".part");
                d.out = Files.newOutputStream(d.tmp);
                d.written = 0;
            }
            if (d.out == null || c.offset() != d.written) return;
            d.out.write(c.data());
            d.written += c.data().length;
            if (d.written >= c.total()) {
                d.out.close();
                Path target = cacheDir().resolve(c.sha1());
                Tools.EXEC.submit(() -> {
                    try {
                        if (!ServerFiles.sha1(d.tmp).equals(c.sha1())) throw new IOException("arquivo corrompido");
                        Files.move(d.tmp, target, StandardCopyOption.REPLACE_EXISTING);
                        DOWNLOADS.remove(c.sha1());
                        d.future.complete(target);
                    } catch (IOException e) {
                        fail(c.sha1(), d, e);
                    }
                });
            }
        } catch (IOException e) {
            fail(c.sha1(), d, e);
        }
    }

    private static void fail(String sha1, Download d, Exception e) {
        DOWNLOADS.remove(sha1);
        try { if (d.out != null) d.out.close(); } catch (IOException ignored) {}
        d.future.completeExceptionally(e);
    }

    public static void clear() {
        PENDING.clear();
        PREPARING.values().forEach(f -> f.completeExceptionally(new IOException("desconectado")));
        PREPARING.clear();
        DOWNLOADS.forEach((k, d) -> fail(k, d, new IOException("desconectado")));
        for (Upload u : UPLOADS) {
            try { u.raf.close(); } catch (IOException ignored) {}
        }
        UPLOADS.clear();
    }
}
