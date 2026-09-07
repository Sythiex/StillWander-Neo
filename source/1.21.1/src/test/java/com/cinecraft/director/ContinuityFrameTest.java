package com.cinecraft.director;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ContinuityFrameTest {
    @Test
    void classifiesOppositeSidesOfAMovingSubjectsAxis() {
        Vec3 target = Vec3.ZERO;
        Vec3 movement = new Vec3(1.0, 0.0, 0.0);

        assertEquals(CameraSide.LEFT,
                ContinuityFrame.cameraSide(new Vec3(0.0, 2.0, 8.0), target, movement));
        assertEquals(CameraSide.RIGHT,
                ContinuityFrame.cameraSide(new Vec3(0.0, 2.0, -8.0), target, movement));
        assertEquals(ScreenDirection.RIGHT,
                ContinuityFrame.screenDirection(new Vec3(0.0, 2.0, 8.0), target, movement));
        assertEquals(ScreenDirection.LEFT,
                ContinuityFrame.screenDirection(new Vec3(0.0, 2.0, -8.0), target, movement));
    }

    @Test
    void stationarySubjectsDoNotInventAnActionAxis() {
        Vec3 camera = new Vec3(4.0, 66.0, 4.0);
        Vec3 target = new Vec3(0.0, 64.0, 0.0);

        assertEquals(CameraSide.UNKNOWN, ContinuityFrame.cameraSide(camera, target, Vec3.ZERO));
        assertEquals(ScreenDirection.STILL, ContinuityFrame.screenDirection(camera, target, Vec3.ZERO));
    }
}
