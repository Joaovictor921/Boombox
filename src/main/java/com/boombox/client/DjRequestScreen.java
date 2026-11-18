package com.boombox.client;

import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Pedir música pro DJ Leo: pesquisa no YouTube (ou cola um link) e paga 1 barra de ouro. */
public class DjRequestScreen extends Screen {
    private static final int W = 300;
    private final int djId;
    private EditBox input;
    private String text = "";
    private List<YouTube.Result> results = List.of();
    private String status = "Escolha a música e o DJ Leo toca na hora (custa 1 barra de ouro)";
    private boolean busy;

    public DjRequestScreen(int djId) {
        super(Component.literal("Pedir música pro DJ Leo"));
        this.djId = djId;
    }

    @Override
    protected void init() {
        int left = width / 2 - W / 2;
        input = new EditBox(font, left, 34, W - 128, 20, Component.literal("Música"));
        input.setMaxLength(2048);
        input.setHint(Component.literal("Nome da música ou link do YouTube"));
        input.setValue(text);
        input.setResponder(s -> text = s);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Pesquisar"), b -> go()).bounds(left + W - 124, 34, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> onClose()).bounds(left + W - 60, 34, 60, 20).build());
        for (int i = 0; i < results.size() && i < 7; i++) {
            YouTube.Result r = results.get(i);
            String d = r.durationSec() > 0 ? "  §7" + YouTube.formatDuration(r.durationSec()) : "";
            String label = r.title();
            while (label.length() > 1 && font.width(label + "...") > W - 60) label = label.substring(0, label.length() - 1);
            if (!label.equals(r.title())) label += "...";
            addRenderableWidget(Button.builder(Component.literal(label + d), b -> request(r.url(), r.title(), r.durationSec()))
                    .bounds(left, 62 + i * 22, W, 20).build());
        }
    }

    /** Link: pede direto. Texto: pesquisa. */
    private void go() {
        String q = text.trim();
        if (q.isEmpty() || busy) return;
        if (q.startsWith("http://") || q.startsWith("https://")) {
            request(q, q, 0);
            return;
        }
        busy = true;
        status = "Pesquisando...";
        Tools.EXEC.submit(() -> {
            try {
                List<YouTube.Result> found = YouTube.search(q, 7);
                Minecraft.getInstance().execute(() -> {
                    busy = false;
                    results = found;
                    status = found.isEmpty() ? "Nada encontrado" : "Clique na música para pedir";
                    rebuildWidgets();
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    busy = false;
                    status = "§cErro na pesquisa: " + e.getMessage();
                });
            }
        });
    }

    private void request(String url, String title, long duration) {
        ClientPlayNetworking.send(new Payloads.DjRequest(djId, url, title, duration));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, "§l♪ Pedir música pro DJ Leo ♪", width / 2, 10, 0xFFFFFFFF);
        g.centeredText(font, status, width / 2, 22, 0xFFFFDD55);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (input != null && input.isFocused() && (event.key() == 257 || event.key() == 335)) {
            go();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
