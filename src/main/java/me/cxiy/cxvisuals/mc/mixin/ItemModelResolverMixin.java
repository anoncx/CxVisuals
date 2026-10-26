package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.swings.Swings;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
  @Inject(method = "updateForTopItem", at = @At("TAIL"))
  private void cxvisuals$tagSwinger(
      ItemStackRenderState output,
      ItemStack item,
      ItemDisplayContext displayContext,
      @Nullable Level level,
      @Nullable ItemOwner owner,
      int seed,
      CallbackInfo ci) {
    Swings.tag(output, item, displayContext, owner);
  }
}
