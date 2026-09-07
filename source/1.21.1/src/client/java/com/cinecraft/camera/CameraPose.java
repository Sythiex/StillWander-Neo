package com.cinecraft.camera;

import net.minecraft.world.phys.Vec3;

public record CameraPose(Vec3 position, float yaw, float pitch, float fov, float focusDistance) {
    public CameraPose(Vec3 position, float yaw, float pitch) {
        this(position, yaw, pitch, Float.NaN, Float.NaN);
    }

    public CameraPose withFov(float value) {
        return new CameraPose(position, yaw, pitch, value, focusDistance);
    }

    public CameraPose withFocusDistance(float value) {
        return new CameraPose(position, yaw, pitch, fov, value);
    }
}
