package com.cinecraft.mixin;

import com.cinecraft.CinecraftClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void cinecraft$input(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (!CinecraftClient.isControlKey(key, scanCode)) CinecraftClient.activity();
    }
}
