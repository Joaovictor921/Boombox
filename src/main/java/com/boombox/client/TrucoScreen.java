package com.boombox.client;

import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Mesa de truco contra um aldeão do bar do Seu Zé. Ganhou a partida (12 pontos)? Leva uma esmeralda. */
public class TrucoScreen extends Screen {
    private static final int W = 300, H = 200;
    private final int opponentId;
    private TrucoGame game = new TrucoGame();
    private int timer;
    private String lastState = "";
    private boolean reported;

    public TrucoScreen(int opponentId) {
        super(Component.literal("Truco"));
        this.opponentId = opponentId;
    }

    private String stateKey() {
        return game.phase + "|" + game.mine.size() + "|" + game.his.size() + "|" + game.myScore + "|" + game.hisScore
                + "|" + game.value + "|" + game.rounds.size() + "|" + game.message;
    }

    @Override
    protected void init() {
        int left = width / 2 - W / 2, top = height / 2 - H / 2;
        boolean myTurn = game.phase == TrucoGame.Phase.PLAYER_TURN;
        // suas cartas
        for (int i = 0; i < game.mine.size(); i++) {
            TrucoGame.Card c = game.mine.get(i);
            int idx = i;
            Button b = Button.builder(Component.literal(c.label()).withColor(c.red() ? 0xFF4040 : 0x202020),
                    bt -> game.playerPlays(idx)).bounds(left + 70 + i * 56, top + 150, 48, 34).build();
            b.active = myTurn;
            addRenderableWidget(b);
        }
        int bx = left + W - 84;
        if (game.phase == TrucoGame.Phase.PLAYER_ANSWER) {
            addRenderableWidget(Button.builder(Component.literal("Aceitar"), b -> game.playerAnswers(1)).bounds(bx, top + 92, 80, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Correr"), b -> game.playerAnswers(0)).bounds(bx, top + 112, 80, 18).build());
            int after = game.pendingRaise == 12 ? 0 : game.pendingRaise == 3 ? 6 : game.pendingRaise + 3;
            if (after != 0) {
                addRenderableWidget(Button.builder(Component.literal(TrucoGame.raiseName(after) + "!"), b -> game.playerAnswers(2))
                        .bounds(bx, top + 132, 80, 18).build());
            }
        } else if (game.phase == TrucoGame.Phase.GAME_OVER) {
            addRenderableWidget(Button.builder(Component.literal("Jogar de novo"), b -> {
                game = new TrucoGame();
                reported = false;
                rebuildWidgets();
            }).bounds(bx - 10, top + 112, 90, 20).build());
        } else {
            int next = game.nextRaise();
            Button t = Button.builder(Component.literal(next == 0 ? "—" : "§c" + TrucoGame.raiseName(next) + "!"), b -> game.playerRaises())
                    .bounds(bx, top + 112, 80, 20).build();
            t.active = myTurn && next != 0 && game.lastRaiser != 1;
            addRenderableWidget(t);
        }
        addRenderableWidget(Button.builder(Component.literal("Sair"), b -> onClose()).bounds(left + 4, top + H - 24, 40, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        timer++;
        if ((game.phase == TrucoGame.Phase.BOT_TURN && timer % 22 == 0) || (game.phase == TrucoGame.Phase.HAND_OVER && timer % 50 == 0)) {
            game.botStep();
        }
        if (game.phase == TrucoGame.Phase.GAME_OVER && !reported) {
            reported = true;
            ClientPlayNetworking.send(new Payloads.TrucoResult(opponentId, game.playerWon()));
        }
        String k = stateKey();
        if (!k.equals(lastState)) {
            lastState = k;
            rebuildWidgets();
        }
    }

    private void card(GuiGraphicsExtractor g, int x, int y, TrucoGame.Card c, String caption) {
        g.fill(x - 1, y - 1, x + 33, y + 45, 0xFF101010);
        g.fill(x, y, x + 32, y + 44, 0xFFF4F1E8);
        if (c != null) {
            int color = c.red() ? 0xFFD02020 : 0xFF202020;
            g.text(font, c.label(), x + 3, y + 3, color, false);
            g.centeredText(font, TrucoGame.SUITS[c.suit()], x + 16, y + 18, color);
        }
        if (caption != null) g.centeredText(font, caption, x + 16, y + 47, 0xFFDDDDDD);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = width / 2 - W / 2, top = height / 2 - H / 2;
        g.fill(left - 4, top - 4, left + W + 4, top + H + 4, 0xFF4A2E18);  // borda de madeira
        g.fill(left, top, left + W, top + H, 0xFF1E6B34);                 // feltro verde
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, "§l♣ Truco no bar do Seu Zé ♣", width / 2, top + 6, 0xFFFFFFFF);
        g.text(font, "Você §e" + game.myScore + "§f  x  §e" + game.hisScore + "§f Aldeão", left + 8, top + 20, 0xFFFFFFFF, false);
        g.text(font, "Mão vale: §e" + game.value, left + W - 80, top + 20, 0xFFFFFFFF, false);
        StringBuilder vazas = new StringBuilder("Vazas: ");
        for (int r : game.rounds) vazas.append(r > 0 ? "§aV " : r < 0 ? "§cA " : "§7E ");
        g.text(font, vazas.toString(), left + 8, top + 32, 0xFFFFFFFF, false);
        card(g, left + 14, top + 52, game.vira, "vira");
        card(g, left + 110, top + 46, game.hisPlayed, "aldeão");
        card(g, left + 160, top + 46, game.myPlayed, "você");
        // cartas viradas do aldeão
        for (int i = 0; i < game.his.size(); i++) {
            int x = left + 222 + i * 12;
            g.fill(x, top + 34, x + 14, top + 52, 0xFF101010);
            g.fill(x + 1, top + 35, x + 13, top + 51, 0xFF8A1C1C);
        }
        String msg = switch (game.phase) {
            case PLAYER_TURN -> "Sua vez: escolha uma carta";
            case BOT_TURN -> "O aldeão está pensando...";
            case PLAYER_ANSWER -> "Aceita, corre ou aumenta?";
            case HAND_OVER -> "";
            case GAME_OVER -> game.playerWon() ? "§aVocê ganhou a partida! Leva uma esmeralda." : "§cO aldeão ganhou a partida.";
        };
        g.centeredText(font, game.message, width / 2, top + 110, 0xFFFFE070);
        g.centeredText(font, msg, width / 2, top + 124, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
