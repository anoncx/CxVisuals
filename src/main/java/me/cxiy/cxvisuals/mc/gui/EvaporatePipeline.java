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

public final class EvaporatePipeline {
  public static final RenderPipeline EVAPORATE =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
          .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/evaporate"))
          .withVertexShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/evaporate"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/evaporate"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
          .withVertexBinding(0, DefaultVertexFormat.ENTITY)
          .withPrimitiveTopology(PrimitiveTopology.QUADS)
          .build();

  private static boolean checked;
  private static boolean usable;

  private EvaporatePipeline() {}

  public static boolean usable() {
    if (!checked) {
      checked = true;
      try {
        usable = RenderSystem.getDevice().precompilePipeline(EVAPORATE).isValid();
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Evaporate shader failed to compile", e);
      }
    }
    return usable;
  }
}
