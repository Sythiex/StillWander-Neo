package com.cinecraft.capture;

import com.cinecraft.camera.CameraPose;
import com.cinecraft.CinecraftClient;
import com.cinecraft.config.CinecraftConfig;
import com.cinecraft.director.CinematicDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Samples generated camera poses to CSV and optionally captures screenshots. */
public final class CinematicRecorder {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final long SAMPLE_INTERVAL_NANOS = 100_000_000L;

    private BufferedWriter writer;
    private Path currentPath;
    private long startedNanos;
    private long lastSampleNanos;
    private long lastScreenshotNanos;

    public boolean start(Minecraft client) {
        if (!CinecraftClient.canRun()) return false;
        if (writer != null) return true;
        try {
            Path directory = client.gameDirectory.toPath().resolve("stillwander").resolve("camera_paths");
            Files.createDirectories(directory);
            currentPath = directory.resolve("stillwander-" + FILE_TIME.format(LocalDateTime.now()) + ".csv");
            writer = Files.newBufferedWriter(currentPath);
            writer.write("time_ms,x,y,z,yaw,pitch,fov,focus_distance,shot_type,subject_type,source\n");
            startedNanos = System.nanoTime();
            lastSampleNanos = 0L;
            lastScreenshotNanos = startedNanos;
            return true;
        } catch (IOException exception) {
            writer = null;
            currentPath = null;
            return false;
        }
    }

    public void tick(Minecraft client, CinematicDirector director) {
        if (!CinecraftClient.canRun() || writer == null || !director.isActive()) return;
        long now = System.nanoTime();
        if (now - lastSampleNanos >= SAMPLE_INTERVAL_NANOS) {
            CameraPose pose = director.pose(1.0f);
            if (pose != null) writeSample(director, pose, now);
            lastSampleNanos = now;
        }

        int screenshotSeconds = CinecraftConfig.INSTANCE.screenshotIntervalSeconds();
        if (screenshotSeconds > 0
                && now - lastScreenshotNanos >= screenshotSeconds * 1_000_000_000L) {
            Screenshot.grab(client.gameDirectory, client.getMainRenderTarget(), message -> { });
            lastScreenshotNanos = now;
        }
    }

    private void writeSample(CinematicDirector director, CameraPose pose, long now) {
        long elapsedMillis = (now - startedNanos) / 1_000_000L;
        try {
            writer.write(String.format(
                    Locale.ROOT,
                    "%d,%.6f,%.6f,%.6f,%.4f,%.4f,%.4f,%.4f,%s,%s,%s%n",
                    elapsedMillis,
                    pose.position().x,
                    pose.position().y,
                    pose.position().z,
                    pose.yaw(),
                    pose.pitch(),
                    pose.fov(),
                    pose.focusDistance(),
                    safe(director.currentShotType()),
                    safe(director.currentSubjectType()),
                    csv(director.plannerSource())
            ));
            writer.flush();
        } catch (IOException exception) {
            stop();
        }
    }

    public void stop() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
        }
        writer = null;
    }

    public boolean isRecording() { return writer != null; }
    public Path currentPath() { return currentPath; }

    private static String safe(Object value) { return value == null ? "none" : value.toString(); }
    private static String csv(String value) { return value == null ? "none" : value.replace(',', '_'); }

}
