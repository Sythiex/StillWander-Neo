package com.cinecraft.compat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Refreshes Dynamic FPS's own derived state without toggling its user configuration. */
final class DynamicFpsCompatibility {
    private static Method changed;
    private static boolean failed;

    private DynamicFpsCompatibility() { }

    static void refresh() {
        if (failed || ModList.get() == null || !ModList.get().isLoaded("dynamic_fps")) return;
        try {
            if (changed == null) changed = Class.forName("dynamic_fps.impl.DynamicFPSMod")
                    .getMethod("onStatusChanged", boolean.class);
            changed.invoke(null, false);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            failed = true;
            LogUtils.getLogger().warn("Still Wander could not refresh Dynamic FPS state", exception);
        }
    }
}
