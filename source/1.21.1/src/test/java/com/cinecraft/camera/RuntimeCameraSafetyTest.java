package com.cinecraft.camera;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

final class RuntimeCameraSafetyTest {
    @Test
    void nonFiniteLiveCoordinatesEndTheShotBeforeTheCollisionQuery() {
        Vec3 focus = new Vec3(0, 70, 0);
        TrackedMovingShot shot = new TrackedMovingShot(t -> new Vec3(4, 70, 0), t -> focus,
                () -> new Vec3(Double.NaN, 70, 0), () -> true, focus, FovPath.none(), 60_000,
                (camera, target) -> { fail("Invalid coordinates must never reach world queries"); return camera; });
        assertNull(shot.sample(0.5f));
        assertTrue(shot.finished());
    }

    @Test
    void aMovingObstacleInvalidatesAnUntrackedShotImmediately() {
        AtomicBoolean clear = new AtomicBoolean(true);
        ValidatedShot shot = new ValidatedShot(new StaticShot(LookAt.pose(new Vec3(4, 70, 0),
                new Vec3(0, 70, 0)), 60_000), pose -> clear.get());
        assertNotNull(shot.sample(0));
        clear.set(false);
        assertNull(shot.sample(0.5f));
        assertTrue(shot.finished());
    }

    @Test
    void aTrackedShotCannotReuseASafePoseOnceMovingGeometryOccupiesIt() {
        AtomicBoolean clear = new AtomicBoolean(true);
        Vec3 focus = new Vec3(0, 70, 0);
        TrackedMovingShot shot = new TrackedMovingShot(t -> new Vec3(4, 70, 0), t -> focus,
                () -> focus, () -> true, focus, FovPath.none(), 60_000,
                (camera, target) -> clear.get() ? camera : null);
        assertNotNull(shot.sample(0));
        clear.set(false);
        assertNull(shot.sample(0.5f));
        assertTrue(shot.finished());
    }
}
