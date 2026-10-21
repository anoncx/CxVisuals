package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.gui.ScreenTilt;
import me.cxiy.cxvisuals.mc.menu.MenuStage;
import me.cxiy.cxvisuals.mc.menu.Skins;
import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
  private static final String CONTENT =
      "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";

  @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$menuPanorama(GuiGraphicsExtractor graphics, float a, CallbackInfo ci) {
    if (MenuStage.owns((Screen) (Object) this)) {
      ci.cancel();
    }
  }

  @Inject(
      method = {
        "extractBlurredBackground",
        "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V",
        "extractTransparentBackground"
      },
      at = @At("HEAD"),
      cancellable = true)
  private void cxvisuals$menuBackdrop(CallbackInfo ci) {
    if (MenuStage.owns((Screen) (Object) this)) {
      ci.cancel();
    }
  }

  @Inject(
      method = "extractRenderStateWithTooltipAndSubtitles",
      at = @At("HEAD"),
      cancellable = true)
  private void cxvisuals$menuBegin(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
    if (MenuStage.render(graphics, (Screen) (Object) this, mouseX, mouseY)) {
      ci.cancel();
    }
  }

  @ModifyVariable(
      method = {"init(II)V", "resize"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0)
  private int cxvisuals$frameWidth(int width) {
    return Skins.framed((Screen) (Object) this) ? MenuStage.frameWidth() : width;
  }

  @ModifyVariable(
      method = {"init(II)V", "resize"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1)
  private int cxvisuals$frameHeight(int height) {
    return Skins.framed((Screen) (Object) this) ? MenuStage.frameHeight() : height;
  }

  @Inject(method = "extractBackground", at = @At("RETURN"))
  private void cxvisuals$tiltAfterBackdrop(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
    if (cxvisuals$top()) {
      Animations.screenBegin(graphics, (Screen) (Object) this);
      ScreenTilt.begin(graphics, (Screen) (Object) this);
    }
  }

  @Inject(
      method = "extractRenderStateWithTooltipAndSubtitles",
      at = @At(value = "INVOKE", target = CONTENT))
  private void cxvisuals$tiltBegin(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
    if (cxvisuals$top()) {
      Animations.screenBegin(graphics, (Screen) (Object) this);
      ScreenTilt.begin(graphics, (Screen) (Object) this);
    }
  }

  @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
  private void cxvisuals$tiltEnd(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
    if (cxvisuals$top()) {
      Animations.screenEnd(graphics);
      ScreenTilt.end(graphics);
    }
  }

  @Unique
  private boolean cxvisuals$top() {
    return (Object) this == Minecraft.getInstance().gui.screen();
  }
}
