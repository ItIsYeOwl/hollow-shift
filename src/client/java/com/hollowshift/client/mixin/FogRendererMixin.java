package com.hollowshift.client.mixin;

import com.hollowshift.client.TensionFog;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tension pulls the fog in from the edge of render distance to under 20 blocks,
 * and turns it from sky-blue into charcoal.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void hollowshift$fogColor(Camera camera, float partialTicks, ClientLevel level,
                                             int renderDistance, float bossColorModifier, CallbackInfo ci) {
        float t = TensionFog.amount(camera);
        if (t <= 0f) return;
        float k = t * 0.85f;
        fogRed = Mth.lerp(k, fogRed, TensionFog.R);
        fogGreen = Mth.lerp(k, fogGreen, TensionFog.G);
        fogBlue = Mth.lerp(k, fogBlue, TensionFog.B);
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0f);
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void hollowshift$fogDistance(Camera camera, FogRenderer.FogMode fogMode, float farPlaneDistance,
                                                boolean shouldCreateFog, float partialTick, CallbackInfo ci) {
        float t = TensionFog.amount(camera);
        if (t <= 0f) return;
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Math.min(start, Mth.lerp(t, start, TensionFog.MIN_START)));
        RenderSystem.setShaderFogEnd(Math.min(end, Mth.lerp(t, end, TensionFog.MIN_END)));
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
    }
}
