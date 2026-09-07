package com.cinecraft.smoke;

import com.cinecraft.CinecraftClient;
import com.cinecraft.compat.CinecraftFlawlessFrames;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.zip.ZipFile;

/** Replays only the recording made in the disposable smoke instance. */
final class ReplaySmokeFixture {
    private static Object handler;
    private static int step;
    private static long playbackStarted;
    private static Class<?> handlerClass;

    static boolean tick(Minecraft client, BiConsumer<Boolean, String> check) throws Exception {
        if (handler == null) {
            Path recordings = client.gameDirectory.toPath().resolve("replay_recordings");
            if (!Files.isDirectory(recordings)) return false;
            Path replay;
            try (var paths = Files.walk(recordings)) {
                replay = paths.filter(p -> p.toString().endsWith(".mcpr"))
                        .max(Comparator.comparing(Path::toString)).orElse(null);
            }
            if (replay == null) return false;
            try (var zip = new ZipFile(replay.toFile())) {
                if (zip.getEntry("metaData.json") == null) return false;
            } catch (java.io.IOException stillSaving) { return false; }
            Class<?> replayClass = Class.forName("com.replaymod.replay.ReplayModReplay");
            Object replayMod = replayClass.getField("instance").get(null);
            client.setScreen(null);
            replayClass.getMethod("startReplay", java.io.File.class).invoke(replayMod, replay.toFile());
            handler = replayClass.getMethod("getReplayHandler").invoke(replayMod);
            if (handler == null) throw new AssertionError("Replay handler did not open");
            handlerClass = Class.forName("com.replaymod.replay.ReplayHandler");
            check.accept(!CinecraftClient.canRun(), "replay session blocks cinematics immediately");
            return false;
        }
        if (client.level == null || client.player == null) return false;
        if (playbackStarted == 0) playbackStarted = System.nanoTime();
        long elapsed = (System.nanoTime() - playbackStarted) / 1_000_000L;
        if (step == 0 && elapsed >= 500) {
            step++;
            for (int key : new int[]{GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_F9, GLFW.GLFW_KEY_N}) {
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(key));
            }
        }
        if (step == 1 && elapsed >= 1000) {
            step++;
            check.accept(!CinecraftClient.canRun() && !CinecraftClient.DIRECTOR.isActive()
                    && !CinecraftClient.isRecordingMode() && !CinecraftFlawlessFrames.isRequested(),
                    "replay playback ignores controls and capture/FPS requests");
            Object sender = handlerClass.getMethod("getReplaySender").invoke(handler);
            Class.forName("com.replaymod.replay.ReplaySender").getMethod("setReplaySpeed", double.class).invoke(sender, 0.0);
        }
        if (step == 2 && elapsed >= 1500) {
            step++;
            check.accept(!CinecraftClient.canRun() && !CinecraftClient.hasCameraControl(), "paused replay retains camera ownership");
            handlerClass.getMethod("doJump", int.class, boolean.class).invoke(handler, 1000, false);
        }
        if (step == 3 && elapsed >= 2000) {
            step++;
            check.accept(!CinecraftClient.canRun() && !CinecraftClient.hasCameraControl(), "replay seeking retains camera ownership");
            handlerClass.getMethod("endReplay").invoke(handler);
            check.accept(!CinecraftClient.DIRECTOR.isActive() && !CinecraftClient.isRecordingMode()
                    && !CinecraftFlawlessFrames.isRequested(), "replay close leaves no cinematic state");
            return true;
        }
        return false;
    }
}
