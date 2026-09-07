package com.cinecraft.smoke;

import com.cinecraft.camera.ShotReferenceFrame;
import com.cinecraft.CinecraftClient;
import com.cinecraft.camera.CameraPose;
import com.cinecraft.camera.CinematicShot;
import com.cinecraft.camera.LookAt;
import com.cinecraft.compat.WorldCoordinates;
import com.cinecraft.compat.CarriedShot;
import com.cinecraft.compat.SublevelCameraPolicy;
import com.cinecraft.camera.FovPath;
import com.cinecraft.compat.CinecraftFlawlessFrames;
import com.cinecraft.director.EntityAction;
import com.cinecraft.director.Framing;
import com.cinecraft.director.SceneScanner;
import com.cinecraft.director.SceneSubject;
import com.cinecraft.director.ScreenPlacement;
import com.cinecraft.director.ShotComposition;
import com.cinecraft.director.ShotPlan;
import com.cinecraft.director.ShotType;
import com.cinecraft.director.SubjectType;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.UUID;

/** World writes are confined to this disposable development fixture. */
final class ShipSmokeFixture {
    private static volatile ServerSubLevel serverShip;
    private static volatile UUID shipId;
    private static volatile Vec3 localCenter;
    private static volatile Throwable failure;
    private static ShotReferenceFrame frame;
    private static Vec3 initialCenter;
    private static Vec3 localRig;

