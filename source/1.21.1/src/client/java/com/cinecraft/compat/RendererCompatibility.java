package com.cinecraft.compat;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Deliberately detects render mods without linking to or modifying them.
 * Cinecraft owns camera state only; vanilla/Sodium/Iris retain the render path.
 */
public final class RendererCompatibility {
    private static volatile boolean irisResolved;
    private static Object irisApi;
    private static Method irisShaderQuery;
    private static Method irisShadowQuery;

    private RendererCompatibility() { }

    public static boolean shadersActive() {
        if (!ModList.get().isLoaded("iris")) {
            return ModList.get().isLoaded("oculus");
        }
        if (!irisResolved) resolveIris();
        if (irisApi == null || irisShaderQuery == null) return true;
        try {
            return (Boolean) irisShaderQuery.invoke(irisApi);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return true;
        }
    }

    private static synchronized void resolveIris() {
        if (irisResolved) return;
        irisResolved = true;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisApi = apiClass.getMethod("getInstance").invoke(null);
            irisShaderQuery = apiClass.getMethod("isShaderPackInUse");
            irisShadowQuery = apiClass.getMethod("isRenderingShadowPass");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            irisApi = null;
            irisShaderQuery = null;
        }
    }

    /** A shader shadow camera must retain the shader renderer's own pose and lens. */
    public static boolean auxiliaryRenderPass() {
        if (!ModList.get().isLoaded("iris")) return false;
        if (!irisResolved) resolveIris();
        if (irisApi == null || irisShadowQuery == null) return true;
        try {
            return (Boolean) irisShadowQuery.invoke(irisApi);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return true;
        }
    }

    public static boolean distantHorizonsAvailable() {
        return ModList.get().isLoaded("distanthorizons");
    }

    public static boolean dynamicFpsAvailable() {
        return ModList.get().isLoaded("dynamic_fps");
    }
}
