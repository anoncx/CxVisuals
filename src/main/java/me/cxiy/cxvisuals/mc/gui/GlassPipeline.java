package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public final class GlassPipeline {
  public static final RenderPipeline GLASS =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.GLOBALS)
          .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
          .withBindGroupLayout(BindGroupLayouts.PROJECTION)
          .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
          .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/glass"))
          .withVertexShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/glass"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/glass"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
          .withVertexBinding(0, DefaultVertexFormat.ENTITY)
          .withPrimitiveTopology(PrimitiveTopology.QUADS)
          .build();

  private static boolean checked;
  private static boolean usable;

  private GlassPipeline() {}

  public static void recheck() {
    if (!usable) {
      checked = false;
    }
  }

  public static boolean usable() {
    if (!checked && Gpu.ready()) {
      checked = true;
      try {
        usable = Gpu.usable(GLASS);
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Glass shader failed to compile", e);
      }
      if (!usable) {
        CxVisualsClient.LOG.warn(
            "Glass pipeline unavailable, the window falls back to a tinted panel");
      }
    }
    return usable;
  }
}
