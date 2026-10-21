package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Optional;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
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
              .withSampler(FieldPasses.MASK)
              .withSampler(FieldPasses.MASK_DEPTH)
              .withSampler(SCENE_DEPTH)
              .withSampler(FIELD);
      if (reshades(EFFECTS[i])) {
        layout.withSampler(SCENE);
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

  static boolean ready(GpuDevice device, Effect effect) {
    int index = effect.ordinal();
    if (!COMPILED[index]) {
      COMPILED[index] = true;
      if (!device.precompilePipeline(PIPELINES[index]).isValid()) {
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
      pass.setPipeline(PIPELINES[effect.ordinal()]);
      pass.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
      pass.setUniform(FieldPasses.BLOCK, uniforms);
      pass.bindTexture(FieldPasses.MASK, mask.getColorTextureView(), sampler);
      pass.bindTexture(FieldPasses.MASK_DEPTH, mask.getDepthTextureView(), sampler);
      pass.bindTexture(SCENE_DEPTH, main.getDepthTextureView(), sampler);
      pass.bindTexture(
          FIELD, field, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      if (reshade) {
        pass.bindTexture(SCENE, sceneView, sampler);
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
