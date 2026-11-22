package com.boombox.client;

import com.boombox.BoomboxMod;
import com.boombox.entity.Raver;
import com.boombox.entity.Sports;
import com.boombox.net.Payloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Botões do futebol e do vôlei. Enquanto você está num jogo, os botões do mouse viram chute/passe (futebol) ou
 * manchete/cortada (vôlei), e as teclas do esporte ficam ativas. Embaixo da tela aparecem as teclas de verdade
 * (as que estão configuradas no seu jogo). No futebol, segure o botão de chutar para carregar a força.
 */
public final class SportsClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(BoomboxMod.id("sports"));
    public static KeyMapping SLIDE, POKE, SET, LEAVE, TABLE;
    private static int sport;
    private static boolean league;
    /** Posição escolhida no futebol (0 = goleiro: a tecla de carrinho vira "pular na bola"). */
    private static int position = -1;
    private static long lastUse;
    private static long leaveAsked;
    /** Chute carregando: desde quando o botão está apertado (0 = não está). */
    private static long chargeStart;
    private static final long FULL_CHARGE_MS = 900;

    private SportsClient() {}

    public static void register() {
        SLIDE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.boombox.carrinho", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));
        POKE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.boombox.colocar_pe", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY));
        SET = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.boombox.levantar", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY));
        LEAVE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.boombox.sair_jogo", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));
        TABLE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.boombox.tabela", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.SportState.TYPE, (p, ctx) -> {
            sport = p.sport();
            league = p.league();
            position = p.position();
            chargeStart = 0;
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.CupState.TYPE, (p, ctx) -> {
            if (p.sport() == Sports.SINUCA) {
                var screen = new SinucaBracketScreen(p.data());
                ctx.client().execute(() -> ctx.client().gui.setScreen(screen));
                return;
            }
            var cup = com.boombox.entity.Cup.decode(p.data());
            ctx.client().execute(() -> ctx.client().gui.setScreen(new CupScreen(cup, p.sport())));
        });
        Raver.openSetupHook = (id, s) -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new MatchSetupScreen(id, s)));
        HudElementRegistry.addLast(BoomboxMod.id("sports_keys"), (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (sport == Sports.NONE || mc.player == null || mc.gui.hud.isHidden()) return;
            int w = g.guiWidth(), y = 30;
            for (String line : hints(mc)) {
                int lw = mc.font.width(line);
                g.fill(w / 2 - lw / 2 - 3, y - 2, w / 2 + lw / 2 + 3, y + 9, 0x80000000);
                g.centeredText(mc.font, line, w / 2, y, 0xFFFFFFFF);
                y += 11;
            }
            if (chargeStart > 0) {
                float c = charge();
                int bw = 80, x0 = w / 2 - bw / 2, by = g.guiHeight() / 2 + 20;
                g.fill(x0 - 1, by - 1, x0 + bw + 1, by + 5, 0xC0000000);
                int col = c < 0.5f ? 0xFF40E040 : c < 0.85f ? 0xFFF0D020 : 0xFFF04020;
                g.fill(x0, by, x0 + (int) (bw * c), by + 4, col);
                g.centeredText(mc.font, sport == Sports.VOLLEY ? "§fForça do corte" : "§fForça do chute", w / 2, by - 10, 0xFFFFFFFF);
            }
        });
    }

    private static String key(KeyMapping k) {
        return "§e[" + k.getTranslatedKeyMessage().getString() + "]§7";
    }

    /** Dicas com as teclas configuradas de verdade. */
    private static String[] hints(Minecraft mc) {
        String attack = key(mc.options.keyAttack), use = key(mc.options.keyUse), sneak = key(mc.options.keyShift);
        if (sport == Sports.FOOTBALL) {
            return new String[]{
                    "§7" + attack + " chutar (segure)  " + use + " passar",
                    position == 0 ? "§7" + key(SLIDE) + " pular na bola (para onde olha)  " + key(POKE) + " colocar o pé"
                            : "§7" + key(SLIDE) + " carrinho  " + key(POKE) + " colocar o pé",
                    "§7" + sneak + "+" + attack + " cavadinha  " + key(LEAVE) + "x2 sair"};
        }
        return new String[]{"§7" + attack + " cortar (segure)  " + use + " manchete  " + key(SET) + " levantar", "§7" + key(LEAVE) + "x2 sair"};
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return sport != Sports.NONE && mc.player != null && mc.gui.screen() == null;
    }

    public static int sport() {
        return sport;
    }

    public static boolean league() {
        return league;
    }

    public static void leave() {
        send(Sports.LEAVE);
    }

    public static void send(int action) {
        send(action, 0f);
    }

    public static void send(int action, float power) {
        ClientPlayNetworking.send(new Payloads.SportAction(action, power));
    }

    private static float charge() {
        return chargeStart == 0 ? 0 : Math.min(1f, (System.currentTimeMillis() - chargeStart) / (float) FULL_CHARGE_MS);
    }

    /** Clique do mouse durante o jogo (main = botão de atacar). No futebol o chute carrega enquanto segura. */
    public static void click(boolean main) {
        // o botão direito, segurado, repete o clique: um por vez
        if (!main) {
            long now = System.currentTimeMillis();
            if (now - lastUse < 350) return;
            lastUse = now;
        }
        if (main) {
            // esquerdo: chute (futebol) ou corte (vôlei), os dois com força carregando enquanto segura
            if (chargeStart == 0) chargeStart = System.currentTimeMillis();
        } else {
            send(sport == Sports.FOOTBALL ? Sports.FUT_PASS : Sports.VOL_BUMP);
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            sport = Sports.NONE;
            chargeStart = 0;
            return;
        }
        // soltou o botão (ou carregou tudo): chuta com a força acumulada
        if (chargeStart > 0 && (!mc.options.keyAttack.isDown() || System.currentTimeMillis() - chargeStart > FULL_CHARGE_MS + 600)) {
            send(sport == Sports.VOLLEY ? Sports.VOL_SPIKE : Sports.FUT_SHOOT, Math.max(0.05f, charge()));
            chargeStart = 0;
        }
        while (TABLE.consumeClick()) send(Sports.OPEN_TABLE);
        while (SLIDE.consumeClick()) if (sport == Sports.FOOTBALL) send(position == 0 ? Sports.GK_DIVE : Sports.FUT_SLIDE);
        while (POKE.consumeClick()) if (sport == Sports.FOOTBALL) send(Sports.FUT_POKE);
        while (SET.consumeClick()) if (sport == Sports.VOLLEY) send(Sports.VOL_SET);
        while (LEAVE.consumeClick()) {
            if (sport == Sports.NONE) continue;
            long now = System.currentTimeMillis();
            if (now - leaveAsked < 3000) {
                leave();
                leaveAsked = 0;
            } else {
                leaveAsked = now;
                mc.player.sendOverlayMessage(Component.literal(league
                        ? "§eAperte " + LEAVE.getTranslatedKeyMessage().getString() + " de novo para desistir do campeonato"
                        : "§eAperte " + LEAVE.getTranslatedKeyMessage().getString() + " de novo para sair do jogo"));
            }
        }
    }
}
