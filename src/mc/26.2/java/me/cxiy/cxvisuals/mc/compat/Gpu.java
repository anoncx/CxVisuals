package me.cxiy.cxvisuals.mc.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

public final class Gpu {
  private Gpu() {}

  public static boolean ready() {
    return true;
  }

  public static boolean usable(RenderPipeline pipeline) {
    return RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
  }

  public static void use(RenderPass pass, RenderPipeline pipeline) {
    pass.setPipeline(pipeline);
  }

  public static void bind(RenderPass pass, String name, GpuTextureView view, GpuSampler sampler) {
    pass.bindTexture(name, view, sampler);
  }
}
