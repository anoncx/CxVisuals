package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExperienceBar.class)
public abstract class ExperienceBarMixin {
  @ModifyExpressionValue(
      method = "extractBackground",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/player/LocalPlayer;experienceProgress:F"))
  private float cxvisuals$easedProgress(float progress) {
    return Animations.experience(progress);
  }
}
