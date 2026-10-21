package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cxiy.cxvisuals.mc.modules.idlecamera.IdleCamera;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class GuiMixin {
  @WrapOperation(
      method = "extractRenderState",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
  private void cxvisuals$hideMenu(
      Screen screen,
      GuiGraphicsExtractor graphics,
      int mouseX,
      int mouseY,
      float a,
      Operation<Void> original) {
    if (!IdleCamera.menuHidden()) {
      original.call(screen, graphics, mouseX, mouseY, a);
    }
  }
}
