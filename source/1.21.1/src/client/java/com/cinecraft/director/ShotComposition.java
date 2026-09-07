package com.cinecraft.director;

import net.minecraft.world.phys.Vec3;

/** Editorial metadata used to score cuts and offset aim from dead-center framing. */
public record ShotComposition(
        Framing framing,
        ScreenPlacement placement,
        int movementDirection,
        EntityAction action,
        boolean rackFocus
) {
    /** Compose against the camera that will actually be rendered. */
    public Vec3 focus(Vec3 camera, Vec3 subject, float fov) {
        Vec3 view = subject.subtract(camera);
        double horizontal = Math.sqrt(view.x * view.x + view.z * view.z);
        if (horizontal < 0.001) return subject;
        Vec3 right = new Vec3(-view.z / horizontal, 0.0, view.x / horizontal);
        double fovScale = Math.tan(Math.toRadians(Math.max(24.0, Math.min(90.0, fov)) * 0.5));
        double placementOffset = placement.direction() * Math.min(3.5, horizontal * fovScale * 0.16);
        double headroom = switch (framing) {
            case DETAIL -> -0.02;
            case CLOSE -> -0.035;
            case MEDIUM -> -0.055;
            case WIDE, EXTREME_WIDE -> -0.075;
        };
        return subject.add(right.scale(placementOffset)).add(0.0, horizontal * headroom, 0.0);
    }
}
