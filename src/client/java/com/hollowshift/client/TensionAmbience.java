package com.hollowshift.client;

import com.hollowshift.HollowConfig;
import com.hollowshift.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A low drone that fades in as tension rises, and a heartbeat above 80.
 * Both drop out completely during a silence.
 */
public final class TensionAmbience {

    private static Drone drone;
    private static int nextBeat;

    private TensionAmbience() {}

    static void tick(Minecraft mc) {
        if (!HollowConfig.get().tensionAmbience || mc.player == null) return;
        float t = ClientState.tension01();

        if (t > 0.25f && (drone == null || !mc.getSoundManager().isActive(drone))) {
            drone = new Drone();
            mc.getSoundManager().play(drone);
        }

        if (t > 0.8f && !ClientState.isSilent() && --nextBeat <= 0) {
            float urgency = (t - 0.8f) / 0.2f; // 0..1
            nextBeat = (int) Mth.lerp(urgency, 30, 16);
            mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.HEARTBEAT.getLocation(), SoundSource.AMBIENT,
                    0.25f + 0.35f * urgency, 1.0f, RandomSource.create(), false, 0,
                    SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        }
    }

    private static final class Drone extends AbstractTickableSoundInstance {
        Drone() {
            super(ModSounds.DRONE, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !HollowConfig.get().tensionAmbience) {
                stop();
                return;
            }
            float t = ClientState.tension01();
            float target = ClientState.isSilent() ? 0f : Mth.clamp((t - 0.25f) / 0.65f, 0f, 1f) * 0.7f;
            volume = Math.max(0.001f, volume + (target - volume) * 0.04f);
            if (t < 0.15f && volume < 0.01f) stop();
        }
    }
}
