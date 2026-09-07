package com.cinecraft.compat;

import com.cinecraft.CinecraftClient;

/** Stable zero-dependency hook for shader packs or companion render integrations. */
public final class CinematicFocusApi {
    private CinematicFocusApi() { }

    public static boolean active() {
        return CinecraftClient.hasCameraControl();
    }

    public static float focusDistance() {
        return active() ? CinecraftClient.DIRECTOR.currentFocusDistance() : Float.NaN;
    }

    public static float fieldOfView() {
        return active() ? CinecraftClient.DIRECTOR.currentFov() : Float.NaN;
    }
}
