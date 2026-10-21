package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
  private static final String FILL = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V";

  @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$begin(
      GuiGraphicsExtractor graphics,
      int screenWidth,
      Scoreboard scoreboard,
      Objective objective,
      CallbackInfo ci) {
    Hud.tabStart();
    if (Hud.begin(Hud.tabList, graphics)) {
      ci.cancel();
    }
  }

  @Inject(method = "extractRenderState", at = @At("RETURN"))
  private void cxvisuals$end(
      GuiGraphicsExtractor graphics,
      int screenWidth,
      Scoreboard scoreboard,
      Objective objective,
      CallbackInfo ci) {
    Hud.tabEnd();
    Hud.end(Hud.tabList, graphics);
  }

  @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = FILL))
  private void cxvisuals$fill(
      GuiGraphicsExtractor graphics,
      int x0,
      int y0,
      int x1,
      int y1,
      int color,
      Operation<Void> original) {
    if (!Hud.tabFill(graphics, x0, y0, x1, y1, color)) {
      original.call(graphics, x0, y0, x1, y1, color);
    }
  }

  @WrapMethod(method = "extractRenderState")
  private void cxvisuals$pop(
      GuiGraphicsExtractor graphics,
      int screenWidth,
      Scoreboard scoreboard,
      Objective objective,
      Operation<Void> original) {
    float scale = Animations.tabScale();
    if (scale == 1.0F) {
      original.call(graphics, screenWidth, scoreboard, objective);
      return;
    }
    float cx = screenWidth * 0.5F;
    graphics.pose().pushMatrix();
    graphics.pose().translate(cx, 10.0F);
    graphics.pose().scale(scale, scale);
    graphics.pose().translate(-cx, -10.0F);
    original.call(graphics, screenWidth, scoreboard, objective);
    graphics.pose().popMatrix();
  }
}
