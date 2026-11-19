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

    /** Carnaval: marchinhas e axé. */
    public static final String[] CARNAVAL = {
            "marchinhas de carnaval antigas", "Daniela Mercury - O Canto da Cidade", "Banda Eva - Leva Eu",
            "Chiclete com Banana - Selva Branca", "É o Tchan - Segura o Tchan", "Olodum - Faraó Divindade do Egito",
            "Ivete Sangalo - Festa", "Asa de Águia - Dança da Manivela", "Bell Marques - Diga Que Valeu",
            "Netinho - Milla", "Timbalada - Beija-Flor", "Claudia Leitte - Largadinho"};
    /** Festa junina: forró e música de São João. */
    public static final String[] ARRAIA = {
            "Luiz Gonzaga - Asa Branca", "Luiz Gonzaga - Olha Pro Céu", "Luiz Gonzaga - São João na Roça",
            "Dominguinhos - Isso Aqui Tá Bom Demais", "Elba Ramalho - De Volta Pro Aconchego", "Falamansa - Xote dos Milagres",
            "Mastruz com Leite - Meu Vaqueiro Meu Peão", "Trio Nordestino - Chililique", "Luiz Gonzaga - Pagode Russo",
            "Alceu Valença - Morena Tropicana", "Flávio José - Tareco e Mariola", "Genival Lacerda - Severina Xique Xique"};

    /** Praia: axé, reggae e pagode de verão. */
    public static final String[] PRAIA = {
            "Natiruts - Sorri Sou Rei", "Armandinho - Pescador", "Maneva - Lisboa", "Chiclete com Banana - Voa Voa",
            "Ivete Sangalo - Sorte Grande", "Jorge Ben Jor - País Tropical", "Tim Maia - Do Leme ao Pontal",
            "Cidade Negra - A Sombra da Maldade", "Armandinho - Ursinho de Dormir", "Netinho - Mila"};
    /** Pagode na laje: samba e pagode. */
    public static final String[] PAGODE = {
            "Zeca Pagodinho - Deixa a Vida Me Levar", "Fundo de Quintal - O Show Tem Que Continuar", "Arlindo Cruz - Meu Lugar",
            "Exaltasamba - Tá Vendo Aquela Lua", "Péricles - Até Que Durou", "Sorriso Maroto - Assim Você Mata o Papai",
            "Turma do Pagode - Lancinho", "Thiaguinho - Buquê de Flores", "Revelação - Deixa Acontecer", "Raça Negra - Cheia de Manias"};

    /** Playlist de uma caixa que toca sozinha, pelo dono (Seu Zé, Carnaval, Arraiá). */
    public static String[] forOwner(String owner) {
        return switch (owner) {
            case "Carnaval" -> CARNAVAL;
            case "Arraiá" -> ARRAIA;
            case "Praia" -> PRAIA;
            case "Pagode" -> PAGODE;
            default -> TRACKS;
        };
    }

    private ZePlaylist() {}

    /** Algumas músicas sorteadas da playlist, sem repetir as que tocaram por último. */
    public static List<String> pick(int count, List<String> recentTitles) {
        return pick(TRACKS, count, recentTitles);
    }

    public static List<String> pick(String[] tracks, int count, List<String> recentTitles) {
        List<String> all = new ArrayList<>(List.of(tracks));
        Collections.shuffle(all);
        List<String> out = new ArrayList<>();
        for (String t : all) {
            if (out.size() >= count) break;
            String song = (t.contains(" - ") ? t.substring(t.indexOf(" - ") + 3) : t).toLowerCase(java.util.Locale.ROOT);
            boolean recent = recentTitles.stream().anyMatch(r -> r.toLowerCase(java.util.Locale.ROOT).contains(song));
            if (!recent) out.add(t);
        }
        for (String t : all) if (out.size() < count && !out.contains(t)) out.add(t);
        return out;
    }
}
