package com.cinecraft.camera;

import net.minecraft.world.phys.Vec3;

/** A generated, level cinematic pan with optional slow push-in or pull-back. */
public record PanPath(
        Vec3 center,
        double startingAngle,
        double sweepRadians,
        double startingRadius,
        double endingRadius,
        double startingY,
        double endingY
) implements CameraPath {
    @Override
    public Vec3 sample(double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        double angle = startingAngle + sweepRadians * clamped;
        double radius = lerp(startingRadius, endingRadius, clamped);
        return new Vec3(
                center.x + Math.cos(angle) * radius,
                lerp(startingY, endingY, clamped),
                center.z + Math.sin(angle) * radius
        );
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }
}
