package com.boombox.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Truco paulista, 1 contra 1 (você contra um aldeão do bar). Baralho de 40 cartas (sem 8, 9 e 10).
 * Força: 4 &lt; 5 &lt; 6 &lt; 7 &lt; Q &lt; J &lt; K &lt; A &lt; 2 &lt; 3, e as manilhas (a carta seguinte à vira) ganham de todas,
 * na ordem ouros &lt; espadas &lt; copas &lt; paus. Cada mão vale 1; "Truco!" sobe para 3, depois 6, 9 e 12.
 * Quem fizer 12 pontos ganha.
 */
public final class TrucoGame {
    public static final String[] RANKS = {"4", "5", "6", "7", "Q", "J", "K", "A", "2", "3"};
    public static final String[] SUITS = {"♦", "♠", "♥", "♣"};
    public static final int TARGET = 12;
    private static final int[] RAISES = {1, 3, 6, 9, 12};

    /** Carta: rank 0..9 (índice em RANKS), naipe 0..3 (índice em SUITS). */
    public record Card(int rank, int suit) {
        public String label() {
            return RANKS[rank] + SUITS[suit];
        }

        public boolean red() {
            return suit == 0 || suit == 2;
        }
    }

    public enum Phase { PLAYER_TURN, BOT_TURN, PLAYER_ANSWER, HAND_OVER, GAME_OVER }

    private final Random rnd = new Random();
    public final List<Card> mine = new ArrayList<>(), his = new ArrayList<>();
    public Card vira;
    public Card myPlayed, hisPlayed;
    /** Resultado de cada vaza da mão: 1 = você, -1 = ele, 0 = empatou. */
    public final List<Integer> rounds = new ArrayList<>();
    public int myScore, hisScore;
    /** Quanto a mão vale agora e quem pediu o último aumento (1 = você, -1 = ele, 0 = ninguém). */
    public int value = 1, lastRaiser;
    /** Pedido de truco esperando resposta: valor proposto. */
    public int pendingRaise;
    public Phase phase;
    /** De quem era a vez quando alguém pediu truco (o jogo volta para ela depois da resposta). */
    private Phase resume = Phase.PLAYER_TURN;
    public String message = "";
    private boolean playerStartsHand = true;
    private boolean playerLeads;

    public TrucoGame() {
        newHand();
    }

    // ------------------------------------------------------------ regras

    private int power(Card c) {
        int manilha = (vira.rank + 1) % RANKS.length;
        if (c.rank == manilha) return 100 + c.suit;
        return c.rank;
    }

    private void newHand() {
        List<Card> deck = new ArrayList<>();
        for (int r = 0; r < RANKS.length; r++) for (int s = 0; s < SUITS.length; s++) deck.add(new Card(r, s));
        Collections.shuffle(deck, rnd);
        mine.clear();
        his.clear();
        for (int i = 0; i < 3; i++) {
            mine.add(deck.remove(0));
            his.add(deck.remove(0));
        }
        vira = deck.remove(0);
        rounds.clear();
        myPlayed = hisPlayed = null;
        value = 1;
        lastRaiser = 0;
        pendingRaise = 0;
        playerLeads = playerStartsHand;
        playerStartsHand = !playerStartsHand;
        phase = playerLeads ? Phase.PLAYER_TURN : Phase.BOT_TURN;
        message = "Vira: " + vira.label() + "  (manilha: " + RANKS[(vira.rank + 1) % RANKS.length] + ")";
    }

    /** Próximo valor de aposta depois do atual (0 se já está em 12). */
    public int nextRaise() {
        for (int r : RAISES) if (r > value) return r;
        return 0;
    }

    public static String raiseName(int v) {
        return switch (v) {
            case 3 -> "TRUCO";
            case 6 -> "SEIS";
            case 9 -> "NOVE";
            default -> "DOZE";
        };
    }

    // ------------------------------------------------------------ ações do jogador

    public void playerPlays(int index) {
        if (phase != Phase.PLAYER_TURN || index < 0 || index >= mine.size()) return;
        myPlayed = mine.remove(index);
        if (hisPlayed == null) {
            phase = Phase.BOT_TURN;
        } else {
            finishRound();
        }
    }

    /** Você pede truco (ou aumenta). */
    public void playerRaises() {
        int next = nextRaise();
        if (phase != Phase.PLAYER_TURN || next == 0 || lastRaiser == 1) return;
        // o aldeão decide: aceita, corre ou aumenta
        double strength = handStrength(his);
        if (strength < 0.35 && rnd.nextDouble() < 0.7) {
            message = "Aldeão correu! Você ganhou " + value + " ponto(s).";
            endHand(1, value);
            return;
        }
        value = next;
        lastRaiser = 1;
        int again = nextRaise();
        if (again != 0 && strength > 0.75 && rnd.nextDouble() < 0.5) {
            pendingRaise = again;
            lastRaiser = -1;
            resume = Phase.PLAYER_TURN;
            phase = Phase.PLAYER_ANSWER;
            message = "Aldeão: " + raiseName(again) + "!!!";
            return;
        }
        message = "Aldeão aceitou! A mão vale " + value + ".";
    }

