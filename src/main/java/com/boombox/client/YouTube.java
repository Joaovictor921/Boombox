package com.boombox.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Pesquisa e download de áudio via yt-dlp. */
public final class YouTube {
    public record Result(String url, String title, long durationSec) {}

    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static final List<String> DIRECT_EXT = List.of(".mp3", ".ogg", ".oga", ".opus", ".wav", ".flac", ".m4a", ".aac", ".webm");

    private YouTube() {}

    public static List<Result> search(String query, int count) throws Exception {
        Tools.ensureReady().get();
        String out = Tools.run(Tools.ytdlpCommand("--flat-playlist", "--print", "%(id)s\t%(title)s\t%(duration)s",
                "ytsearch" + count + ":" + query), 60);
        List<Result> list = new ArrayList<>();
        for (String line : out.split("\\R")) {
            String[] parts = line.split("\t");
            if (parts.length < 2 || parts[0].isBlank()) continue;
            list.add(new Result("https://www.youtube.com/watch?v=" + parts[0].trim(), parts[1].trim(),
                    parts.length > 2 ? parseDuration(parts[2]) : 0));
        }
        return list;
    }

    /** Título e duração de um link qualquer suportado pelo yt-dlp. */
    public static Result info(String url) throws Exception {
        if (isDirectAudio(url)) {
            String name = url.replaceAll("[?#].*$", "");
            name = name.substring(name.lastIndexOf('/') + 1);
            return new Result(url, java.net.URLDecoder.decode(name, StandardCharsets.UTF_8), 0);
        }
        Tools.ensureReady().get();
        String out = Tools.run(Tools.ytdlpCommand("--no-playlist", "--skip-download", "--print", "%(title)s\t%(duration)s", url), 60);
        String line = out.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        String[] parts = line.split("\t");
        String title = parts.length > 0 && !parts[0].isBlank() ? parts[0].trim() : url;
        long dur = parts.length > 1 ? parseDuration(parts[1]) : 0;
        return new Result(url, title, dur);
    }

    private static final java.util.regex.Pattern VIDEO_ID = java.util.regex.Pattern.compile(
            "(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})");

