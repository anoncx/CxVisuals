package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.hud.Achievements;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeToast.class)
public abstract class RecipeToastMixin {
  @Inject(method = "addItem", at = @At("HEAD"))
  private void cxvisuals$unlocked(ItemStack category, ItemStack unlocked, CallbackInfo ci) {
    Achievements.recipe(unlocked);
  }
}
