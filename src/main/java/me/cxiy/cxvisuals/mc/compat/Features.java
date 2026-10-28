package me.cxiy.cxvisuals.mc.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

public final class Features {
  private Features() {}

  public static void solid(GameRenderer renderer, SubmitNodeStorage storage, RenderTarget target) {
    try (FeatureRenderDispatcher.PreparedFrame frame =
            renderer.featureRenderDispatcher().prepareFrame(storage);
        RenderPass pass = open(target)) {
      frame.executeSolid(pass);
    }
  }

  public static void outline(
      GameRenderer renderer, SubmitNodeStorage storage, RenderTarget target) {
    try (FeatureRenderDispatcher.PreparedFrame frame =
            renderer.featureRenderDispatcher().prepareFrame(storage);
        RenderPass pass = open(target)) {
      frame.executeOutline(pass);
    }
  }

  private static RenderPass open(RenderTarget target) {
    RenderPass pass =
        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                () -> "CxVisuals features",
                target.getColorTextureView(),
                Optional.empty(),
                target.getDepthTextureView(),
                OptionalDouble.empty());
    RenderSystem.bindDefaultUniforms(pass);
    return pass;
  }
}
