package com.cinecraft.compat;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Loaded only when Sable is present. Never use its logical broad phase for render samples. */
final class SableWorldQueries {
    private SableWorldQueries() { }

    static List<ShipRenderSample> renderSamples(Level level, float partialTick) {
        return SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                .map(ship -> ShipRenderSample.of(ship, ship instanceof ClientSubLevelAccess clientShip
                        ? clientShip.renderPose(partialTick) : ship.logicalPose()))
                .toList();
    }

    static BlockHitResult clipUnprojected(Level level, Vec3 from, Vec3 to, ClipContext.Fluid fluid, Entity entity) {
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, fluid, entity);
        ((ClipContextExtension) context).sable$setDoNotProject(true);
        return level.clip(context);
    }
}
