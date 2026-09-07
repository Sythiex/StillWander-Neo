package com.cinecraft.compat;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Session-scoped rendering request. NeoForge Dynamic FPS uses an optional mixin;
 * the consumer bridge remains available for additional FREX providers.
 */
public final class CinecraftFlawlessFrames implements Consumer<Function<String, Consumer<Boolean>>> {
    private static Consumer<Boolean> switcher;
    private static boolean requested;

    @Override
    public void accept(Function<String, Consumer<Boolean>> provider) {
        switcher = provider.apply("Still Wander cinematic camera");
        switcher.accept(requested);
    }

    /**
     * Requests normal rendering only for the lifetime of an active cinematic.
     * The session owner must clear activation, idle, capture, and director state before release:
     * providers and Dynamic FPS may synchronously query that state here.
     */
    public static void setActive(boolean active) {
        if (requested == active) return;
        requested = active;
        if (switcher != null) switcher.accept(active);
        DynamicFpsCompatibility.refresh();
    }

    public static boolean isRegistered() {
        return switcher != null;
    }

    public static boolean isRequested() {
        return requested;
    }
}