    /** Id do vídeo do YouTube num link, ou null. */
    public static String videoId(String url) {
        java.util.regex.Matcher m = VIDEO_ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Músicas parecidas: usa o "Mix" do YouTube daquele vídeo (a mesma rádio automática do site).
     */
    public static List<Result> related(String url, int count) throws Exception {
        String id = videoId(url);
        if (id == null) return List.of();
        Tools.ensureReady().get();
        String out = Tools.run(Tools.ytdlpCommand("--flat-playlist", "--playlist-items", "1-" + (count + 1),
                "--print", "%(id)s\t%(title)s\t%(duration)s",
                "https://www.youtube.com/watch?v=" + id + "&list=RD" + id), 60);
        List<Result> list = new ArrayList<>();
        for (String line : out.split("\\R")) {
            String[] parts = line.split("\t");
            if (parts.length < 2 || parts[0].isBlank() || parts[0].trim().equals(id)) continue;
            list.add(new Result("https://www.youtube.com/watch?v=" + parts[0].trim(), parts[1].trim(),
                    parts.length > 2 ? parseDuration(parts[2]) : 0));
        }
        return list;
    }

    public static boolean isDirectAudio(String url) {
        String path = url.replaceAll("[?#].*$", "").toLowerCase(Locale.ROOT);
        return DIRECT_EXT.stream().anyMatch(path::endsWith) && !path.contains("youtube.com") && !path.contains("youtu.be");
    }

    /** Link direto do áudio (para o ffmpeg tocar sem baixar) e o User-Agent que o YouTube espera. */
    public record StreamInfo(String url, String userAgent) {}

    /**
     * Pede ao yt-dlp só o endereço do áudio, sem baixar nada. Prefere formatos "https" simples,
     * que permitem pular para qualquer ponto da música (necessário para a sincronia).
     */
    private record CachedStream(StreamInfo info, long time) {}
    private static final Map<String, CachedStream> STREAM_CACHE = new ConcurrentHashMap<>();

    /** Link direto do áudio, guardado por 20 min (continuar depois de pausar fica instantâneo). */
    public static StreamInfo streamUrl(String url) throws Exception {
        CachedStream c = STREAM_CACHE.get(url);
        if (c != null && System.currentTimeMillis() - c.time < 20 * 60_000L) return c.info;
        StreamInfo info = resolveStream(url);
        STREAM_CACHE.put(url, new CachedStream(info, System.currentTimeMillis()));
        return info;
    }

    /** Busca o link com antecedência (ex.: próxima música da fila). */
    public static void prefetch(String url) {
        if (url.startsWith("local:") || isDirectAudio(url) || STREAM_CACHE.containsKey(url)) return;
        Tools.EXEC.submit(() -> {
            try {
                streamUrl(url);
            } catch (Exception ignored) {}
        });
    }

    private static final Map<String, CachedStream> VIDEO_CACHE = new ConcurrentHashMap<>();

    /**
     * Link direto só do vídeo (sem som), em até 360p, para o telão. Prefere H.264 (decodifica leve)
     * e links https simples (dá para pular para qualquer ponto).
     */
    public static StreamInfo videoStreamUrl(String url) throws Exception {
        CachedStream c = VIDEO_CACHE.get(url);
        if (c != null && System.currentTimeMillis() - c.time < 20 * 60_000L) return c.info;
        Tools.ensureReady().get();
        String out = Tools.run(Tools.ytdlpCommand(
                "--no-playlist", "--skip-download",
                "-f", "bv*[height<=360][vcodec^=avc1][protocol^=http]/bv*[height<=360][protocol^=http]"
                        + "/bv*[height<=480]/b[height<=480]/b",
                "--print", "%(url)s",
                "--print", "%(http_headers.User-Agent)s",
                url), 60);
        List<String> lines = out.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
        if (lines.isEmpty() || !lines.get(0).startsWith("http")) throw new IOException("yt-dlp não retornou o link do vídeo");
        String ua = lines.size() > 1 && !lines.get(1).equals("NA") ? lines.get(1) : null;
        StreamInfo info = new StreamInfo(lines.get(0), ua);
        VIDEO_CACHE.put(url, new CachedStream(info, System.currentTimeMillis()));
        return info;
    }

    private static StreamInfo resolveStream(String url) throws Exception {
        Tools.ensureReady().get();
        String out = Tools.run(Tools.ytdlpCommand(
                "--no-playlist", "--skip-download",
                "-f", "bestaudio[protocol=https]/bestaudio[protocol=http]/bestaudio/best",
                "--print", "%(url)s",
                "--print", "%(http_headers.User-Agent)s",
                url), 60);
        List<String> lines = out.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
        if (lines.isEmpty() || !lines.get(0).startsWith("http")) throw new IOException("yt-dlp não retornou o link do áudio");
        String ua = lines.size() > 1 && !lines.get(1).equals("NA") ? lines.get(1) : null;
        return new StreamInfo(lines.get(0), ua);
    }

    /**
     * Baixa o áudio para o cache local (uma vez) e devolve o caminho do arquivo.
     * Baixar antes de tocar é muito mais confiável que transmitir direto do YouTube.
     */
    public static Path downloadAudio(String url) throws Exception {
        Tools.ensureReady().get();
        String key = hash(url);
        Path dir = Tools.baseDir().resolve("cache").resolve("web");
        Files.createDirectories(dir);
        synchronized (LOCKS.computeIfAbsent(key, k -> new Object())) {
            Path cached = findCached(dir, key);
            if (cached != null) return cached;
            String out = Tools.run(Tools.ytdlpCommand(
                    "--no-playlist", "--no-simulate", "--no-part",
                    "-f", "bestaudio/best",
                    "--max-filesize", "300M",
                    "-o", dir.resolve(key + ".%(ext)s").toString(),
                    "--print", "after_move:filepath",
                    url), 600);
            String path = out.lines().map(String::trim).filter(l -> !l.isEmpty()).reduce((a, b) -> b).orElse("");
            Path p = path.isEmpty() ? findCached(dir, key) : Path.of(path);
            if (p == null || !Files.isRegularFile(p)) throw new IOException("yt-dlp não gerou o arquivo");
            return p;
        }
    }

    private static Path findCached(Path dir, String key) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(key + ".") && !n.endsWith(".part") && !n.endsWith(".ytdl");
            }).findFirst().orElse(null);
        }
    }

    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 20);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static long parseDuration(String s) {
        try {
            return (long) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String formatDuration(long sec) {
        if (sec <= 0) return "";
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
