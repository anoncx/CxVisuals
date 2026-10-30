package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.shaders.SceneDepth;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class PreparedFrameMixin {
  @Inject(method = "executeTranslucent", at = @At("HEAD"))
  private void cxvisuals$cleanDepth(CallbackInfo ci) {
    SceneDepth.capture();
  }
}
