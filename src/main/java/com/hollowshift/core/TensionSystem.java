package com.hollowshift.core;

import com.hollowshift.HollowShift;
import com.hollowshift.entity.HollowEntity;
import com.hollowshift.net.ModPayloads;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The hidden TensionLevel (0..100). Updated once a second.
 *
 * Rises: darkness, underground, night, standing still too long away from home,
 *        looking at the Hollow, and each scare the Director fires.
 * Falls: light, being home, daylight on the surface, sleeping.
 *
 * The world also gets slowly worse: rise rates grow with the number of days survived.
 */
public final class TensionSystem {

    public static final AttachmentType<Double> TENSION = AttachmentRegistry.<Double>builder()
            .persistent(Codec.DOUBLE)
            .copyOnDeath()
            .initializer(() -> 0.0)
            .buildAndRegister(HollowShift.id("tension"));

    private static final Map<UUID, Vec3> ANCHOR = new HashMap<>();
    private static final Map<UUID, Integer> STILL_SECONDS = new HashMap<>();

    private TensionSystem() {}

    public static double get(ServerPlayer p) {
        Double t = p.getAttached(TENSION);
        return t == null ? 0 : t;
    }

    public static void set(ServerPlayer p, double value) {
        p.setAttached(TENSION, Math.max(0, Math.min(100, value)));
    }

    public static void add(ServerPlayer p, double delta) {
        set(p, get(p) + delta);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isSpectator() || p.isCreative()) continue;
                update(p);
                if (ServerPlayNetworking.canSend(p, ModPayloads.TensionSync.TYPE)) {
                    ServerPlayNetworking.send(p, new ModPayloads.TensionSync((float) get(p)));
                }
            }
        });

        // A night's sleep takes the edge off.
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayer p) add(p, -15);
        });
    }

    /** One second of tension change. */
    private static void update(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos eyes = BlockPos.containing(p.getEyePosition());

        long day = level.getDayTime() / 24000 + 1;
        int timeOfDay = (int) (level.getDayTime() % 24000);
        boolean night = timeOfDay >= 13000 && timeOfDay < 23000;
        boolean underground = !level.canSeeSky(eyes) && eyes.getY() < level.getSeaLevel();
        boolean home = Base.isInBase(p);
        int blockLight = level.getBrightness(LightLayer.BLOCK, eyes);
        int brightness = level.getMaxLocalRawBrightness(eyes); // sky (adjusted for night) or block, whichever is higher

        double worsening = Math.min(1.6, 1.0 + 0.03 * (day - 1));
        double delta = 0;

        // --- rising ---
        if (brightness <= 4) delta += 0.6;
        else if (brightness <= 7) delta += 0.2;
        if (underground) delta += 0.25;
        if (night && !home) delta += 0.15;
        if (!home && stillTooLong(p)) delta += 0.2;
        if (watchingHollow(p)) delta += 3.0;
        delta *= worsening;

        // --- falling ---
        if (blockLight >= 12) delta -= 0.5;
        if (home) delta -= 0.6;
        if (!night && !underground) delta -= 0.3;

        if (delta != 0) add(p, delta);
    }

    /** True once the player has stayed within a few blocks of one spot for over a minute. */
    private static boolean stillTooLong(ServerPlayer p) {
        UUID id = p.getUUID();
        Vec3 anchor = ANCHOR.get(id);
        if (anchor == null || anchor.distanceToSqr(p.position()) > 16) {
            ANCHOR.put(id, p.position());
            STILL_SECONDS.put(id, 0);
            return false;
        }
        int s = STILL_SECONDS.merge(id, 1, Integer::sum);
        return s > 60;
    }

    private static boolean watchingHollow(ServerPlayer p) {
        for (HollowEntity h : p.serverLevel().getEntitiesOfClass(HollowEntity.class,
                p.getBoundingBox().inflate(96))) {
            if (Sight.isWatching(p, h)) return true;
        }
        return false;
    }
}
