package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import me.cxiy.cxvisuals.mc.gui.HudText;
import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import me.cxiy.cxvisuals.mc.modules.inventory.Inventory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
  @Shadow
  public abstract Matrix3x2fStack pose();

  @WrapOperation(
      method =
          "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addText(Lnet/minecraft/client/renderer/state/gui/GuiTextRenderState;)V"))
  private void cxvisuals$hudFont(
      GuiRenderState state,
      GuiTextRenderState made,
      Operation<Void> original,
      @Local(argsOnly = true) Font font,
      @Local(argsOnly = true) FormattedCharSequence text,
      @Local(argsOnly = true, ordinal = 0) int x,
      @Local(argsOnly = true, ordinal = 1) int y,
      @Local(argsOnly = true, ordinal = 2) int color,
      @Local(argsOnly = true) boolean dropShadow) {
    if (HudText.active()) {
      HudText.submit(state, font, text, made.pose, x, y, color, dropShadow, made.scissor);
    } else {
      original.call(state, made);
    }
  }

  @WrapMethod(method = "tooltip")
  private void cxvisuals$tooltipPop(
      Font font,
      List<ClientTooltipComponent> lines,
      int x,
      int y,
      ClientTooltipPositioner positioner,
      Identifier style,
      Operation<Void> original) {
    float scale = Animations.tooltipScale();
    if (scale == 1.0F) {
      original.call(font, lines, x, y, positioner, style);
      return;
    }
    Matrix3x2fStack pose = pose();
    pose.pushMatrix();
    pose.translate(x, y);
    pose.scale(scale, scale);
    pose.translate(-x, -y);
    original.call(font, lines, x, y, positioner, style);
    pose.popMatrix();
  }

  @Inject(
      method =
          "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
      at = @At("HEAD"),
      cancellable = true)
  private void cxvisuals$mapFace(
      @Nullable LivingEntity owner,
      @Nullable Level level,
      ItemStack stack,
      int x,
      int y,
      int seed,
      CallbackInfo ci) {
    if (Inventory.replacingMapFaces()
        && Inventory.mapFace((GuiGraphicsExtractor) (Object) this, stack, x, y)) {
      ci.cancel();
    }
  }
}
