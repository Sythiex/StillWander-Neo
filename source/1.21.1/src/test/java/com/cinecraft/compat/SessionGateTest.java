package com.cinecraft.compat;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.cinecraft.compat.SessionGate.BlockReason.*;
import static org.junit.jupiter.api.Assertions.*;

final class SessionGateTest {
    @Test
    void synchronousReleaseObserversSeeFreshIdleStateOnWorldReplacement() {
        AtomicBoolean idle = new AtomicBoolean(true);
        SessionGate gate = new SessionGate(() -> assertFalse(idle.get(),
                "A synchronous FPS refresh must not retain the previous world's idle request"),
                () -> idle.set(false));
        assertTrue(gate.update(new Object(), new Object(), NONE));
        idle.set(true);
        assertTrue(gate.update(new Object(), new Object(), NONE));
    }

    @Test
    void replayEntryReleasesCaptureAndRenderingEvenWithoutATick() {
        AtomicInteger releases = new AtomicInteger();
        AtomicInteger idleResets = new AtomicInteger();
        SessionGate gate = new SessionGate(releases::incrementAndGet, idleResets::incrementAndGet);
        Object world = new Object(), player = new Object();
        assertTrue(gate.update(world, player, NONE));
        assertFalse(gate.update(world, player, REPLAY));
        assertEquals(2, releases.get());
        assertFalse(gate.update(world, player, REPLAY));
        assertEquals(2, releases.get(), "Paused replays do not repeatedly restore someone else's state");
        assertTrue(gate.update(world, player, NONE));
        assertEquals(3, idleResets.get(), "Returning from a replay starts a fresh idle countdown");
    }

    @Test
    void cameraYieldAndWorldOrPlayerReplacementAlwaysResetTheSession() {
        AtomicInteger resets = new AtomicInteger();
        SessionGate gate = new SessionGate(() -> { }, resets::incrementAndGet);
        Object world = new Object(), player = new Object();
        gate.update(world, player, NONE);
        assertFalse(gate.update(world, player, EXTERNAL_CAMERA));
        assertTrue(gate.update(world, player, NONE));
        assertTrue(gate.update(new Object(), player, NONE));
        assertTrue(gate.update(world, new Object(), NONE));
        assertFalse(gate.update(null, null, NO_WORLD));
        assertEquals(6, resets.get());
    }
}
