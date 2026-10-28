package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.shaders.EntityShaders;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {
  @Inject(method = "renderAllFeatures", at = @At("HEAD"))
  private void cxvisuals$handLens(SubmitNodeStorage submitNodeStorage, CallbackInfo ci) {
    EntityShaders.handDrawn();
  }
}
