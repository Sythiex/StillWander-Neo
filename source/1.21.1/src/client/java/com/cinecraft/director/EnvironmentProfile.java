package com.cinecraft.director;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Cached measurements of the space around the idle player. */
public record EnvironmentProfile(
        SpaceType spaceType,
        boolean underground,
        double surfaceDepth,
        Vec3 playerFocus,
        Vec3 landscapeCenter,
        List<Vec3> landscapeAnchors,
        double averageClearance,
        double skyVisibility,
        double terrainRelief,
        double waterCoverage,
        double sceneRadius,
        double maxCameraDistance,
        double maxVerticalRise,
        BiomeMood biomeMood,
        SceneWeather weather,
        SceneTime sceneTime,
        DimensionMood dimensionMood
) {
    public EnvironmentProfile {
        landscapeAnchors = List.copyOf(landscapeAnchors);
    }

    public boolean supportsWideShots() {
        return !underground && maxCameraDistance >= 18.0;
    }
}
