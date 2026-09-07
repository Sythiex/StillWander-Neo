package com.cinecraft.compat;

import com.cinecraft.camera.ShotReferenceFrame;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/** A shot's planned rig stays relative to the ship through translation and rotation. */
public final class ShipReferenceFrame implements ShotReferenceFrame {
    private final SubLevelAccess ship;
    private final Pose3d initial;
    private final BooleanSupplier available;

    public ShipReferenceFrame(SubLevelAccess ship, BooleanSupplier available) {
        this.ship = ship;
        this.initial = new Pose3d(ship.logicalPose());
        this.available = available;
    }

    public Vec3 planned(Vec3 point) { return initial.transformPositionInverse(point); }
    public Vec3 live(Vec3 point) { return WorldCoordinates.pose(ship).transformPositionInverse(point); }
    public Vec3 render(Vec3 point, float partialTick) {
        return (ship instanceof ClientSubLevelAccess clientShip
                ? clientShip.renderPose(partialTick) : ship.logicalPose()).transformPosition(point);
    }
    public boolean available() { return available.getAsBoolean(); }
}
