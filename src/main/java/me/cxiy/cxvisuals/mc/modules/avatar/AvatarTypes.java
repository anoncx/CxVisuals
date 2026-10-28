package me.cxiy.cxvisuals.mc.modules.avatar;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.HashMap;
import java.util.Map;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.IrisCompat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

final class AvatarTypes {
  static final RenderPipeline CULLED =
      RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
          .withLocation(
              Identifier.fromNamespaceAndPath(
                  CxVisualsClient.MOD_ID, "pipeline/avatar_cutout_cull"))
          .withShaderDefine("ALPHA_CUTOUT", 0.1F)
          .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .withColorTargetState(ColorTargetState.DEFAULT)
          .build();
  static final RenderPipeline DOUBLE_SIDED =
      RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/avatar_cutout"))
          .withShaderDefine("ALPHA_CUTOUT", 0.1F)
          .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .withCull(false)
          .withColorTargetState(ColorTargetState.DEFAULT)
          .build();
  static final RenderPipeline TRANSLUCENT =
      RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
          .withLocation(
              Identifier.fromNamespaceAndPath(
                  CxVisualsClient.MOD_ID, "pipeline/avatar_translucent"))
          .withShaderDefine("ALPHA_CUTOUT", 0.1F)
          .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .withCull(false)
          .build();

  private static final Map<Identifier, RenderType> CULLED_TYPES = new HashMap<>();
  private static final Map<Identifier, RenderType> DOUBLE_SIDED_TYPES = new HashMap<>();
  private static final Map<Identifier, RenderType> TRANSLUCENT_TYPES = new HashMap<>();

  static {
    IrisCompat.entityPipeline(CULLED, false);
    IrisCompat.entityPipeline(DOUBLE_SIDED, false);
    IrisCompat.entityPipeline(TRANSLUCENT, true);
  }

  private AvatarTypes() {}

  static RenderType culled(Identifier texture) {
    return CULLED_TYPES.computeIfAbsent(texture, id -> create("avatar_cutout_cull", CULLED, id));
  }

  static RenderType doubleSided(Identifier texture) {
    return DOUBLE_SIDED_TYPES.computeIfAbsent(
        texture, id -> create("avatar_cutout", DOUBLE_SIDED, id));
  }

  static RenderType translucent(Identifier texture) {
    return TRANSLUCENT_TYPES.computeIfAbsent(
        texture, id -> create("avatar_translucent", TRANSLUCENT, id));
  }

  private static RenderType create(String name, RenderPipeline pipeline, Identifier texture) {
    return RenderType.create(
        name,
        RenderSetup.builder(pipeline)
            .withTexture("Sampler0", texture)
            .useLightmap()
            .useOverlay()
            .affectsCrumbling()
            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
            .createRenderSetup());
  }
}
