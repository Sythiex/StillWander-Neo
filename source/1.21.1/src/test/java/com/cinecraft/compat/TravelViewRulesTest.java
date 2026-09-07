package com.cinecraft.compat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;

final class TravelViewRulesTest {
    @Test
    void absentSublevelsLeaveSubjectSelectionUntouchedWithoutConsultingTheScanner() {
        SublevelCameraPolicy policy = new SublevelCameraPolicy();
        var subject = new com.cinecraft.director.SceneSubject(com.cinecraft.director.SubjectType.LANDSCAPE, Vec3.ZERO, "ordinary");
        assertFalse(policy.update(null));
        assertFalse(policy.active());
        assertSame(subject, policy.prepareSubject(null, null, subject, false));
        assertSame(subject, policy.prepareSubject(null, null, subject, true));
    }

    @Test
    void selectionPrefersAnApproachingSideViewAndDisablesBiasWhenHovering() {
        Vec3 forward = new Vec3(5, 0, 0);
        Vec3 aheadSide = new Vec3(25, 0, 10);
        assertTrue(TravelViewRules.aheadScore(Vec3.ZERO, aheadSide, forward)
                > TravelViewRules.aheadScore(Vec3.ZERO, new Vec3(-25, 0, 10), forward));
        assertTrue(TravelViewRules.aheadScore(Vec3.ZERO, aheadSide, forward)
                > TravelViewRules.aheadScore(Vec3.ZERO, new Vec3(25, 0, 0), forward));
        assertEquals(0, TravelViewRules.aheadScore(Vec3.ZERO, aheadSide, new Vec3(0.01, 30, 0)));
    }

    @Test
    void passAllowsApproachThenTwoSecondsOfRecessionWithoutResettingOnATurn() {
        Vec3 target = new Vec3(30, 0, 10);
        TravelViewRules.Pass pass = new TravelViewRules.Pass(Vec3.ZERO, target, new Vec3(5, 0, 0));
        assertFalse(pass.finished(new Vec3(29, 0, 0), target, 1_000_000_000L));
        assertFalse(pass.finished(new Vec3(31, 0, 0), target, 2_000_000_000L));
        assertFalse(pass.finished(new Vec3(40, 0, 0), target, 3_900_000_000L));
        assertTrue(pass.finished(new Vec3(25, 0, 0), target, 4_000_000_000L));
        TravelViewRules.Pass hovering = new TravelViewRules.Pass(Vec3.ZERO, target, Vec3.ZERO);
        assertFalse(hovering.finished(new Vec3(100, 0, 0), target, 100_000_000_000L));
    }

    @Test
    void sharpClosePassesAreRejectedButDistantSceneryIsComfortable() {
        assertFalse(TravelViewRules.comfortable(Vec3.ZERO, new Vec3(0, 0, 5), new Vec3(30, 0, 0)));
        assertTrue(TravelViewRules.comfortable(Vec3.ZERO, new Vec3(0, 0, 100), new Vec3(30, 0, 0)));
    }

    @Test
    void loadedSightlinesCheckIntermediateChunksAndNegativeCoordinates() {
        assertFalse(TravelViewRules.loadedLine(new Vec3(-40, 70, 2), new Vec3(40, 70, 2), (x, z) -> x != -1));
        assertTrue(TravelViewRules.loadedLine(new Vec3(-40, 70, 2), new Vec3(40, 70, 2), (x, z) -> true));
        assertTrue(TravelViewRules.loadedLine(new Vec3(0, 70, 0), new Vec3(0, 100, 0), (x, z) -> x == 0 && z == 0));
    }

    @Test
    void diagonalSightlinesCannotSkipTinyChunkCrossingsOrCorners() {
        assertFalse(TravelViewRules.loadedLine(new Vec3(15.99, 70, 15.9), new Vec3(32, 70, 32),
                (x, z) -> !(x == 1 && z == 0)));
        assertFalse(TravelViewRules.loadedLine(new Vec3(15, 70, 15), new Vec3(17, 70, 17),
                (x, z) -> !(x == 0 && z == 1)));
    }

    @Test
    void cornerEndpointsStayWithinTheSegmentInBothDirections() {
        for (int boundary : new int[]{-16, 0, 16}) {
            for (int distance : new int[]{1, 33}) {
                for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1}) {
                    Vec3 from = new Vec3(boundary + dx * distance, 70, boundary + dz * distance);
                    Vec3 to = new Vec3(boundary, 70, boundary);
                    int startX = (int) Math.floor(from.x / 16.0), startZ = (int) Math.floor(from.z / 16.0);
                    int end = boundary / 16;
                    BiPredicate<Integer, Integer> loaded = (x, z) ->
                            x >= Math.min(startX, end) && x <= Math.max(startX, end)
                                    && z >= Math.min(startZ, end) && z <= Math.max(startZ, end);
                    assertTrue(TravelViewRules.loadedLine(from, to, loaded), from + " -> " + to);
                    assertTrue(TravelViewRules.loadedLine(to, from, loaded), to + " -> " + from);
                }
            }
        }
    }

    @Test
    void cornerEndpointChunksMustStillBeLoaded() {
        Vec3 from = new Vec3(15, 70, 17), to = new Vec3(16, 70, 16);
        for (int missingX : new int[]{0, 1}) {
            assertFalse(TravelViewRules.loadedLine(from, to, (x, z) -> x != missingX || z != 1));
            assertFalse(TravelViewRules.loadedLine(to, from, (x, z) -> x != missingX || z != 1));
        }
    }

    @Test
    void invalidOrUnboundedSightlinesFailClosed() {
        assertFalse(TravelViewRules.loadedLine(Vec3.ZERO, new Vec3(Double.NaN, 0, 0),
                (x, z) -> { fail("Invalid query"); return true; }));
        assertFalse(TravelViewRules.loadedLine(Vec3.ZERO, new Vec3(100_000, 0, 0), (x, z) -> true));
    }
}
