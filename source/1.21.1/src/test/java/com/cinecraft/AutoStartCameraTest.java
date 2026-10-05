package com.cinecraft;

import com.cinecraft.camera.CameraPose;
import com.cinecraft.camera.StaticShot;
import com.cinecraft.compat.CinecraftFlawlessFrames;
import com.cinecraft.config.CinecraftConfig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real client configuration and cleanup without launching a world. */
final class AutoStartCameraTest {
    @TempDir
    static Path gameDirectory;

    @BeforeAll
    static void configureTemporaryGameDirectory() {
        FMLPaths.loadAbsolutePaths(gameDirectory);
    }

    @BeforeEach
    void reset() throws Exception {
        clientFlag("manualActivation", false);
        clientFlag("recordingMode", false);
        CinecraftConfig.INSTANCE.resetDefaults();
        CinecraftClient.applyConfiguration();
        CinecraftClient.DIRECTOR.stop();
        CinecraftClient.IDLE.activity();
        CinecraftFlawlessFrames.setActive(false);
    }

    @AfterEach
    void cleanUp() throws Exception {
        // Do not retain a test observer in the static rendering bridge.
        new CinecraftFlawlessFrames().accept(name -> active -> { });
        reset();
    }

    @Test
    void oldConfigsDefaultToOnAndOffPersistsUntilReset() throws Exception {
        Path path = gameDirectory.resolve("config/stillwander.json");
        Files.writeString(path, "{\"idleSeconds\":45}");
        CinecraftConfig old = loadConfig();
        assertTrue(old.autoStartCamera());
        assertEquals(45, old.idleSeconds());
        old.autoStartCamera(false);
        old.save();
        CinecraftConfig saved = loadConfig();
        assertFalse(saved.autoStartCamera());
        assertEquals(45, saved.idleSeconds());
        saved.resetDefaults();
        assertTrue(loadConfig().autoStartCamera());
    }

    @Test
    void offBlocksAnExpiredIdleTimerButAllowsManualAndCaptureRequests() throws Exception {
        CinecraftConfig.INSTANCE.autoStartCamera(false);
        CinecraftClient.applyConfiguration();
        expireIdle();
        assertFalse(requested());
        assertFalse(CinecraftFlawlessFrames.isRequested());
        clientFlag("manualActivation", true);
        assertTrue(requested());
        clientFlag("manualActivation", false);
        clientFlag("recordingMode", true);
        assertTrue(requested());
        clientFlag("recordingMode", false);
        assertFalse(requested());
    }

    @Test
    void disablingAutomaticSessionClearsStateBeforeRenderingRelease() throws Exception {
        expireIdle();
        assertTrue(requested());
        startShot();
        CinecraftFlawlessFrames.setActive(true);
        AtomicBoolean released = new AtomicBoolean();
        new CinecraftFlawlessFrames().accept(name -> active -> {
            if (!active) {
                assertFalse(CinecraftClient.DIRECTOR.isActive());
                assertFalse(CinecraftClient.isRecordingMode());
                assertFalse(CinecraftClient.IDLE.isIdle());
                assertFalse(CinecraftFlawlessFrames.isRequested());
                released.set(true);
            }
        });
        CinecraftConfig.INSTANCE.autoStartCamera(false);
        CinecraftClient.applyConfiguration();
        assertTrue(released.get());
        assertFalse(requested());
    }

    @Test
    void changingAutoStartPreservesManualAndCaptureSessions() throws Exception {
        for (String flag : new String[]{"manualActivation", "recordingMode"}) {
            reset();
            clientFlag(flag, true);
            startShot();
            CinecraftFlawlessFrames.setActive(true);
            CinecraftConfig.INSTANCE.autoStartCamera(false);
            CinecraftClient.applyConfiguration();
            assertTrue(CinecraftClient.DIRECTOR.isActive());
            assertTrue(CinecraftFlawlessFrames.isRequested());
            assertTrue(requested());
            CinecraftConfig.INSTANCE.autoStartCamera(true);
            CinecraftClient.applyConfiguration();
            assertTrue(CinecraftClient.DIRECTOR.isActive());
            assertTrue(requested());
        }
    }

    @Test
    void reEnablingStartsFreshCountdownButUnchangedSettingsDoNotResetIt() throws Exception {
        CinecraftConfig.INSTANCE.autoStartCamera(false);
        CinecraftClient.applyConfiguration();
        expireIdle();
        CinecraftConfig.INSTANCE.autoStartCamera(true);
        CinecraftClient.applyConfiguration();
        assertFalse(CinecraftClient.IDLE.isIdle());
        assertFalse(requested());
        expireIdle();
        CinecraftClient.applyConfiguration();
        assertTrue(requested());
    }

    private static CinecraftConfig loadConfig() throws Exception {
        var load = CinecraftConfig.class.getDeclaredMethod("load");
        load.setAccessible(true);
        return (CinecraftConfig) load.invoke(null);
    }

    private static boolean requested() throws Exception {
        var requested = CinecraftClient.class.getDeclaredMethod("cinematicRequested");
        requested.setAccessible(true);
        return (boolean) requested.invoke(null);
    }

    private static void clientFlag(String name, boolean value) throws Exception {
        field(CinecraftClient.class, name).setBoolean(null, value);
    }

    private static void expireIdle() throws Exception {
        field(CinecraftClient.IDLE.getClass(), "lastActivityMillis")
                .setLong(CinecraftClient.IDLE, System.currentTimeMillis() - 601_000L);
        assertTrue(CinecraftClient.IDLE.isIdle());
    }

    private static void startShot() throws Exception {
        field(CinecraftClient.DIRECTOR.getClass(), "currentShot").set(CinecraftClient.DIRECTOR,
                new StaticShot(new CameraPose(Vec3.ZERO, 0, 0), 60_000));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
