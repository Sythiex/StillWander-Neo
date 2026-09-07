package com.cinecraft.compat;

/** Shared transition handling for ticks, render hooks, input, capture, and FPS requests. */
public final class SessionGate {
    public enum BlockReason { NONE, NO_WORLD, REPLAY, EXTERNAL_CAMERA, UNAVAILABLE_API }

    private final Runnable release;
    private final Runnable resetIdle;
    private Object world;
    private Object player;
    private BlockReason reason = BlockReason.NO_WORLD;

    public SessionGate(Runnable release, Runnable resetIdle) {
        this.release = release;
        this.resetIdle = resetIdle;
    }

    public boolean update(Object currentWorld, Object currentPlayer, BlockReason currentReason) {
        if (currentWorld != world || currentPlayer != player || currentReason != reason) {
            // Publish the restriction first, so cleanup can never expose the previous session.
            world = currentWorld;
            player = currentPlayer;
            reason = currentReason;
            // Release callbacks can synchronously refresh FPS policy, including idle eligibility.
            resetIdle.run();
            release.run();
        }
        return reason == BlockReason.NONE;
    }

    public BlockReason reason() { return reason; }
}
