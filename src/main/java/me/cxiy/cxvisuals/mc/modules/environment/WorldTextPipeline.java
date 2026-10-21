package me.cxiy.cxvisuals.mc.modules.environment;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.Optional;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public final class WorldTextPipeline {
  public static RenderPipeline depth;
  public static RenderPipeline flat;

  private static boolean checked;
  private static boolean usable;

  private WorldTextPipeline() {}

  private static RenderPipeline build(String location, Optional<DepthStencilState> depth) {
    return RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, location))
        .withVertexShader(
            Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/world_text"))
        .withFragmentShader(
            Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/world_text"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(depth)
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .build();
  }

  public static boolean usable() {
    if (!checked) {
      checked = true;
      try {
        GpuDevice device = RenderSystem.getDevice();
        depth =
            build(
                "pipeline/world_text",
                Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)));
        flat = build("pipeline/world_text_flat", Optional.empty());
        usable =
            device.precompilePipeline(depth).isValid() && device.precompilePipeline(flat).isValid();
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("World text shader failed to compile", e);
      }
      if (!usable) {
        CxVisualsClient.LOG.warn("World text unavailable, lyrics are not drawn in the world");
      }
    }
    return usable;
  }
}
