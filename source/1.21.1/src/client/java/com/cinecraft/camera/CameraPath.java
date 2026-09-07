package com.cinecraft.camera;

import net.minecraft.world.phys.Vec3;

/** A renderer-independent camera trajectory sampled from zero to one. */
@FunctionalInterface
public interface CameraPath {
    Vec3 sample(double progress);
}
