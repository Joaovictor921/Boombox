package com.boombox.client;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Vídeos tocando nos telões. Telões mostrando a mesma música dividem o mesmo vídeo.
 * Um vídeo que nenhum telão pediu por 3 segundos é fechado.
 */
public final class VideoManager {
    private record Entry(VideoStream stream, long[] lastUsed) {}

    private static final Map<String, Entry> VIDEOS = new HashMap<>();

    private VideoManager() {}

    /** Música com vídeo? (YouTube e outros sites; arquivos e links de áudio direto não têm imagem). */
    public static boolean hasVideo(ClientBoomboxManager.State st) {
        String u = st.song.url();
        return !u.startsWith("local:") && !YouTube.isDirectAudio(u);
    }

    /** Textura do quadro atual do vídeo dessa caixa, ou null (ainda carregando / sem vídeo). Thread de desenho. */
    public static Identifier frameFor(ClientBoomboxManager.State st) {
        if (st == null || !hasVideo(st)) return null;
        String key = st.song.url() + "|" + st.song.startMs() + "|" + st.song.paused();
        Entry e = VIDEOS.get(key);
        if (e == null) {
            var song = st.song;
            VideoStream vs = new VideoStream(song.url(), () -> song.positionMs(ClientBoomboxManager.serverNow()));
            vs.start();
            e = new Entry(vs, new long[1]);
            VIDEOS.put(key, e);
        }
        e.lastUsed[0] = System.currentTimeMillis();
        if (e.stream.failed()) return null;
        return e.stream.update();
    }

    /** Fecha vídeos que ninguém está olhando (chamado a cada tick do cliente). */
    public static void tick() {
        long now = System.currentTimeMillis();
        Iterator<Entry> it = VIDEOS.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (now - e.lastUsed[0] > 3000) {
                e.stream.close();
                it.remove();
            }
        }
    }

    /** Algum telão já mostrou um quadro de vídeo? (usado no teste) */
    public static boolean anyFrame() {
        return VIDEOS.values().stream().anyMatch(e -> e.stream.hasFrame());
    }

    public static void clear() {
        VIDEOS.values().forEach(e -> e.stream.close());
        VIDEOS.clear();
    }
}
