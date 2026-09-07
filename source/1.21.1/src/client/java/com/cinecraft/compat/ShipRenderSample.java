package com.cinecraft.compat;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.BiFunction;

/** Pose and conservative bounds frozen for one sample, shared by clearance and rays. */
record ShipRenderSample(Pose3dc pose, BoundingBox3dc bounds) {
    static ShipRenderSample of(SubLevelAccess ship, Pose3dc renderPose) {
        Pose3d pose = new Pose3d(renderPose);
        // Inverting the logical global AABB is conservative even for rotation.
        // Transforming it again includes the entire rendered hull.
        BoundingBox3d bounds = new BoundingBox3d(ship.boundingBox())
                .transformInverse(ship.logicalPose()).transform(pose);
        return new ShipRenderSample(pose, bounds);
    }

    boolean intersects(BoundingBox3dc query) {
        // Invalid transforms cannot safely exclude geometry.
        return !Double.isFinite(bounds.minX()) || !Double.isFinite(bounds.minY())
                || !Double.isFinite(bounds.minZ()) || !Double.isFinite(bounds.maxX())
                || !Double.isFinite(bounds.maxY()) || !Double.isFinite(bounds.maxZ())
                || bounds.intersects(query);
    }

    static BlockHitResult clip(List<ShipRenderSample> ships, Vec3 from, Vec3 to,
                               BiFunction<Vec3, Vec3, BlockHitResult> unprojectedClip) {
        BlockHitResult nearest = unprojectedClip.apply(from, to);
        double distance = from.distanceToSqr(nearest.getLocation());
        BoundingBox3d query = new BoundingBox3d(from, to).expand(1.0e-7);
        for (ShipRenderSample ship : ships) {
            if (!ship.intersects(query)) continue;
            Vec3 localFrom = ship.pose.transformPositionInverse(from);
            Vec3 localTo = ship.pose.transformPositionInverse(to);
            if (!WorldCoordinates.finite(localFrom) || !WorldCoordinates.finite(localTo)) {
                return new BlockHitResult(from, nearest.getDirection(), nearest.getBlockPos(), true);
            }
            BlockHitResult hit = unprojectedClip.apply(localFrom, localTo);
            if (hit.getType() == HitResult.Type.MISS) continue;
            Vec3 global = ship.pose.transformPosition(hit.getLocation());
            if (!WorldCoordinates.finite(global)) {
                return new BlockHitResult(from, hit.getDirection(), hit.getBlockPos(), true);
            }
            double hitDistance = from.distanceToSqr(global);
            if (hitDistance < distance || nearest.getType() == HitResult.Type.MISS) {
                nearest = new BlockHitResult(global, hit.getDirection(), hit.getBlockPos(), hit.isInside());
                distance = hitDistance;
            }
        }
        return nearest;
    }
}
