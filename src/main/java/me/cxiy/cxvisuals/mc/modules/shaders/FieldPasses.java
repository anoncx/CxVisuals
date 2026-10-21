package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
import me.cxiy.cxvisuals.modules.shaders.GlowField;
import me.cxiy.cxvisuals.modules.shaders.ScreenBounds;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.joml.Vector4fc;

final class FieldPasses {
  static final String BLOCK = "EntityFx";
  static final String MASK = "MaskSampler";
  static final String MASK_DEPTH = "MaskDepthSampler";

  private static final String FIELD = "FieldSampler";
  private static final String LEVEL = "LevelSampler";
  private static final int LEVELS = GlowField.LEVELS;
  private static final int TARGET_USAGE =
      GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
  private static final Vector4fc NOTHING = new Vector4f(0.0F);

  private static final RenderPipeline FIRST =
      pass(
          "entity_field_first",
          target(GpuFormat.RGBA16_FLOAT),
          BindGroupLayout.builder()
              .withSampler(MASK)
              .withSampler(MASK_DEPTH)
              .withUniform(BLOCK, UniformType.UNIFORM_BUFFER)
              .build());
  private static final RenderPipeline DOWN =
      pass(
          "entity_field_down",
          target(GpuFormat.RGBA16_FLOAT),
          BindGroupLayout.builder()
              .withSampler(FIELD)
              .withUniform(BLOCK, UniformType.UNIFORM_BUFFER)
              .build());
  private static final RenderPipeline UP =
      pass(
          "entity_field_up",
          target(GpuFormat.RGBA16_FLOAT),
          BindGroupLayout.builder()
              .withSampler(FIELD)
              .withSampler(LEVEL)
              .withUniform(BLOCK, UniformType.UNIFORM_BUFFER)
              .build());

  private static final GpuTexture[] down = new GpuTexture[LEVELS + 1];
  private static final GpuTextureView[] downView = new GpuTextureView[LEVELS + 1];
  private static final GpuTexture[] up = new GpuTexture[LEVELS];
  private static final GpuTextureView[] upView = new GpuTextureView[LEVELS];

  private static RenderTarget mask;
  private static RenderTarget worldMask;
  private static RenderTarget handMask;
  private static int width;
  private static int height;
  private static boolean compiled;

  private FieldPasses() {}

  static RenderPipeline pass(String shader, ColorTargetState target, BindGroupLayout layout) {
    return RenderPipeline.builder()
        .withBindGroupLayout(layout)
        .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/" + shader))
        .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
        .withFragmentShader(
            Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/" + shader))
        .withColorTargetState(target)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .build();
  }

  private static ColorTargetState target(GpuFormat format) {
    return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
  }

  static boolean prepare(GpuDevice device, RenderTarget main) {
    if (main.width <= 0 || main.height <= 0 || main.getDepthTextureView() == null) {
      return false;
    }
    if (!compiled) {
      compiled = true;
      if (!device.precompilePipeline(FIRST).isValid()
          || !device.precompilePipeline(DOWN).isValid()
          || !device.precompilePipeline(UP).isValid()) {
        throw new IllegalStateException("glow field shaders failed to compile");
      }
    }
    if (worldMask == null) {
      worldMask =
          new TextureTarget(
              "CxVisuals entity mask", main.width, main.height, true, GpuFormat.RGBA8_UNORM);
      handMask =
          new TextureTarget(
              "CxVisuals hand mask", main.width, main.height, true, GpuFormat.RGBA8_UNORM);
      mask = worldMask;
    } else if (worldMask.width != main.width || worldMask.height != main.height) {
      worldMask.resize(main.width, main.height);
      handMask.resize(main.width, main.height);
    }
    if (down[1] == null || width != main.width || height != main.height) {
      release();
      width = main.width;
      height = main.height;
      for (int level = 1; level <= LEVELS; level++) {
        int index = level;
        int w = ScreenBounds.size(width, level);
        int h = ScreenBounds.size(height, level);
        down[level] =
            device.createTexture(
                () -> "cxvisuals_entity_field_down_" + index,
                TARGET_USAGE,
                GpuFormat.RGBA16_FLOAT,
                w,
                h,
                1,
                1);
        downView[level] = device.createTextureView(down[level]);
        if (level < LEVELS) {
          up[level] =
              device.createTexture(
                  () -> "cxvisuals_entity_field_up_" + index,
                  TARGET_USAGE,
                  GpuFormat.RGBA16_FLOAT,
                  w,
                  h,
                  1,
                  1);
          upView[level] = device.createTextureView(up[level]);
        }
      }
    }
    return true;
  }

  static RenderTarget select(boolean hand) {
    mask = hand ? handMask : worldMask;
    return mask;
  }

  static void wipe(CommandEncoder encoder, ScreenBounds bounds) {
    encoder.clearColorAndDepthTextures(
        mask.getColorTexture(),
        NOTHING,
        mask.getDepthTexture(),
        0.0,
        bounds.x(),
        bounds.y(),
        bounds.width(),
        bounds.height());
  }

  static RenderTarget mask() {
    return mask;
  }

  static GpuTextureView run(CommandEncoder encoder, GpuBuffer uniforms, ScreenBounds bounds) {
    GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    try (RenderPass pass =
        encoder.createRenderPass(() -> "CxVisuals glow field", downView[1], Optional.empty())) {
      pass.setPipeline(FIRST);
      pass.enableScissor(bounds.x(1), bounds.y(1), bounds.width(1), bounds.height(1));
      pass.setUniform(BLOCK, uniforms);
      pass.bindTexture(MASK, mask.getColorTextureView(), nearest);
      pass.bindTexture(MASK_DEPTH, mask.getDepthTextureView(), nearest);
      pass.draw(3, 1, 0, 0);
    }
    for (int level = 2; level <= LEVELS; level++) {
      try (RenderPass pass =
          encoder.createRenderPass(
              () -> "CxVisuals glow field", downView[level], Optional.empty())) {
        pass.setPipeline(DOWN);
        pass.enableScissor(
            bounds.x(level), bounds.y(level), bounds.width(level), bounds.height(level));
        pass.setUniform(BLOCK, uniforms);
        pass.bindTexture(FIELD, downView[level - 1], linear);
        pass.draw(3, 1, 0, 0);
      }
    }
    GpuTextureView from = downView[LEVELS];
    for (int level = LEVELS - 1; level >= 1; level--) {
      try (RenderPass pass =
          encoder.createRenderPass(() -> "CxVisuals glow field", upView[level], Optional.empty())) {
        pass.setPipeline(UP);
        pass.enableScissor(
            bounds.x(level), bounds.y(level), bounds.width(level), bounds.height(level));
        pass.setUniform(BLOCK, uniforms);
        pass.bindTexture(FIELD, from, linear);
        pass.bindTexture(LEVEL, downView[level], nearest);
        pass.draw(3, 1, 0, 0);
      }
      from = upView[level];
    }
    return from;
  }

  private static void release() {
    for (int level = 0; level <= LEVELS; level++) {
      downView[level] = close(downView[level]);
      down[level] = close(down[level]);
      if (level < LEVELS) {
        upView[level] = close(upView[level]);
        up[level] = close(up[level]);
      }
    }
  }

  private static GpuTextureView close(GpuTextureView view) {
    if (view != null) {
      view.close();
    }
    return null;
  }

  private static GpuTexture close(GpuTexture texture) {
    if (texture != null) {
      texture.close();
    }
    return null;
  }
}
