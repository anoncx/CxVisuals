package me.cxiy.cxvisuals.mc.modules.deaths;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import java.nio.ByteBuffer;
import java.util.Optional;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Features;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.mc.modules.shaders.SceneDepth;
import me.cxiy.cxvisuals.mc.compat.Targets;
import me.cxiy.cxvisuals.modules.shaders.ScreenBounds;
import net.minecraft.client.renderer.GameRenderer;
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
                  .withUniform(CORPSE, UniformType.COMBINED_IMAGE_SAMPLER)
                  .withUniform(CORPSE_DEPTH, UniformType.COMBINED_IMAGE_SAMPLER)
                  .withUniform(SCENE_DEPTH, UniformType.COMBINED_IMAGE_SAMPLER)
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
    return Gpu.usable(COMPOSITE) && CorpseTypes.compiles(device);
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
      body = Targets.create("CxVisuals corpse", main.width, main.height);
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
    Targets.clear(encoder, body, NOTHING, bounds.x(), bounds.y(), bounds.width(), bounds.height());
    GpuBufferSlice lens = RenderSystem.getProjectionMatrixBuffer();
    ProjectionType lensType = RenderSystem.getProjectionType();
    Matrix4fStack stack = RenderSystem.getModelViewStack();
    stack.pushMatrix().set(view);
    RenderSystem.setProjectionMatrix(projection, projectionType);
    try {
      Features.solid(renderer, storage, body);
    } finally {
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
      Gpu.use(pass, COMPOSITE);
      pass.enableScissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
      pass.setUniform(BLOCK, uniforms);
      Gpu.bind(pass, CORPSE, body.getColorTextureView(), nearest);
      Gpu.bind(pass, CORPSE_DEPTH, body.getDepthTextureView(), nearest);
      Gpu.bind(pass, SCENE_DEPTH, SceneDepth.view(main), nearest);
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
