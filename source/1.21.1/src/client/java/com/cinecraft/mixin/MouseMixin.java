package com.cinecraft.mixin;

import com.cinecraft.CinecraftClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseMixin {
    @Inject(method = "onMove", at = @At("HEAD"))
    private void cinecraft$move(long window, double x, double y, CallbackInfo ci) { CinecraftClient.activity(); }

    @Inject(method = "onPress", at = @At("HEAD"))
    private void cinecraft$click(long window, int button, int action, int modifiers, CallbackInfo ci) { CinecraftClient.activity(); }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void cinecraft$scroll(long window, double horizontal, double vertical, CallbackInfo ci) { CinecraftClient.activity(); }
}
