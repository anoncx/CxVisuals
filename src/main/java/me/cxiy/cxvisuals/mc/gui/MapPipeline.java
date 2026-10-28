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

public final class MapPipeline {
  public static final RenderPipeline MAP =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
          .withBindGroupLayout(BindGroupLayouts.PROJECTION)
          .withBindGroupLayout(BindGroupLayouts.GLOBALS)
          .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
          .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/map"))
          .withVertexShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/map"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/map"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
          .withVertexBinding(0, DefaultVertexFormat.ENTITY)
          .withPrimitiveTopology(PrimitiveTopology.QUADS)
          .build();

  private static boolean checked;
  private static boolean usable;

  private MapPipeline() {}

  public static boolean usable() {
    if (!checked && Gpu.ready()) {
      checked = true;
      try {
        usable = Gpu.usable(MAP);
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Map shader failed to compile", e);
      }
    }
    return usable;
  }
}
