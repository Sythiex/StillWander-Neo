package com.cinecraft.compat;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class ShipRenderSampleTest {
    @Test
    void fastTranslationIncludesHullOutsideLogicalPaddingAndClipsIt() {
        Ship ship = new Ship();
        ship.last.position().add(-40, 0, 0);
        for (float partial : new float[]{0, 0.25f, 0.5f, 0.75f, 1}) {
            ShipRenderSample sample = ShipRenderSample.of(ship, ship.renderPose(partial));
            Vec3 center = sample.pose().transformPosition(ship.center);
            BoundingBox3d query = new BoundingBox3d(center, center).expand(1);
            if (partial <= 0.5f) assertFalse(ship.boundingBox().intersects(query));
            assertTrue(sample.intersects(query));
            assertTrue(ship.local.contains(sample.pose().transformPositionInverse(center)));
            assertHullHit(sample, ship, center);
        }
    }

    @Test
    void intermediateRotationExtendsBeyondBothEndpointBounds() {
        Ship ship = new Ship();
        ship.last.orientation().rotateY(Math.PI);
        ShipRenderSample sample = ShipRenderSample.of(ship, ship.renderPose(0.5f));
        Vec3 point = sample.pose().transformPosition(ship.center.add(6, 0, 0));
        BoundingBox3d query = new BoundingBox3d(point, point).expand(1);
        assertFalse(ship.boundingBox().intersects(query));
        assertFalse(new BoundingBox3d(ship.local).transform(ship.last).intersects(query));
        assertTrue(sample.intersects(query));
        assertHullHit(sample, ship, point);
    }

    @Test
    void rayKeepsNearestTerrainOrShipHitRegardlessOfCandidateOrder() {
        Ship ship = new Ship();
        ShipRenderSample low = ShipRenderSample.of(ship, ship.logical);
        Pose3d raised = new Pose3d(ship.logical);
        raised.position().add(0, 5, 0);
        ShipRenderSample high = ShipRenderSample.of(ship, raised);
        Vec3 from = new Vec3(100, 90, 100), to = new Vec3(100, 60, 100);
        for (var samples : List.of(List.of(low, high), List.of(high, low))) {
            BlockHitResult hit = ShipRenderSample.clip(samples, from, to, ship::clip);
            assertEquals(76, hit.getLocation().y, 0.00001);
            BlockHitResult terrain = ShipRenderSample.clip(samples, from, to, (start, end) ->
                    start.equals(from) ? new BlockHitResult(new Vec3(100, 80, 100), Direction.UP, BlockPos.ZERO, false)
                            : ship.clip(start, end));
            assertEquals(80, terrain.getLocation().y, 0.00001);
        }
    }

    @Test
    void nonFinitePoseFailsClosedAndSnapshotDoesNotFollowLaterMutation() {
        Ship ship = new Ship();
        ShipRenderSample sample = ShipRenderSample.of(ship, ship.logical);
        ship.logical.position().add(1000, 0, 0);
        assertEquals(100, sample.pose().position().x());
        Pose3d invalid = new Pose3d();
        invalid.position().set(Double.NaN, 0, 0);
        ShipRenderSample bad = ShipRenderSample.of(ship, invalid);
        assertTrue(bad.intersects(new BoundingBox3d(-1, -1, -1, 1, 1, 1)));
        assertEquals(HitResult.Type.BLOCK, ShipRenderSample.clip(List.of(bad), Vec3.ZERO,
                new Vec3(0, 1, 0), ship::clip).getType());
    }

    private static void assertHullHit(ShipRenderSample sample, Ship ship, Vec3 point) {
        BlockHitResult hit = ShipRenderSample.clip(List.of(sample), point.add(0, 3, 0),
                point.add(0, -3, 0), ship::clip);
        assertEquals(HitResult.Type.BLOCK, hit.getType());
        assertEquals(point.y + 1, hit.getLocation().y, 0.00001);
    }

    private static final class Ship implements ClientSubLevelAccess {
        final Vec3 center = new Vec3(20_000_000, 64, 20_000_000);
        final AABB local = new AABB(center.add(-8, -1, -1), center.add(8, 1, 1));
        final Pose3d logical = new Pose3d();
        final Pose3d last;
        Ship() {
            logical.rotationPoint().set(center.x, center.y, center.z);
            logical.position().set(100, 70, 100);
            last = new Pose3d(logical);
        }
        BlockHitResult clip(Vec3 from, Vec3 to) {
            return local.clip(from, to).map(point -> new BlockHitResult(point, Direction.UP,
                    BlockPos.containing(point), false)).orElseGet(() ->
                    BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to)));
        }
        public Pose3dc logicalPose() { return logical; }
        public Pose3dc lastPose() { return last; }
        public Pose3dc renderPose() { return renderPose(0.5f); }
        public Pose3dc renderPose(float partial) { return new Pose3d(last).lerp(logical, partial); }
        public BoundingBox3dc boundingBox() { return new BoundingBox3d(local).transform(logical); }
        public UUID getUniqueId() { return new UUID(0, 1); }
        public String getName() { return "Fast ship"; }
    }
}
