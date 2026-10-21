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

public final class ShapePipeline {
  public static final RenderPipeline SHAPE =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.GLOBALS)
          .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
          .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/shape"))
          .withVertexShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/shape"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/shape"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
          .withVertexBinding(0, DefaultVertexFormat.ENTITY)
          .withPrimitiveTopology(PrimitiveTopology.QUADS)
          .build();

  private static boolean checked;
  private static boolean usable;

  private ShapePipeline() {}

  public static void recheck() {
    if (!usable) {
      checked = false;
    }
  }

  public static boolean usable() {
    if (!checked) {
      checked = true;
      try {
        usable = RenderSystem.getDevice().precompilePipeline(SHAPE).isValid();
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Shape shader failed to compile", e);
      }
      if (!usable) {
        CxVisualsClient.LOG.warn(
            "Shape pipeline unavailable, settings window falls back to flat rectangles");
      }
    }
    return usable;
  }
}
