package com.cinecraft;

import com.cinecraft.capture.CinematicRecorder;
import com.cinecraft.compat.CinecraftFlawlessFrames;
import com.cinecraft.compat.CameraCompatibility;
import com.cinecraft.compat.SessionGate;
import com.cinecraft.compat.RendererCompatibility;
import com.cinecraft.compat.WorldCoordinates;
import com.cinecraft.camera.CameraPose;
import net.minecraft.client.Camera;
import com.cinecraft.config.CinecraftConfig;
import com.cinecraft.config.CinecraftSettingsScreen;
import com.cinecraft.debug.CinecraftDebugHud;
import com.cinecraft.director.CinematicDirector;
import com.cinecraft.idle.DamageMonitor;
import com.cinecraft.idle.IdleDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client-only entry point and the intentionally small public bridge for mixins. */
@Mod(value = "stillwander", dist = Dist.CLIENT)
public final class CinecraftClient {
    public static final IdleDetector IDLE = new IdleDetector(25_000L);
    public static final CinematicDirector DIRECTOR = new CinematicDirector();
    private static final DamageMonitor DAMAGE = new DamageMonitor();
    private static final CinematicRecorder RECORDER = new CinematicRecorder();
    private static final SessionGate SESSION = new SessionGate(() -> {
        stopCinematic(false);
        DAMAGE.reset();
        drainControlPresses();
    }, IDLE::activity);
    private static final String CONTROLS = "key.categories.stillwander.controls";
    private static KeyMapping toggleCinematic;
    private static KeyMapping openSettings;
    private static KeyMapping toggleRecording;
    private static KeyMapping toggleDebug;
    private static KeyMapping nextShot;
    private static boolean manualActivation;
    private static boolean recordingMode;

    public static void activity() {
        if (!canRun()) return;
        if (recordingMode) return;
        stopCinematic(true);
    }

    private static void stopCinematic(boolean resetIdle) {
        manualActivation = false;
        recordingMode = false;
        RECORDER.stop();
        if (resetIdle) IDLE.activity();
        DIRECTOR.stop();
        // The synchronous Dynamic FPS refresh must see the fully released session.
        CinecraftFlawlessFrames.setActive(false);
    }

    /** Lets Cinecraft controls reach the client-tick handler before ordinary input exits. */
    public static boolean isControlKey(int key, int scanCode) {
        if (!canRun()) return false;
        return matches(toggleCinematic, key, scanCode)
                || matches(openSettings, key, scanCode)
                || matches(toggleRecording, key, scanCode)
                || matches(toggleDebug, key, scanCode)
                || matches(nextShot, key, scanCode);
    }

    private static boolean matches(KeyMapping binding, int key, int scanCode) {
        return binding != null && binding.matches(key, scanCode);
    }

    public static void applyConfiguration() {
        IDLE.setTimeoutMillis(CinecraftConfig.INSTANCE.idleSeconds() * 1_000L);
    }

    public static boolean isRecordingMode() {
        return recordingMode;
    }

    /** True slightly before, and for the full lifetime of, any cinematic session. */
    public static boolean requiresUnthrottledRendering() {
        return canRun() && (recordingMode || manualActivation || IDLE.isIdle() || DIRECTOR.isActive());
    }

    public static boolean canRun() {
        Minecraft client = Minecraft.getInstance();
        return SESSION.update(client == null ? null : client.level, client == null ? null : client.player,
                CameraCompatibility.blockReason(client));
    }

    public static boolean hasCameraControl() {
        return canRun() && DIRECTOR.isActive() && !RendererCompatibility.auxiliaryRenderPass();
    }

    public static boolean hasCameraControl(Camera camera) {
        return hasCameraControl() && camera == Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static CameraPose cameraPose(Camera camera, float partialTick) {
        if (!hasCameraControl(camera)) return null;
        return WorldCoordinates.duringRender(Minecraft.getInstance().level, partialTick,
                () -> DIRECTOR.pose(partialTick));
    }

    public CinecraftClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(CinecraftClient::registerKeys);
        modBus.addListener(CinecraftDebugHud::register);
        NeoForge.EVENT_BUS.addListener(CinecraftClient::tick);
        NeoForge.EVENT_BUS.addListener(CinecraftClient::disconnect);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new CinecraftSettingsScreen(parent));
        applyConfiguration();
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        toggleCinematic = register(event, "key.stillwander.toggle_cinematic", GLFW.GLFW_KEY_B);
        openSettings = register(event, "key.stillwander.open_settings", GLFW.GLFW_KEY_F7);
        toggleRecording = register(event, "key.stillwander.toggle_recording", GLFW.GLFW_KEY_F8);
        toggleDebug = register(event, "key.stillwander.toggle_debug", GLFW.GLFW_KEY_F9);
        nextShot = register(event, "key.stillwander.next_shot", GLFW.GLFW_KEY_N);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!canRun()) {
            drainControlPresses();
            IDLE.activity();
            return;
        }
        handleControls(client);
        boolean damaged = DAMAGE.update(client.player);
        if (damaged && CinecraftConfig.INSTANCE.exitOnDamage()) {
            stopCinematic(true);
            return;
        }
        boolean cinematicRequested = recordingMode || manualActivation || IDLE.isIdle();
        if (cinematicRequested) {
            CinecraftFlawlessFrames.setActive(true);
            DIRECTOR.tick(client);
            if (!DIRECTOR.isActive()) {
                stopCinematic(true);
                return;
            }
            RECORDER.tick(client, DIRECTOR);
            if (recordingMode && !RECORDER.isRecording()) stopCinematic(true);
        } else {
            DIRECTOR.stop();
            CinecraftFlawlessFrames.setActive(false);
        }
    }

    private static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        SESSION.update(null, null, SessionGate.BlockReason.NO_WORLD);
    }

    private static KeyMapping register(RegisterKeyMappingsEvent event, String id, int defaultKey) {
        KeyMapping binding = new KeyMapping(
                id,
                InputConstants.Type.KEYSYM,
                defaultKey,
                CONTROLS
        );
        event.register(binding);
        return binding;
    }

    private static void handleControls(Minecraft client) {
        while (toggleCinematic.consumeClick()) {
            if (recordingMode || manualActivation || DIRECTOR.isActive()) {
                stopCinematic(true);
            } else {
                manualActivation = true;
                IDLE.activity();
            }
        }
        while (openSettings.consumeClick()) {
            stopCinematic(true);
            client.setScreen(new CinecraftSettingsScreen(client.screen));
        }
        while (toggleRecording.consumeClick()) {
            if (recordingMode) {
                stopCinematic(true);
            } else if (RECORDER.start(client)) {
                recordingMode = true;
                manualActivation = false;
                IDLE.activity();
            }
        }
        while (toggleDebug.consumeClick()) {
            CinecraftConfig config = CinecraftConfig.INSTANCE;
            config.debugOverlay(!config.debugOverlay());
            config.save();
        }
        while (nextShot.consumeClick()) {
            if (DIRECTOR.isActive()) DIRECTOR.nextShot();
        }
    }

    private static void drainControlPresses() {
        drain(toggleCinematic);
        drain(openSettings);
        drain(toggleRecording);
        drain(toggleDebug);
        drain(nextShot);
    }

    private static void drain(KeyMapping binding) {
        while (binding != null && binding.consumeClick()) { }
    }
}
