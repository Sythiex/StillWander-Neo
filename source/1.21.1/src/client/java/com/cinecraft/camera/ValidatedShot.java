package com.cinecraft.camera;

import java.util.function.Predicate;

/** Revalidates untracked shots so newly moving geometry cannot invalidate an old survey silently. */
public final class ValidatedShot implements CinematicShot {
    private final CinematicShot shot;
    private final Predicate<CameraPose> safe;
    private boolean invalid;

    public ValidatedShot(CinematicShot shot, Predicate<CameraPose> safe) {
        this.shot = shot;
        this.safe = safe;
    }

    public CameraPose sample(float partialTick) {
        if (invalid) return null;
        CameraPose pose = shot.sample(partialTick);
        if (pose == null || !safe.test(pose)) {
            invalid = true;
            return null;
        }
        return pose;
    }

    public boolean finished() { return invalid || shot.finished(); }
}
