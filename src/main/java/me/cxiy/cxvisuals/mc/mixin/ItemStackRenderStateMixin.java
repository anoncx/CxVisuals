package me.cxiy.cxvisuals.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cxiy.cxvisuals.mc.modules.swings.SwingTagged;
import me.cxiy.cxvisuals.mc.modules.swings.Swings;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements SwingTagged {
  @Unique private LivingEntity cxvisuals$swinger;

  @Override
  public LivingEntity cxvisuals$swinger() {
    return cxvisuals$swinger;
  }

  @Override
  public void cxvisuals$assign(LivingEntity swinger) {
    cxvisuals$swinger = swinger;
  }

  @Inject(method = "clear", at = @At("HEAD"))
  private void cxvisuals$forget(CallbackInfo ci) {
    cxvisuals$swinger = null;
  }

  @Inject(method = "submit", at = @At("HEAD"))
  private void cxvisuals$beginSwing(
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      int overlayCoords,
      int outlineColor,
      CallbackInfo ci) {
    if (cxvisuals$swinger != null) {
      Swings.begin(this);
    }
  }

  @Inject(method = "submit", at = @At("RETURN"))
  private void cxvisuals$endSwing(
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      int overlayCoords,
      int outlineColor,
      CallbackInfo ci) {
    if (cxvisuals$swinger != null) {
      Swings.end();
    }
  }
}
