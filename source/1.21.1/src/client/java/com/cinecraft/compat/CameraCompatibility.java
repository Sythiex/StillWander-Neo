package com.cinecraft.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/** Session detection never reads or changes replay recording, packets, or timelines. */
public final class CameraCompatibility {
    private static final OptionalActivityProbe REPLAY = new OptionalActivityProbe(
            () -> loaded("reforgedplaymod") || loaded("replaymod"),
            "com.replaymod.replay.ReplayModReplay", "instance", "getReplayHandler",
            error -> LogUtils.getLogger().warn("Still Wander disabled: replay session API unavailable", error));
    private static final OptionalActivityProbe FREECAM = new OptionalActivityProbe(
            () -> loaded("freecam"), "net.xolt.freecam.Freecam", null, "isEnabled",
            error -> LogUtils.getLogger().warn("Still Wander disabled: Freecam state API unavailable", error));

    private CameraCompatibility() { }

    public static SessionGate.BlockReason blockReason(Minecraft client) {
        OptionalActivityProbe.State replay = REPLAY.query();
        if (replay == OptionalActivityProbe.State.ACTIVE) return SessionGate.BlockReason.REPLAY;
        if (replay == OptionalActivityProbe.State.UNAVAILABLE) return SessionGate.BlockReason.UNAVAILABLE_API;
        if (client == null || client.level == null || client.player == null) return SessionGate.BlockReason.NO_WORLD;
        OptionalActivityProbe.State freecam = FREECAM.query();
        if (freecam == OptionalActivityProbe.State.ACTIVE) return SessionGate.BlockReason.EXTERNAL_CAMERA;
        if (freecam == OptionalActivityProbe.State.UNAVAILABLE) return SessionGate.BlockReason.UNAVAILABLE_API;
        // Also covers vanilla spectating and mods which substitute the camera entity.
        if (client.getCameraEntity() != client.player) return SessionGate.BlockReason.EXTERNAL_CAMERA;
        return SessionGate.BlockReason.NONE;
    }

    private static boolean loaded(String id) {
        return ModList.get() != null && ModList.get().isLoaded(id);
    }
}
