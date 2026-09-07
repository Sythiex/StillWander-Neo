package com.cinecraft.compat;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class OptionalActivityProbeTest {
    public static final class ReplayApi {
        public static ReplayApi instance;
        public Object recording;
        public Object replay;
        public Object getReplayHandler() { return replay; }
    }

    @Test
    void recordingAloneIsNotAReplaySession() {
        OptionalActivityProbe probe = new OptionalActivityProbe(() -> true, ReplayApi.class.getName(),
                "instance", "getReplayHandler", error -> fail(error));
        ReplayApi.instance = null;
        assertEquals(OptionalActivityProbe.State.INACTIVE, probe.query());
        ReplayApi.instance = new ReplayApi();
        ReplayApi.instance.recording = new Object();
        assertEquals(OptionalActivityProbe.State.INACTIVE, probe.query());
        ReplayApi.instance.replay = new Object();
        assertEquals(OptionalActivityProbe.State.ACTIVE, probe.query());
        ReplayApi.instance.replay = null;
        assertEquals(OptionalActivityProbe.State.INACTIVE, probe.query());
    }

    @Test
    void absentModDoesNotLinkItsClassesAndBrokenInstalledApiWarnsOnce() {
        AtomicInteger failures = new AtomicInteger();
        OptionalActivityProbe absent = new OptionalActivityProbe(() -> false, "absent.Mod", null,
                "isEnabled", error -> failures.incrementAndGet());
        assertEquals(OptionalActivityProbe.State.INACTIVE, absent.query());
        OptionalActivityProbe broken = new OptionalActivityProbe(() -> true, "absent.Mod", null,
                "isEnabled", error -> failures.incrementAndGet());
        assertEquals(OptionalActivityProbe.State.UNAVAILABLE, broken.query());
        assertEquals(OptionalActivityProbe.State.UNAVAILABLE, broken.query());
        assertEquals(1, failures.get());
    }
}