    /** Resposta a um pedido do aldeão: 0 = correr, 1 = aceitar, 2 = aumentar. */
    public void playerAnswers(int answer) {
        if (phase != Phase.PLAYER_ANSWER) return;
        if (answer == 0) {
            message = "Você correu. Aldeão ganhou " + value + " ponto(s).";
            endHand(-1, value);
            return;
        }
        value = pendingRaise;
        pendingRaise = 0;
        if (answer == 2 && nextRaise() != 0) {
            int next = nextRaise();
            if (handStrength(his) < 0.55) {
                message = "Aldeão correu do " + raiseName(next) + "! Você ganhou " + value + ".";
                endHand(1, value);
                return;
            }
            value = next;
            lastRaiser = 1;
            message = "Aldeão aceitou o " + raiseName(next) + "! A mão vale " + value + ".";
        } else {
            message = "Você aceitou. A mão vale " + value + ".";
        }
        phase = resume;
    }

    /** Chamado pela tela de tempos em tempos: o aldeão joga (com um atraso para dar tempo de ver). */
    public void botStep() {
        if (phase == Phase.HAND_OVER) {
            if (myScore >= TARGET || hisScore >= TARGET) phase = Phase.GAME_OVER;
            else newHand();
            return;
        }
        if (phase != Phase.BOT_TURN) return;
        // às vezes pede truco antes de jogar
        int next = nextRaise();
        if (next != 0 && lastRaiser != -1 && hisPlayed == null) {
            double s = handStrength(his);
            if (s > 0.7 && rnd.nextDouble() < 0.45 || rnd.nextDouble() < 0.06) { // às vezes é blefe
                pendingRaise = next;
                lastRaiser = -1;
                resume = Phase.BOT_TURN;
                phase = Phase.PLAYER_ANSWER;
                message = "Aldeão: " + raiseName(next) + "!!!";
                return;
            }
        }
        hisPlayed = his.remove(chooseCard());
        if (myPlayed == null) phase = Phase.PLAYER_TURN;
        else finishRound();
    }

    private int chooseCard() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < his.size(); i++) order.add(i);
        order.sort((a, b) -> Integer.compare(power(his.get(a)), power(his.get(b))));
        if (myPlayed != null) {
            // responde: a menor carta que ganha; se nenhuma ganha, joga a menor
            for (int i : order) if (power(his.get(i)) > power(myPlayed)) return i;
            return order.get(0);
        }
        // sai jogando: a do meio (guarda a mais forte)
        return order.get(order.size() / 2);
    }

    private double handStrength(List<Card> hand) {
        if (hand.isEmpty()) return 0.5;
        double sum = 0;
        for (Card c : hand) sum += Math.min(1.0, power(c) >= 100 ? 1.0 : c.rank / 9.0);
        int won = 0;
        for (int r : rounds) won += r;
        return Math.max(0, Math.min(1, sum / hand.size() + (won < 0 ? 0.25 : won > 0 ? -0.25 : 0)));
    }

    private void finishRound() {
        int a = power(myPlayed), b = power(hisPlayed);
        int result = Integer.compare(a, b);
        rounds.add(result);
        message = result > 0 ? "Você ganhou a vaza com " + myPlayed.label()
                : result < 0 ? "Aldeão ganhou a vaza com " + hisPlayed.label() : "Empatou (cangou)!";
        int winner = handWinner();
        if (winner != 0 || rounds.size() == 3) {
            if (winner == 0) {
                message += " Ninguém pontua.";
                endHand(0, 0);
            } else {
                message += winner > 0 ? " — Você ganhou a mão (+" + value + ")" : " — Aldeão ganhou a mão (+" + value + ")";
                endHand(winner, value);
            }
            return;
        }
        // quem ganhou a vaza começa a próxima (empate: quem saiu antes)
        if (result != 0) playerLeads = result > 0;
        myPlayed = hisPlayed = null;
        phase = playerLeads ? Phase.PLAYER_TURN : Phase.BOT_TURN;
    }

    /** 1 = você ganhou a mão, -1 = ele, 0 = ainda não decidiu (ou empatou tudo). */
    private int handWinner() {
        int me = 0, him = 0;
        for (int r : rounds) {
            if (r > 0) me++;
            if (r < 0) him++;
        }
        if (me >= 2) return 1;
        if (him >= 2) return -1;
        if (rounds.size() >= 2 && rounds.get(0) == 0) return Integer.signum(rounds.get(1)); // 1ª empatada: vale a 2ª
        if (rounds.size() >= 2 && rounds.get(1) == 0 && rounds.get(0) != 0) return rounds.get(0); // 2ª empatada: vale a 1ª
        if (rounds.size() == 3) {
            if (rounds.get(2) != 0) return rounds.get(2);
            for (int r : rounds) if (r != 0) return r;
        }
        return 0;
    }

    private void endHand(int winner, int points) {
        if (winner > 0) myScore += points;
        if (winner < 0) hisScore += points;
        phase = Phase.HAND_OVER;
    }

    public boolean playerWon() {
        return myScore >= TARGET;
    }
}
