package com.cinecraft.compat;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import com.cinecraft.camera.TrackedMovingShot;
import com.cinecraft.camera.FovPath;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

final class ShipReferenceFrameTest {
    @Test
    void trackedShotCarriesItsCameraThroughShipRotationWithoutDampingShipMotion() {
        Ship ship = new Ship();
        ship.logical.rotationPoint().set(20_000_000, 64, 20_000_000);
        ship.logical.position().set(100, 70, -50);
        Vec3 target = new Vec3(20_000_000, 65, 20_000_000);
        Vec3 localCamera = target.add(4, 2, 0);
        Vec3 plannedCamera = ship.logical.transformPosition(localCamera);
        ShipReferenceFrame frame = new ShipReferenceFrame(ship, () -> true);
        TrackedMovingShot shot = new TrackedMovingShot(t -> plannedCamera,
                t -> ship.logical.transformPosition(target), () -> ship.logical.transformPosition(target),
                () -> true, ship.logical.transformPosition(target), FovPath.none(), 60_000,
                (camera, focus) -> camera, frame);
        assertNear(plannedCamera, shot.sample(1).position());
        ship.last.set(ship.logical);
        ship.logical.position().add(10, 0, 0);
        ship.logical.orientation().rotateY(Math.PI / 2);
        assertNear(ship.logical.transformPosition(localCamera), shot.sample(1).position());
    }

    @Test
    void translationRotationAndRenderInterpolationKeepTheRigInTheShipFrame() {
        Ship ship = new Ship();
        ship.logical.rotationPoint().set(20_000_000, 64, 20_000_000);
        ship.logical.position().set(100, 70, -50);
        Vec3 localCamera = new Vec3(20_000_004, 67, 20_000_000);
        Vec3 globalCamera = ship.logical.transformPosition(localCamera);
        ShipReferenceFrame frame = new ShipReferenceFrame(ship, () -> true);
        assertNear(localCamera, frame.planned(globalCamera));

        ship.last.set(ship.logical);
        ship.logical.position().add(10, 0, 0);
        ship.logical.orientation().rotateY(Math.PI / 2);
        assertNear(localCamera, frame.planned(globalCamera));
        assertNear(new Vec3(110, 73, -54), frame.render(localCamera, 1));
        assertNear(localCamera, frame.live(ship.logical.transformPosition(localCamera)));
        Vec3 halfway = frame.render(localCamera, 0.5f);
        assertNear(new Vec3(105 + Math.sqrt(8), 73, -50 - Math.sqrt(8)), halfway);
    }

    @Test
    void removalInvalidatesTheFrameWithoutReturningPlotCoordinates() {
        AtomicBoolean loaded = new AtomicBoolean(true);
        ShipReferenceFrame frame = new ShipReferenceFrame(new Ship(), loaded::get);
        assertTrue(frame.available());
        loaded.set(false);
        assertFalse(frame.available());
    }

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) < 0.00001, expected + " != " + actual);
    }

    private static final class Ship implements ClientSubLevelAccess {
        final Pose3d logical = new Pose3d();
        final Pose3d last = new Pose3d();
        public Pose3dc logicalPose() { return logical; }
        public Pose3dc lastPose() { return last; }
        public Pose3dc renderPose() { return renderPose(0.5f); }
        public Pose3dc renderPose(float partialTick) { return new Pose3d(last).lerp(logical, partialTick); }
        public BoundingBox3dc boundingBox() { return new BoundingBox3d(); }
        public UUID getUniqueId() { return new UUID(0, 1); }
        public String getName() { return "Test ship"; }
    }
}
