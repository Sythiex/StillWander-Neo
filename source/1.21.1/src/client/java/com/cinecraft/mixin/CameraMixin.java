package com.cinecraft.mixin;

import com.cinecraft.CinecraftClient;
import com.cinecraft.camera.CameraPose;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow private boolean detached;
    @Shadow private float yRot;
    @Shadow private float xRot;
    @Shadow private float roll;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("TAIL"))
    private void cinecraft$applyPose(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        CameraPose pose = CinecraftClient.cameraPose((Camera) (Object) this, tickDelta);
        if (pose == null) return;
        // WorldRenderer hides the focused entity for a first-person camera.
        // Mark only this rendered frame as third person so the player is visible.
        this.detached = true;
        setPosition(pose.position().x, pose.position().y, pose.position().z);
        // The pose is already in world space. NeoForge's setRotation(FFF) is hooked by
        // Sable to inherit a seat's ship rotation, which would rotate this view twice.
        // Keep all orientation fields in sync using Camera's zero-roll convention.
        this.yRot = pose.yaw();
        this.xRot = pose.pitch();
        this.roll = 0.0f;
        this.rotation.rotationYXZ((float) Math.PI - this.yRot * (float) (Math.PI / 180.0),
                -this.xRot * (float) (Math.PI / 180.0), 0.0f);
        this.forwards.set(0, 0, -1).rotate(this.rotation);
        this.up.set(0, 1, 0).rotate(this.rotation);
        this.left.set(-1, 0, 0).rotate(this.rotation);
    }
}
