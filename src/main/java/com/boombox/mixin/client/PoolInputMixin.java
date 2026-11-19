package com.boombox.mixin.client;

import com.boombox.client.PoolClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Na sinuca o boneco fica parado (só o agachar passa, para sair da mesa). */
@Mixin(KeyboardInput.class)
public abstract class PoolInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void boombox$freeze(CallbackInfo ci) {
        if (!PoolClient.active()) return;
        boolean sneak = keyPresses.shift();
        keyPresses = new Input(false, false, false, false, false, sneak, false);
        moveVector = Vec2.ZERO;
    }
}
