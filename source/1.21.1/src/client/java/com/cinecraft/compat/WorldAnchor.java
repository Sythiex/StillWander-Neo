package com.cinecraft.compat;

import com.cinecraft.camera.ShotReferenceFrame;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A fixed feature can belong to a moving ship while its published target stays global. */
public record WorldAnchor(Level level, Vec3 local, SubLevelAccess ship) {
    public static WorldAnchor at(Level level, Vec3 point) {
        return new WorldAnchor(level, point, SableCompanion.INSTANCE.getContaining(level, point));
    }

    public Vec3 current() { return ship == null ? local : WorldCoordinates.pose(ship).transformPosition(local); }
    public boolean available() {
        return level.hasChunkAt(BlockPos.containing(local))
                && (ship == null || SableCompanion.INSTANCE.getContaining(level, local) == ship);
    }
    public ShotReferenceFrame referenceFrame() {
        return WorldCoordinates.referenceFrame(level, ship, current());
    }
}
