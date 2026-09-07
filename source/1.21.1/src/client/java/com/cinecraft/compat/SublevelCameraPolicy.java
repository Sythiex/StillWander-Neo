package com.cinecraft.compat;

import com.cinecraft.camera.CameraPose;
import com.cinecraft.camera.CinematicShot;
import com.cinecraft.camera.FovPath;
import com.cinecraft.camera.ShotReferenceFrame;
import com.cinecraft.director.EntityAction;
import com.cinecraft.director.EnvironmentProfile;
import com.cinecraft.director.Framing;
import com.cinecraft.director.SceneScanner;
import com.cinecraft.director.SceneSubject;
import com.cinecraft.director.ScreenPlacement;
import com.cinecraft.director.ShotComposition;
import com.cinecraft.director.ShotPlan;
import com.cinecraft.director.ShotType;
import com.cinecraft.director.SubjectType;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/** Optional policy at the director boundary; no shot choices or RNG draws occur outside sublevels. */
public final class SublevelCameraPolicy {
    private SubLevelAccess ship;
    private Vec3 velocity = Vec3.ZERO;

    public boolean active() { return ship != null; }

    /** Called once per director tick, including while a shot is running. */
    public boolean update(Minecraft client) {
        SubLevelAccess current = WorldCoordinates.shipsPresent() ? WorldCoordinates.shipOf(client.player) : null;
        boolean changed = current != ship;
        ship = current;
        if (ship == null) velocity = Vec3.ZERO;
        else {
            Vec3 origin = WorldCoordinates.entityPosition(client.player);
            Vec3 local = ship.logicalPose().transformPositionInverse(origin);
            Vec3 measured = origin.subtract(ship.lastPose().transformPosition(local)).scale(20.0);
            if (!WorldCoordinates.finite(measured)) measured = Vec3.ZERO;
            velocity = changed ? measured : velocity.lerp(measured, 0.25);
        }
        return changed;
    }

    public void reset() { ship = null; velocity = Vec3.ZERO; }

    public SceneSubject prepareSubject(Minecraft client, SceneScanner scanner, SceneSubject subject, boolean wide) {
        return active() && !wide && subject.type() == SubjectType.LANDSCAPE && velocity.horizontalDistance() >= 0.5
                ? scanner.landscapeAhead(client, velocity, subject) : subject;
    }

    public CinematicShot createShot(Minecraft client, SceneScanner scanner, SceneSubject subject,
                                    ShotPlan plan, EnvironmentProfile profile) {
        SubLevelAccess carrierShip = ship;
        var level = client.level;
        var player = client.player;
        Vec3 origin = WorldCoordinates.entityPosition(player);
        Vec3 local = carrierShip.logicalPose().transformPositionInverse(origin);
        BooleanSupplier available = () -> client.level == level && client.player == player
                && WorldCoordinates.shipOf(player) == carrierShip
                && level.hasChunkAt(BlockPos.containing(local))
                && SableCompanion.INSTANCE.getContaining(level, local) == carrierShip;
        boolean attached = subject.belongsTo(carrierShip);
        ShotReferenceFrame frame = attached ? new ShipReferenceFrame(carrierShip, available)
                : new StabilizedShipFrame(carrierShip, origin, available);
        boolean authoredView = plan.type() == ShotType.PROCEDURAL_PANORAMA
                || plan.type() == ShotType.PROCEDURAL_AERIAL || plan.type() == ShotType.PROCEDURAL_PASSAGE;
        boolean landmark = subject.type() == SubjectType.LANDSCAPE && !authoredView;
        TravelViewRules.Pass pass = new TravelViewRules.Pass(origin, subject.target(), velocity);
        return new CarriedShot(plan, frame,
                new CarriedShot.Tracking(subject.target(), subject::currentTarget, subject::isAvailable,
                        attached, subject.hasLiveTracking()), authoredView,
                (camera, focus) -> {
                    Vec3 passenger = WorldCoordinates.entityPosition(player);
                    if (!TravelViewRules.within(camera, passenger, profile.maxCameraDistance(), profile.maxVerticalRise())
                            || !WorldCoordinates.finite(focus)
                            || focus.distanceTo(passenger) > Math.max(24.0, profile.maxCameraDistance() * 2 + profile.sceneRadius())) return false;
                    if (!TravelViewRules.loadedLine(passenger, camera, level::hasChunk)
                            || !TravelViewRules.loadedLine(camera, focus, level::hasChunk)) return false;
                    // Reserve a little loaded ground around the rig before reaching a streaming edge.
                    for (int dx : new int[]{-4, 4}) for (int dz : new int[]{-4, 4}) {
                        if (!level.hasChunkAt(BlockPos.containing(camera.add(dx, 0, dz)))) return false;
                    }
                    if (!authoredView) {
                        Vec3 target = subject.currentTarget();
                        if (!TravelViewRules.loadedLine(camera, target, level::hasChunk)) return false;
                        if (WorldCoordinates.clip(level, camera, target, ClipContext.Fluid.NONE, player).getType() != HitResult.Type.MISS) return false;
                        if (landmark && !TravelViewRules.comfortable(camera, target, velocity)) return false;
                    }
                    return scanner.isViewUsable(client, camera, focus);
                }, () -> landmark && pass.finished(WorldCoordinates.entityPosition(player), subject.target(), System.nanoTime()));
    }

    public CinematicShot fallback(Minecraft client, SceneScanner scanner, CameraPose pose, EnvironmentProfile profile) {
        SceneSubject subject = scanner.playerSubject(client);
        ShotPlan plan = new ShotPlan(ShotType.PROCEDURAL_SUBJECT, progress -> pose.position(),
                progress -> subject.currentTarget(), progress -> subject.currentTarget(),
                FovPath.fixed(Float.isFinite(pose.fov()) ? pose.fov() : 60.0f), 4_000,
                new ShotComposition(Framing.MEDIUM, ScreenPlacement.CENTER, 0, EntityAction.STILL, false), "sublevel:fallback");
        return createShot(client, scanner, subject, plan, profile);
    }
}
