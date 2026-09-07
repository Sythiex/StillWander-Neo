package com.cinecraft.compat;

import com.cinecraft.camera.ShotReferenceFrame;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.List;

/** Scene planning uses global logical coordinates; frame sampling uses global render coordinates. */
public final class WorldCoordinates {
    private static float partialTick = Float.NaN;
    private static List<ShipRenderSample> renderShips;
    private static final Vec3 INVALID = new Vec3(Double.NaN, Double.NaN, Double.NaN);

    private WorldCoordinates() { }

    public static boolean shipsPresent() {
        return ModList.get() != null && ModList.get().isLoaded("sable");
    }

    static Pose3dc pose(SubLevelAccess ship) {
        return Float.isFinite(partialTick) && ship instanceof ClientSubLevelAccess clientShip
                ? clientShip.renderPose(partialTick) : ship.logicalPose();
    }

    public static float samplePartialTick() {
        return Float.isFinite(partialTick) ? partialTick : 1.0f;
    }

    public static Vec3 entityOffset(Entity entity, Vec3 offset) {
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(entity);
        return entityPosition(entity).add(ship == null ? offset : pose(ship).transformNormal(offset));
    }

    public static <T> T duringRender(Level level, float framePartialTick, Supplier<T> action) {
        float previous = partialTick;
        List<ShipRenderSample> previousShips = renderShips;
        partialTick = framePartialTick;
        try {
            renderShips = shipsPresent() ? SableWorldQueries.renderSamples(level, framePartialTick) : null;
            return action.get();
        } finally {
            partialTick = previous;
            renderShips = previousShips;
        }
    }

    /** Inputs and render-sampled hits use global coordinates; logical hits follow Sable's API. */
    public static BlockHitResult clip(Level level, Vec3 from, Vec3 to, ClipContext.Fluid fluid, Entity entity) {
        if (renderShips == null) {
            return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, fluid, entity));
        }
        return ShipRenderSample.clip(renderShips, from, to,
                (start, end) -> SableWorldQueries.clipUnprojected(level, start, end, fluid, entity));
    }

    public static Vec3 toWorld(Level level, Vec3 point) {
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, point);
        if (ship != null) return pose(ship).transformPosition(point);
        return SableCompanion.INSTANCE.isInPlotGrid(level, point) ? INVALID : point;
    }

    public static Vec3 entityPosition(Entity entity) {
        Vec3 point = entity.position();
        if (SableCompanion.INSTANCE.isInPlotGrid(entity)) return toWorld(entity.level(), point);
        if (!Float.isFinite(partialTick)) return point;
        SubLevelAccess ship = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity);
        if (ship != null) {
            Vec3 previous = ship.lastPose().transformPositionInverse(new Vec3(entity.xo, entity.yo, entity.zo));
            Vec3 current = ship.logicalPose().transformPositionInverse(point);
            return pose(ship).transformPosition(previous.lerp(current, partialTick));
        }
        return entity.getPosition(partialTick);
    }

    public static Vec3 entityVelocity(Entity entity) {
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(entity);
        return ship == null ? entity.getDeltaMovement() : pose(ship).transformNormal(entity.getDeltaMovement());
    }

    public static ShotReferenceFrame referenceFrame(Entity entity, Vec3 target) {
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(entity);
        if (ship == null) ship = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(entity);
        return referenceFrame(entity.level(), ship, target);
    }

    public static ShotReferenceFrame referenceFrame(Level level, SubLevelAccess ship, Vec3 target) {
        if (ship == null) return ShotReferenceFrame.WORLD;
        Vec3 localAnchor = ship.logicalPose().transformPositionInverse(target);
        return new ShipReferenceFrame(ship, () -> level.hasChunkAt(BlockPos.containing(localAnchor))
                && SableCompanion.INSTANCE.getContaining(level, localAnchor) == ship);
    }

    /** Visits the ordinary block and every loaded ship block overlapping a global sample. */
    public static boolean blocksAt(Level level, Vec3 point, BiConsumer<BlockPos, BlockState> visitor) {
        BlockPos pos = BlockPos.containing(point);
        boolean loaded = level.hasChunkAt(pos);
        if (loaded) visitor.accept(pos, level.getBlockState(pos));
        if (!shipsPresent()) return loaded;
        BoundingBox3d bounds = new BoundingBox3d(point.subtract(1.0, 1.0, 1.0), point.add(1.0, 1.0, 1.0));
        if (renderShips != null) {
            for (ShipRenderSample ship : renderShips) {
                if (!ship.intersects(bounds)) continue;
                Vec3 localPoint = ship.pose().transformPositionInverse(point);
                if (!finite(localPoint)) { loaded = false; continue; }
                BlockPos local = BlockPos.containing(localPoint);
                if (level.hasChunkAt(local)) visitor.accept(local, level.getBlockState(local));
                else loaded = false;
            }
            return loaded;
        }
        for (SubLevelAccess ship : SableCompanion.INSTANCE.getAllIntersecting(level, bounds)) {
            BlockPos local = BlockPos.containing(pose(ship).transformPositionInverse(point));
            if (level.hasChunkAt(local)) visitor.accept(local, level.getBlockState(local));
            else loaded = false;
        }
        return loaded;
    }

    public static boolean clearAt(Level level, Vec3 point) {
        if (!finite(point) || !level.hasChunkAt(BlockPos.containing(point))) return false;
        boolean[] clear = {true};
        boolean loaded = blocksAt(level, point, (pos, state) -> {
            if (!state.getCollisionShape(level, pos).isEmpty()) clear[0] = false;
        });
        return loaded && clear[0];
    }

    public static boolean fluidAt(Level level, Vec3 point) {
        boolean[] fluid = {false};
        blocksAt(level, point, (pos, state) -> {
            if (!state.getFluidState().isEmpty()) fluid[0] = true;
        });
        return fluid[0];
    }

    public static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
