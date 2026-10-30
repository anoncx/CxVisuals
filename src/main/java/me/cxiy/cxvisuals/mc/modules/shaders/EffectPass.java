package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.modules.shaders.Effect;
import me.cxiy.cxvisuals.modules.shaders.ScreenBounds;

final class EffectPass {
  private static final String FIELD = "FieldSampler";
  private static final String SCENE_DEPTH = "SceneDepthSampler";
  private static final String SCENE = "SceneSampler";
  private static final int SCENE_USAGE =
      GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
  private static final Effect[] EFFECTS = Effect.values();
  private static final RenderPipeline[] PIPELINES = new RenderPipeline[EFFECTS.length];
  private static final boolean[] COMPILED = new boolean[EFFECTS.length];
  private static final boolean[] BROKEN = new boolean[EFFECTS.length];

  private static GpuTexture scene;
  private static GpuTextureView sceneView;

  static {
    ColorTargetState blend = new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
    for (int i = 0; i < EFFECTS.length; i++) {
      BindGroupLayout.Builder layout =
          BindGroupLayout.builder()
              .withUniform(FieldPasses.MASK, UniformType.COMBINED_IMAGE_SAMPLER)
              .withUniform(FieldPasses.MASK_DEPTH, UniformType.COMBINED_IMAGE_SAMPLER)
              .withUniform(SCENE_DEPTH, UniformType.COMBINED_IMAGE_SAMPLER)
              .withUniform(FIELD, UniformType.COMBINED_IMAGE_SAMPLER);
      if (reshades(EFFECTS[i])) {
        layout.withUniform(SCENE, UniformType.COMBINED_IMAGE_SAMPLER);
      }
      PIPELINES[i] =
          FieldPasses.pass(
              EFFECTS[i].shader(),
              blend,
              layout.withUniform(FieldPasses.BLOCK, UniformType.UNIFORM_BUFFER).build());
    }
  }

  private EffectPass() {}

  private static boolean reshades(Effect effect) {
    return effect == Effect.FROST
        || effect == Effect.HOLOGRAM
        || effect == Effect.GLITCH
        || effect == Effect.SIGNAL;
  }

  static boolean ready(Effect effect) {
    int index = effect.ordinal();
    if (!COMPILED[index] && Gpu.ready()) {
      COMPILED[index] = true;
      if (!Gpu.usable(PIPELINES[index])) {
        BROKEN[index] = true;
        CxVisualsClient.LOG.warn(
            "Entity shader {} failed to compile and does nothing", effect.shader());
      }
    }
    return !BROKEN[index];
  }

  static void draw(
      GpuDevice device,
      CommandEncoder encoder,
      GpuBuffer uniforms,
      RenderTarget main,
      ScreenBounds bounds,
      GpuTextureView field,
      Effect effect) {
    boolean reshade = reshades(effect);
    if (reshade) {
      if (scene == null || scene.getWidth(0) != main.width || scene.getHeight(0) != main.height) {
        release();
        scene =
            device.createTexture(
                () -> "cxvisuals_entity_scene",
                SCENE_USAGE,
                GpuFormat.RGBA8_UNORM,
                main.width,
                main.height,
                1,
                1);
        sceneView = device.createTextureView(scene);
      }
      encoder.copyTextureToTexture(
          main.getColorTexture(),
          scene,
          0,
          bounds.x(),
          bounds.y(),
          bounds.x(),
          bounds.y(),
          bounds.width(),
          bounds.height());
    }
    GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    RenderTarget mask = FieldPasses.mask();
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals entity shader", main.getColorTextureView(), Optional.empty())) {
      Gpu.use(pass, PIPELINES[effect.ordinal()]);
      pass.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
      pass.setUniform(FieldPasses.BLOCK, uniforms);
      Gpu.bind(pass, FieldPasses.MASK, mask.getColorTextureView(), sampler);
      Gpu.bind(pass, FieldPasses.MASK_DEPTH, mask.getDepthTextureView(), sampler);
      Gpu.bind(pass, SCENE_DEPTH, SceneDepth.view(main), sampler);
      Gpu.bind(
          pass, FIELD, field, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      if (reshade) {
        Gpu.bind(pass, SCENE, sceneView, sampler);
      }
      pass.draw(3, 1, 0, 0);
    }
  }

  private static void release() {
    if (sceneView != null) {
      sceneView.close();
      sceneView = null;
    }
    if (scene != null) {
      scene.close();
      scene = null;
    }
  }
}
