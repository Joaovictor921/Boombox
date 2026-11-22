package com.boombox.entity;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 3.6: campeonato de futebol ou de vôlei em formato de copa. 16 times em 4 grupos de 4 (o seu time é o 0, no grupo A);
 * cada um joga 3 rodadas no grupo, os 2 primeiros de cada grupo vão para as quartas, depois semifinal e final.
 * Os jogos dos outros times são simulados. O mesmo objeto vai para a tela do jogador (encode/decode).
 */
public class Cup {
    public static final int GROUP_ROUNDS = 3, QUARTER = 3, SEMI = 4, FINAL = 5, DONE = 6;
    /** Jogos de cada rodada do grupo (posição dentro do grupo). */
    private static final int[][][] ROUND = {{{0, 1}, {2, 3}}, {{0, 2}, {1, 3}}, {{0, 3}, {1, 2}}};

    public final boolean volley;
    public final String[] names = new String[16];
    public final int[] colors = new int[16], pts = new int[16], gf = new int[16], ga = new int[16], played = new int[16];
    /** 0..2 = rodadas do grupo, 3 = quartas, 4 = semi, 5 = final, 6 = acabou. */
    public int stage;
    /** Chaveamento: 0-7 quartas (em pares), 8-11 semis, 12-13 final, 14 campeão (-1 = ainda não se sabe). */
    public final int[] ko = new int[15];
    /** Placar de cada jogo do mata-mata (0-3 quartas, 4-5 semis, 6 final), do ponto de vista "primeiro x segundo". */
    public final String[] koScore = new String[7];
    /** Placar dos jogos do seu time no grupo (para a tela). */
    public final String[] myGroupGames = {"", "", ""};
    /** O seu time foi eliminado. */
    public boolean out;

    public Cup(boolean volley) {
        this.volley = volley;
        Arrays.fill(ko, -1);
        Arrays.fill(koScore, "");
        Arrays.fill(names, "");
    }

    public void init(RandomSource rnd, String mine, int myColor) {
        names[0] = clean(mine);
        colors[0] = myColor & 0xFFFFFF;
        var others = Sports.randomTeams(rnd, volley, 15, List.of(names[0]), List.of(colors[0]));
        for (int i = 0; i < 15; i++) {
            names[i + 1] = clean(others.get(i)[0]);
            colors[i + 1] = Integer.parseInt(others.get(i)[1]);
        }
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace(';', ' ');
    }

    public static String groupLetter(int g) {
        return String.valueOf((char) ('A' + g));
    }

    public boolean finished() {
        return stage >= DONE || out;
    }

    public boolean knockout() {
        return stage >= QUARTER && stage <= FINAL;
    }

    public int champion() {
        return ko[14];
    }

    public String stageName() {
        if (stage < GROUP_ROUNDS) return "Grupos • rodada " + (stage + 1) + "/3";
        return switch (stage) {
            case QUARTER -> "Quartas de final";
            case SEMI -> "Semifinal";
            case FINAL -> "Final";
            default -> "Encerrado";
        };
    }

    // ------------------------------------------------------------ chave do mata-mata
    private static int slotA(int k) {
        return k < 4 ? 2 * k : k < 6 ? 8 + 2 * (k - 4) : 12;
    }

    private static int winSlot(int k) {
        return k < 4 ? 8 + k : k < 6 ? 12 + (k - 4) : 14;
    }

    private static int firstMatch(int stage) {
        return stage == QUARTER ? 0 : stage == SEMI ? 4 : 6;
    }

    private static int matchCount(int stage) {
        return stage == QUARTER ? 4 : stage == SEMI ? 2 : 1;
    }

    /** Jogo do mata-mata (0..6) em que o seu time está nessa fase, ou -1. */
    private int myMatch() {
        if (!knockout()) return -1;
        for (int k = firstMatch(stage); k < firstMatch(stage) + matchCount(stage); k++) {
            if (ko[slotA(k)] == 0 || ko[slotA(k) + 1] == 0) return k;
        }
        return -1;
    }

    /** Adversário do seu time no próximo jogo (-1 = nenhum). */
    public int opponent() {
        if (finished()) return -1;
        if (stage < GROUP_ROUNDS) {
            for (int[] m : ROUND[stage]) {
                if (m[0] == 0) return m[1];
                if (m[1] == 0) return m[0];
            }
            return -1;
        }
        int k = myMatch();
        if (k < 0) return -1;
        return ko[slotA(k)] == 0 ? ko[slotA(k) + 1] : ko[slotA(k)];
    }

    // ------------------------------------------------------------ resultados
    private void record(int x, int y, int sx, int sy) {
        gf[x] += sx; ga[x] += sy; gf[y] += sy; ga[y] += sx;
        played[x]++; played[y]++;
        if (sx > sy) pts[x] += 3;
        else if (sy > sx) pts[y] += 3;
        else { pts[x]++; pts[y]++; }
    }

