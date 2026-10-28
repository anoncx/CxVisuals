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

public final class FeedPipeline {
  public static final RenderPipeline FEED =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
          .withBindGroupLayout(BindGroupLayouts.PROJECTION)
          .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
          .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/feed"))
          .withVertexShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/feed"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/feed"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
          .withVertexBinding(0, DefaultVertexFormat.ENTITY)
          .withPrimitiveTopology(PrimitiveTopology.QUADS)
          .build();

  public static final RenderPipeline IMAGE =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
          .withBindGroupLayout(BindGroupLayouts.PROJECTION)
          .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/map_image"))
          .withVertexShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/feed"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/map_image"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
          .withVertexBinding(0, DefaultVertexFormat.ENTITY)
          .withPrimitiveTopology(PrimitiveTopology.QUADS)
          .build();

  private static boolean checked;
  private static boolean usable;

  private FeedPipeline() {}

  public static boolean usable() {
    if (!checked && Gpu.ready()) {
      checked = true;
      try {
        usable = Gpu.usable(FEED) && Gpu.usable(IMAGE);
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Window feed shader failed to compile", e);
      }
    }
    return usable;
  }
}
