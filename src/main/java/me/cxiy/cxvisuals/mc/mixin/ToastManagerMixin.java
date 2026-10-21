package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.hud.Achievements;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
  @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$achievements(Toast toast, CallbackInfo ci) {
    if (Achievements.intercept(toast)) {
      ci.cancel();
    }
  }
}
