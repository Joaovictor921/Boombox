package com.boombox.client;

import com.boombox.BoomboxMod;
import com.boombox.QueueItem;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Playlists do jogador, uma por arquivo de texto em .minecraft/boombox_playlists/&lt;nome&gt;.txt.
 * Cada linha: {@code link | título | duração em segundos} (só o link já basta; linhas com # são comentários).
 * Arquivos da pasta boombox_musicas ficam como {@code arquivo:caminho/dentro/da/pasta.mp3}.
 * As playlists são lidas quando o jogo inicia e salvas a cada mudança — dá para editar os .txt à mão também.
 */
public final class Playlists {
    public static final String FILE_PREFIX = "arquivo:";

    public record Song(String ref, String title, long durationSec) {
        public boolean isFile() { return ref.startsWith(FILE_PREFIX); }
    }

    public static final class Playlist {
        public final String name;
        public final List<Song> songs = new ArrayList<>();

        Playlist(String name) {
            this.name = name;
        }
    }

    private static final List<Playlist> ALL = new ArrayList<>();
    private static boolean loaded;

    private Playlists() {}

    public static Path dir() {
        Path d = Minecraft.getInstance().gameDirectory.toPath().resolve("boombox_playlists");
        try {
            Files.createDirectories(d);
        } catch (IOException ignored) {}
        return d;
    }

    /** Lê todas as playlists da pasta (chamado ao iniciar o jogo e ao abrir o menu). */
    public static synchronized void load() {
        ALL.clear();
        try (Stream<Path> s = Files.list(dir())) {
            for (Path f : s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")).toList()) {
                String file = f.getFileName().toString();
                Playlist pl = new Playlist(file.substring(0, file.length() - 4));
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    Song song = parse(line);
                    if (song != null) pl.songs.add(song);
                }
                ALL.add(pl);
            }
        } catch (IOException e) {
            BoomboxMod.LOGGER.warn("Boombox: não foi possível ler as playlists", e);
        }
        ALL.sort(Comparator.comparing(p -> p.name.toLowerCase(Locale.ROOT)));
        loaded = true;
    }

    private static Song parse(String line) {
        line = line.strip();
        if (line.isEmpty() || line.startsWith("#")) return null;
        String[] parts = line.split("\\s*\\|\\s*");
        String ref = parts[0].strip();
        if (!(ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("local:") || ref.startsWith(FILE_PREFIX))) {
            return null;
        }
        String title = parts.length > 1 && !parts[1].isBlank() ? parts[1].strip() : ref;
        long dur = 0;
        if (parts.length > 2) {
            try {
                dur = Long.parseLong(parts[2].strip());
            } catch (NumberFormatException ignored) {}
        }
        return new Song(ref, title, dur);
    }

    public static synchronized List<Playlist> all() {
        if (!loaded) load();
        return Collections.unmodifiableList(new ArrayList<>(ALL));
    }

    public static synchronized Playlist get(String name) {
        for (Playlist p : all()) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    /** Cria (ou devolve, se já existe) uma playlist. */
    public static synchronized Playlist create(String rawName) {
        String name = rawName.strip().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (name.isEmpty()) name = "Playlist";
        if (name.length() > 40) name = name.substring(0, 40);
        Playlist existing = get(name);
        if (existing != null) return existing;
        Playlist p = new Playlist(name);
        ALL.add(p);
        ALL.sort(Comparator.comparing(x -> x.name.toLowerCase(Locale.ROOT)));
        save(p);
        return p;
    }

    public static synchronized void delete(Playlist p) {
        ALL.remove(p);
        try {
            Files.deleteIfExists(dir().resolve(p.name + ".txt"));
        } catch (IOException e) {
            BoomboxMod.LOGGER.warn("Boombox: não foi possível apagar a playlist", e);
        }
    }

    /** Adiciona uma música (sem repetir o mesmo link). Devolve false se já estava lá. */
    public static synchronized boolean add(Playlist p, Song song) {
        for (Song s : p.songs) if (s.ref().equals(song.ref())) return false;
        p.songs.add(song);
        save(p);
        return true;
    }

    public static synchronized void remove(Playlist p, int index) {
        if (index < 0 || index >= p.songs.size()) return;
        p.songs.remove(index);
        save(p);
    }

    public static synchronized void move(Playlist p, int index, int delta) {
        int to = index + delta;
        if (index < 0 || index >= p.songs.size() || to < 0 || to >= p.songs.size()) return;
        Collections.swap(p.songs, index, to);
        save(p);
    }

    private static void save(Playlist p) {
        List<String> lines = new ArrayList<>();
        lines.add("# Playlist da Boombox: " + p.name);
        lines.add("# Formato: link | título | duração (segundos). Pode editar este arquivo à mão.");
        for (Song s : p.songs) {
            lines.add(s.ref() + " | " + s.title().replace("|", "/") + " | " + s.durationSec());
        }
        try {
            Files.write(dir().resolve(p.name + ".txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            BoomboxMod.LOGGER.warn("Boombox: não foi possível salvar a playlist", e);
        }
    }

    public static Song fileSong(Path file) {
        String rel = ClientFiles.musicDir().relativize(file).toString().replace('\\', '/');
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return new Song(FILE_PREFIX + rel, dot > 0 ? name.substring(0, dot) : name, 0);
    }

    /**
     * Converte as músicas em itens da fila. Arquivos locais são enviados ao servidor antes
     * (por isso é assíncrono). Arquivos que não existem mais são ignorados.
     */
    public static CompletableFuture<List<QueueItem>> toQueueItems(List<Song> songs) {
        List<CompletableFuture<QueueItem>> parts = new ArrayList<>();
        for (Song s : songs) {
            if (s.isFile()) {
                Path f = ClientFiles.musicDir().resolve(s.ref().substring(FILE_PREFIX.length()));
                if (!Files.isRegularFile(f)) continue;
                parts.add(ClientFiles.prepare(f).exceptionally(e -> null));
            } else {
                parts.add(CompletableFuture.completedFuture(new QueueItem(s.ref(), s.title(), s.durationSec())));
            }
        }
        return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new)).thenApply(v -> {
            List<QueueItem> out = new ArrayList<>();
            for (CompletableFuture<QueueItem> f : parts) {
                QueueItem q = f.join();
                if (q != null) out.add(q);
            }
            return out;
        });
    }
}
