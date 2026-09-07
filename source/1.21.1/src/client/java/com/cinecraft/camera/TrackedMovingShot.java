package com.cinecraft.camera;

import net.minecraft.world.phys.Vec3;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** A damped entity-relative rig that removes tick-step jitter from live tracking. */
public final class TrackedMovingShot implements CinematicShot {
    private static final double TRACKING_TIME_CONSTANT_SECONDS = 0.70;
    private static final long SUBJECT_LOSS_GRACE_NANOS = 1_200_000_000L;
    private static final long UNSAFE_VIEW_GRACE_NANOS = 1_500_000_000L;

    private final CameraPath basePath;
    private final CameraPath liveFocusPath;
    private final Supplier<Vec3> trackingCenter;
    private final BooleanSupplier targetAvailable;
    private final Vec3 originalTarget;
    private final FovPath fovPath;
    private final BiFunction<Vec3, Vec3, Vec3> cameraResolver;
    private final ShotReferenceFrame referenceFrame;
    private final long startedNanos = System.nanoTime();
    private final long durationNanos;
    private long previousNanos = startedNanos;
    private Vec3 smoothedTarget;
    private Vec3 smoothedFocus;
    private Vec3 lastSafeCamera;
    private Vec3 lastSafeFocus;
    private long unavailableSince;
    private long unsafeSince;
    private boolean gracefulExit;

    public TrackedMovingShot(
            CameraPath basePath,
            CameraPath liveFocusPath,
            Supplier<Vec3> trackingCenter,
            BooleanSupplier targetAvailable,
            Vec3 originalTarget,
            FovPath fovPath,
            long durationMillis,
            BiFunction<Vec3, Vec3, Vec3> cameraResolver
    ) {
        this(basePath, liveFocusPath, trackingCenter, targetAvailable, originalTarget, fovPath,
                durationMillis, cameraResolver, ShotReferenceFrame.WORLD);
    }

    public TrackedMovingShot(
            CameraPath basePath, CameraPath liveFocusPath, Supplier<Vec3> trackingCenter,
            BooleanSupplier targetAvailable, Vec3 originalTarget, FovPath fovPath, long durationMillis,
            BiFunction<Vec3, Vec3, Vec3> cameraResolver, ShotReferenceFrame referenceFrame
    ) {
        this.basePath = basePath;
        this.liveFocusPath = liveFocusPath;
        this.trackingCenter = trackingCenter;
        this.targetAvailable = targetAvailable;
        this.originalTarget = referenceFrame.planned(originalTarget);
        this.referenceFrame = referenceFrame;
        this.fovPath = fovPath;
        this.smoothedTarget = this.originalTarget;
        this.smoothedFocus = referenceFrame.live(liveFocusPath.sample(0.0));
        this.durationNanos = durationMillis * 1_000_000L;
        this.cameraResolver = cameraResolver;
        this.lastSafeCamera = basePath.sample(0.0);
        this.lastSafeFocus = originalTarget;
    }

    @Override
    public CameraPose sample(float tickDelta) {
        if (!referenceFrame.available()) {
            gracefulExit = true;
            return null;
        }
        long now = System.nanoTime();
        double deltaSeconds = Math.min(0.10, Math.max(0.0, (now - previousNanos) / 1_000_000_000.0));
        previousNanos = now;
        double alpha = 1.0 - Math.exp(-deltaSeconds / TRACKING_TIME_CONSTANT_SECONDS);

        if (!targetAvailable.getAsBoolean()) {
            if (unavailableSince == 0L) unavailableSince = now;
            if (now - unavailableSince >= SUBJECT_LOSS_GRACE_NANOS) gracefulExit = true;
        } else {
            unavailableSince = 0L;
        }

        Vec3 rawTarget = targetAvailable.getAsBoolean() ? referenceFrame.live(trackingCenter.get()) : smoothedTarget;
        Vec3 rawFocus = referenceFrame.live(liveFocusPath.sample(
                Math.min(1.0, (now - startedNanos) / (double) durationNanos)));
        if (!finite(rawTarget) || !finite(rawFocus)) {
            gracefulExit = true;
            return null;
        }
        smoothedTarget = smoothedTarget.lerp(rawTarget, alpha);
        Vec3 trackingOffset = smoothedTarget.subtract(originalTarget);

        double progress = Math.min(1.0, (now - startedNanos) / (double) durationNanos);
        Vec3 desiredCamera = referenceFrame.render(referenceFrame.planned(basePath.sample(progress)).add(trackingOffset), tickDelta);
        smoothedFocus = smoothedFocus.lerp(rawFocus, alpha);
        Vec3 renderedFocus = referenceFrame.render(smoothedFocus, tickDelta);
        Vec3 resolvedCamera = cameraResolver.apply(desiredCamera, renderedFocus);
        if (resolvedCamera != null) {
            lastSafeCamera = resolvedCamera;
            lastSafeFocus = renderedFocus;
            unsafeSince = 0L;
        } else {
            Vec3 safeFallback = cameraResolver.apply(lastSafeCamera, lastSafeFocus);
            if (safeFallback == null) {
                gracefulExit = true;
                return null;
            }
            lastSafeCamera = safeFallback;
            if (unsafeSince == 0L) unsafeSince = now;
            if (now - unsafeSince >= UNSAFE_VIEW_GRACE_NANOS) gracefulExit = true;
        }
        return LookAt.pose(lastSafeCamera, lastSafeFocus)
                .withFov(fovPath.sample(progress));
    }

    @Override
    public boolean finished() {
        return gracefulExit || System.nanoTime() - startedNanos >= durationNanos;
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
