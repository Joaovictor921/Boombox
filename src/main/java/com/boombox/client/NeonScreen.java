package com.boombox.client;

import com.boombox.bar.NeonSignBlockEntity;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Tela do neon personalizado: texto, cor (ou arco-íris) e se pisca. */
public class NeonScreen extends Screen {
    private final BlockPos pos;
    private EditBox text;
    private int color;
    private boolean blink;

    public NeonScreen(BlockPos pos) {
        super(Component.literal("Neon personalizado"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        String start = "NEON";
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof NeonSignBlockEntity be) {
            start = be.text;
            color = be.color;
            blink = be.blink;
        }
        int cx = width / 2, top = height / 2 - 70;
        text = new EditBox(font, cx - 110, top + 20, 220, 20, Component.literal("Texto"));
        text.setMaxLength(NeonSignBlockEntity.MAX_LEN);
        text.setValue(start);
        addRenderableWidget(text);
        setInitialFocus(text);
        int n = NeonSignBlockEntity.COLORS.length + 1;
        for (int i = 0; i < n; i++) {
            int idx = i;
            int col = i % 7, row = i / 7;
            addRenderableWidget(Button.builder(Component.literal(i == NeonSignBlockEntity.RAINBOW ? "🌈" : "■"), b -> color = idx)
                    .bounds(cx - 110 + col * 32, top + 56 + row * 24, 28, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal(""), b -> blink = !blink).bounds(cx - 110, top + 108, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§aPronto"), b -> onClose()).bounds(cx + 4, top + 108, 106, 20).build());
    }

    @Override
    public void onClose() {
        ClientPlayNetworking.send(new Payloads.NeonEdit(pos, text.getValue(), color, blink));
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2, top = height / 2 - 70;
        g.fill(cx - 120, top - 8, cx + 120, top + 136, 0xE0101018);
        g.outline(cx - 120, top - 8, 240, 144, 0xFFFF4CF0);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, "§d§l✦ Neon personalizado ✦", cx, top, 0xFFFFFFFF);
        g.text(font, "§7Cor: §f" + (color >= NeonSignBlockEntity.RAINBOW ? "Arco-íris" : NeonSignBlockEntity.COLOR_NAMES[color]), cx - 110, top + 45, 0xFFFFFFFF, false);
        // cor de cada quadradinho por cima do botão
        for (int i = 0; i < NeonSignBlockEntity.COLORS.length; i++) {
            int col = i % 7, row = i / 7;
            int x = cx - 110 + col * 32 + 8, y = top + 56 + row * 24 + 5;
            g.fill(x, y, x + 12, y + 10, 0xFF000000 | NeonSignBlockEntity.COLORS[i]);
            if (i == color) g.outline(x - 3, y - 3, 18, 16, 0xFFFFFFFF);
        }
        g.centeredText(font, blink ? "§ePiscando: sim" : "§7Piscando: não", cx - 57, top + 114, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
