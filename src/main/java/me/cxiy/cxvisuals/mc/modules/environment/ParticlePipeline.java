package me.cxiy.cxvisuals.mc.modules.environment;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

final class ParticlePipeline {
  static RenderPipeline pipeline;

  private static boolean checked;
  private static boolean usable;

  private ParticlePipeline() {}

  static boolean usable() {
    if (!checked && Gpu.ready()) {
      checked = true;
      try {
        Identifier shader =
            Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/environment_particles");
        pipeline =
            RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        CxVisualsClient.MOD_ID, "pipeline/environment_particles"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withColorTargetState(
                    new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .withDepthStencilState(
                    new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withCull(false)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
        usable = Gpu.usable(pipeline);
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Environment particle shader failed to compile", e);
      }
      if (!usable) {
        CxVisualsClient.LOG.warn("Environment particles unavailable, nothing drifts in the air");
      }
    }
    return usable;
  }
}
