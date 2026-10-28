package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import me.cxiy.cxvisuals.mc.gui.ScreenTilt;
import me.cxiy.cxvisuals.mc.menu.MenuInput;
import me.cxiy.cxvisuals.mc.menu.MenuStage;
import me.cxiy.cxvisuals.mc.modules.hud.Probe;
import me.cxiy.cxvisuals.mc.modules.idlecamera.IdleCamera;
import me.cxiy.cxvisuals.mc.modules.zoom.Zoom;
import me.cxiy.cxvisuals.menu.views.MenuView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.cxiy.cxvisuals.mc.compat.Mouse;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
  private static final int PRESS = 1;
  private static final String CLICKED =
      "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z";
  private static final String RELEASED =
      "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z";
  private static final String DRAGGED =
      "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z";
  private static final String TURN = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V";
  private static final String SMOOTH = "Lnet/minecraft/client/Options;smoothCamera:Z";
  private static final String SCROLLED =
      "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z";

  @Shadow private double xpos;

  @Shadow private double ypos;

  @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$button(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
    IdleCamera.IDLE.poke();
    if (IdleCamera.menuHidden()) {
      ci.cancel();
      return;
    }
    if (action == PRESS) {
      Probe.click(Mouse.button(info));
    }
  }

  @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$scroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
    IdleCamera.IDLE.poke();
    if (IdleCamera.menuHidden() || Zoom.scroll(yOffset)) {
      ci.cancel();
    }
  }

  @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = TURN))
  private void cxvisuals$zoomTurn(
      LocalPlayer player, double x, double y, Operation<Void> original) {
    original.call(player, Zoom.turn(x), Zoom.turn(y));
  }

  @ModifyExpressionValue(method = "turnPlayer", at = @At(value = "FIELD", target = SMOOTH))
  private boolean cxvisuals$zoomSmooth(boolean smooth) {
    return smooth || Zoom.smoothTurn();
  }

  @Inject(method = "onMove", at = @At("HEAD"))
  private void cxvisuals$move(
      long handle, double x, double y, double xrel, double yrel, CallbackInfo ci) {
    IdleCamera.IDLE.mouseMoved(x, y);
  }

  @ModifyReturnValue(
      method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D",
      at = @At("RETURN"))
  private double cxvisuals$tiltX(double x, Window window) {
    double flat =
        ScreenTilt.active() ? ScreenTilt.unmapX(x, MouseHandler.getScaledYPos(window, ypos)) : x;
    return MenuStage.pointerX(Minecraft.getInstance().gui.screen(), flat);
  }

  @ModifyReturnValue(
      method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D",
      at = @At("RETURN"))
  private double cxvisuals$tiltY(double y, Window window) {
    double flat =
        ScreenTilt.active() ? ScreenTilt.unmapY(MouseHandler.getScaledXPos(window, xpos), y) : y;
    return MenuStage.pointerY(Minecraft.getInstance().gui.screen(), flat);
  }

  @WrapOperation(method = "onButton", at = @At(value = "INVOKE", target = CLICKED))
  private boolean cxvisuals$menuClick(
      Screen screen, MouseButtonEvent event, boolean doubled, Operation<Boolean> original) {
    MenuView view = MenuInput.claim(screen);
    if (view != null) {
      return MenuInput.pressed(view, event);
    }
    return MenuInput.framePressed(screen, event) || original.call(screen, event, doubled);
  }

  @WrapOperation(method = "onButton", at = @At(value = "INVOKE", target = RELEASED))
  private boolean cxvisuals$menuRelease(
      Screen screen, MouseButtonEvent event, Operation<Boolean> original) {
    MenuView view = MenuInput.claim(screen);
    if (view != null) {
      return MenuInput.released(view, event);
    }
    MenuInput.frameReleased(screen, event);
    return original.call(screen, event);
  }

  @WrapOperation(method = "handleAccumulatedMovement", at = @At(value = "INVOKE", target = DRAGGED))
  private boolean cxvisuals$menuDrag(
      Screen screen, MouseButtonEvent event, double dx, double dy, Operation<Boolean> original) {
    MenuView view = MenuInput.claim(screen);
    return view == null ? original.call(screen, event, dx, dy) : MenuInput.dragged(view, event);
  }

  @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = SCROLLED))
  private boolean cxvisuals$menuScroll(
      Screen screen,
      double x,
      double y,
      double scrollX,
      double scrollY,
      Operation<Boolean> original) {
    MenuView view = MenuInput.claim(screen);
    return view == null
        ? original.call(screen, x, y, scrollX, scrollY)
        : MenuInput.scrolled(view, x, y, scrollY);
  }
}
