package com.cinecraft.mixin;

import com.cinecraft.CinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's equivalent of the Dynamic FPS Fabric FREX compatibility query. */
@Pseudo
@Mixin(targets = "net.lostluma.dynamic_fps.impl.neoforge.service.NeoForgeModCompat", remap = false)
abstract class DynamicFpsMixin {
    @Inject(method = "isDisabled", at = @At("RETURN"), cancellable = true, require = 0)
    private void cinecraft$keepCinematicRendering(CallbackInfoReturnable<Boolean> cir) {
        if (CinecraftClient.requiresUnthrottledRendering()) cir.setReturnValue(true);
    }
}
