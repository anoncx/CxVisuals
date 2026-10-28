package me.cxiy.cxvisuals.mc.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityShaders;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {
  @Inject(method = "renderAllFeatures", at = @At("HEAD"))
  private static void cxvisuals$handLens(
      RenderPass renderPass, FeatureRenderDispatcher.PreparedFrame frame, CallbackInfo ci) {
    EntityShaders.handDrawn();
  }
}
