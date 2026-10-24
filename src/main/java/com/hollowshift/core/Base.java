package com.hollowshift.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * "Home" is the area around the bed the player last slept in.
 * No bed set yet means no home, so nowhere is safe.
 */
public final class Base {

    public static final int RADIUS = 24;

    private Base() {}

    public static BlockPos bed(ServerPlayer p) {
        BlockPos bed = p.getRespawnPosition();
        if (bed == null || p.getRespawnDimension() != p.level().dimension()) return null;
        return bed;
    }

    public static boolean isInBase(ServerPlayer p) {
        BlockPos bed = bed(p);
        return bed != null && bed.closerThan(p.blockPosition(), RADIUS);
    }

    /** Where tampering events (doors, chests, signs) look. Falls back to the player's position. */
    public static BlockPos center(ServerPlayer p) {
        BlockPos bed = bed(p);
        return bed != null ? bed : p.blockPosition();
    }
}
