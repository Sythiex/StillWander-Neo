package com.cinecraft.mixin;

import com.cinecraft.CinecraftClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents vanilla head bob and hurt tilt from being layered over a directed camera pose. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void cinecraft$disableHurtTilt(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (CinecraftClient.hasCameraControl()) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void cinecraft$disableViewBob(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (CinecraftClient.hasCameraControl()) ci.cancel();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void cinecraft$applyShotFov(
            Camera camera,
            float tickDelta,
            boolean changingFov,
            CallbackInfoReturnable<Double> cir
    ) {
        if (!CinecraftClient.hasCameraControl(camera)) return;
        float fov = CinecraftClient.DIRECTOR.currentFov();
        if (Float.isFinite(fov)) cir.setReturnValue((double) fov);
    }
}
