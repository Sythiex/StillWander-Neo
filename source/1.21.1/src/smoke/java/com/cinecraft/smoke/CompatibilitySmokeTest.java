package com.cinecraft.smoke;

import com.cinecraft.CinecraftClient;
import com.cinecraft.compat.CinecraftFlawlessFrames;
import com.cinecraft.compat.WorldCoordinates;
import com.cinecraft.config.CinecraftConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Opt-in development harness. This source set is never packaged in the release JAR. */
@EventBusSubscriber(modid = "stillwander", value = Dist.CLIENT)
public final class CompatibilitySmokeTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> RESULTS = new ArrayList<>();
    private static final long STARTED = System.nanoTime();
    private static boolean started;
    private static boolean finished;
    private static boolean loggedOut;
    private static int ticks;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("stillwander.smoke") || finished) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if ((System.nanoTime() - STARTED) / 1_000_000_000L > 180) {
                throw new AssertionError("Timed out waiting for smoke world");
            }
            if (!started && client.screen instanceof AccessibilityOnboardingScreen) {
                client.options.onboardAccessibility = false;
                client.setScreen(new TitleScreen());
            }
            if (!started && client.screen instanceof TitleScreen) {
                started = true;
                client.options.pauseOnLostFocus = false;
                client.options.renderDistance().set(6);
                client.options.simulationDistance().set(5);
                client.options.hideGui = false;
                CinecraftConfig config = CinecraftConfig.INSTANCE;
                config.idleSeconds(600);
                config.hideHud(true);
                config.debugOverlay(false);
                config.landscapeShots(false);
                config.entityShots(false);
                config.groupShots(false);
                config.featureShots(false);
                config.playerDetailShots(false);
                CinecraftClient.applyConfiguration();
                client.createWorldOpenFlows().createFreshLevel("stillwander-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("Still Wander smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(246813579L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        new TitleScreen());
                return;
            }
            if (ticks >= 200 && ModList.get().isLoaded("reforgedplaymod")) return;
            if (client.level == null || client.player == null) return;
            ticks++;
            if (ticks == 40 && ModList.get().isLoaded("sable")) ShipSmokeFixture.assemble(client);
            if (ticks == 70 && ModList.get().isLoaded("sable")) ShipSmokeFixture.board(client);
            if (ticks == 100) {
                client.setScreen(null);
                check(CinecraftClient.canRun(), "normal gameplay eligible (including ordinary replay recording)");
                press(GLFW.GLFW_KEY_B);
            }
            if (ticks == 110 && ModList.get().isLoaded("sable")) {
                ShipSmokeFixture.beginMotion(client);
                check(true, "player tracked aboard assembled Sable ship");
            }
            if (ticks == 120) {
                check(CinecraftClient.DIRECTOR.isActive(), "cinematic activation");
                check(client.options.hideGui, "cinematic hides HUD");
                check(CinecraftFlawlessFrames.isRequested(), "rendering requested");
                check(WorldCoordinates.finite(client.gameRenderer.getMainCamera().getPosition()), "finite rendered camera");
                if (ModList.get().isLoaded("sable")) {
                    ShipSmokeFixture.validate(client);
                    check(true, "moving and fast rendered ship hull collision, raycast projection, and interpolated rig");
                }
                Screenshot.grab(client.gameDirectory, client.getMainRenderTarget(), message -> { });
                if (ModList.get().isLoaded("dynamic_fps")) check(dynamicFpsDisabled(), "Dynamic FPS bypass applied");
                if (ModList.get().isLoaded("dynamic_fps")) {
                    DynamicFpsSmokeFixture.validateRelease(false);
                    check(true, "Dynamic FPS returns to UNFOCUSED immediately after activity shutdown");
                }
                press(GLFW.GLFW_KEY_F8);
            }
            if (ticks == 122 && ModList.get().isLoaded("sable")) ShipSmokeFixture.sit(client);
            if (ticks == 132 && ModList.get().isLoaded("sable")) {
                ShipSmokeFixture.validateSeatedCamera(client);
                check(true, "seated rotated ship camera faces world focus at three partial ticks and restores ordinary view");
            }
            if (ticks == 140) {
                check(CinecraftClient.isRecordingMode(), "standalone capture starts");
                if (ModList.get().isLoaded("freecam")) {
                    Class.forName("net.xolt.freecam.Freecam").getMethod("toggle").invoke(null);
                } else CinecraftClient.activity(); // Capture intentionally ignores activity; stop with its own key.
                if (!ModList.get().isLoaded("freecam")) press(GLFW.GLFW_KEY_F8);
            }
            if (ticks == 150) {
                if (ModList.get().isLoaded("freecam")) check(!CinecraftClient.canRun(), "Freecam owns camera");
                check(!CinecraftClient.DIRECTOR.isActive() && !CinecraftClient.isRecordingMode(), "camera and capture released");
                check(!client.options.hideGui, "HUD restored");
                check(!CinecraftFlawlessFrames.isRequested(), "rendering request released");
                if (ModList.get().isLoaded("dynamic_fps")) check(!dynamicFpsDisabled(), "Dynamic FPS bypass released");
                if (ModList.get().isLoaded("freecam")) {
                    press(GLFW.GLFW_KEY_B);
                    press(GLFW.GLFW_KEY_F8);
                    press(GLFW.GLFW_KEY_F7);
                }
            }
            if (ticks == 160 && ModList.get().isLoaded("freecam")) {
                check(!CinecraftClient.DIRECTOR.isActive() && !CinecraftClient.isRecordingMode() && client.screen == null,
                        "controls inert during Freecam");
                Class.forName("net.xolt.freecam.Freecam").getMethod("toggle").invoke(null);
            }
            if (ticks == 170) {
                check(CinecraftClient.canRun(), "gameplay eligibility restored");
                check(!CinecraftClient.IDLE.isIdle() && !CinecraftClient.DIRECTOR.isActive(), "fresh idle countdown");
                press(GLFW.GLFW_KEY_B);
            }
            if (ticks == 190) {
                check(CinecraftClient.DIRECTOR.isActive(), "cinematic can restart after handoff");
                // Simulate another owner's HUD change while our session is active.
                client.options.hideGui = false;
                if (ModList.get().isLoaded("dynamic_fps")) {
                    DynamicFpsSmokeFixture.validateRelease(true);
                    check(true, "Dynamic FPS returns to INVISIBLE immediately after failed-shot shutdown");
                }
            }
            if (ticks == 200) {
                check(!client.options.hideGui, "external HUD change preserved");
                client.level.disconnect();
                client.disconnect(new TitleScreen());
                check(!CinecraftClient.canRun() && !CinecraftClient.DIRECTOR.isActive()
                        && !CinecraftFlawlessFrames.isRequested(), "logout cleanup");
                loggedOut = true;
                if (!ModList.get().isLoaded("reforgedplaymod")) finish(client, null);
            }
        } catch (Throwable failure) {
            finish(client, failure);
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("stillwander.smoke") || finished) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if ((System.nanoTime() - STARTED) / 1_000_000_000L > 180) throw new AssertionError("Smoke timeout");
            // ReplayMod pauses game ticks while its renderer keeps running.
            if (loggedOut && ModList.get().isLoaded("reforgedplaymod")
                    && ReplaySmokeFixture.tick(client, CompatibilitySmokeTest::check)) finish(client, null);
        } catch (Throwable failure) { finish(client, failure); }
    }

    private static boolean dynamicFpsDisabled() throws ReflectiveOperationException {
        Class<?> type = Class.forName("net.lostluma.dynamic_fps.impl.neoforge.service.NeoForgeModCompat");
        return (boolean) type.getMethod("isDisabled").invoke(type.getConstructor().newInstance());
    }

    private static void press(int key) {
        KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(key));
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        RESULTS.add("PASS " + label);
        LOGGER.info("STILLWANDER_SMOKE PASS {}", label);
    }

    private static void finish(Minecraft client, Throwable failure) {
        finished = true;
        if (failure != null) LOGGER.error("STILLWANDER_SMOKE FAILED", failure);
        RESULTS.add(failure == null ? "SUCCESS" : "FAILED " + failure);
        try {
            Files.write(client.gameDirectory.toPath().resolve("stillwander-smoke-result.txt"), RESULTS);
        } catch (Exception e) {
            LOGGER.error("Cannot write smoke result", e);
        }
        LOGGER.info("STILLWANDER_SMOKE {}", failure == null ? "SUCCESS" : "FAILED");
        client.stop();
    }
}
