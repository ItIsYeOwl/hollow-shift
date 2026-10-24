package com.hollowshift.core;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The cow that isn't a cow. It never grazes, never makes a sound, always faces you,
 * and when killed it simply isn't there any more: no death animation, no drops.
 */
public final class FakeMobs {

    public static final String TAG = "hollowshift_not_a_cow";
    private static final String TARGET_PREFIX = "hollowshift_watching_";

    private FakeMobs() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity.getTags().contains(TAG)) {
                entity.discard();
                return false;
            }
            return true;
        });

        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level.getGameTime() % 5 != 0) return;
            for (Cow cow : level.getEntities(EntityType.COW, c -> c.getTags().contains(TAG))) {
                ServerPlayer watched = target(level, cow);
                if (watched == null || cow.tickCount > 24000 || cow.distanceTo(watched) > 128) {
                    cow.discard();
                    continue;
                }
                Vec3 d = watched.getEyePosition().subtract(cow.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                cow.setYRot(yaw);
                cow.setYBodyRot(yaw);
                cow.setYHeadRot(yaw);
            }
        });
    }

    /** Places a fake cow 14-22 blocks away, at the edge of the player's view. */
    public static boolean spawnCow(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Cow cow = EntityType.COW.create(level);
        if (cow == null) return false;
        Vec3 look = Sight.flatLook(p);
        double side = p.getRandom().nextBoolean() ? 1 : -1;
        Vec3 dir = new Vec3(look.x * 0.5 - look.z * 0.86 * side, 0, look.z * 0.5 + look.x * 0.86 * side);
        double dist = 14 + p.getRandom().nextInt(8);
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(p.getX() + dir.x * dist, p.getY(), p.getZ() + dir.z * dist));
        if (Math.abs(top.getY() - p.getY()) > 12) return false;
        cow.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0, 0);
        cow.setNoAi(true);
        cow.setSilent(true);
        cow.addTag(TAG);
        cow.addTag(TARGET_PREFIX + p.getUUID());
        return level.addFreshEntity(cow);
    }

    private static ServerPlayer target(ServerLevel level, Cow cow) {
        for (String t : cow.getTags()) {
            if (t.startsWith(TARGET_PREFIX)) {
                try {
                    return (ServerPlayer) level.getPlayerByUUID(java.util.UUID.fromString(t.substring(TARGET_PREFIX.length())));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
