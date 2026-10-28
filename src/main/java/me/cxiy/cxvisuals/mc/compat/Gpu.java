package me.cxiy.cxvisuals.mc.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.Minecraft;

public final class Gpu {
  private static final Set<RenderPipeline> BROKEN =
      Collections.newSetFromMap(new IdentityHashMap<>());

  private Gpu() {}

  public static boolean ready() {
    return Minecraft.getInstance().isGameLoadFinished();
  }

  public static boolean usable(RenderPipeline pipeline) {
    if (BROKEN.contains(pipeline) || !ready()) {
      return false;
    }
    try {
      if (RenderSystem.getCompiledPipelineNullable(pipeline) != null) {
        return true;
      }
    } catch (RuntimeException ignored) {
    }
    BROKEN.add(pipeline);
    return false;
  }

  public static void use(RenderPass pass, RenderPipeline pipeline) {
    pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
  }

  public static void bind(RenderPass pass, String name, GpuTextureView view, GpuSampler sampler) {
    pass.setUniform(name, view, sampler);
  }
}
