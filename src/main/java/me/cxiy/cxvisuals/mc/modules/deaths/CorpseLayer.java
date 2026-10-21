package me.cxiy.cxvisuals.mc.modules.deaths;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
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
import java.nio.ByteBuffer;
import java.util.Optional;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.shaders.ScreenBounds;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

final class CorpseLayer {
  static final class Look {
    final Matrix4f unproject = new Matrix4f();
    float originX;
    float originY;
    float originZ;
    float height;
    float floor;
    float width;
    float progress;
    int fate;
    float tall;
    int first;
    int second;
    float intensity;
    float clock;
    float focusX;
    float focusY;
    float horizon;
    boolean zeroToOne;
  }

  private static final String BLOCK = "Death";
  private static final String CORPSE = "CorpseSampler";
  private static final String CORPSE_DEPTH = "CorpseDepthSampler";
  private static final String SCENE_DEPTH = "SceneDepthSampler";
  private static final int UNIFORM_SIZE =
      new Std140SizeCalculator()
          .putMat4f()
          .putVec4()
          .putVec4()
          .putVec4()
          .putVec4()
          .putVec4()
          .putVec4()
          .putVec4()
          .get();
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final Vector4fc NOTHING = new Vector4f(0.0F);
  private static final RenderPipeline COMPOSITE =
      RenderPipeline.builder()
          .withBindGroupLayout(
              BindGroupLayout.builder()
                  .withSampler(CORPSE)
                  .withSampler(CORPSE_DEPTH)
                  .withSampler(SCENE_DEPTH)
                  .withUniform(BLOCK, UniformType.UNIFORM_BUFFER)
                  .build())
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/death_composite"))
          .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/death_composite"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .build();

  private RenderTarget body;
  private GpuBuffer uniforms;
  private final Matrix4f view = new Matrix4f();
  private GpuBufferSlice projection;
  private ProjectionType projectionType;

  static boolean compiles(GpuDevice device) {
    return device.precompilePipeline(COMPOSITE).isValid() && CorpseTypes.compiles(device);
  }

  void lens() {
    view.set(RenderSystem.getModelViewStack());
    projection = RenderSystem.getProjectionMatrixBuffer();
    projectionType = RenderSystem.getProjectionType();
  }

  boolean prepare(GpuDevice device, RenderTarget main) {
    if (main.width <= 0
        || main.height <= 0
        || main.getDepthTextureView() == null
        || projection == null) {
      return false;
    }
    if (body == null) {
      body =
          new TextureTarget(
              "CxVisuals corpse", main.width, main.height, true, GpuFormat.RGBA8_UNORM);
      uniforms =
          device.createBuffer(() -> "cxvisuals_death_composite", UNIFORM_USAGE, UNIFORM_SIZE);
    } else if (body.width != main.width || body.height != main.height) {
      body.resize(main.width, main.height);
    }
    return true;
  }

  void draw(
      GameRenderer renderer,
      CommandEncoder encoder,
      CorpseStorage storage,
      ScreenBounds bounds,
      Look look) {
    RenderTarget main = renderer.mainRenderTarget();
    encoder.clearColorAndDepthTextures(
        body.getColorTexture(),
        NOTHING,
        body.getDepthTexture(),
        0.0,
        bounds.x(),
        bounds.y(),
        bounds.width(),
        bounds.height());
    GpuBufferSlice lens = RenderSystem.getProjectionMatrixBuffer();
    ProjectionType lensType = RenderSystem.getProjectionType();
    Matrix4fStack stack = RenderSystem.getModelViewStack();
    stack.pushMatrix().set(view);
    RenderSystem.setProjectionMatrix(projection, projectionType);
    RenderSystem.outputColorTextureOverride = body.getColorTextureView();
    RenderSystem.outputDepthTextureOverride = body.getDepthTextureView();
    try (FeatureRenderDispatcher.PreparedFrame frame =
        renderer.featureRenderDispatcher().prepareFrame(storage)) {
      frame.executeSolid();
    } finally {
      RenderSystem.outputColorTextureOverride = null;
      RenderSystem.outputDepthTextureOverride = null;
      RenderSystem.setProjectionMatrix(lens, lensType);
      stack.popMatrix();
    }
    try (MemoryStack memory = MemoryStack.stackPush()) {
      ByteBuffer data =
          Std140Builder.onStack(memory, UNIFORM_SIZE)
              .putMat4f(look.unproject)
              .putVec4(look.originX, look.originY, look.originZ, look.height)
              .putVec4(look.floor, look.width, look.progress, look.fate)
              .putVec4(
                  look.zeroToOne ? 1.0F : 2.0F,
                  look.zeroToOne ? 0.0F : -1.0F,
                  main.width,
                  main.height)
              .putVec4(
                  DeathBatch.red(look.first),
                  DeathBatch.green(look.first),
                  DeathBatch.blue(look.first),
                  look.intensity)
              .putVec4(
                  DeathBatch.red(look.second),
                  DeathBatch.green(look.second),
                  DeathBatch.blue(look.second),
                  look.clock)
              .putVec4(bounds.x(), look.tall, 0.0F, bounds.y())
              .putVec4(look.focusX, look.focusY, look.horizon, 0.0F)
              .get();
      encoder.writeToBuffer(uniforms.slice(), data);
    }
    GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals death composite", main.getColorTextureView(), Optional.empty())) {
      pass.setPipeline(COMPOSITE);
      pass.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
      pass.setUniform(BLOCK, uniforms);
      pass.bindTexture(CORPSE, body.getColorTextureView(), nearest);
      pass.bindTexture(CORPSE_DEPTH, body.getDepthTextureView(), nearest);
      pass.bindTexture(SCENE_DEPTH, main.getDepthTextureView(), nearest);
      pass.draw(3, 1, 0, 0);
    }
  }

  void release() {
    if (body != null) {
      body.destroyBuffers();
      uniforms.close();
      body = null;
      uniforms = null;
    }
  }
}
