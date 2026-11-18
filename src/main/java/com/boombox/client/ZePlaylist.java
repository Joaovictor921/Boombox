package com.boombox.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Músicas do bar do Seu Zé: a playlist "Bar do mirlu" do Spotify
 * (open.spotify.com/playlist/5abYiCKyQnplyprV3JxX4b). O Spotify não deixa tocar fora do app, então cada
 * música é procurada no YouTube pelo nome e artista.
 */
public final class ZePlaylist {
    public static final String[] TRACKS = {
            "Grupo Revelação - Ô Queiroz",
            "Chitãozinho & Xororó - Evidências",
            "Zé Neto & Cristiano - Largado As Traças",
            "Conrado & Aleksandro - Só Se For Gelada",
            "Grupo Revelação - Deixa Acontecer",
            "Raimundos - Mulher de fases",
            "Roupa Nova - Whisky a Go-Go",
            "Os Barões Da Pisadinha - Tá Rocheda",
            "Os Barões Da Pisadinha - Recairei",
            "Israel & Rodolffo - Marca Evidente",
            "Trio Parada Dura - Telefone Mudo",
            "Teodoro & Sampaio - Pitoco",
            "Gino & Geno - Bebo Pa Carai",
            "Teodoro & Sampaio - É mentira dela",
            "Bruno & Marrone - Boate Azul",
            "Trio Parada Dura - Castelo De Amor",
            "Leandro & Leonardo - Pense em mim",
            "Tião Carreiro & Pardinho - Boi soberano",
            "Seu Jorge - Amiga da Minha Mulher",
            "Trio Parada Dura - Blusa Vermelha",
            "Milionário & José Rico - Estrada da vida",
            "Trio Parada Dura - Fuscão Preto",
            "Victor & Leo - Borboletas",
            "Leandro & Leonardo - Um Sonhador",
            "Chitãozinho & Xororó - Sinônimos",
    };

    private ZePlaylist() {}

    /** Algumas músicas sorteadas da playlist, sem repetir as que tocaram por último. */
    public static List<String> pick(int count, List<String> recentTitles) {
        List<String> all = new ArrayList<>(List.of(TRACKS));
        Collections.shuffle(all);
        List<String> out = new ArrayList<>();
        for (String t : all) {
            if (out.size() >= count) break;
            String song = t.substring(t.indexOf(" - ") + 3).toLowerCase(java.util.Locale.ROOT);
            boolean recent = recentTitles.stream().anyMatch(r -> r.toLowerCase(java.util.Locale.ROOT).contains(song));
            if (!recent) out.add(t);
        }
        for (String t : all) if (out.size() < count && !out.contains(t)) out.add(t);
        return out;
    }
}
