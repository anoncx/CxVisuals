package me.cxiy.cxvisuals.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cxiy.cxvisuals.mc.modules.nametags.Nametags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class TextDisplayRendererMixin {
  @Inject(method = "submitInner", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$restyle(
      TextDisplayEntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      float interpolationProgress,
      CallbackInfo ci) {
    if (Nametags.captureDisplay(state, poseStack)) {
      ci.cancel();
    }
  }
}
