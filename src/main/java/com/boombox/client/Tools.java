package com.boombox.client;

import com.boombox.BoomboxMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Localiza (ou baixa na primeira vez) as ferramentas externas usadas para tocar música:
 * yt-dlp (YouTube), ffmpeg (decodificar áudio) e deno (exigido pelo yt-dlp para o YouTube).
 * Tudo fica em .minecraft/boombox/bin.
 */
public final class Tools {
    public static final ExecutorService EXEC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Boombox-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");
    private static final String ARCH = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    private static final boolean ARM = ARCH.contains("aarch64") || ARCH.contains("arm");

    private static CompletableFuture<Void> ready;
    private static volatile String ytdlp;
    private static volatile String ffmpeg;
    private static volatile String deno;
    private static volatile boolean ownYtdlp;

    private Tools() {}

    public static Path baseDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("boombox");
    }

    public static Path binDir() {
        return baseDir().resolve("bin");
    }

    public static synchronized CompletableFuture<Void> ensureReady() {
        if (ready == null || ready.isCompletedExceptionally()) {
            ready = CompletableFuture.runAsync(Tools::setup, EXEC);
        }
        return ready;
    }

    public static String ffmpeg() { return ffmpeg; }

    private static void setup() {
        try {
            Files.createDirectories(binDir());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String exe = WINDOWS ? ".exe" : "";

        // ---- ffmpeg ----
        Path ownFfmpeg = binDir().resolve("ffmpeg" + exe);
        if (Files.isRegularFile(ownFfmpeg)) ffmpeg = ownFfmpeg.toString();
        else if (works("ffmpeg", "-version")) ffmpeg = "ffmpeg";
        else if (WINDOWS) {
            chat("Baixando ffmpeg (só na primeira vez, ~100 MB)...");
            downloadZipEntry("https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip",
                    "/bin/ffmpeg.exe", ownFfmpeg);
            ffmpeg = ownFfmpeg.toString();
        } else {
            throw new IllegalStateException("Instale o ffmpeg (ex.: sudo apt install ffmpeg / brew install ffmpeg)");
        }

        // ---- yt-dlp ----
        Path ownYt = binDir().resolve("yt-dlp" + exe);
        if (!Files.isRegularFile(ownYt) && works("yt-dlp", "--version")) {
            ytdlp = "yt-dlp";
        } else {
            if (!Files.isRegularFile(ownYt)) {
                chat("Baixando yt-dlp...");
                String asset = WINDOWS ? "yt-dlp.exe" : MAC ? "yt-dlp_macos" : (ARM ? "yt-dlp_linux_aarch64" : "yt-dlp_linux");
                download("https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + asset, ownYt);
                ownYt.toFile().setExecutable(true);
            }
            ytdlp = ownYt.toString();
            ownYtdlp = true;
        }

        // ---- deno (runtime JavaScript que o yt-dlp usa para o YouTube) ----
        Path ownDeno = binDir().resolve("deno" + exe);
        if (Files.isRegularFile(ownDeno)) deno = ownDeno.toString();
        else if (!works("deno", "--version")) {
            try {
                chat("Baixando deno (necessário para o YouTube)...");
                String asset = WINDOWS ? "deno-x86_64-pc-windows-msvc.zip"
                        : MAC ? (ARM ? "deno-aarch64-apple-darwin.zip" : "deno-x86_64-apple-darwin.zip")
                        : (ARM ? "deno-aarch64-unknown-linux-gnu.zip" : "deno-x86_64-unknown-linux-gnu.zip");
                downloadZipEntry("https://github.com/denoland/deno/releases/latest/download/" + asset, "deno" + exe, ownDeno);
                ownDeno.toFile().setExecutable(true);
                deno = ownDeno.toString();
            } catch (Exception e) {
                BoomboxMod.LOGGER.warn("Não foi possível baixar o deno; o YouTube pode falhar", e);
            }
        }

        // Mantém o yt-dlp próprio atualizado (o YouTube muda com frequência)
        if (ownYtdlp) {
            EXEC.submit(() -> {
                try {
                    run(List.of(ytdlp, "-U"), 120);
                } catch (Exception ignored) {}
            });
        }
        chat("Pronto para tocar!");
    }

    /** Comando base do yt-dlp, com as opções de ffmpeg e deno. */
    public static List<String> ytdlpCommand(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ytdlp);
        cmd.add("--no-warnings");
        cmd.add("--encoding");
        cmd.add("utf-8");
        if (ffmpeg != null && !ffmpeg.equals("ffmpeg")) {
            cmd.add("--ffmpeg-location");
            cmd.add(ffmpeg);
        }
        if (deno != null && ownYtdlp) {
            cmd.add("--js-runtimes");
            cmd.add("deno:" + deno);
        }
        cmd.addAll(List.of(args));
        return cmd;
    }

    /** Executa um comando e devolve o stdout (UTF-8). Lança exceção se falhar. */
    public static String run(List<String> cmd, int timeoutSec) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8", "1");
        Process p = pb.start();
        CompletableFuture<String> err = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        }, EXEC);
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("tempo esgotado");
        }
        if (p.exitValue() != 0) {
            String e = err.join().trim();
            String last = e.isEmpty() ? "código " + p.exitValue() : e.lines().reduce((a, b) -> b).orElse(e);
            throw new IOException(last);
        }
        return out;
    }

    /** Duração de um arquivo de áudio em segundos (lida pelo ffmpeg), ou 0 se não souber. */
    public static long probeDuration(java.nio.file.Path file) {
        try {
            ensureReady().get();
            Process p = new ProcessBuilder(ffmpeg, "-hide_banner", "-nostdin", "-i", file.toString())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(10, TimeUnit.SECONDS);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Duration: (\\d+):(\\d+):(\\d+)").matcher(out);
            if (m.find()) {
                return Long.parseLong(m.group(1)) * 3600 + Long.parseLong(m.group(2)) * 60 + Long.parseLong(m.group(3));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static boolean works(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static HttpClient http() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    private static InputStream open(String url) throws IOException, InterruptedException {
        HttpResponse<InputStream> res = http().send(HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "boombox-mod").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + " em " + url);
        return res.body();
    }

    /**
     * 3.7.1: download que trava (internet caiu no meio) não pode ficar esperando para sempre: se passar 30 segundos sem
     * chegar nada, a conexão é fechada e o download falha. Na próxima música o mod tenta de novo.
     */
    private static InputStream guard(InputStream raw) {
        long[] last = {System.currentTimeMillis()};
        var watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "boombox-download-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() - last[0] > 30_000) {
                BoomboxMod.LOGGER.warn("Download parado há 30 s; desistindo (tenta de novo na próxima música)");
                try {
                    raw.close();
                } catch (IOException ignored) {
                }
                watchdog.shutdown();
            }
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
        return new java.io.FilterInputStream(raw) {
            @Override
            public int read() throws IOException {
                int b = super.read();
                last[0] = System.currentTimeMillis();
                return b;
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                int n = super.read(buf, off, len);
                last[0] = System.currentTimeMillis();
                return n;
            }

            @Override
            public void close() throws IOException {
                watchdog.shutdownNow();
                super.close();
            }
        };
    }

    private static void download(String url, Path target) {
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = guard(open(url))) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao baixar " + url + ": " + e.getMessage(), e);
        }
    }

    private static void downloadZipEntry(String url, String entrySuffix, Path target) {
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (ZipInputStream zip = new ZipInputStream(guard(open(url)))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (!e.isDirectory() && (name.endsWith(entrySuffix) || name.equals(entrySuffix))) {
                    Files.copy(zip, tmp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
            throw new IOException(entrySuffix + " não encontrado no zip");
        } catch (Exception e) {
            throw new RuntimeException("Falha ao baixar " + url + ": " + e.getMessage(), e);
        }
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§d[Boombox]§r " + msg));
        });
    }
}
