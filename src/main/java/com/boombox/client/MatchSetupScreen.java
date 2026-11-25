package com.boombox.client;

import com.boombox.entity.Sports;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tela do organizador (futebol ou vôlei): amistoso (times sorteados) ou campeonato (você escolhe o nome e a cor do
 * seu time). No futebol você também escolhe a sua posição. Mostra as teclas de verdade de cada esporte, o botão de
 * sair (quando já está jogando) e o de escolher a música.
 */
public class MatchSetupScreen extends Screen {
    private final int organizer, sport;
    private boolean league;
    private EditBox name;
    private int color;
    private int position = 6; // atacante

    public MatchSetupScreen(int organizer, int sport) {
        super(Component.literal(sport == Sports.FOOTBALL ? "Futebol de Várzea" : "Vôlei de Praia"));
        this.organizer = organizer;
        this.sport = sport;
    }

    /** Altura (a partir do topo) de cada parte da tela. */
    private int positionsY(int top) {
        return league ? top + 140 : top + 62;
    }

    private int keysY(int top) {
        int y = sport == Sports.FOOTBALL ? positionsY(top) + 62 : (league ? top + 140 : top + 62);
        return y;
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2, top = height / 2 - 125;
        addRenderableWidget(Button.builder(Component.literal((league ? "" : "§e▶ ") + "Amistoso"), b -> { league = false; init(); })
                .bounds(cx - 120, top + 22, 116, 20).build());
        addRenderableWidget(Button.builder(Component.literal((league ? "§e▶ " : "") + "Campeonato"), b -> { league = true; init(); })
                .bounds(cx + 4, top + 22, 116, 20).build());
        if (league) {
            String old = name == null ? "" : name.getValue();
            name = new EditBox(font, cx - 120, top + 62, 240, 20, Component.literal("Nome do time"));
            name.setMaxLength(24);
            name.setValue(old);
            name.setHint(Component.literal("§7Nome do seu time"));
            addRenderableWidget(name);
            for (int i = 0; i < Sports.COLORS.length; i++) {
                int idx = i;
                addRenderableWidget(Button.builder(Component.literal(""), b -> color = idx)
                        .bounds(cx - 120 + (i % 6) * 40, top + 98 + (i / 6) * 20, 36, 18).build());
            }
        }
        if (sport == Sports.FOOTBALL) {
            int py = positionsY(top) + 12;
            for (int i = 0; i < Sports.POSITIONS.length; i++) {
                int idx = i, col = i < 4 ? i : i - 4, row = i < 4 ? 0 : 1;
                int bw = 58, x = cx - 120 + col * 61 + (row == 1 ? 30 : 0);
                addRenderableWidget(Button.builder(Component.literal((position == i ? "§e" : "§f") + Sports.POSITIONS[i]), b -> { position = idx; init(); })
                        .bounds(x, py + row * 22, bw, 20).build());
            }
        }
        int by = keysY(top) + 26;
        if (SportsClient.sport() == sport) {
            addRenderableWidget(Button.builder(Component.literal(SportsClient.league() ? "§c§lDesistir do campeonato" : "§c§lSair do amistoso"), b -> {
                SportsClient.leave();
                onClose();
            }).bounds(cx - 120, by, 116, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("§a§lJogar!"), b -> start()).bounds(cx - 120, by, 116, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Agora não"), b -> onClose()).bounds(cx + 4, by, 116, 20).build());
        boolean playing = SportsClient.sport() == sport;
        int row = by + 24;
        if (!playing) {
            // 3.5.5: jogar no mesmo time de quem já está em campo; 3.7: ou no time adversário (jogador contra jogador)
            addRenderableWidget(Button.builder(Component.literal("§bEntrar no time do amigo"), b -> {
                ClientPlayNetworking.send(new Payloads.MatchSetup(organizer, Sports.JUNTAR, "", 0, position));
                onClose();
            }).bounds(cx - 120, row, 116, 20).build());
            addRenderableWidget(Button.builder(Component.literal("§cJogar contra o amigo"), b -> {
                ClientPlayNetworking.send(new Payloads.MatchSetup(organizer, Sports.CONTRA, "", 0, position));
                onClose();
            }).bounds(cx + 4, row, 116, 20).build());
            row += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("§d♪ Música"), b -> minecraft.gui.setScreen(DjRequestScreen.organizer(organizer)))
                .bounds(cx - 120, row, 116, 20).build());
        // 3.6: grupos e chaveamento da copa
        addRenderableWidget(Button.builder(Component.literal("§6🏆 Tabela da copa"), b -> {
            ClientPlayNetworking.send(new Payloads.MatchSetup(organizer, Sports.TABELA, "", 0, position));
        }).bounds(cx + 4, row, 116, 20).build());
        if (sport == Sports.FOOTBALL) {
            // 3.8: Liga dos Amigos (cada um monta seu time) e apostas nos jogos da copa
            row += 24;
            addRenderableWidget(Button.builder(Component.literal("§b★ Liga dos amigos"), b -> Client38.send(com.boombox.server.Features38.A_LEAGUE_OPEN, ""))
                    .bounds(cx - 120, row, 116, 20).build());
            addRenderableWidget(Button.builder(Component.literal("§e$ Apostar"), b -> Client38.send(com.boombox.server.Features38.A_BET_OPEN, Integer.toString(organizer)))
                    .bounds(cx + 4, row, 116, 20).build());
            if (!playing) {
                // 3.9: clássico contra a vila rival e treino de cobrança
                row += 24;
                addRenderableWidget(Button.builder(Component.literal("§c⚔ Clássico"), b -> {
                    ClientPlayNetworking.send(new Payloads.MatchSetup(organizer, Sports.CLASSICO, "", 0, position));
                    onClose();
                }).bounds(cx - 120, row, 116, 20).build());
                addRenderableWidget(Button.builder(Component.literal("§b◎ Treino de cobrança"), b -> {
                    ClientPlayNetworking.send(new Payloads.MatchSetup(organizer, Sports.TREINO, "", 0, position));
                    onClose();
                }).bounds(cx + 4, row, 116, 20).build());
            }
        }
    }

    private void start() {
        ClientPlayNetworking.send(new Payloads.MatchSetup(organizer, league ? Sports.CAMPEONATO : Sports.AMISTOSO,
                league && name != null ? name.getValue() : "", Sports.COLORS[color], position));
        onClose();
    }

    private static String k(KeyMapping key) {
        return "§e[" + key.getTranslatedKeyMessage().getString() + "]§7";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2, top = height / 2 - 125;
        int bottom = keysY(top) + 26 + (sport == Sports.FOOTBALL ? (SportsClient.sport() == sport ? 98 : 122) : 74);
        g.fill(cx - 130, top - 8, cx + 130, bottom, 0xE0101820);
        g.outline(cx - 130, top - 8, 260, bottom - top + 8, sport == Sports.FOOTBALL ? 0xFF3CB043 : 0xFFF0C040);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, (sport == Sports.FOOTBALL ? "§a§l⚽ " : "§e§l🏐 ") + getTitle().getString(), cx, top, 0xFFFFFFFF);
        g.centeredText(font, league ? "§7Copa: 4 grupos, depois quartas, semifinal e final"
                : "§7Um jogo contra um time sorteado (cores diferentes)", cx, top + 46, 0xFFFFFFFF);
        if (league) g.text(font, "§fCor do time: §e" + Sports.COLOR_NAMES[color], cx - 120, top + 87, 0xFFFFFFFF, false);
        if (league) {
            for (int i = 0; i < Sports.COLORS.length; i++) {
                int x = cx - 120 + (i % 6) * 40 + 4, y = top + 98 + (i / 6) * 20 + 3;
                g.fill(x, y, x + 28, y + 12, 0xFF000000 | Sports.COLORS[i]);
                if (i == color) g.outline(x - 3, y - 3, 34, 18, 0xFFFFFFFF);
            }
        }
        if (sport == Sports.FOOTBALL) {
            g.text(font, "§fSua posição: §e" + Sports.POSITIONS[position] + (position == 0 ? " §7(" + k(SportsClient.SLIDE) + " pula na bola)" : ""),
                    cx - 120, positionsY(top), 0xFFFFFFFF, false);
        }
        var o = minecraft.options;
        int ky = keysY(top);
        if (sport == Sports.FOOTBALL) {
            g.centeredText(font, "§7" + k(o.keyAttack) + " chutar  " + k(o.keyUse) + " passar  " + k(SportsClient.SLIDE) + " carrinho  "
                    + k(SportsClient.POKE) + " pé", cx, ky, 0xFFFFFFFF);
            g.centeredText(font, "§7Passe por cima da bola para dominar • " + k(SportsClient.LEAVE) + "x2 sai", cx, ky + 11, 0xFFFFFFFF);
        } else {
            g.centeredText(font, "§7" + k(o.keyAttack) + " cortar (segure)  " + k(o.keyUse) + " manchete  " + k(SportsClient.SET) + " levantar",
                    cx, ky, 0xFFFFFFFF);
            g.centeredText(font, "§7Até 3 toques por lado, um de cada • " + k(SportsClient.LEAVE) + "x2 sai", cx, ky + 11, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