    /** Placar sorteado de um jogo dos outros: futebol de 0 a 4 gols; vôlei até 12. */
    private int[] simulate(RandomSource rnd, boolean mustWin) {
        if (volley) {
            int lose = 3 + rnd.nextInt(8);
            return rnd.nextBoolean() ? new int[]{12, lose} : new int[]{lose, 12};
        }
        int[] goals = {0, 0, 1, 1, 1, 2, 2, 3, 4};
        return new int[]{goals[rnd.nextInt(goals.length)], goals[rnd.nextInt(goals.length)]};
    }

    /** Ordem do grupo: pontos, saldo, gols (ou pontos) pró. */
    public List<Integer> groupOrder(int g) {
        List<Integer> o = new ArrayList<>();
        for (int i = 0; i < 4; i++) o.add(g * 4 + i);
        o.sort((p, q) -> pts[p] != pts[q] ? pts[q] - pts[p]
                : (gf[q] - ga[q]) != (gf[p] - ga[p]) ? (gf[q] - ga[q]) - (gf[p] - ga[p]) : gf[q] != gf[p] ? gf[q] - gf[p] : p - q);
        return o;
    }

    /**
     * Resultado do jogo do seu time ("my" x "their"). Num empate do mata-mata, "tieWinner" diz quem passou
     * (0 = o seu time, 1 = o outro: pênaltis ou gol de ouro, decidido na partida). Simula os outros jogos da fase e
     * devolve as mensagens para anunciar.
     */
    public List<String> playerResult(int my, int their, int tieWinner, RandomSource rnd) {
        List<String> msgs = new ArrayList<>();
        int opp = opponent();
        if (opp < 0) return msgs;
        if (stage < GROUP_ROUNDS) {
            record(0, opp, my, their);
            myGroupGames[stage] = names[0] + " " + my + " x " + their + " " + names[opp];
            StringBuilder others = new StringBuilder("§7Outros jogos da rodada: ");
            int shown = 0;
            for (int g = 0; g < 4; g++) {
                for (int[] m : ROUND[stage]) {
                    int x = g * 4 + m[0], y = g * 4 + m[1];
                    if (x == 0 || y == 0) continue;
                    int[] s = simulate(rnd, false);
                    record(x, y, s[0], s[1]);
                    if (g == 0 || shown < 2) {
                        others.append(shown++ > 0 ? ", " : "").append("§f").append(names[x]).append(" ").append(s[0]).append(" x ")
                                .append(s[1]).append(" ").append(names[y]).append("§7");
                    }
                }
            }
            msgs.add("§7Os outros jogos da rodada já foram jogados: veja a tabela na tela (tecla da tabela, J).");
            stage++;
            if (stage == GROUP_ROUNDS) drawBracket(msgs, rnd);
            else {
                var order = groupOrder(0);
                msgs.add("§6Grupo A: §f" + (order.indexOf(0) + 1) + "º lugar com " + pts[0] + " pontos. §7Próximo: §e" + names[opponent()]);
            }
            return msgs;
        }
        // mata-mata
        int k = myMatch();
        boolean first = ko[slotA(k)] == 0;
        boolean iWon = my > their || (my == their && tieWinner == 0);
        String extra = my == their ? (tieWinner == 0 ? " §7(passou nos pênaltis)" : " §7(caiu nos pênaltis)") : "";
        koScore[k] = first ? my + " x " + their : their + " x " + my;
        if (my == their) koScore[k] += " (pên.)";
        ko[winSlot(k)] = iWon ? 0 : opp;
        String phase = stageName();
        playOthers(k, rnd, msgs);
        if (iWon) {
            if (stage == FINAL) {
                msgs.add("§6§l🏆 " + names[0].toUpperCase() + " É CAMPEÃO DA COPA! 🏆");
            } else {
                stage++;
                msgs.add("§a§lClassificado! §r§f" + names[0] + " passou de fase" + extra + "§f. " + stageName() + ": §e" + names[0] + " x " + names[opponent()]);
            }
            if (stage == FINAL && ko[14] == 0) stage = DONE;
        } else {
            out = true;
            msgs.add("§cEliminado na " + phase.toLowerCase() + "!" + extra + " §fO " + names[opp] + " segue na copa.");
            // o resto da copa é simulado para saber quem foi campeão
            while (stage < FINAL) {
                stage++;
                playOthers(-1, rnd, msgs);
            }
            msgs.add("§6🏆 Campeão da copa: §e" + names[ko[14]] + "§6!");
        }
        if (ko[14] >= 0) stage = DONE;
        return msgs;
    }

