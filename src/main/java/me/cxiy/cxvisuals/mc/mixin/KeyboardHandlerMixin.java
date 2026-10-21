package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cxiy.cxvisuals.mc.menu.MenuInput;
import me.cxiy.cxvisuals.mc.modules.idlecamera.IdleCamera;
import me.cxiy.cxvisuals.mc.modules.inventory.Inventory;
import me.cxiy.cxvisuals.menu.views.MenuView;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
  private static final int RELEASE = 0;
  private static final String PRESSED =
      "Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z";
  private static final String TYPED =
      "Lnet/minecraft/client/gui/screens/Screen;charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z";

  @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$key(long handle, int action, KeyEvent event, CallbackInfo ci) {
    if (action != RELEASE) {
      IdleCamera.IDLE.poke();
      if (IdleCamera.menuHidden()) {
        ci.cancel();
      }
    }
  }

  @WrapOperation(method = "keyPress", at = @At(value = "INVOKE", target = PRESSED))
  private boolean cxvisuals$menuKey(Screen screen, KeyEvent event, Operation<Boolean> original) {
    MenuView view = MenuInput.claim(screen);
    return view == null ? original.call(screen, event) : MenuInput.key(view, event);
  }

  @WrapOperation(method = "charTyped", at = @At(value = "INVOKE", target = TYPED))
  private boolean cxvisuals$menuChar(
      Screen screen, CharacterEvent event, Operation<Boolean> original) {
    if (screen instanceof AbstractContainerScreen<?> container
        && Inventory.charTyped(container, event.codepoint())) {
      return true;
    }
    MenuView view = MenuInput.claim(screen);
    return view == null ? original.call(screen, event) : MenuInput.typed(view, event);
  }
}
