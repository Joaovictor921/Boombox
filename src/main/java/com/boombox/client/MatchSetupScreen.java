package com.boombox.client;

import com.boombox.entity.Sports;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tela do organizador (futebol ou vôlei): amistoso (times sorteados) ou campeonato (você escolhe o nome e a cor do
 * seu time). Também lembra os botões de cada esporte.
 */
public class MatchSetupScreen extends Screen {
    private final int organizer, sport;
    private boolean league;
    private EditBox name;
    private int color;

    public MatchSetupScreen(int organizer, int sport) {
        super(Component.literal(sport == Sports.FOOTBALL ? "Futebol de Várzea" : "Vôlei de Praia"));
        this.organizer = organizer;
        this.sport = sport;
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2, top = height / 2 - 95;
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
                        .bounds(cx - 120 + (i % 6) * 40, top + 100 + (i / 6) * 22, 36, 18).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("§a§lJogar!"), b -> start()).bounds(cx - 120, top + 170, 116, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Agora não"), b -> onClose()).bounds(cx + 4, top + 170, 116, 20).build());
    }

    private void start() {
        ClientPlayNetworking.send(new Payloads.MatchSetup(organizer, league ? Sports.CAMPEONATO : Sports.AMISTOSO,
                league && name != null ? name.getValue() : "", Sports.COLORS[color]));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2, top = height / 2 - 95;
        g.fill(cx - 130, top - 8, cx + 130, top + 232, 0xE0101820);
        g.outline(cx - 130, top - 8, 260, 240, sport == Sports.FOOTBALL ? 0xFF3CB043 : 0xFFF0C040);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, (sport == Sports.FOOTBALL ? "§a§l⚽ " : "§e§l🏐 ") + getTitle().getString(), cx, top, 0xFFFFFFFF);
        g.centeredText(font, league ? "§73 jogos contra times de outras vilas, e um troféu para o campeão"
                : "§7Um jogo contra um time sorteado (cores diferentes)", cx, top + 46, 0xFFFFFFFF);
        if (league) {
            g.text(font, "§fCor do time: §e" + Sports.COLOR_NAMES[color], cx - 120, top + 88, 0xFFFFFFFF, false);
            for (int i = 0; i < Sports.COLORS.length; i++) {
                int x = cx - 120 + (i % 6) * 40 + 4, y = top + 100 + (i / 6) * 22 + 3;
                g.fill(x, y, x + 28, y + 12, 0xFF000000 | Sports.COLORS[i]);
                if (i == color) g.outline(x - 3, y - 3, 34, 18, 0xFFFFFFFF);
            }
        }
        String keys = sport == Sports.FOOTBALL
                ? "§7Esq: §fchutar §7• Dir: §fpassar §7• R: §fcarrinho §7• G: §fcolocar o pé"
                : "§7Esq: §fmanchete §7• V: §flevantar §7• Dir: §fcortar";
        g.centeredText(font, keys, cx, top + 150, 0xFFFFFFFF);
        g.centeredText(font, sport == Sports.FOOTBALL ? "§7Passe por cima da bola para dominar" : "§7Até 3 toques por lado, um de cada",
                cx, top + 160, 0xFFFFFFFF);
        g.centeredText(font, "§8(teclas em Opções → Controles → Boombox)", cx, top + 198, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
