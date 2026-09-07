package com.cinecraft.compat;

import net.minecraft.world.phys.Vec3;

import java.util.function.BiPredicate;

/** Bounded, loaded-only rules used exclusively by cameras carried by a sublevel. */
public final class TravelViewRules {
    private TravelViewRules() { }

    public static double aheadScore(Vec3 origin, Vec3 target, Vec3 velocity) {
        double speed = velocity.horizontalDistance();
        Vec3 offset = target.subtract(origin);
        double distance = offset.horizontalDistance();
        if (speed < 0.5 || distance < 1.0) return 0.0;
        double ahead = (offset.x * velocity.x + offset.z * velocity.z) / speed;
        double side = Math.abs(offset.x * velocity.z - offset.z * velocity.x) / speed;
        // A diagonal approach gives a longer, gentler pass than a point directly underneath.
        return 45.0 * ahead / distance + (ahead > 0 ? 15.0 * Math.min(1.0, side / 8.0) : 0.0);
    }

    public static boolean within(Vec3 camera, Vec3 player, double horizontal, double vertical) {
        Vec3 offset = camera.subtract(player);
        return WorldCoordinates.finite(camera) && WorldCoordinates.finite(player)
                && offset.horizontalDistanceSqr() <= horizontal * horizontal && Math.abs(offset.y) <= vertical;
    }

    public static boolean comfortable(Vec3 camera, Vec3 target, Vec3 velocity) {
        Vec3 view = target.subtract(camera);
        double distanceSquared = view.lengthSqr();
        return distanceSquared >= 16.0
                && view.cross(velocity).length() / distanceSquared <= Math.toRadians(30.0);
    }

    /** Visit every chunk crossed by the sightline, including both sides of a corner crossing. */
    public static boolean loadedLine(Vec3 from, Vec3 to, BiPredicate<Integer, Integer> loaded) {
        if (!WorldCoordinates.finite(from) || !WorldCoordinates.finite(to)) return false;
        int x = (int) Math.floor(from.x / 16.0), z = (int) Math.floor(from.z / 16.0);
        int endX = (int) Math.floor(to.x / 16.0), endZ = (int) Math.floor(to.z / 16.0);
        double dx = to.x - from.x, dz = to.z - from.z;
        int stepX = Double.compare(dx, 0), stepZ = Double.compare(dz, 0);
        double strideX = dx == 0 ? Double.POSITIVE_INFINITY : 16.0 / Math.abs(dx);
        double strideZ = dz == 0 ? Double.POSITIVE_INFINITY : 16.0 / Math.abs(dz);
        double nextX = dx == 0 ? Double.POSITIVE_INFINITY : ((x + (stepX > 0 ? 1 : 0)) * 16.0 - from.x) / dx;
        double nextZ = dz == 0 ? Double.POSITIVE_INFINITY : ((z + (stepZ > 0 ? 1 : 0)) * 16.0 - from.z) / dz;
        for (int visited = 0; visited < 256; visited++) {
            if (!loaded.test(x, z)) return false;
            if (x == endX && z == endZ) return true;
            // An axis already in its endpoint chunk must not step past a terminal corner.
            if (x == endX) nextX = Double.POSITIVE_INFINITY;
            if (z == endZ) nextZ = Double.POSITIVE_INFINITY;
            if (nextX == nextZ) {
                if (!loaded.test(x + stepX, z) || !loaded.test(x, z + stepZ)) return false;
                x += stepX; z += stepZ; nextX += strideX; nextZ += strideZ;
            } else if (nextX < nextZ) {
                x += stepX; nextX += strideX;
            } else {
                z += stepZ; nextZ += strideZ;
            }
        }
        return false;
    }

    /** A pass is measured against its original approach, so pilot turns cannot reset the linger. */
    public static final class Pass {
        private final Vec3 direction;
        private final boolean approached;
        private long passedAt = Long.MIN_VALUE;

        public Pass(Vec3 origin, Vec3 target, Vec3 velocity) {
            direction = velocity.horizontalDistance() < 0.5 ? Vec3.ZERO
                    : new Vec3(velocity.x, 0, velocity.z).normalize();
            approached = target.subtract(origin).dot(direction) > 0;
        }

        public boolean finished(Vec3 origin, Vec3 target, long now) {
            if (approached && passedAt == Long.MIN_VALUE && target.subtract(origin).dot(direction) <= 0) passedAt = now;
            return passedAt != Long.MIN_VALUE && now - passedAt >= 2_000_000_000L;
        }
    }
}
