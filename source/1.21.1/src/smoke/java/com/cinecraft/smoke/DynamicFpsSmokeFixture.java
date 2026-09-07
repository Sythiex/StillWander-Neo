package com.cinecraft.smoke;

import com.cinecraft.CinecraftClient;
import com.cinecraft.camera.CameraPose;
import com.cinecraft.camera.CinematicShot;
import com.cinecraft.compat.CinecraftFlawlessFrames;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;

/** Exercises the real transformed mod's derived state without requiring desktop focus changes. */
final class DynamicFpsSmokeFixture {
    static void validateRelease(boolean failedShot) throws ReflectiveOperationException {
        Class<?> fps = Class.forName("dynamic_fps.impl.DynamicFPSMod");
        Object window = fps.getMethod("getWindow").invoke(null);
        Field focused = field(window.getClass(), "isFocused");
        Field hovered = field(window.getClass(), "isHovered");
        Field iconified = field(window.getClass(), "isIconified");
        boolean oldFocus = focused.getBoolean(window), oldHover = hovered.getBoolean(window);
        boolean oldIconified = iconified.getBoolean(window);
        try {
            focused.setBoolean(window, false);
            hovered.setBoolean(window, false);
            iconified.setBoolean(window, failedShot);
            fps.getMethod("onStatusChanged", boolean.class).invoke(null, false);
            assertState(fps, "FOCUSED");
            if (failedShot) {
                field(CinecraftClient.DIRECTOR.getClass(), "currentShot").set(CinecraftClient.DIRECTOR,
                        new CinematicShot() {
                            public CameraPose sample(float partial) { return null; }
                            public boolean finished() { return false; }
                        });
                var tick = CinecraftClient.class.getDeclaredMethod("tick", ClientTickEvent.Post.class);
                tick.setAccessible(true);
                tick.invoke(null, new Object[]{null});
            } else CinecraftClient.activity();
            // No status refresh or focus event here: shutdown must have updated the cached state.
            assertState(fps, failedShot ? "INVISIBLE" : "UNFOCUSED");
            if (CinecraftClient.DIRECTOR.isActive() || CinecraftClient.isRecordingMode()
                    || CinecraftFlawlessFrames.isRequested() || CinecraftClient.requiresUnthrottledRendering()) {
                throw new AssertionError("Cinematic state not fully released");
            }
            CinecraftClient.activity();
            assertState(fps, failedShot ? "INVISIBLE" : "UNFOCUSED");
        } finally {
            focused.setBoolean(window, oldFocus);
            hovered.setBoolean(window, oldHover);
            iconified.setBoolean(window, oldIconified);
            fps.getMethod("onStatusChanged", boolean.class).invoke(null, false);
        }
    }

    private static void assertState(Class<?> fps, String expected) throws ReflectiveOperationException {
        Object state = fps.getMethod("powerState").invoke(null);
        if (!expected.equals(state.toString())) throw new AssertionError("Dynamic FPS: expected " + expected + ", got " + state);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