    /** Joga (sorteando) os jogos da fase atual que ainda não têm resultado, menos o "skip". */
    private void playOthers(int skip, RandomSource rnd, List<String> msgs) {
        for (int k = firstMatch(stage); k < firstMatch(stage) + matchCount(stage); k++) {
            if (k == skip || !koScore[k].isEmpty()) continue;
            int a = ko[slotA(k)], b = ko[slotA(k) + 1];
            if (a < 0 || b < 0) continue;
            int[] s = simulate(rnd, true);
            boolean aWins = s[0] > s[1] || (s[0] == s[1] && rnd.nextBoolean());
            koScore[k] = s[0] + " x " + s[1] + (s[0] == s[1] ? " (pên.)" : "");
            ko[winSlot(k)] = aWins ? a : b;
        }
    }

    /** Fim dos grupos: os 2 primeiros de cada grupo vão para as quartas (1º de um grupo pega o 2º de outro). */
    private void drawBracket(List<String> msgs, RandomSource rnd) {
        int[][] q = new int[4][];
        for (int g = 0; g < 4; g++) {
            var o = groupOrder(g);
            q[g] = new int[]{o.get(0), o.get(1)};
        }
        int[] order = {q[0][0], q[1][1], q[2][0], q[3][1], q[1][0], q[0][1], q[3][0], q[2][1]};
        System.arraycopy(order, 0, ko, 0, 8);
        StringBuilder sb = new StringBuilder("§6§lFim da fase de grupos! §r§fQuartas: ");
        for (int k = 0; k < 4; k++) sb.append(k > 0 ? "§7, §f" : "").append(names[ko[2 * k]]).append(" x ").append(names[ko[2 * k + 1]]);
        msgs.add(sb.toString());
        stage = QUARTER;
        if (myMatch() < 0) {
            out = true;
            var o = groupOrder(0);
            msgs.add("§cO " + names[0] + " ficou em " + (o.indexOf(0) + 1) + "º no grupo A e foi eliminado.");
            playOthers(-1, rnd, msgs);
            while (stage < FINAL) {
                stage++;
                playOthers(-1, rnd, msgs);
            }
            msgs.add("§6🏆 Campeão da copa: §e" + names[ko[14]] + "§6!");
            stage = DONE;
        } else {
            msgs.add("§a§lClassificado para as quartas! §r§fAdversário: §e" + names[opponent()]);
        }
    }

    // ------------------------------------------------------------ para a tela
    public String encode() {
        StringBuilder b = new StringBuilder();
        b.append(volley ? 1 : 0).append('\t').append(stage).append('\t').append(out ? 1 : 0).append('\n');
        for (int i = 0; i < 16; i++) {
            b.append(names[i]).append('\t').append(colors[i]).append('\t').append(pts[i]).append('\t').append(gf[i]).append('\t')
                    .append(ga[i]).append('\t').append(played[i]).append('\n');
        }
        for (int i = 0; i < 15; i++) b.append(i > 0 ? "\t" : "").append(ko[i]);
        b.append('\n').append(String.join("\t", koScore)).append('\n').append(String.join("\t", myGroupGames));
        return b.toString();
    }

    public static Cup decode(String s) {
        String[] lines = s.split("\n", -1);
        String[] h = lines[0].split("\t");
        Cup c = new Cup(h[0].equals("1"));
        c.stage = Integer.parseInt(h[1]);
        c.out = h[2].equals("1");
        for (int i = 0; i < 16; i++) {
            String[] t = lines[1 + i].split("\t", -1);
            c.names[i] = t[0];
            c.colors[i] = Integer.parseInt(t[1]);
            c.pts[i] = Integer.parseInt(t[2]);
            c.gf[i] = Integer.parseInt(t[3]);
            c.ga[i] = Integer.parseInt(t[4]);
            c.played[i] = Integer.parseInt(t[5]);
        }
        String[] k = lines[17].split("\t");
        for (int i = 0; i < 15; i++) c.ko[i] = Integer.parseInt(k[i]);
        String[] sc = lines[18].split("\t", -1);
        for (int i = 0; i < 7 && i < sc.length; i++) c.koScore[i] = sc[i];
        if (lines.length > 19) {
            String[] mg = lines[19].split("\t", -1);
            for (int i = 0; i < 3 && i < mg.length; i++) c.myGroupGames[i] = mg[i];
        }
        return c;
    }

    /** Mensagens curtas da tabela para o chat (quando não dá para abrir a tela). */
    public List<String> chatTable() {
        List<String> l = new ArrayList<>();
        l.add("§6§l🏆 Copa §r§7(" + stageName() + ")");
        for (int g = 0; g < 4; g++) {
            StringBuilder b = new StringBuilder("§eGrupo " + groupLetter(g) + ": ");
            int pos = 1;
            for (int i : groupOrder(g)) b.append(pos > 1 ? "§7, " : "").append("§f").append(pos++).append(". ").append(names[i]).append(" §7").append(pts[i]);
            l.add(b.toString());
        }
        return l;
    }
}
