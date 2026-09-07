package com.cinecraft.director;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** One accepted planning decision retained in a bounded runtime diagnostic buffer. */
public record ShotTrace(
        long index,
        String subjectKey,
        SubjectType subjectType,
        ShotType shotType,
        Framing scale,
        CameraSide cameraSide,
        ScreenDirection screenDirection,
        Vec3 startCamera,
        Vec3 endCamera,
        float openingFov,
        float endingFov,
        long durationMillis,
        String source,
        double score,
        double continuityAdjustment,
        List<String> continuityReasons,
        int rejectedCandidates,
        long planningMicros
) {
    public ShotTrace {
        continuityReasons = List.copyOf(continuityReasons);
    }

    public String summary() {
        return String.format(
                Locale.ROOT,
                "#%d %s %s score %.1f continuity %+.1f in %d us",
                index,
                scale,
                subjectType,
                score,
                continuityAdjustment,
                planningMicros
        );
    }
}