    static void assemble(Minecraft client) {
        BlockPos anchor = client.player.blockPosition().above(4);
        UUID playerId = client.player.getUUID();
        client.getSingleplayerServer().execute(() -> {
            try {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                var level = player.serverLevel();
                var blocks = new ArrayList<BlockPos>();
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                    BlockPos block = anchor.offset(x, 0, z);
                    level.setBlockAndUpdate(block, Blocks.OAK_PLANKS.defaultBlockState());
                    blocks.add(block);
                }
                serverShip = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks,
                        new BoundingBox3i(anchor.offset(-4, 0, -4), anchor.offset(4, 1, 4)));
                localCenter = Vec3.atCenterOf(serverShip.getPlot().getCenterBlock());
                shipId = serverShip.getUniqueId();
            } catch (Throwable error) { failure = error; }
        });
    }

    static void board(Minecraft client) {
        UUID playerId = client.player.getUUID();
        client.getSingleplayerServer().execute(() -> {
            try {
                if (serverShip == null) throw new AssertionError("Ship not assembled", failure);
                Vec3 center = serverShip.logicalPose().transformPosition(Vec3.atCenterOf(serverShip.getPlot().getCenterBlock()));
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                player.teleportTo(center.x, center.y + 0.6, center.z);
            } catch (Throwable error) { failure = error; }
        });
    }

    static void beginMotion(Minecraft client) {
        if (failure != null) throw new AssertionError("Ship fixture failed", failure);
        SubLevelAccess ship = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(client.player);
        if (ship == null || !ship.getUniqueId().equals(shipId)) throw new AssertionError("Player must be aboard assembled ship");
        initialCenter = ship.logicalPose().transformPosition(localCenter);
        frame = WorldCoordinates.referenceFrame(client.player, initialCenter);
        localRig = frame.planned(initialCenter.add(6, 4, 0));
        client.getSingleplayerServer().execute(() -> {
            try {
                RigidBodyHandle.of(serverShip).addLinearAndAngularVelocity(
                        new Vector3d(3, 3, 0), new Vector3d(0, 0.6, 0));
            } catch (Throwable error) { failure = error; }
        });
    }

    static void sit(Minecraft client) {
        UUID playerId = client.player.getUUID();
        client.getSingleplayerServer().execute(() -> {
            try {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                BlockPos seat = serverShip.getPlot().getCenterBlock().above().east(2);
                var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "white_seat"));
                if (block == Blocks.AIR) throw new AssertionError("Create seat missing");
                player.serverLevel().setBlockAndUpdate(seat, block.defaultBlockState());
                Class.forName("com.simibubi.create.content.contraptions.actors.seat.SeatBlock")
                        .getMethod("sitDown", Level.class, BlockPos.class, Entity.class)
                        .invoke(null, player.serverLevel(), seat, player);
                if (!player.isPassenger()) throw new AssertionError("Player did not mount seat");
            } catch (Throwable error) { failure = error; }
        });
    }

    static void validateSeatedCamera(Minecraft client) throws ReflectiveOperationException {
        if (failure != null) throw new AssertionError("Seat fixture failed", failure);
        if (client.player.getVehicle() == null || !client.player.getVehicle().getClass().getName().endsWith("SeatEntity")) {
            throw new AssertionError("Client player must ride an actual Create seat");
        }
        var ship = (ClientSubLevelAccess) SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(client.player);
        if (ship == null || !ship.getUniqueId().equals(shipId)) throw new AssertionError("Seat must belong to fixture ship");
        Camera camera = client.gameRenderer.getMainCamera();
        var shotField = CinecraftClient.DIRECTOR.getClass().getDeclaredField("currentShot");
        shotField.setAccessible(true);
        Object savedShot = shotField.get(CinecraftClient.DIRECTOR);
        try {
            for (float partial : new float[]{0.25f, 0.5f, 1.0f}) {
                var render = ship.renderPose(partial);
                Vec3 focus = render.transformPosition(localCenter.add(2, 2, 0));
                Vec3 position = render.transformPosition(localCenter.add(7, 5, 6));
                CameraPose pose = LookAt.pose(position, focus);
                Vec3 expected = focus.subtract(position).normalize();
                // Establish that Sable's ordinary seated camera really inherits a nontrivial rotation.
                var inherited = dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper
                        .getEntityOrientation(client.player,
                                level -> ((ClientSubLevelAccess) level).renderPose(partial), partial,
                                dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper.Type.CAMERA);
                if (inherited == null || new Vec3(inherited.transform(expected.toVector3f()))
                        .distanceTo(expected) < 0.05) throw new AssertionError("Seated fixture needs inherited ship rotation");
                shotField.set(CinecraftClient.DIRECTOR, new CinematicShot() {
                    public CameraPose sample(float delta) { return pose; }
                    public boolean finished() { return false; }
                });
                // Exercise the transformed Camera.setup, including both mods' real mixins.
                camera.setup(client.level, client.player, false, false, partial);
                assertDirection(new Vec3(camera.getLookVector()), expected, "seated cinematic look vector");
                assertDirection(new Vec3(new Vector3f(0, 0, -1).rotate(camera.rotation())), expected,
                        "seated cinematic quaternion");
                // Use full-precision trig: Minecraft's directionFromRotation lookup table
                // is less accurate than the tolerance used for the camera quaternion.
                double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
                assertDirection(new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                        Math.cos(yaw) * Math.cos(pitch)), expected, "seated cinematic Euler angles");
                if (camera.getPosition().distanceTo(position) > 0.00001 || camera.getRoll() != 0) {
                    throw new AssertionError("Cinematic position and zero roll must match world pose");
                }
                Vector3f up = new Vector3f(0, 1, 0).rotate(camera.rotation());
                Vector3f left = new Vector3f(-1, 0, 0).rotate(camera.rotation());
                assertDirection(new Vec3(camera.getUpVector()), new Vec3(up), "camera up basis");
                assertDirection(new Vec3(camera.getLeftVector()), new Vec3(left), "camera left basis");
            }
            shotField.set(CinecraftClient.DIRECTOR, null);
            Camera ordinary = new Camera();
            ordinary.setup(client.level, client.player, false, false, 1);
            camera.setup(client.level, client.player, false, false, 1);
            assertDirection(new Vec3(camera.getLookVector()), new Vec3(ordinary.getLookVector()),
                    "ordinary seated camera restored after cinematic ownership ends");
        } finally {
            shotField.set(CinecraftClient.DIRECTOR, savedShot);
            camera.setup(client.level, client.player, false, false, 1);
        }
    }

    private static void assertDirection(Vec3 actual, Vec3 expected, String label) {
        if (!WorldCoordinates.finite(actual) || actual.distanceTo(expected) > 0.00001) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    static void validatePassengerLandscapes(Minecraft client) throws ReflectiveOperationException {
        var ship = (ClientSubLevelAccess) WorldCoordinates.shipOf(client.player);
        if (ship == null || !client.player.isPassenger()) throw new AssertionError("Passenger landscape fixture needs a seated player");
        var shotField = CinecraftClient.DIRECTOR.getClass().getDeclaredField("currentShot");
        shotField.setAccessible(true);
        Object savedShot = shotField.get(CinecraftClient.DIRECTOR);
        if (!(savedShot instanceof CarriedShot)) throw new AssertionError("Director must use carried execution aboard a sublevel");
        Pose3d logical = (Pose3d) ship.logicalPose();
        Pose3d last = (Pose3d) ship.lastPose();
        Pose3d savedLogical = new Pose3d(logical), savedLast = new Pose3d(last);
        Camera camera = client.gameRenderer.getMainCamera();
        try {
            SceneScanner scanner = new SceneScanner(new java.util.Random(781));
            var profile = scanner.survey(client);
            for (boolean panorama : new boolean[]{false, true}) {
                logical.set(savedLogical);
                last.set(savedLast);
                ship.renderPose(0); // Clear Sable's pose cache after fixture edits.
                Vec3 origin = WorldCoordinates.entityPosition(client.player);
                Vec3 localOrigin = logical.transformPositionInverse(origin);
                Vec3 position = origin.add(5, 10, 7);
                Vec3 target = origin.add(30, 10, 20);
                var composition = new ShotComposition(Framing.WIDE, ScreenPlacement.LEFT_THIRD, 0, EntityAction.STILL, false);
                var subject = new SceneSubject(SubjectType.LANDSCAPE, target, "smoke:passing-scenery");
                var plan = new ShotPlan(panorama ? ShotType.PROCEDURAL_PANORAMA : ShotType.PROCEDURAL_TRAVERSE,
                        progress -> position, progress -> composition.focus(position, target, 60),
                        progress -> target, FovPath.fixed(60), 60_000, composition, "smoke:passenger");
                SublevelCameraPolicy policy = new SublevelCameraPolicy();
                policy.update(client);
                CinematicShot carried = policy.createShot(client, scanner, subject, plan, profile);
                shotField.set(CinecraftClient.DIRECTOR, carried);
                last.set(logical);
                logical.position().add(8, 0, 0);
                logical.orientation().rotateY(0.7).rotateX(0.3).rotateZ(0.4);
                ship.renderPose(0);
                for (float partial : new float[]{0.25f, 0.5f, 1}) {
                    Vec3 displacement = ship.renderPose(partial).transformPosition(localOrigin).subtract(origin);
                    Vec3 expectedPosition = position.add(displacement);
                    Vec3 expectedFocus = panorama ? composition.focus(position, target, 60).add(displacement)
                            : composition.focus(expectedPosition, target, 60);
                    camera.setup(client.level, client.player, false, false, partial);
                    if (camera.getPosition().distanceTo(expectedPosition) > 0.0001 || camera.getRoll() != 0) {
                        throw new AssertionError("Landscape camera must travel with passenger without rotating its offset: panorama=" + panorama);
                    }
                    assertDirection(new Vec3(camera.getLookVector()), expectedFocus.subtract(expectedPosition).normalize(),
                            panorama ? "compass-stable passenger panorama" : "transported camera keeps world landmark");
                }
            }
        } finally {
            logical.set(savedLogical);
            last.set(savedLast);
            ship.renderPose(0);
            shotField.set(CinecraftClient.DIRECTOR, savedShot);
            camera.setup(client.level, client.player, false, false, 1);
        }
    }

    static void validateTravelCut(Minecraft client) throws ReflectiveOperationException {
        if (!CinecraftClient.isRecordingMode()) throw new AssertionError("Travel cut must start during capture");
        Vec3 origin = WorldCoordinates.entityPosition(client.player);
        var composition = new ShotComposition(Framing.WIDE, ScreenPlacement.CENTER, 0, EntityAction.STILL, false);
        var plan = new ShotPlan(ShotType.PROCEDURAL_PANORAMA, p -> origin.add(5, 8, 0),
                p -> origin.add(20, 8, 0), p -> origin.add(20, 8, 0), FovPath.fixed(60), 60_000, composition, "smoke:lost-view");
        var field = CinecraftClient.DIRECTOR.getClass().getDeclaredField("currentShot");
        field.setAccessible(true);
        field.set(CinecraftClient.DIRECTOR, new CarriedShot(plan, ShotReferenceFrame.WORLD,
                new CarriedShot.Tracking(origin, () -> origin, () -> false, false, false), true,
                (camera, focus) -> false, () -> false));
        var tick = CinecraftClient.class.getDeclaredMethod("tick", net.neoforged.neoforge.client.event.ClientTickEvent.Post.class);
        tick.setAccessible(true);
        tick.invoke(null, new Object[]{null});
        if (!CinecraftClient.isRecordingMode() || !CinecraftClient.DIRECTOR.isActive() || !CinecraftFlawlessFrames.isRequested()
                || !(field.get(CinecraftClient.DIRECTOR) instanceof CarriedShot)
                || CinecraftClient.DIRECTOR.pose(1) == null) {
            throw new AssertionError("Lost travel view must cut to a safe carried shot without releasing capture or FPS");
        }
    }

    static void validate(Minecraft client) {
        if (failure != null) throw new AssertionError("Ship fixture failed", failure);
        SubLevelAccess ship = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(client.player);
        if (ship == null) throw new AssertionError("Player lost ship tracking during motion");
        Vec3 center = ship.logicalPose().transformPosition(localCenter);
        if (center.distanceTo(initialCenter) < 0.05) throw new AssertionError("Ship must actually move");
        if (WorldCoordinates.clearAt(client.level, center)) throw new AssertionError("Ship hull must block global camera clearance");
        Vec3 hit = client.level.clip(new ClipContext(center.add(0, 3, 0), center.add(0, -0.2, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player)).getLocation();
        if (WorldCoordinates.toWorld(client.level, hit).distanceTo(center) > 1.0) throw new AssertionError("Ship raycast must project to global coordinates");
        WorldCoordinates.duringRender(client.level, 0.5f, () -> {
            Vec3 camera = frame.render(localRig, 0.5f);
            if (!frame.available() || !WorldCoordinates.finite(camera)
                    || frame.live(camera).distanceTo(localRig) > 0.00001) {
                throw new AssertionError("Interpolated ship rig must retain its local offset");
            }
            if (!WorldCoordinates.clearAt(client.level, camera)) throw new AssertionError("Ship rig clearance");
            return null;
        });
        validateFastRenderPose(client, (ClientSubLevelAccess) ship);
    }

    private static void validateFastRenderPose(Minecraft client, ClientSubLevelAccess ship) {
        Pose3d previous = (Pose3d) ship.lastPose();
        Pose3d saved = new Pose3d(previous);
        try {
            // Deterministic client-only fixture: a rendered hull far beyond the logical query.
            previous.set(ship.logicalPose());
            previous.position().add(-40, 0, 0);
            previous.orientation().rotateY(Math.PI / 2);
            ship.renderPose(0); // Invalidate Sable's cached partial tick.
            var render = ship.renderPose(0.25f);
            Vec3 center = render.transformPosition(localCenter);
            if (ship.boundingBox().intersects(new BoundingBox3d(center, center).expand(1))) {
                throw new AssertionError("Fast ship fixture must escape logical bounds and padding");
            }
            WorldCoordinates.duringRender(client.level, 0.25f, () -> {
                if (!client.level.hasChunkAt(BlockPos.containing(center))) throw new AssertionError("Fixture world chunk missing");
                if (WorldCoordinates.clearAt(client.level, center)) throw new AssertionError("Rendered fast hull missed by clearance");
                Vec3 from = render.transformPosition(localCenter.add(0, 3, 0));
                Vec3 to = render.transformPosition(localCenter.add(0, -0.2, 0));
                var hit = WorldCoordinates.clip(client.level, from, to, ClipContext.Fluid.NONE, client.player);
                if (hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceTo(center) > 1) {
                    throw new AssertionError("Rendered fast hull missed by raycast");
                }
                return null;
            });
        } finally {
            previous.set(saved);
            ship.renderPose(0);
            ship.renderPose(0.25f);
        }
    }
}
