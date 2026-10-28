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

public final class FacePipeline {
  public static final RenderPipeline FACE = pipeline("face", "face", "face");
  public static final RenderPipeline ICON = pipeline("icon", "face", "icon");
  public static final RenderPipeline MOB = pipeline("mob_face", "mob_face", "mob_face");

  private static boolean checked;
  private static boolean usable;

  private FacePipeline() {}

  private static RenderPipeline pipeline(String name, String vertex, String fragment) {
    return RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/" + name))
        .withVertexShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/" + vertex))
        .withFragmentShader(
            Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/" + fragment))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .build();
  }

  public static boolean usable() {
    if (!checked && Gpu.ready()) {
      checked = true;
      try {
        usable = Gpu.usable(FACE) && Gpu.usable(ICON) && Gpu.usable(MOB);
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Player head shader failed to compile", e);
      }
      if (!usable) {
        CxVisualsClient.LOG.warn("Player heads unavailable, the player list shows plain tiles");
      }
    }
    return usable;
  }
}
