package me.cxiy.cxvisuals.mc.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

public final class Features {
  private Features() {}

  public static void solid(GameRenderer renderer, SubmitNodeStorage storage, RenderTarget target) {
    redirect(target);
    try (FeatureRenderDispatcher.PreparedFrame frame =
        renderer.featureRenderDispatcher().prepareFrame(storage)) {
      frame.executeSolid();
    } finally {
      restore();
    }
  }

  public static void outline(
      GameRenderer renderer, SubmitNodeStorage storage, RenderTarget target) {
    redirect(target);
    try (FeatureRenderDispatcher.PreparedFrame frame =
        renderer.featureRenderDispatcher().prepareFrame(storage)) {
      frame.executeOutline();
    } finally {
      restore();
    }
  }

  private static void redirect(RenderTarget target) {
    RenderSystem.outputColorTextureOverride = target.getColorTextureView();
    RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
  }

  private static void restore() {
    RenderSystem.outputColorTextureOverride = null;
    RenderSystem.outputDepthTextureOverride = null;
  }
}
