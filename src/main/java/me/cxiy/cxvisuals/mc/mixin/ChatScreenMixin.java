package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import me.cxiy.cxvisuals.mc.compat.Mouse;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
  @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$mediaClick(
      MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
    if (Mouse.button(event) == 0 && Hud.click(event.x(), event.y())) {
      cir.setReturnValue(true);
    }
  }

  @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$feedScroll(
      double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
    if (Hud.scroll(x, y, scrollY)) {
      cir.setReturnValue(true);
    }
  }
}
