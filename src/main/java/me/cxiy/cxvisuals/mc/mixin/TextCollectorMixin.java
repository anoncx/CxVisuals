package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.cxiy.cxvisuals.mc.gui.HudText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.GuiGraphicsExtractor$RenderingTextCollector")
public abstract class TextCollectorMixin {
  @WrapOperation(
      method =
          "accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addText(Lnet/minecraft/client/renderer/state/gui/GuiTextRenderState;)V"))
  private void cxvisuals$hudFont(
      GuiRenderState state,
      GuiTextRenderState made,
      Operation<Void> original,
      @Local(argsOnly = true) FormattedCharSequence text,
      @Local(argsOnly = true) ActiveTextCollector.Parameters parameters,
      @Local(argsOnly = true, ordinal = 1) int y,
      @Local(ordinal = 2) int leftX) {
    if (HudText.active()) {
      HudText.submit(
          state,
          Minecraft.getInstance().font,
          text,
          parameters.pose(),
          leftX,
          y,
          ARGB.white(parameters.opacity()),
          true,
          parameters.scissor());
    } else {
      original.call(state, made);
    }
  }
}
