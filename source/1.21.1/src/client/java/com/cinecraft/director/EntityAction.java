package com.cinecraft.director;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Lightweight client-side action classification used to select coverage. */
public enum EntityAction {
    STILL,
    WALKING,
    RUNNING,
    SWIMMING,
    FLYING,
    SLEEPING,
    USING_ITEM,
    COMBAT;

    public static EntityAction detect(Entity entity) {
        if (entity instanceof LivingEntity living) {
            if (living.isSleeping()) return SLEEPING;
            if (living.hurtTime > 0 || living.getLastHurtMob() != null) return COMBAT;
            if (living.isUsingItem() || living.swinging) return USING_ITEM;
        }
        if (entity.isSwimming() || entity.isInWater()) return SWIMMING;
        double horizontalSpeedSquared = entity.getDeltaMovement().horizontalDistanceSqr();
        if (!entity.onGround() && (horizontalSpeedSquared > 0.006 || Math.abs(entity.getDeltaMovement().y) > 0.045)) {
            return FLYING;
        }
        if (horizontalSpeedSquared > 0.10) return RUNNING;
        if (horizontalSpeedSquared > 0.0025) return WALKING;
        return STILL;
    }

    public double leadDistance() {
        return switch (this) {
            case RUNNING, FLYING -> 2.4;
            case SWIMMING -> 1.7;
            case WALKING, COMBAT -> 1.1;
            case USING_ITEM -> 0.45;
            default -> 0.0;
        };
    }

    public boolean moving() {
        return this == WALKING || this == RUNNING || this == SWIMMING || this == FLYING || this == COMBAT;
    }
}
