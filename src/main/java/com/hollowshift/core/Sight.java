package com.hollowshift.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** "Can the player see this?" Used by every effect so nothing changes while it's being watched. */
public final class Sight {

    private Sight() {}

    /** Inside the forward view cone (~70 degrees either side). Ignores walls. */
    public static boolean inViewCone(ServerPlayer p, Vec3 point, double cos) {
        Vec3 look = p.getViewVector(1.0f);
        Vec3 to = point.subtract(p.getEyePosition()).normalize();
        return look.dot(to) > cos;
    }

    public static boolean inView(ServerPlayer p, BlockPos pos) {
        return inViewCone(p, Vec3.atCenterOf(pos), 0.35);
    }

    /** Actually looking at an entity: in a narrow cone and nothing solid in between. */
    public static boolean isWatching(ServerPlayer p, Entity e) {
        return inViewCone(p, e.getEyePosition(), 0.5) && p.hasLineOfSight(e);
    }

    /** Horizontal unit vector the player is facing. */
    public static Vec3 flatLook(ServerPlayer p) {
        Vec3 look = p.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
    }
}
