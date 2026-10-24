package com.hollowshift.client.mixin;

import com.hollowshift.client.TensionPostEffect;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the tension screen effect right after the world is drawn, before the HUD. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER))
    private void hollowshift$tensionEffect(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        TensionPostEffect.render(deltaTracker.getGameTimeDeltaTicks());
    }
}
