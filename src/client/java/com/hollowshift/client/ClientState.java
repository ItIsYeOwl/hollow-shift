package com.hollowshift.client;

import com.hollowshift.net.ModPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

/** What the client knows: smoothed tension, and any one-off cues in progress. */
public final class ClientState {

    private static float targetTension;
    private static float tension;          // 0..100, smoothed
    private static int silenceTicks;
    private static float decay;            // 0..1, fades out
    private static int savingTicks;
    private static GenericMessageScreen fakeSaveScreen;
    private static long ticks;

    private static final SoundSource[] SILENCED = {
            SoundSource.AMBIENT, SoundSource.MUSIC, SoundSource.NEUTRAL,
            SoundSource.HOSTILE, SoundSource.WEATHER, SoundSource.RECORDS
    };

    private ClientState() {}

    public static float tension01() {
        return tension / 100f;
    }

    public static boolean isSilent() {
        return silenceTicks > 0;
    }

    public static float decay() {
        return decay;
    }

    public static long ticks() {
        return ticks;
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.TensionSync.TYPE,
                (payload, context) -> context.client().execute(() -> targetTension = payload.tension()));

        ClientPlayNetworking.registerGlobalReceiver(ModPayloads.Cue.TYPE,
                (payload, context) -> context.client().execute(() -> cue(context.client(), payload.cue(), payload.ticks())));

        ClientTickEvents.END_CLIENT_TICK.register(ClientState::tick);
    }

    private static void cue(Minecraft mc, String cue, int t) {
        switch (cue) {
            case "silence" -> {
                silenceTicks = t;
                mc.getMusicManager().stopPlaying();
            }
            case "decay" -> decay = 1f;
            case "saving" -> {
                if (mc.screen == null) {
                    fakeSaveScreen = new GenericMessageScreen(Component.translatable("menu.savingLevel"));
                    mc.setScreen(fakeSaveScreen);
                    savingTicks = t;
                }
            }
            default -> { }
        }
    }

    private static void tick(Minecraft mc) {
        ticks++;
        if (mc.level == null) {
            tension = targetTension = 0;
            silenceTicks = 0;
            return;
        }

        tension += (targetTension - tension) * 0.05f;
        decay = Math.max(0f, decay - 0.035f);

        if (silenceTicks > 0) {
            silenceTicks--;
            for (SoundSource s : SILENCED) mc.getSoundManager().stop(null, s);
        }

        if (savingTicks > 0 && --savingTicks == 0 && mc.screen == fakeSaveScreen) {
            mc.setScreen(null);
            fakeSaveScreen = null;
        }

        TensionAmbience.tick(mc);
    }
}
