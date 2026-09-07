package com.cinecraft.camera;

import net.minecraft.world.phys.Vec3;

/** A straight, constant-speed camera rail used for corridors, paths, and gaps between scenery. */
public record RailPath(Vec3 start, Vec3 end) implements CameraPath {
    @Override
    public Vec3 sample(double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return start.lerp(end, clamped);
    }
}
