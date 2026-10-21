package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public final class GlassPipeline {
  public static final RenderPipeline GLASS =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.GLOBALS)
          .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
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
    if (!checked) {
      checked = true;
      try {
        usable = RenderSystem.getDevice().precompilePipeline(GLASS).isValid();
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
