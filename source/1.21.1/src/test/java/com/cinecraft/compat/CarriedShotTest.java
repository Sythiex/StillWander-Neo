package com.cinecraft.compat;

import com.cinecraft.camera.*;
import com.cinecraft.director.*;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class CarriedShotTest {
    private static final Vec3 ORIGIN = new Vec3(100, 70, 0);
    private static final Vec3 CAMERA = ORIGIN.add(4, 4, 0);
    private static final Vec3 TARGET = ORIGIN.add(30, 0, 20);
    private static final ShotComposition COMPOSITION = new ShotComposition(Framing.WIDE, ScreenPlacement.LEFT_THIRD,
            0, EntityAction.STILL, false);

    @Test
    void panoramaTravelsThousandsOfBlocksWithoutInheritingTurnsOrBanks() {
        Ship ship = new Ship();
        AtomicLong clock = new AtomicLong();
        ShotPlan plan = plan(p -> CAMERA.add(0, 0, p * 5), p -> TARGET.add(p * 15, p * 3, 0));
        CarriedShot moving = shot(plan, stabilized(ship, () -> true), false, true, clock);
        CarriedShot stationary = shot(plan, ShotReferenceFrame.WORLD, false, true, clock);
        ship.last.set(ship.logical);
        ship.logical.position().add(1200, 50, -700);
        ship.logical.orientation().rotateY(1.8).rotateX(0.6).rotateZ(0.8);
        clock.set(20_000_000_000L);
        CameraPose original = stationary.sample(1);
        for (float partial : new float[]{0.25f, 0.5f, 1.0f}) {
            CameraPose pose = sample(moving, partial);
            Vec3 displacement = ship.renderPose(partial).transformPosition(ship.localAnchor).subtract(ORIGIN);
            near(original.position().add(displacement), pose.position());
            assertEquals(original.yaw(), pose.yaw(), 0.00001);
            assertEquals(original.pitch(), pose.pitch(), 0.00001);
            assertEquals(original.fov(), pose.fov());
        }
    }

    @Test
    void landmarkStaysInWorldSpaceAndCannotCancelCameraTransport() {
        Ship ship = new Ship();
        AtomicLong clock = new AtomicLong();
        CarriedShot shot = shot(plan(p -> CAMERA, p -> TARGET), stabilized(ship, () -> true), false, false, clock);
        for (int tick = 0; tick < 40; tick++) {
            ship.last.set(ship.logical);
            ship.logical.position().add(3, 0, 0);
            clock.addAndGet(50_000_000L);
            CameraPose pose = sample(shot, 1);
            Vec3 expectedCamera = CAMERA.add((tick + 1) * 3, 0, 0);
            near(expectedCamera, pose.position());
            CameraPose expected = LookAt.pose(expectedCamera, COMPOSITION.focus(expectedCamera, TARGET, 60));
            assertEquals(expected.yaw(), pose.yaw(), 0.00001);
            assertEquals(expected.pitch(), pose.pitch(), 0.00001);
        }
    }

    @Test
    void anotherMovingSubjectCanChangeAimWithoutDraggingTheCameraOffItsCarrier() {
        Ship ship = new Ship();
        AtomicLong clock = new AtomicLong();
        AtomicReference<Vec3> target = new AtomicReference<>(TARGET);
        ShotPlan plan = plan(p -> CAMERA, p -> target.get());
        CarriedShot shot = new CarriedShot(plan, stabilized(ship, () -> true),
                new CarriedShot.Tracking(TARGET, target::get, () -> true, false, true), false,
                (camera, focus) -> true, () -> false, clock::get);
        CameraPose before = sample(shot, 1);
        target.set(TARGET.add(-100, 0, 60));
        ship.last.set(ship.logical);
        ship.logical.position().add(20, 0, 0);
        clock.addAndGet(100_000_000L);
        CameraPose after = sample(shot, 1);
        near(CAMERA.add(20, 0, 0), after.position());
        assertNotEquals(before.yaw(), after.yaw());
    }

    @Test
    void onboardTrackingRetainsLocalOffsetsWithoutDampingShipMotion() {
        Ship ship = new Ship();
        Vec3 localTarget = ship.logical.transformPositionInverse(TARGET);
        Vec3 localCamera = ship.logical.transformPositionInverse(CAMERA);
        AtomicLong clock = new AtomicLong();
        ShotPlan plan = plan(p -> CAMERA, p -> WorldCoordinates.pose(ship).transformPosition(localTarget));
        CarriedShot shot = new CarriedShot(plan, new ShipReferenceFrame(ship, () -> true),
                new CarriedShot.Tracking(TARGET, () -> WorldCoordinates.pose(ship).transformPosition(localTarget),
                        () -> true, true, true), false, (camera, focus) -> true, () -> false, clock::get);
        ship.last.set(ship.logical);
        ship.logical.position().add(80, 0, 0);
        ship.logical.orientation().rotateY(1.1);
        clock.addAndGet(50_000_000L);
        near(ship.renderPose(0.5f).transformPosition(localCamera), sample(shot, 0.5f).position());
    }

    @Test
    void aBlockedMoveUsesOnlyATransportedRevalidatedFallbackAndRequestsACut() {
        Ship ship = new Ship();
        AtomicLong clock = new AtomicLong();
        AtomicBoolean blocked = new AtomicBoolean();
        AtomicBoolean allBlocked = new AtomicBoolean();
        CarriedShot shot = new CarriedShot(plan(p -> CAMERA.add(p * 8, 0, 0), p -> TARGET),
                stabilized(ship, () -> true), tracking(false), false,
                (camera, focus) -> !allBlocked.get() && (!blocked.get() || camera.x < 125), () -> false, clock::get);
        near(CAMERA, sample(shot, 1).position());
        ship.last.set(ship.logical);
        ship.logical.position().add(20, 0, 0);
        blocked.set(true);
        clock.set(30_000_000_000L);
        near(CAMERA.add(20, 0, 0), sample(shot, 1).position());
        assertTrue(shot.finished());
        allBlocked.set(true);
        assertNull(sample(shot, 1));
    }

    @Test
    void bothSubjectLossAndCarrierLossStopBeforeQueryingGeometry() {
        for (boolean loseCarrier : new boolean[]{false, true}) {
            AtomicBoolean available = new AtomicBoolean(true);
            CarriedShot shot = new CarriedShot(plan(p -> CAMERA, p -> TARGET),
                    stabilized(new Ship(), loseCarrier ? available::get : () -> true),
                    new CarriedShot.Tracking(TARGET, () -> TARGET, loseCarrier ? () -> true : available::get, false, false),
                    false, (camera, focus) -> { fail("Lost frame/subject must not query geometry"); return false; }, () -> false);
            available.set(false);
            assertNull(sample(shot, 1));
            assertTrue(shot.finished());
        }
    }

    @Test
    void invalidSamplesNeverReachWorldQueries() {
        Vec3 invalid = new Vec3(Double.NaN, 70, 0);
        for (boolean invalidCamera : new boolean[]{false, true}) {
            CarriedShot shot = new CarriedShot(plan(p -> invalidCamera ? invalid : CAMERA, p -> invalidCamera ? TARGET : invalid),
                    ShotReferenceFrame.WORLD, tracking(false), false,
                    (camera, focus) -> { fail("Non-finite samples must not query geometry"); return false; }, () -> false);
            assertNull(shot.sample(1));
            assertTrue(shot.finished());
        }
    }

    @Test
    void livePlayerDistanceEndsAShotEvenWhenItsTerrainStillExists() {
        AtomicReference<Vec3> player = new AtomicReference<>(ORIGIN);
        CarriedShot shot = new CarriedShot(plan(p -> CAMERA, p -> TARGET), ShotReferenceFrame.WORLD,
                tracking(false), false, (camera, focus) -> TravelViewRules.within(camera, player.get(), 48, 32), () -> false);
        assertNotNull(shot.sample(1));
        player.set(ORIGIN.add(200, 0, 0));
        assertNull(shot.sample(1));
        assertTrue(shot.finished());
    }

    @Test
    void endingAPassKeepsASafePoseUntilTheDirectorMakesItsNextCut() {
        AtomicBoolean passed = new AtomicBoolean();
        CarriedShot shot = new CarriedShot(plan(p -> CAMERA, p -> TARGET), ShotReferenceFrame.WORLD,
                tracking(false), false, (camera, focus) -> true, passed::get);
        assertNotNull(shot.sample(1));
        assertFalse(shot.finished());
        passed.set(true);
        assertNotNull(shot.sample(1));
        assertTrue(shot.finished());
    }

    private static CarriedShot shot(ShotPlan plan, ShotReferenceFrame frame, boolean follows, boolean authored, AtomicLong clock) {
        return new CarriedShot(plan, frame, tracking(follows), authored, (camera, focus) -> true, () -> false, clock::get);
    }

    private static CarriedShot.Tracking tracking(boolean follows) {
        return new CarriedShot.Tracking(TARGET, () -> TARGET, () -> true, follows, false);
    }

    private static ShotPlan plan(CameraPath camera, CameraPath subject) {
        return new ShotPlan(ShotType.PROCEDURAL_PANORAMA, camera,
                p -> COMPOSITION.focus(camera.sample(p), subject.sample(p), 60), subject,
                FovPath.fixed(60), 60_000, COMPOSITION, "test");
    }

    private static StabilizedShipFrame stabilized(Ship ship, java.util.function.BooleanSupplier available) {
        return new StabilizedShipFrame(ship, ORIGIN, available);
    }

    private static CameraPose sample(CarriedShot shot, float partial) {
        return WorldCoordinates.duringRender(null, partial, () -> shot.sample(partial));
    }

    private static void near(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) < 0.00001, expected + " != " + actual);
    }

    private static final class Ship implements ClientSubLevelAccess {
        final Pose3d logical = new Pose3d();
        final Pose3d last = new Pose3d();
        final Vec3 localAnchor = new Vec3(20_000_002, 66, 20_000_000);
        Ship() {
            logical.rotationPoint().set(20_000_000, 64, 20_000_000);
            logical.position().set(98, 68, 0);
            last.set(logical);
        }
        public Pose3dc logicalPose() { return logical; }
        public Pose3dc lastPose() { return last; }
        public Pose3dc renderPose() { return renderPose(0.5f); }
        public Pose3dc renderPose(float partial) { return new Pose3d(last).lerp(logical, partial); }
        public BoundingBox3dc boundingBox() { return new BoundingBox3d(); }
        public UUID getUniqueId() { return new UUID(0, 2); }
        public String getName() { return "Passenger test ship"; }
    }
}
