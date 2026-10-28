package me.cxiy.cxvisuals.mc.compat;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import org.joml.Vector4fc;

public final class Targets {
  private Targets() {}

  public static void clear(
      CommandEncoder encoder,
      RenderTarget target,
      Vector4fc color,
      int x,
      int y,
      int width,
      int height) {
    encoder.clearColorAndDepthTextures(
        target.getColorTexture(), color, target.getDepthTexture(), 0.0, x, y, width, height);
  }

  public static TextureTarget create(String label, int width, int height) {
    return new TextureTarget(label, width, height, true, GpuFormat.RGBA8_UNORM);
  }
}
