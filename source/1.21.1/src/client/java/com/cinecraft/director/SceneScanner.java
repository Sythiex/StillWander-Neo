package com.cinecraft.director;

import com.cinecraft.camera.CameraPath;
import com.cinecraft.compat.WorldCoordinates;
import com.cinecraft.compat.WorldAnchor;
import com.cinecraft.camera.CameraPose;
import com.cinecraft.camera.LookAt;
import com.cinecraft.camera.ArcLengthSplinePath;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.Objects;

/** Surveys the scene and provides collision and visibility tests to the procedural planner. */
public final class SceneScanner {
    private static final int RECENT_SUBJECT_LIMIT = 8;
    private static final double SURVEY_RANGE = 48.0;
    private static final double MINIMUM_CAMERA_GROUND_CLEARANCE = 0.85;
    private static final double MAXIMUM_RUNTIME_CAMERA_LIFT = 2.4;

    private final ArrayDeque<String> recentSubjects = new ArrayDeque<>();
    private final ArrayDeque<SubjectType> environmentDeck = new ArrayDeque<>();
    private final Random random;

    public SceneScanner() {
        this(new Random());
    }

    /** Allows repeatable scanner choices in fixtures and diagnostics. */
    public SceneScanner(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public SceneSubject playerSubject(Minecraft client) {
        Player player = client.player;
        return new SceneSubject(
                SubjectType.PLAYER,
                WorldCoordinates.entityPosition(player).add(0.0, player.getBbHeight() * 0.55, 0.0),
                "player",
                player
        );
    }

    public SceneSubject landscapeSubject(EnvironmentProfile profile) {
        Vec3 center = profile.landscapeCenter();
        String key = "wide:" + Math.round(center.x / 8.0) + ":" + Math.round(center.z / 8.0);
        return new SceneSubject(SubjectType.LANDSCAPE, center, key);
    }

    /** Sublevel-only preference; the ordinary scanner keeps its original selection and random draws. */
    public SceneSubject landscapeAhead(Minecraft client, Vec3 velocity, SceneSubject fallback) {
        List<WeightedSubject> candidates = new ArrayList<>();
        collectLandscapes(client, candidates);
        Vec3 origin = WorldCoordinates.entityPosition(client.player);
        return candidates.stream()
                .filter(candidate -> candidate.subject().key().equals(fallback.key())
                        || !recentSubjects.contains(candidate.subject().key()))
                .max(Comparator.comparingDouble(candidate -> candidate.score()
                        + com.cinecraft.compat.TravelViewRules.aheadScore(origin, candidate.subject().target(), velocity)))
                .map(candidate -> { remember(candidate.subject().key()); return candidate.subject(); })
                .orElse(fallback);
    }

    /** Selects armor or an occupied hand as a live character-detail target. */
    public SceneSubject playerDetailSubject(Minecraft client) {
        Player player = client.player;
        List<SubjectFocus> choices = new ArrayList<>();
        if (!player.getMainHandItem().isEmpty()) choices.add(SubjectFocus.MAIN_HAND);
        if (!player.getOffhandItem().isEmpty()) choices.add(SubjectFocus.OFF_HAND);
        boolean hasArmor = false;
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };
        for (EquipmentSlot slot : armorSlots) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                hasArmor = true;
                break;
            }
        }
        if (hasArmor) {
            choices.add(SubjectFocus.HEAD);
            choices.add(SubjectFocus.CHEST);
        }
        if (choices.isEmpty()) {
            choices.add(SubjectFocus.HEAD);
            choices.add(SubjectFocus.CHEST);
        }
        SubjectFocus focus = choices.get(random.nextInt(choices.size()));
        return new SceneSubject(
                SubjectType.PLAYER_DETAIL,
                SceneSubject.targetFor(player, focus),
                "player_detail:" + focus,
                player,
                focus
        );
    }

    /** Measures enclosure, visibility, terrain extent, relief, and surrounding water. */
    public EnvironmentProfile survey(Minecraft client) {
        Vec3 playerFocus = playerSubject(client).target();
        double clearanceTotal = 0.0;
        for (int direction = 0; direction < 16; direction++) {
            double angle = Math.PI * 2.0 * direction / 16.0;
            Vec3 end = playerFocus.add(Math.cos(angle) * SURVEY_RANGE, 0.0, Math.sin(angle) * SURVEY_RANGE);
            clearanceTotal += rayDistance(client, playerFocus, end, SURVEY_RANGE);
        }
        double averageClearance = clearanceTotal / 16.0;
        double skyVisibility = measureSkyVisibility(client, playerFocus);
        SurfaceSurvey surface = surveySurface(client, playerFocus);
        BlockPos playerBlock = BlockPos.containing(WorldCoordinates.entityPosition(client.player));
        boolean directlySkyVisible = client.level.canSeeSky(playerBlock);
        double surfaceDepth = Math.max(0.0,
                client.level.getHeight(Heightmap.Types.MOTION_BLOCKING, playerBlock.getX(), playerBlock.getZ())
                        - playerBlock.getY());
        boolean underground = !directlySkyVisible && skyVisibility < 0.34 && surfaceDepth > 8.0;

        SpaceType spaceType;
        if (underground) {
            spaceType = SpaceType.ENCLOSED;
        } else if (skyVisibility < 0.58 || averageClearance < 14.0) {
            spaceType = SpaceType.TRANSITIONAL;
        } else if (skyVisibility > 0.86 && averageClearance > 30.0) {
            spaceType = SpaceType.VAST;
        } else {
            spaceType = SpaceType.OPEN;
        }

        int viewDistanceChunks = client.options.renderDistance().get();
        double renderBound = clamp(viewDistanceChunks * 16.0 - 16.0, 16.0, 80.0);
        double spatialBound = switch (spaceType) {
            case ENCLOSED -> 6.5;
            case TRANSITIONAL -> 32.0;
            case OPEN -> 48.0;
            case VAST -> 72.0;
        };
        double horizontalBound = Math.min(renderBound, spatialBound);
        double verticalBound = underground ? 5.5 : clamp(renderBound * 0.95, 32.0, 72.0);
        DimensionMood dimensionMood = dimensionMood(client);
        BiomeMood biomeMood = biomeMood(client, playerBlock, underground, dimensionMood);

        return new EnvironmentProfile(
                spaceType,
                underground,
                surfaceDepth,
                playerFocus,
                surface.center(),
                surface.anchors(),
                averageClearance,
                skyVisibility,
                surface.relief(),
                surface.waterCoverage(),
                surface.radius(),
                horizontalBound,
                verticalBound,
                biomeMood,
                weather(client, playerBlock),
                sceneTime(client),
                dimensionMood
        );
    }

    private static DimensionMood dimensionMood(Minecraft client) {
        if (client.level.dimension().equals(Level.NETHER)) return DimensionMood.NETHER;
        if (client.level.dimension().equals(Level.END)) return DimensionMood.END;
        if (client.level.dimension().equals(Level.OVERWORLD)) return DimensionMood.OVERWORLD;
        return DimensionMood.OTHER;
    }

    private static BiomeMood biomeMood(
            Minecraft client,
            BlockPos playerBlock,
            boolean underground,
            DimensionMood dimension
    ) {
        if (dimension == DimensionMood.NETHER) return BiomeMood.NETHER;
        if (dimension == DimensionMood.END) return BiomeMood.END;
        if (underground) return BiomeMood.CAVE;
        String path = client.level.getBiome(playerBlock)
                .unwrapKey()
                .map(key -> key.location().getPath())
                .orElse("");
        if (containsAny(path, "snow", "frozen", "ice", "grove")) return BiomeMood.SNOW;
        if (containsAny(path, "ocean", "beach", "river")) return BiomeMood.OCEAN;
        if (containsAny(path, "mountain", "peak", "slope", "windswept", "cliff")) return BiomeMood.MOUNTAIN;
        if (containsAny(path, "forest", "taiga", "jungle", "woods", "cherry")) return BiomeMood.FOREST;
        if (containsAny(path, "desert", "badlands", "savanna")) return BiomeMood.DESERT;
        if (containsAny(path, "swamp", "mangrove")) return BiomeMood.SWAMP;
        if (containsAny(path, "plains", "meadow", "field")) return BiomeMood.PLAINS;
        return BiomeMood.OTHER;
    }

    private static SceneWeather weather(Minecraft client, BlockPos playerBlock) {
        if (client.level.isThundering()) return SceneWeather.THUNDER;
        if (!client.level.isRaining()) return SceneWeather.CLEAR;
        return client.level.getBiome(playerBlock).value().getPrecipitationAt(playerBlock) == Biome.Precipitation.SNOW
                ? SceneWeather.SNOW
                : SceneWeather.RAIN;
    }

    private static SceneTime sceneTime(Minecraft client) {
        long time = Math.floorMod(client.level.getDayTime(), 24_000L);
        if (time >= 22_500L || time < 1_000L) return SceneTime.SUNRISE;
        if (time >= 11_500L && time < 13_500L) return SceneTime.SUNSET;
        if (time >= 13_500L && time < 22_500L) return SceneTime.NIGHT;
        return SceneTime.DAY;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    /** Selects a non-recent nearby entity, detail, or landscape point. */
    public SceneSubject findEnvironmentSubject(Minecraft client) {
        List<WeightedSubject> candidates = new ArrayList<>();
        collectEntities(client, candidates);
        collectGroups(client, candidates);
        collectFeatures(client, candidates);
        collectLandscapes(client, candidates);
        candidates.sort(Comparator.comparingDouble(WeightedSubject::score).reversed());

        List<WeightedSubject> pool = chooseEnvironmentPool(candidates);
        if (pool.isEmpty()) return playerSubject(client);

        SceneSubject selected = pool.get(random.nextInt(pool.size())).subject();
        remember(selected.key());
        return selected;
    }

    /** Requests a particular coverage category, with ordinary environment selection as fallback. */
    public SceneSubject findSubject(Minecraft client, SubjectType desired) {
        if (desired == SubjectType.PLAYER) return playerSubject(client);
        List<WeightedSubject> candidates = new ArrayList<>();
        collectEntities(client, candidates);
        collectGroups(client, candidates);
        collectFeatures(client, candidates);
        collectLandscapes(client, candidates);
        candidates.sort(Comparator.comparingDouble(WeightedSubject::score).reversed());

        List<WeightedSubject> pool = candidates.stream()
                .filter(candidate -> candidate.subject().type() == desired)
                .filter(candidate -> !recentSubjects.contains(candidate.subject().key()))
                .limit(5)
                .toList();
        if (pool.isEmpty()) {
            pool = candidates.stream()
                    .filter(candidate -> candidate.subject().type() == desired)
                    .limit(5)
                    .toList();
        }
        if (pool.isEmpty()) return findEnvironmentSubject(client);

        SceneSubject selected = pool.get(random.nextInt(pool.size())).subject();
        remember(selected.key());
        return selected;
    }

    public void resetSubjects() {
        recentSubjects.clear();
        environmentDeck.clear();
    }

    /** Rotates through entities, interesting blocks, and landscapes when each is available. */
    private List<WeightedSubject> chooseEnvironmentPool(List<WeightedSubject> candidates) {
        for (int attempt = 0; attempt < 4; attempt++) {
            if (environmentDeck.isEmpty()) refillEnvironmentDeck();
            SubjectType desired = environmentDeck.removeFirst();
            List<WeightedSubject> freshOfType = candidates.stream()
                    .filter(candidate -> candidate.subject().type() == desired)
                    .filter(candidate -> !recentSubjects.contains(candidate.subject().key()))
                    .limit(5)
                    .toList();
            if (!freshOfType.isEmpty()) return freshOfType;

            List<WeightedSubject> anyOfType = candidates.stream()
                    .filter(candidate -> candidate.subject().type() == desired)
                    .limit(5)
                    .toList();
            if (!anyOfType.isEmpty()) return anyOfType;
        }
        return candidates.stream()
                .filter(candidate -> !recentSubjects.contains(candidate.subject().key()))
                .limit(5)
                .toList();
    }

    private void refillEnvironmentDeck() {
        List<SubjectType> types = new ArrayList<>(List.of(
                SubjectType.ENTITY,
                SubjectType.GROUP,
                SubjectType.FEATURE,
                SubjectType.LANDSCAPE
        ));
        Collections.shuffle(types, random);
        environmentDeck.addAll(types);
    }

    public CameraPose findPlayerView(Minecraft client) {
        Vec3 target = playerSubject(client).target();
        double baseAngle = Math.toRadians(client.player.getYRot() + 180.0);
        double[] radii = {1.15, 1.7, 2.5, 3.5, 4.5, 6.5};
        for (double radius : radii) {
            for (int index = 0; index < 12; index++) {
                double angle = baseAngle + Math.PI * 2.0 * index / 12.0;
                Vec3 camera = new Vec3(
                        target.x + Math.cos(angle) * radius,
                        target.y + Math.min(1.3, 0.3 + radius * 0.22),
                        target.z + Math.sin(angle) * radius
                );
                if (isUsable(client, camera, target)) return LookAt.pose(camera, target);
            }
        }
        return null;
    }

    /** Rejects a generated spline if any sampled frame is unsafe or loses its moving focus. */
    public boolean isPathUsable(
            Minecraft client,
            CameraPath cameraPath,
            CameraPath focusPath,
            CameraPath subjectPath,
            EnvironmentProfile profile
    ) {
        Vec3 playerPosition = WorldCoordinates.entityPosition(client.player);
        for (int sample = 0; sample <= 24; sample++) {
            double progress = sample / 24.0;
            Vec3 camera = cameraPath.sample(progress);
            Vec3 focus = focusPath.sample(progress);
            Vec3 subject = subjectPath.sample(progress);
            double dx = camera.x - playerPosition.x;
            double dz = camera.z - playerPosition.z;
            if (dx * dx + dz * dz > profile.maxCameraDistance() * profile.maxCameraDistance()) return false;
            if (Math.abs(camera.y - playerPosition.y) > profile.maxVerticalRise()) return false;
            if (!isUsable(client, camera, focus)) return false;
            if (raycast(client, camera, subject).getType() != HitResult.Type.MISS) return false;
        }
        return true;
    }

    /** Scores open backgrounds, sky silhouettes, and water-backed compositions. */
    public double compositionScore(
            Minecraft client,
            ShotPlan plan,
            EnvironmentProfile profile
    ) {
        double score = 0.0;
        for (double progress : new double[]{0.15, 0.50, 0.85}) {
            Vec3 camera = plan.path().sample(progress);
            Vec3 subject = plan.subjectPath().sample(progress);
            Vec3 direction = subject.subtract(camera);
            if (direction.lengthSqr() < 0.001) continue;
            direction = direction.normalize();
            Vec3 backgroundStart = subject.add(direction.scale(0.45));
            Vec3 backgroundEnd = subject.add(direction.scale(14.0));
            boolean openBackground = raycast(client, backgroundStart, backgroundEnd).getType() == HitResult.Type.MISS;
            if (openBackground) score += 3.5;

            BlockPos subjectBlock = BlockPos.containing(subject);
            boolean sky = client.level.canSeeSky(subjectBlock);
            if (sky) score += 1.5;
            if (sky && openBackground
                    && (profile.sceneTime() == SceneTime.SUNRISE || profile.sceneTime() == SceneTime.SUNSET)) {
                score += 3.0;
            }
            for (int depth = 0; depth <= 3; depth++) {
                if (WorldCoordinates.fluidAt(client.level, Vec3.atCenterOf(subjectBlock.below(depth)))) {
                    score += profile.sceneTime() == SceneTime.DAY ? 1.0 : 2.0;
                    break;
                }
            }
        }
        return score;
    }

    /**
     * Keeps a tracked rig clear of terrain after its subject changes elevation.
     * A small upward correction is preferred; null asks the shot to retain its
     * previous safe frame when neither clearance nor line of sight can be kept.
     */
    public Vec3 resolveRuntimeCamera(Minecraft client, Vec3 camera, Vec3 focus) {
        for (double lift = 0.0; lift <= MAXIMUM_RUNTIME_CAMERA_LIFT; lift += 0.15) {
            Vec3 candidate = camera.add(0.0, lift, 0.0);
            if (isUsable(client, candidate, focus)) return candidate;
        }
        return null;
    }

    public boolean isPoseUsable(Minecraft client, CameraPose pose) {
        if (!Float.isFinite(pose.yaw()) || !Float.isFinite(pose.pitch())
                || !Float.isFinite(pose.focusDistance()) || pose.focusDistance() <= 0.0f) return false;
        Vec3 focus = pose.position().add(Vec3.directionFromRotation(pose.pitch(), pose.yaw()).scale(pose.focusDistance()));
        return isUsable(client, pose.position(), focus);
    }

    public boolean isViewUsable(Minecraft client, Vec3 camera, Vec3 focus) {
        return isUsable(client, camera, focus);
    }

    /** Finds a collision-clear 3D corridor between two intended rail endpoints. */
    public java.util.Optional<CameraPath> findNavigablePath(
            Minecraft client,
            Vec3 requestedStart,
            Vec3 requestedEnd
    ) {
        BlockPos start = nearestRoutable(client, BlockPos.containing(requestedStart));
        BlockPos goal = nearestRoutable(client, BlockPos.containing(requestedEnd));
        if (start == null || goal == null) return java.util.Optional.empty();

        int margin = 6;
        int minimumX = Math.min(start.getX(), goal.getX()) - margin;
        int maximumX = Math.max(start.getX(), goal.getX()) + margin;
        int minimumY = Math.min(start.getY(), goal.getY()) - 4;
        int maximumY = Math.max(start.getY(), goal.getY()) + 5;
        int minimumZ = Math.min(start.getZ(), goal.getZ()) - margin;
        int maximumZ = Math.max(start.getZ(), goal.getZ()) + margin;

        PriorityQueue<RouteNode> open = new PriorityQueue<>(Comparator.comparingDouble(RouteNode::estimatedTotal));
        Map<BlockPos, Double> cost = new HashMap<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        cost.put(start, 0.0);
        open.add(new RouteNode(start, heuristic(start, goal)));
        int maximumVisited = switch (com.cinecraft.config.CinecraftConfig.INSTANCE.quality()) {
            case PERFORMANCE -> 1_500;
            case BALANCED -> 3_000;
            case CINEMATIC -> 5_500;
        };

        int visited = 0;
        while (!open.isEmpty() && visited++ < maximumVisited) {
            BlockPos current = open.remove().position();
            if (!closed.add(current)) continue;
            if (current.equals(goal)) {
                List<Vec3> controls = simplifyRoute(client, reconstruct(previous, current));
                if (controls.size() < 2) return java.util.Optional.empty();
                controls.set(0, requestedStart);
                controls.set(controls.size() - 1, requestedEnd);
                return java.util.Optional.of(new ArcLengthSplinePath(controls));
            }

            for (int[] step : ROUTE_STEPS) {
                BlockPos next = current.offset(step[0], step[1], step[2]);
                if (next.getX() < minimumX || next.getX() > maximumX
                        || next.getY() < minimumY || next.getY() > maximumY
                        || next.getZ() < minimumZ || next.getZ() > maximumZ
                        || closed.contains(next)
                        || !isCameraVolumeClear(client, routePoint(next))) {
                    continue;
                }
                double stepCost = step[1] == 0
                        ? (step[0] != 0 && step[2] != 0 ? 1.42 : 1.0)
                        : 1.35;
                double tentative = cost.get(current) + stepCost;
                if (tentative >= cost.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                cost.put(next, tentative);
                previous.put(next, current);
                open.add(new RouteNode(next, tentative + heuristic(next, goal)));
            }
        }
        return java.util.Optional.empty();
    }

    /** Pre-samples a landscape move and softly follows the measured terrain relief. */
    public CameraPath terrainFollowingPath(Minecraft client, CameraPath original, double desiredClearance) {
        List<Vec3> points = new ArrayList<>();
        double[] requiredY = new double[25];
        for (int index = 0; index < requiredY.length; index++) {
            Vec3 point = original.sample(index / (double) (requiredY.length - 1));
            double surface = surfaceY(client, point);
            double terrainY = Double.isFinite(surface) ? surface + desiredClearance : point.y;
            requiredY[index] = clamp(terrainY, point.y - 4.0, point.y + 7.0);
        }
        for (int pass = 0; pass < 3; pass++) {
            double[] smoothed = requiredY.clone();
            for (int index = 1; index < requiredY.length - 1; index++) {
                smoothed[index] = requiredY[index - 1] * 0.25
                        + requiredY[index] * 0.50
                        + requiredY[index + 1] * 0.25;
            }
            requiredY = smoothed;
        }
        for (int index = 0; index < requiredY.length; index++) {
            Vec3 point = original.sample(index / (double) (requiredY.length - 1));
            Vec3 adjusted = new Vec3(point.x, requiredY[index], point.z);
            for (double lift = 0.0; lift <= 3.0 && !isCameraVolumeClear(client, adjusted); lift += 0.20) {
                adjusted = new Vec3(point.x, requiredY[index] + lift, point.z);
            }
            points.add(adjusted);
        }
        return new ArcLengthSplinePath(points);
    }

    private static final int[][] ROUTE_STEPS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, 0}, {0, -1, 0}
    };

    private BlockPos nearestRoutable(Minecraft client, BlockPos origin) {
        for (int radius = 0; radius <= 2; radius++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (isCameraVolumeClear(client, routePoint(candidate))) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private List<Vec3> reconstruct(Map<BlockPos, BlockPos> previous, BlockPos end) {
        List<Vec3> reversed = new ArrayList<>();
        BlockPos current = end;
        reversed.add(routePoint(current));
        while (previous.containsKey(current)) {
            current = previous.get(current);
            reversed.add(routePoint(current));
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private List<Vec3> simplifyRoute(Minecraft client, List<Vec3> points) {
        if (points.size() <= 2) return new ArrayList<>(points);
        List<Vec3> simplified = new ArrayList<>();
        int anchor = 0;
        simplified.add(points.getFirst());
        while (anchor < points.size() - 1) {
            int farthest = anchor + 1;
            for (int candidate = anchor + 2; candidate < points.size(); candidate++) {
                if (!volumeLineClear(client, points.get(anchor), points.get(candidate))) break;
                farthest = candidate;
            }
            simplified.add(points.get(farthest));
            anchor = farthest;
        }
        return simplified;
    }

    private boolean volumeLineClear(Minecraft client, Vec3 start, Vec3 end) {
        int samples = Math.max(2, (int) Math.ceil(start.distanceTo(end) / 0.35));
        for (int index = 0; index <= samples; index++) {
            if (!isCameraVolumeClear(client, start.lerp(end, index / (double) samples))) return false;
        }
        return true;
    }

    private double surfaceY(Minecraft client, Vec3 point) {
        HitResult hit = raycast(client, point.add(0.0, 12.0, 0.0), point.add(0.0, -24.0, 0.0));
        return hit.getType() == HitResult.Type.MISS ? Double.NaN
                : WorldCoordinates.toWorld(client.level, hit.getLocation()).y;
    }

    private boolean isCameraVolumeClear(Minecraft client, Vec3 camera) {
        Vec3[] offsets = {
                Vec3.ZERO,
                new Vec3(0.24, 0.0, 0.0), new Vec3(-0.24, 0.0, 0.0),
                new Vec3(0.0, 0.0, 0.24), new Vec3(0.0, 0.0, -0.24),
                new Vec3(0.0, 0.18, 0.0), new Vec3(0.0, -0.18, 0.0)
        };
        for (Vec3 offset : offsets) {
            if (!WorldCoordinates.clearAt(client.level, camera.add(offset))) return false;
        }
        HitResult floor = raycast(client, camera, camera.add(0.0, -MINIMUM_CAMERA_GROUND_CLEARANCE, 0.0));
        return floor.getType() == HitResult.Type.MISS;
    }

    private static Vec3 routePoint(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.90, pos.getZ() + 0.5);
    }

    private static double heuristic(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dy = (first.getY() - second.getY()) * 1.25;
        double dz = first.getZ() - second.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private record RouteNode(BlockPos position, double estimatedTotal) { }

    private void collectEntities(Minecraft client, List<WeightedSubject> candidates) {
        Player player = client.player;
        List<Entity> entities = client.level.getEntities(
                player,
                player.getBoundingBox().move(WorldCoordinates.entityPosition(player).subtract(player.position())).inflate(24.0),
                entity -> entity.isAlive() && !entity.isSpectator()
        );
        for (Entity entity : entities) {
            double distance = entity.distanceTo(player);
            double movement = Math.min(18.0, entity.getDeltaMovement().lengthSqr() * 120.0);
            double livingBonus = entity instanceof LivingEntity ? 18.0 : 0.0;
            double score = 58.0 + livingBonus + movement - distance * 1.1;
            Vec3 target = SceneSubject.targetFor(entity, SubjectFocus.CENTER);
            candidates.add(new WeightedSubject(
                    new SceneSubject(SubjectType.ENTITY, target, "entity:" + entity.getUUID(), entity),
                    score
            ));
        }
    }

    private void collectGroups(Minecraft client, List<WeightedSubject> candidates) {
        Player player = client.player;
        List<Entity> entities = new ArrayList<>(client.level.getEntities(
                player,
                player.getBoundingBox().move(WorldCoordinates.entityPosition(player).subtract(player.position())).inflate(24.0),
                entity -> entity.isAlive() && !entity.isSpectator()
        ));
        entities.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
        int limit = Math.min(10, entities.size());
        int added = 0;
        for (int firstIndex = 0; firstIndex < limit && added < 12; firstIndex++) {
            Entity first = entities.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < limit && added < 12; secondIndex++) {
                Entity second = entities.get(secondIndex);
                double separation = first.distanceTo(second);
                if (separation < 1.2 || separation > 10.0) continue;
                double playerDistance = (first.distanceTo(player) + second.distanceTo(player)) * 0.5;
                double motion = (first.getDeltaMovement().horizontalDistanceSqr()
                        + second.getDeltaMovement().horizontalDistanceSqr()) * 65.0;
                double score = 88.0 + Math.min(14.0, motion) - separation * 1.7 - playerDistance * 0.65;
                candidates.add(new WeightedSubject(SceneSubject.entityGroup(first, second), score));
                added++;
            }
        }

        if (added > 0) return;
        List<WeightedSubject> features = new ArrayList<>();
        collectFeatures(client, features);
        features.stream()
                .filter(candidate -> candidate.subject().type() == SubjectType.FEATURE)
                .max(Comparator.comparingDouble(WeightedSubject::score))
                .ifPresent(feature -> candidates.add(new WeightedSubject(
                        SceneSubject.playerAndFeature(
                                player,
                                feature.subject(),
                                "player_feature:" + feature.subject().key()
                        ),
                        feature.score() + 8.0
                )));
    }

    private void collectFeatures(Minecraft client, List<WeightedSubject> candidates) {
        Player player = client.player;
        int centerX = BlockPos.containing(WorldCoordinates.entityPosition(player)).getX();
        int centerY = BlockPos.containing(WorldCoordinates.entityPosition(player)).getY();
        int centerZ = BlockPos.containing(WorldCoordinates.entityPosition(player)).getZ();

        for (int x = centerX - 10; x <= centerX + 10; x++) {
            for (int y = centerY - 5; y <= centerY + 6; y++) {
                for (int z = centerZ - 10; z <= centerZ + 10; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    WorldCoordinates.blocksAt(client.level, Vec3.atCenterOf(pos), (localPos, state) -> {
                        if (!state.hasBlockEntity() && state.getLightEmission(client.level, localPos) < 8) return;
                        Vec3 target = Vec3.atCenterOf(localPos).add(0.0, 0.65, 0.0);
                        WorldAnchor anchor = WorldAnchor.at(client.level, target);
                        double distance = anchor.current().distanceTo(WorldCoordinates.entityPosition(player));
                        if (distance < 2.5) return;
                        double score = (state.hasBlockEntity() ? 72.0 : 58.0)
                                + state.getLightEmission(client.level, localPos) - distance;
                        candidates.add(new WeightedSubject(SceneSubject.feature(anchor, "feature:" + localPos.asLong()), score));
                    });
                }
            }
        }
    }

    private void collectLandscapes(Minecraft client, List<WeightedSubject> candidates) {
        Player player = client.player;
        int playerY = BlockPos.containing(WorldCoordinates.entityPosition(player)).getY();
        int[] distances = {10, 18, 26};
        for (int direction = 0; direction < 12; direction++) {
            double angle = Math.PI * 2.0 * direction / 12.0;
            for (int distance : distances) {
                int x = (int) Math.floor(WorldCoordinates.entityPosition(player).x + Math.cos(angle) * distance);
                int z = (int) Math.floor(WorldCoordinates.entityPosition(player).z + Math.sin(angle) * distance);
                SurfacePoint surface = findTopSurface(client, x, z, playerY);
                if (surface == null || surface.water()) continue;
                double heightInterest = Math.min(18.0, Math.abs(surface.position().getY() - playerY) * 1.6);
                double score = 38.0 + distance * 0.55 + heightInterest;
                Vec3 target = surface.focus();
                candidates.add(new WeightedSubject(
                        new SceneSubject(SubjectType.LANDSCAPE, target, "landscape:" + direction + ":" + distance),
                        score
                ));
            }
        }
    }

    private SurfaceSurvey surveySurface(Minecraft client, Vec3 playerFocus) {
        int playerY = BlockPos.containing(WorldCoordinates.entityPosition(client.player)).getY();
        List<SurfacePoint> land = new ArrayList<>();
        int water = 0;
        int sampled = 0;
        for (int dx = -40; dx <= 40; dx += 8) {
            for (int dz = -40; dz <= 40; dz += 8) {
                int x = (int) Math.floor(playerFocus.x) + dx;
                int z = (int) Math.floor(playerFocus.z) + dz;
                SurfacePoint point = findTopSurface(client, x, z, playerY);
                if (point == null) continue;
                sampled++;
                if (point.water()) water++;
                else land.add(point);
            }
        }

        if (land.isEmpty()) {
            return new SurfaceSurvey(playerFocus, List.of(playerFocus), 8.0, 0.0,
                    sampled == 0 ? 0.0 : water / (double) sampled);
        }

        double centerX = land.stream().mapToDouble(point -> point.focus().x).average().orElse(playerFocus.x);
        double centerZ = land.stream().mapToDouble(point -> point.focus().z).average().orElse(playerFocus.z);
        Vec3 approximateCenter = new Vec3(centerX, playerFocus.y, centerZ);
        Vec3 center = land.stream()
                .min(Comparator.comparingDouble(point -> horizontalDistance(point.focus(), approximateCenter)))
                .map(SurfacePoint::focus)
                .orElse(playerFocus);
        double centerY = center.y;
        double minimumY = land.stream().mapToDouble(point -> point.focus().y).min().orElse(centerY);
        double maximumY = land.stream().mapToDouble(point -> point.focus().y).max().orElse(centerY);
        double radius = land.stream().mapToDouble(point -> horizontalDistance(point.focus(), center)).max().orElse(8.0);

        List<Vec3> anchors = new ArrayList<>();
        anchors.add(center);
        anchors.add(playerFocus);
        land.stream()
                .sorted(Comparator.comparingDouble((SurfacePoint point) -> horizontalDistance(point.focus(), center)).reversed())
                .limit(8)
                .map(SurfacePoint::focus)
                .forEach(anchors::add);
        land.stream()
                .max(Comparator.comparingInt(point -> point.position().getY()))
                .map(SurfacePoint::focus)
                .ifPresent(anchors::add);

        double coverage = sampled == 0 ? 0.0 : water / (double) sampled;
        return new SurfaceSurvey(center, anchors, clamp(radius, 8.0, 56.0), maximumY - minimumY, coverage);
    }

    private SurfacePoint findTopSurface(Minecraft client, int x, int z, int playerY) {
        HitResult hit = WorldCoordinates.clip(client.level,
                new Vec3(x + 0.5, playerY + 32.0, z + 0.5),
                new Vec3(x + 0.5, playerY - 24.0, z + 0.5),
                ClipContext.Fluid.ANY, client.player);
        if (hit.getType() == HitResult.Type.MISS) return null;
        Vec3 global = WorldCoordinates.toWorld(client.level, hit.getLocation());
        if (!WorldCoordinates.finite(global)) return null;
        boolean water = WorldCoordinates.fluidAt(client.level, global.add(0.0, -0.05, 0.0));
        return new SurfacePoint(BlockPos.containing(global), global.add(0.0, water ? 0.35 : 1.15, 0.0), water);
    }

    private double measureSkyVisibility(Minecraft client, Vec3 playerFocus) {
        Vec3[] offsets = {
                Vec3.ZERO,
                new Vec3(2.5, 0.0, 0.0), new Vec3(-2.5, 0.0, 0.0),
                new Vec3(0.0, 0.0, 2.5), new Vec3(0.0, 0.0, -2.5),
                new Vec3(2.5, 0.0, 2.5), new Vec3(-2.5, 0.0, 2.5),
                new Vec3(2.5, 0.0, -2.5), new Vec3(-2.5, 0.0, -2.5)
        };
        int visible = 0;
        for (Vec3 offset : offsets) {
            Vec3 start = playerFocus.add(offset);
            HitResult hit = raycast(client, start, start.add(0.0, 48.0, 0.0));
            if (hit.getType() == HitResult.Type.MISS) visible++;
        }
        return visible / (double) offsets.length;
    }

    private double rayDistance(Minecraft client, Vec3 start, Vec3 end, double missDistance) {
        HitResult hit = raycast(client, start, end);
        return hit.getType() == HitResult.Type.MISS ? missDistance : start.distanceTo(WorldCoordinates.toWorld(client.level, hit.getLocation()));
    }

    private HitResult raycast(Minecraft client, Vec3 start, Vec3 end) {
        return WorldCoordinates.clip(client.level,
                start,
                end,
                ClipContext.Fluid.NONE,
                client.player
        );
    }

    private void remember(String key) {
        recentSubjects.remove(key);
        recentSubjects.addFirst(key);
        while (recentSubjects.size() > RECENT_SUBJECT_LIMIT) recentSubjects.removeLast();
    }

    private boolean isUsable(Minecraft client, Vec3 camera, Vec3 target) {
        if (!WorldCoordinates.finite(camera) || !WorldCoordinates.finite(target)) return false;
        Vec3[] clearanceOffsets = {
                Vec3.ZERO,
                new Vec3(0.24, 0.0, 0.0), new Vec3(-0.24, 0.0, 0.0),
                new Vec3(0.0, 0.0, 0.24), new Vec3(0.0, 0.0, -0.24),
                new Vec3(0.0, 0.18, 0.0), new Vec3(0.0, -0.18, 0.0)
        };
        for (Vec3 offset : clearanceOffsets) {
            if (!WorldCoordinates.clearAt(client.level, camera.add(offset))) return false;
        }
        HitResult floor = raycast(
                client,
                camera,
                camera.add(0.0, -MINIMUM_CAMERA_GROUND_CLEARANCE, 0.0)
        );
        if (floor.getType() != HitResult.Type.MISS) return false;
        return raycast(client, camera, target).getType() == HitResult.Type.MISS;
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record WeightedSubject(SceneSubject subject, double score) { }

    private record SurfacePoint(BlockPos position, Vec3 focus, boolean water) { }

    private record SurfaceSurvey(
            Vec3 center,
            List<Vec3> anchors,
            double radius,
            double relief,
            double waterCoverage
    ) { }
}
