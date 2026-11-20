package com.boombox.client;

import com.boombox.BoomboxMod;
import com.boombox.entity.Raver;
import com.boombox.entity.Sports;
import com.boombox.net.Payloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * 3.5: botões do futebol e do vôlei. Enquanto você está num jogo, o clique esquerdo e o direito viram chute/passe
 * (futebol) ou manchete/cortada (vôlei), e as teclas R (carrinho), G (colocar o pé) e V (levantar) ficam ativas.
 * As teclas dá para trocar em Opções → Controles → Boombox.
 */
public final class SportsClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(BoomboxMod.id("sports"));
    public static KeyMapping SLIDE, POKE, SET;
    private static int sport;

    private SportsClient() {}

    public static void register() {
        SLIDE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.boombox.carrinho", InputConstants.Type.KEYBOARD, 82, CATEGORY));
        POKE = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.boombox.colocar_pe", InputConstants.Type.KEYBOARD, 71, CATEGORY));
        SET = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.boombox.levantar", InputConstants.Type.KEYBOARD, 86, CATEGORY));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.SportState.TYPE, (p, ctx) -> sport = p.sport());
        Raver.openSetupHook = (id, s) -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new MatchSetupScreen(id, s)));
    }

    /** Está num jogo (os cliques são do esporte)? */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return sport != Sports.NONE && mc.player != null && mc.gui.screen() == null;
    }

    public static int sport() {
        return sport;
    }

    public static void send(int action) {
        ClientPlayNetworking.send(new Payloads.SportAction(action));
    }

    /** Clique do mouse durante o jogo (main = esquerdo). */
    public static void click(boolean main) {
        if (sport == Sports.FOOTBALL) send(main ? Sports.FUT_SHOOT : Sports.FUT_PASS);
        else if (sport == Sports.VOLLEY) send(main ? Sports.VOL_BUMP : Sports.VOL_SPIKE);
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            sport = Sports.NONE;
            return;
        }
        while (SLIDE.consumeClick()) if (sport == Sports.FOOTBALL) send(Sports.FUT_SLIDE);
        while (POKE.consumeClick()) if (sport == Sports.FOOTBALL) send(Sports.FUT_POKE);
        while (SET.consumeClick()) if (sport == Sports.VOLLEY) send(Sports.VOL_SET);
    }
}
