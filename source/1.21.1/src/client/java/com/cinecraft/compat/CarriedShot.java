package com.cinecraft.compat;

import com.cinecraft.camera.CameraPose;
import com.cinecraft.camera.CinematicShot;
import com.cinecraft.camera.LookAt;
import com.cinecraft.camera.ShotReferenceFrame;
import com.cinecraft.director.ShotPlan;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Sublevel execution: transport owns position; subject tracking or an authored sweep owns aim. */
public final class CarriedShot implements CinematicShot {
    public record Tracking(Vec3 initial, Supplier<Vec3> target, BooleanSupplier available,
                           boolean followsCamera, boolean smooth) { }

    private final ShotPlan plan;
    private final ShotReferenceFrame carrier;
    private final Tracking tracking;
    private final BiPredicate<Vec3, Vec3> safe;
    private final BooleanSupplier endComposition;
    private final LongSupplier clock;
    private final long started;
    private final Vec3 initialTarget;
    private final Vec3[] authoredViews;
    private long previous;
    private Vec3 smoothedTarget;
    private Vec3 smoothedSubject;
    private Vec3 lastSafeOffset;
    private boolean cut;

    public CarriedShot(ShotPlan plan, ShotReferenceFrame carrier, Tracking tracking, boolean authoredView,
                       BiPredicate<Vec3, Vec3> safe, BooleanSupplier endComposition) {
        this(plan, carrier, tracking, authoredView, safe, endComposition, System::nanoTime);
    }

    CarriedShot(ShotPlan plan, ShotReferenceFrame carrier, Tracking tracking, boolean authoredView,
                BiPredicate<Vec3, Vec3> safe, BooleanSupplier endComposition, LongSupplier clock) {
        this.plan = plan;
        this.carrier = carrier;
        this.tracking = tracking;
        this.safe = safe;
        this.endComposition = endComposition;
        this.clock = clock;
        started = previous = clock.getAsLong();
        initialTarget = carrier.planned(tracking.initial());
        smoothedTarget = initialTarget;
        Vec3 subject = plan.subjectPath().sample(0);
        smoothedSubject = tracking.followsCamera() ? carrier.planned(subject) : subject;
        // Freeze the planned viewing sweep. Some planner closures consult live subject frames.
        // Store vectors in the carrier's coordinates: stabilized carriers leave compass axes alone.
        authoredViews = authoredView ? new Vec3[65] : null;
        if (authoredViews != null) {
            for (int i = 0; i < authoredViews.length; i++) {
                double progress = i / (double) (authoredViews.length - 1);
                authoredViews[i] = carrier.planned(plan.focusPath().sample(progress))
                        .subtract(carrier.planned(plan.path().sample(progress)));
            }
        }
    }

    @Override
    public CameraPose sample(float partialTick) {
        if (!carrier.available() || !tracking.available().getAsBoolean()) return invalid();
        long now = clock.getAsLong();
        double progress = Math.min(1.0, Math.max(0.0, (now - started) / (plan.durationMillis() * 1_000_000.0)));
        double seconds = Math.min(0.10, Math.max(0.0, (now - previous) / 1_000_000_000.0));
        previous = now;
        double alpha = 1.0 - Math.exp(-seconds / 0.70);
        float fov = plan.fovPath().sample(progress);
        if (Float.isInfinite(fov)) return invalid();
        Vec3 offset = carrier.planned(plan.path().sample(progress));
        Vec3 subject = plan.subjectPath().sample(progress);
        if (!WorldCoordinates.finite(offset) || !WorldCoordinates.finite(subject)) return invalid();
        if (authoredViews == null) {
            if (tracking.followsCamera()) {
                Vec3 raw = tracking.target().get();
                if (!WorldCoordinates.finite(raw)) return invalid();
                smoothedTarget = smoothedTarget.lerp(carrier.live(raw), alpha);
                offset = offset.add(smoothedTarget.subtract(initialTarget));
                subject = carrier.live(subject);
            }
            smoothedSubject = tracking.smooth() ? smoothedSubject.lerp(subject, alpha) : subject;
            subject = tracking.followsCamera() ? carrier.render(smoothedSubject, partialTick) : smoothedSubject;
        }
        Vec3 view = authoredViews == null ? null : authoredView(progress);
        if (!WorldCoordinates.finite(subject) || (view != null && !WorldCoordinates.finite(view))) return invalid();
        Vec3 desired = carrier.render(offset, partialTick);
        if (!WorldCoordinates.finite(desired)) return invalid();
        if (endComposition.getAsBoolean()) cut = true;
        CameraPose pose = resolve(desired, subject, view, fov, partialTick);
        if (pose != null) {
            lastSafeOffset = carrier.live(pose.position());
            return pose;
        }
        // Request an ordinary cut. Until the next tick, only a transported, revalidated fallback is legal.
        cut = true;
        if (lastSafeOffset == null) return null;
        return resolve(carrier.render(lastSafeOffset, partialTick), subject, view, fov, partialTick);
    }

    private CameraPose resolve(Vec3 desired, Vec3 subject, Vec3 view, float fov, float partialTick) {
        if (!WorldCoordinates.finite(desired)) return null;
        for (double lift = 0; lift <= 2.4; lift += 0.15) {
            Vec3 camera = desired.add(0, lift, 0);
            Vec3 focus = view == null
                    ? plan.composition().focus(camera, subject, Float.isFinite(fov) ? fov : 60.0f)
                    : carrier.render(carrier.live(camera).add(view), partialTick);
            if (!WorldCoordinates.finite(focus) || focus.distanceToSqr(camera) < 0.000001) return null;
            if (safe.test(camera, focus)) return LookAt.pose(camera, focus).withFov(fov);
        }
        return null;
    }

    private Vec3 authoredView(double progress) {
        double index = progress * (authoredViews.length - 1);
        int first = Math.min(authoredViews.length - 2, (int) index);
        return authoredViews[first].lerp(authoredViews[first + 1], index - first);
    }

    private CameraPose invalid() { cut = true; return null; }

    @Override
    public boolean finished() {
        return cut || clock.getAsLong() - started >= plan.durationMillis() * 1_000_000L;
    }
}
