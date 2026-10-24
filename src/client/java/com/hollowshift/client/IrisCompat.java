package com.hollowshift.client;

import com.hollowshift.HollowConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Detects an active Iris shader pack without depending on Iris at compile time.
 * With withIrisShaders = "auto", the mod's own fog and screen effects step aside
 * while a pack is active, so the two don't stack into mud.
 */
public final class IrisCompat {

    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
    private static long lastCheck = -1;
    private static boolean cached;

    private IrisCompat() {}

    public static boolean modEffectsAllowed() {
        if (!IRIS || "always".equalsIgnoreCase(HollowConfig.get().withIrisShaders)) return true;
        return !shaderPackInUse();
    }

    private static boolean shaderPackInUse() {
        long now = ClientState.ticks();
        if (now - lastCheck < 20 && lastCheck >= 0) return cached;
        lastCheck = now;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            cached = (boolean) api.getMethod("isShaderPackInUse").invoke(instance);
        } catch (Throwable t) {
            cached = false;
        }
        return cached;
    }
}
