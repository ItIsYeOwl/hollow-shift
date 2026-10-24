package com.hollowshift.client;

import com.hollowshift.HollowConfig;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;

/** How strongly tension should thicken and darken the fog right now (0 = not at all). */
public final class TensionFog {

    /** Fog closes in to this distance at full tension (blocks). */
    public static final float MIN_END = 18f;
    public static final float MIN_START = 2f;
    /** The colour the fog drifts toward: a deep, suffocating charcoal. */
    public static final float R = 0.050f, G = 0.052f, B = 0.060f;

    private TensionFog() {}

    public static float amount(Camera camera) {
        if (!HollowConfig.get().tensionFog || !IrisCompat.modEffectsAllowed()) return 0f;
        if (camera.getFluidInCamera() != FogType.NONE) return 0f;
        float t = ClientState.tension01();
        // Nothing below 30% tension, full effect at 100%, eased.
        float x = Math.max(0f, Math.min(1f, (t - 0.3f) / 0.7f));
        return x * x * (3 - 2 * x);
    }
}
