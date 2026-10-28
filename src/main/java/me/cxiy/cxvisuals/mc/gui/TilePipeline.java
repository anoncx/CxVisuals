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

public final class TilePipeline {
  public static final RenderPipeline TILE =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
          .withBindGroupLayout(BindGroupLayouts.PROJECTION)
          .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
          .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/tile"))
          .withVertexShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/tile"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/tile"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
          .withVertexBinding(0, DefaultVertexFormat.ENTITY)
          .withPrimitiveTopology(PrimitiveTopology.QUADS)
          .build();

  private static boolean checked;
  private static boolean usable;

  private TilePipeline() {}

  public static boolean usable() {
    if (!checked && Gpu.ready()) {
      checked = true;
      try {
        usable = Gpu.usable(TILE);
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Tile shader failed to compile", e);
      }
    }
    return usable;
  }
}
