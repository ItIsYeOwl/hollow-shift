package com.hollowshift.entity;

import com.hollowshift.core.Base;
import com.hollowshift.core.Sight;
import com.hollowshift.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Finds believable places to put the Hollow, then puts it there. */
public final class HollowSpawner {

    private HollowSpawner() {}

    /** Far away, roughly where the player might glance. The first sighting. */
    public static boolean distantFigure(ServerPlayer p) {
        BlockPos pos = surfaceSpot(p, 0, 55, 40, 56);
        return pos != null && spawn(p, pos, HollowEntity.Mode.OBSERVE, 20 * 60);
    }

    /** Behind the player, close enough to matter. Approaches only while unseen. */
    public static boolean stalker(ServerPlayer p, boolean thief) {
        HollowEntity.Mode mode = thief ? HollowEntity.Mode.THIEF : HollowEntity.Mode.STALK;
        BlockPos pos = p.serverLevel().canSeeSky(p.blockPosition())
                ? surfaceSpot(p, 180, 40, 20, 28)
                : caveSpot(p, 180, 45, 14, 22);
        return pos != null && spawn(p, pos, mode, 20 * 90);
    }

    /** Standing somewhere inside the base, out of view, ideally in a darker corner. */
    public static boolean inBase(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos center = Base.center(p);
        RandomSource r = p.getRandom();
        BlockPos best = null;
        int bestLight = 99;
        for (int i = 0; i < 80; i++) {
            BlockPos c = center.offset(r.nextInt(17) - 8, r.nextInt(7) - 3, r.nextInt(17) - 8);
            if (!standable(level, c) || Sight.inView(p, c) || c.closerThan(p.blockPosition(), 4)) continue;
            int light = level.getBrightness(LightLayer.BLOCK, c);
            if (light < bestLight) {
                best = c;
                bestLight = light;
            }
        }
        return best != null && spawn(p, best, HollowEntity.Mode.LURK, 20 * 60);
    }

    /** In the open, in daylight, in front of the player. It doesn't hide. */
    public static boolean daylightVisitor(ServerPlayer p) {
        BlockPos pos = surfaceSpot(p, 0, 40, 28, 40);
        return pos != null && spawn(p, pos, HollowEntity.Mode.VISITOR, 20 * 45);
    }

    // ------------------------------------------------------------------------

    public static boolean spawn(ServerPlayer p, BlockPos pos, HollowEntity.Mode mode, int lifetime) {
        HollowEntity h = ModEntities.HOLLOW.create(p.serverLevel());
        if (h == null) return false;
        h.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        h.setup(mode, p, lifetime);
        return p.serverLevel().addFreshEntity(h);
    }

    /** A spot on the surface at `angle` degrees from the player's facing (0 = ahead, 180 = behind). */
    private static BlockPos surfaceSpot(ServerPlayer p, double angle, double spread, double minDist, double maxDist) {
        ServerLevel level = p.serverLevel();
        RandomSource r = p.getRandom();
        for (int i = 0; i < 16; i++) {
            Vec3 dir = rotate(Sight.flatLook(p), angle + (r.nextDouble() * 2 - 1) * spread);
            double d = minDist + r.nextDouble() * (maxDist - minDist);
            BlockPos column = BlockPos.containing(p.getX() + dir.x * d, p.getY(), p.getZ() + dir.z * d);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (Math.abs(top.getY() - p.getY()) > 20) continue;
            if (!standable(level, top)) continue;
            if (level.getBrightness(LightLayer.BLOCK, top) >= 8) continue;
            if (Base.bed(p) != null && Base.bed(p).closerThan(top, Base.RADIUS)) continue;
            return top;
        }
        return null;
    }

    /** A spot in the same cave system: near the player's height, with floor and headroom. */
    private static BlockPos caveSpot(ServerPlayer p, double angle, double spread, double minDist, double maxDist) {
        ServerLevel level = p.serverLevel();
        RandomSource r = p.getRandom();
        for (int i = 0; i < 40; i++) {
            Vec3 dir = rotate(Sight.flatLook(p), angle + (r.nextDouble() * 2 - 1) * spread);
            double d = minDist + r.nextDouble() * (maxDist - minDist);
            BlockPos base = BlockPos.containing(p.getX() + dir.x * d, p.getY(), p.getZ() + dir.z * d);
            for (int dy = 0; dy <= 6; dy++) {
                for (int sign : new int[]{-1, 1}) {
                    BlockPos c = base.above(dy * sign);
                    if (standable(level, c) && level.getBrightness(LightLayer.BLOCK, c) < 8) return c;
                }
            }
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above()) && level.isEmptyBlock(pos.above(2))
                && level.getFluidState(pos).isEmpty();
    }

    private static Vec3 rotate(Vec3 v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vec3(v.x * cos - v.z * sin, 0, v.x * sin + v.z * cos);
    }
}
