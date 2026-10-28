package me.cxiy.cxvisuals.mc.modules.deaths;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.mc.modules.shaders.MaskTypes;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

final class CorpseTypes {
  private static final String TEXTURE = "Sampler0";
  private static final int CULL = 1;
  private static final int TRIANGLES = 2;
  private static final int MAX_SOURCES = 4096;

  private static final RenderPipeline[] PIPELINES = {
    pipeline("corpse", PrimitiveTopology.QUADS, false),
    pipeline("corpse_cull", PrimitiveTopology.QUADS, true),
    pipeline("corpse_triangles", PrimitiveTopology.TRIANGLES, false),
    pipeline("corpse_triangles_cull", PrimitiveTopology.TRIANGLES, true)
  };
  private static final RenderType SKIP =
      RenderType.create(
          "cxvisuals_corpse_skip", RenderSetup.builder(PIPELINES[0]).createRenderSetup());
  private static final IdentityHashMap<RenderType, RenderType> BY_SOURCE = new IdentityHashMap<>();

  @SuppressWarnings("unchecked")
  private static final Map<Identifier, RenderType>[] BY_TEXTURE = new Map[PIPELINES.length];

  static {
    for (int i = 0; i < BY_TEXTURE.length; i++) {
      BY_TEXTURE[i] = new HashMap<>();
    }
  }

  private CorpseTypes() {}

  static boolean compiles(GpuDevice device) {
    for (RenderPipeline pipeline : PIPELINES) {
      if (!Gpu.usable(pipeline)) {
        return false;
      }
    }
    return true;
  }

  static RenderType of(RenderType source) {
    RenderType corpse = BY_SOURCE.get(source);
    if (corpse == null) {
      if (BY_SOURCE.size() >= MAX_SOURCES) {
        BY_SOURCE.clear();
      }
      corpse = resolve(source);
      BY_SOURCE.put(source, corpse);
    }
    return corpse == SKIP ? null : corpse;
  }

  private static RenderType resolve(RenderType source) {
    if (source.format() != DefaultVertexFormat.ENTITY) {
      return SKIP;
    }
    PrimitiveTopology topology = source.primitiveTopology();
    if (topology != PrimitiveTopology.QUADS && topology != PrimitiveTopology.TRIANGLES) {
      return SKIP;
    }
    Identifier texture = MaskTypes.texture(source);
    if (texture == null) {
      return SKIP;
    }
    int variant =
        (topology == PrimitiveTopology.TRIANGLES ? TRIANGLES : 0)
            | (source.pipeline().isCull() ? CULL : 0);
    return BY_TEXTURE[variant].computeIfAbsent(
        texture,
        id ->
            RenderType.create(
                "cxvisuals_corpse",
                RenderSetup.builder(PIPELINES[variant])
                    .withTexture(TEXTURE, id)
                    .useLightmap()
                    .useOverlay()
                    .createRenderSetup()));
  }

  private static RenderPipeline pipeline(String name, PrimitiveTopology topology, boolean cull) {
    Identifier shader = Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/corpse");
    return RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withBindGroupLayout(BindGroupLayouts.LIGHTING)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
        .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/" + name))
        .withVertexShader(shader)
        .withFragmentShader(shader)
        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        .withShaderDefine("PER_FACE_LIGHTING")
        .withVertexBinding(0, DefaultVertexFormat.ENTITY)
        .withPrimitiveTopology(topology)
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .withCull(cull)
        .withColorTargetState(ColorTargetState.DEFAULT)
        .build();
  }
}
