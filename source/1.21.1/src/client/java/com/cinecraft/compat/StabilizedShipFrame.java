package com.cinecraft.compat;

import com.cinecraft.camera.ShotReferenceFrame;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/** Carries world-aligned offsets by a point near the passenger, without rotating the rig. */
public final class StabilizedShipFrame implements ShotReferenceFrame {
    private final ShotReferenceFrame shipFrame;
    private final Vec3 anchor;
    private final Vec3 initial;

    public StabilizedShipFrame(SubLevelAccess ship, Vec3 worldAnchor, BooleanSupplier available) {
        shipFrame = new ShipReferenceFrame(ship, available);
        anchor = shipFrame.planned(worldAnchor);
        initial = worldAnchor;
    }

    public Vec3 planned(Vec3 point) { return point.subtract(initial); }
    public Vec3 live(Vec3 point) {
        return point.subtract(shipFrame.render(anchor, WorldCoordinates.samplePartialTick()));
    }
    public Vec3 render(Vec3 point, float partialTick) { return shipFrame.render(anchor, partialTick).add(point); }
    public boolean available() { return shipFrame.available(); }
}
