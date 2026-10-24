package com.hollowshift;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * Only active when the HOLLOWSHIFT_CI environment variable is set (the GitHub build sets it).
 * A dedicated test server saves its world and shuts itself down as soon as it has started,
 * so the automated build can check the mod loads and reuse the world for the client test.
 */
public final class CiHooks {

    public static final boolean ACTIVE = System.getenv("HOLLOWSHIFT_CI") != null;

    private CiHooks() {}

    static void register() {
        if (!ACTIVE) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!(server instanceof DedicatedServer)) return;
            HollowShift.LOG.info("[CI] dedicated server started with The Hollow Shift; saving and stopping");
            server.execute(() -> {
                server.saveEverything(false, true, true);
                server.halt(false);
            });
        });
    }
}
