package com.hollowshift.client;

import com.hollowshift.CiHooks;
import com.hollowshift.HollowShift;
import com.hollowshift.entity.HollowEntity;
import com.hollowshift.entity.HollowSpawner;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Automated visual check, only active when HOLLOWSHIFT_CI is set (the GitHub build sets it).
 * Once in a world, it walks through tension levels, a peripheral-decay pulse, the Hollow in view
 * and a night scene, takes a screenshot of each, and then quits the game.
 */
final class CiCapture {

    private static int t = -1;

    private CiCapture() {}

    static void register() {
        if (CiHooks.ACTIVE) ClientTickEvents.END_CLIENT_TICK.register(CiCapture::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        t++;
        switch (t) {
            case 0 -> {
                mc.options.hideGui = true;
                command(mc, "time set 6000");
                command(mc, "weather clear");
                command(mc, "gamerule doDaylightCycle false");
                ClientState.forceTension(0f);
            }
            case 5 -> mc.player.setXRot(4f);
            case 160 -> shot(mc, "1-calm");
            case 162 -> ClientState.cueDecay();
            case 168 -> shot(mc, "2-peripheral-decay");
            case 175 -> ClientState.forceTension(55f);
            case 330 -> shot(mc, "3-uneasy");
            case 335 -> ClientState.forceTension(100f);
            case 500 -> shot(mc, "4-terrified");
            case 520 -> {
                ClientState.forceTension(35f);
                spawnHollow(mc);
            }
            case 640 -> shot(mc, "5-the-hollow");
            case 645 -> {
                command(mc, "time set 18000");
                ClientState.forceTension(85f);
            }
            case 800 -> shot(mc, "6-night");
            case 820 -> {
                HollowShift.LOG.info("[CI] screenshots done, quitting");
                mc.stop();
            }
            default -> { }
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                msg -> HollowShift.LOG.info("[CI] {}", msg.getString()));
    }

    private static void command(Minecraft mc, String cmd) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
        }
    }

    /** The Hollow, standing 16 blocks straight ahead, in the open. */
    private static void spawnHollow(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p == null) return;
            Vec3 look = p.getViewVector(1f);
            Vec3 ahead = p.position().add(new Vec3(look.x, 0, look.z).normalize().scale(16));
            BlockPos ground = p.serverLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(ahead));
            boolean ok = HollowSpawner.spawn(p, ground, HollowEntity.Mode.VISITOR, 20 * 30);
            HollowShift.LOG.info("[CI] spawned the Hollow at {}: {}", ground, ok);
        });
    }
}
