package com.hollowshift.client;

import com.google.gson.JsonSyntaxException;
import com.hollowshift.HollowConfig;
import com.hollowshift.HollowShift;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

/**
 * Full-screen post-processing driven by tension: desaturation, crushed shadows,
 * a closing vignette, colour fringing at the edges above 75% tension, an unsteady
 * flicker, and a brief rot at the edges of vision on "peripheral_decay".
 *
 * The shader itself is assets/minecraft/shaders/program/hollowshift_tension.fsh.
 */
public final class TensionPostEffect {

    private static final ResourceLocation CHAIN = HollowShift.id("shaders/post/tension.json");

    private static PostChain chain;
    private static boolean failed;
    private static int width, height;

    private TensionPostEffect() {}

    public static void render(float partialTick) {
        HollowConfig cfg = HollowConfig.get();
        if (!cfg.postEffects || !IrisCompat.modEffectsAllowed()) return;

        float t = ClientState.tension01();
        float decay = ClientState.decay();
        if (t < 0.02f && decay < 0.01f) return;

        Minecraft mc = Minecraft.getInstance();
        if (chain == null && !failed) load(mc);
        if (chain == null) return;

        RenderTarget main = mc.getMainRenderTarget();
        if (main.width != width || main.height != height) {
            width = main.width;
            height = main.height;
            chain.resize(width, height);
        }

        boolean calm = cfg.photosensitiveMode;
        chain.setUniform("Tension", t);
        chain.setUniform("Decay", decay);
        chain.setUniform("HSTime", (ClientState.ticks() + partialTick) / 20f);
        chain.setUniform("Aberration", cfg.chromaticAberration && !calm ? 1f : 0f);
        chain.setUniform("Flicker", calm ? 0f : 1f);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(partialTick);
        main.bindWrite(true);
    }

    private static void load(Minecraft mc) {
        try {
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN);
            width = height = -1;
        } catch (IOException | JsonSyntaxException e) {
            failed = true;
            HollowShift.LOG.error("Couldn't load the tension shader; screen effects are off", e);
        }
    }

    /** Called on resource reload (F3+T, pack changes). */
    public static void reset() {
        if (chain != null) chain.close();
        chain = null;
        failed = false;
    }
}
