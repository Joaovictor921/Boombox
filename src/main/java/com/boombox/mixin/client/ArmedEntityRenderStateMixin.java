package com.boombox.mixin.client;

import com.boombox.BoomboxItem;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Quando um jogador segura a boombox, esconde o item "normal" da mão em terceira pessoa:
 * a HeldBoomboxLayer desenha ela ao lado da cabeça no lugar.
 */
@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin {
    @Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
    private static void boombox$hideHeldItem(LivingEntity entity, ArmedEntityRenderState state,
                                             ItemModelResolver resolver, float partialTick, CallbackInfo ci) {
        if (!(entity instanceof Player)) return;
        if (BoomboxItem.isBoombox(state.rightHandItemStack)) state.rightHandItemState.clear();
        if (BoomboxItem.isBoombox(state.leftHandItemStack)) state.leftHandItemState.clear();
        // pulseira de luz: o item some da mão e a BraceletLayer desenha a pulseira no pulso
        if (state.rightHandItemStack.is(com.boombox.bar.BarBlocks.GLOW_BRACELET)) state.rightHandItemState.clear();
        if (state.leftHandItemStack.is(com.boombox.bar.BarBlocks.GLOW_BRACELET)) state.leftHandItemState.clear();
    }
}
