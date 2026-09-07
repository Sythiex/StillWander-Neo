package com.cinecraft.director;

import com.cinecraft.camera.ShotReferenceFrame;
import com.cinecraft.compat.WorldAnchor;
import com.cinecraft.compat.WorldCoordinates;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/** One live subject, a fixed feature, or a two-subject composition. */
public final class SceneSubject {
    private final SubjectType type;
    private final Vec3 target;
    private final String key;
    private final Entity trackedEntity;
    private final SubjectFocus focus;
    private final Vec3 primaryTarget;
    private final Entity secondaryEntity;
    private final Vec3 secondaryTarget;
    private final EntityAction action;
    private WorldAnchor anchor;
    private SceneSubject secondaryFeature;

    public static SceneSubject feature(WorldAnchor anchor, String key) {
        SceneSubject subject = new SceneSubject(SubjectType.FEATURE, anchor.current(), key);
        subject.anchor = anchor;
        return subject;
    }

    public ShotReferenceFrame referenceFrame() {
        if (anchor != null) return anchor.referenceFrame();
        return trackedEntity == null ? ShotReferenceFrame.WORLD : WorldCoordinates.referenceFrame(trackedEntity, target);
    }

    public SceneSubject(SubjectType type, Vec3 target, String key) {
        this(type, target, key, null, SubjectFocus.CENTER);
    }

    public SceneSubject(SubjectType type, Vec3 target, String key, Entity trackedEntity) {
        this(type, target, key, trackedEntity, SubjectFocus.CENTER);
    }

    public SceneSubject(
            SubjectType type,
            Vec3 target,
            String key,
            Entity trackedEntity,
            SubjectFocus focus
    ) {
        this(
                type,
                target,
                key,
                trackedEntity,
                focus,
                target,
                null,
                null,
                trackedEntity == null ? EntityAction.STILL : EntityAction.detect(trackedEntity)
        );
    }

    private SceneSubject(
            SubjectType type,
            Vec3 target,
            String key,
            Entity trackedEntity,
            SubjectFocus focus,
            Vec3 primaryTarget,
            Entity secondaryEntity,
            Vec3 secondaryTarget,
            EntityAction action
    ) {
        this.type = type;
        this.target = target;
        this.key = key;
        this.trackedEntity = trackedEntity;
        this.focus = focus;
        this.primaryTarget = primaryTarget;
        this.secondaryEntity = secondaryEntity;
        this.secondaryTarget = secondaryTarget;
        this.action = action;
    }

    public static SceneSubject entityGroup(Entity first, Entity second) {
        Vec3 firstTarget = targetFor(first, SubjectFocus.CENTER);
        Vec3 secondTarget = targetFor(second, SubjectFocus.CENTER);
        return new SceneSubject(
                SubjectType.GROUP,
                firstTarget.lerp(secondTarget, 0.5),
                "group:" + first.getUUID() + ":" + second.getUUID(),
                first,
                SubjectFocus.CENTER,
                firstTarget,
                second,
                secondTarget,
                faster(EntityAction.detect(first), EntityAction.detect(second))
        );
    }

    public static SceneSubject playerAndFeature(Entity player, SceneSubject feature, String key) {
        Vec3 playerTarget = targetFor(player, SubjectFocus.CENTER);
        SceneSubject subject = new SceneSubject(
                SubjectType.GROUP,
                playerTarget.lerp(feature.target(), 0.5),
                key,
                player,
                SubjectFocus.CENTER,
                playerTarget,
                null,
                feature.target(),
                EntityAction.STILL
        );
        subject.secondaryFeature = feature;
        return subject;
    }

    public SubjectType type() { return type; }
    public Vec3 target() { return target; }
    public String key() { return key; }
    public Entity trackedEntity() { return trackedEntity; }
    public SubjectFocus focus() { return focus; }
    public Entity secondaryEntity() { return secondaryEntity; }
    public EntityAction action() { return action; }
    public boolean isGroup() { return type == SubjectType.GROUP; }
    public boolean hasLiveTracking() { return trackedEntity != null || anchor != null; }

    /** Mixed groups must not drag the passenger's camera into another reference frame. */
    public boolean belongsTo(dev.ryanhcode.sable.companion.SubLevelAccess ship) {
        if (ship == null) return false;
        boolean primary = anchor != null ? anchor.ship() == ship
                : trackedEntity != null && WorldCoordinates.shipOf(trackedEntity) == ship;
        if (!primary) return false;
        if (secondaryFeature != null) return secondaryFeature.belongsTo(ship);
        if (secondaryEntity != null) return WorldCoordinates.shipOf(secondaryEntity) == ship;
        return secondaryTarget == null;
    }

