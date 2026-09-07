package com.cinecraft.camera;

import net.minecraft.world.phys.Vec3;

/** Separates planned, live, and rendered coordinates for a moving camera rig. */
public interface ShotReferenceFrame {
    ShotReferenceFrame WORLD = new ShotReferenceFrame() {
        public Vec3 planned(Vec3 point) { return point; }
        public Vec3 live(Vec3 point) { return point; }
        public Vec3 render(Vec3 point, float partialTick) { return point; }
        public boolean available() { return true; }
    };

    Vec3 planned(Vec3 worldPoint);
    Vec3 live(Vec3 worldPoint);
    Vec3 render(Vec3 framePoint, float partialTick);
    boolean available();
}
