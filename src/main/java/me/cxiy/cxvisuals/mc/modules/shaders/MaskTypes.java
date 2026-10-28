package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.mixin.RenderSetupAccessor;
import me.cxiy.cxvisuals.mc.mixin.RenderTypeAccessor;
import me.cxiy.cxvisuals.mc.mixin.TextureBindingAccessor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class MaskTypes {
  private static final String TEXTURE = "Sampler0";
  private static final int CULL = 1;
  private static final int TRIANGLES = 2;
  private static final int MAX_SOURCES = 4096;

  private static final RenderPipeline[] PIPELINES = {
    pipeline("entity_mask", PrimitiveTopology.QUADS, false),
    pipeline("entity_mask_cull", PrimitiveTopology.QUADS, true),
    pipeline("entity_mask_triangles", PrimitiveTopology.TRIANGLES, false),
    pipeline("entity_mask_triangles_cull", PrimitiveTopology.TRIANGLES, true)
  };
  private static final RenderType SKIP =
      RenderType.create(
          "cxvisuals_entity_mask_skip", RenderSetup.builder(PIPELINES[0]).createRenderSetup());
  private static final IdentityHashMap<RenderType, RenderType> BY_SOURCE = new IdentityHashMap<>();

  @SuppressWarnings("unchecked")
  private static final Map<Identifier, RenderType>[] BY_TEXTURE = new Map[PIPELINES.length];

  static {
    for (int i = 0; i < BY_TEXTURE.length; i++) {
      BY_TEXTURE[i] = new HashMap<>();
    }
  }

  private MaskTypes() {}

  static RenderType of(RenderType source) {
    RenderType mask = BY_SOURCE.get(source);
    if (mask == null) {
      if (BY_SOURCE.size() >= MAX_SOURCES) {
        BY_SOURCE.clear();
      }
      mask = resolve(source);
      BY_SOURCE.put(source, mask);
    }
    return mask == SKIP ? null : mask;
  }

  private static RenderType resolve(RenderType source) {
    if (!source.isOutline() && source.outline().isEmpty()) {
      return SKIP;
    }
    PrimitiveTopology topology = source.primitiveTopology();
    if (topology != PrimitiveTopology.QUADS && topology != PrimitiveTopology.TRIANGLES) {
      return SKIP;
    }
    Identifier texture = texture(source);
    if (texture == null) {
      return SKIP;
    }
    int variant =
        (topology == PrimitiveTopology.TRIANGLES ? TRIANGLES : 0)
            | (source.pipeline().isCull() ? CULL : 0);
    return BY_TEXTURE[variant].computeIfAbsent(texture, id -> create(PIPELINES[variant], id));
  }

  public static Identifier texture(RenderType source) {
    RenderSetup setup = ((RenderTypeAccessor) source).cxvisuals$state();
    Map<String, ?> textures = ((RenderSetupAccessor) (Object) setup).cxvisuals$textures();
    Object binding = textures.get(TEXTURE);
    if (binding == null && !textures.isEmpty()) {
      binding = textures.values().iterator().next();
    }
    return binding == null ? null : ((TextureBindingAccessor) binding).cxvisuals$location();
  }

  private static RenderType create(RenderPipeline pipeline, Identifier texture) {
    return RenderType.create(
        "cxvisuals_entity_mask",
        RenderSetup.builder(pipeline)
            .withTexture(TEXTURE, texture)
            .setOutline(RenderSetup.OutlineProperty.IS_OUTLINE)
            .createRenderSetup());
  }

  private static RenderPipeline pipeline(String name, PrimitiveTopology topology, boolean cull) {
    return RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/" + name))
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .withPrimitiveTopology(topology)
        .withCull(cull)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .build();
  }
}