    public Vec3 movementVector() {
        if (trackedEntity == null) return Vec3.ZERO;
        Vec3 movement = WorldCoordinates.entityVelocity(trackedEntity);
        if (secondaryEntity != null) movement = movement.add(WorldCoordinates.entityVelocity(secondaryEntity)).scale(0.5);
        return movement;
    }

    public boolean isAvailable() {
        if (anchor != null && !anchor.available()) return false;
        if (secondaryFeature != null && !secondaryFeature.isAvailable()) return false;
        if (trackedEntity != null && (trackedEntity.isRemoved() || !trackedEntity.isAlive())) return false;
        return secondaryEntity == null || (!secondaryEntity.isRemoved() && secondaryEntity.isAlive());
    }

    public Vec3 currentTarget() {
        Vec3 primary = primaryCurrentTarget();
        if (secondaryTarget == null) return addLead(primary, trackedEntity, action);
        Vec3 secondary = secondaryCurrentTarget();
        Vec3 center = primary.lerp(secondary, 0.5);
        Vec3 averageVelocity = trackedEntity == null ? Vec3.ZERO : WorldCoordinates.entityVelocity(trackedEntity);
        if (secondaryEntity != null) averageVelocity = averageVelocity.add(WorldCoordinates.entityVelocity(secondaryEntity)).scale(0.5);
        return addLead(center, averageVelocity, action.leadDistance() * 0.65);
    }

    public Vec3 rackFocusTarget(double progress) {
        if (secondaryTarget == null) return currentTarget();
        double eased = progress * progress * (3.0 - 2.0 * progress);
        return primaryCurrentTarget().lerp(secondaryCurrentTarget(), eased);
    }

    private Vec3 primaryCurrentTarget() {
        if (anchor != null) return anchor.current();
        if (trackedEntity == null || trackedEntity.isRemoved()) return primaryTarget;
        return targetFor(trackedEntity, focus);
    }

    private Vec3 secondaryCurrentTarget() {
        if (secondaryFeature != null) return secondaryFeature.currentTarget();
        if (secondaryEntity == null || secondaryEntity.isRemoved()) return secondaryTarget;
        return targetFor(secondaryEntity, SubjectFocus.CENTER);
    }

    private static Vec3 addLead(Vec3 point, Entity entity, EntityAction action) {
        if (entity == null) return point;
        return addLead(point, WorldCoordinates.entityVelocity(entity), action.leadDistance());
    }

    private static Vec3 addLead(Vec3 point, Vec3 velocity, double distance) {
        Vec3 horizontal = new Vec3(velocity.x, 0.0, velocity.z);
        if (horizontal.lengthSqr() < 0.0001 || distance <= 0.0) return point;
        return point.add(horizontal.normalize().scale(distance));
    }

    public static Vec3 targetFor(Entity entity, SubjectFocus focus) {
        double height = entity.getBbHeight();
        return switch (focus) {
            case CENTER -> WorldCoordinates.entityOffset(entity, new Vec3(0.0, height * 0.55, 0.0));
            case HEAD -> WorldCoordinates.entityOffset(entity, new Vec3(0.0, height * 0.86, 0.0));
            case CHEST -> WorldCoordinates.entityOffset(entity, new Vec3(0.0, height * 0.62, 0.0));
            case MAIN_HAND, OFF_HAND -> handTarget(entity, focus == SubjectFocus.MAIN_HAND);
        };
    }

    private static Vec3 handTarget(Entity entity, boolean mainHand) {
        double yaw = Math.toRadians(entity.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
        boolean mainArmRight = !(entity instanceof LivingEntity living) || living.getMainArm() == HumanoidArm.RIGHT;
        boolean useRight = mainHand == mainArmRight;
        double side = useRight ? 0.42 : -0.42;
        return WorldCoordinates.entityOffset(entity, new Vec3(0.0, entity.getBbHeight() * 0.56, 0.0)
                .add(right.scale(side))
                .add(forward.scale(0.14)));
    }

    private static EntityAction faster(EntityAction first, EntityAction second) {
        return actionRank(first) >= actionRank(second) ? first : second;
    }

    private static int actionRank(EntityAction action) {
        return switch (action) {
            case COMBAT -> 7;
            case FLYING, RUNNING -> 6;
            case SWIMMING -> 5;
            case WALKING -> 4;
            case USING_ITEM -> 3;
            case STILL -> 2;
            case SLEEPING -> 1;
        };
    }
}
