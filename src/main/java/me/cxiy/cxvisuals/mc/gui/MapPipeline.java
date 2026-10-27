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

public final class MapPipeline {
  public static final RenderPipeline MAP =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
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
    if (!checked) {
      checked = true;
      try {
        usable = RenderSystem.getDevice().precompilePipeline(MAP).isValid();
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Map shader failed to compile", e);
      }
    }
    return usable;
  }
}
