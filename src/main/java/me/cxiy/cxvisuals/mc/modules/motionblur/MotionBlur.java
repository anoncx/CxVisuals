package me.cxiy.cxvisuals.mc.modules.motionblur;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.Optional;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.compat.IrisCompat;
import me.cxiy.cxvisuals.modules.motionblur.MotionBlurModule;
import me.cxiy.cxvisuals.modules.motionblur.Reprojection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class MotionBlur {
  private static final String UNIFORMS = "MotionBlur";
  private static final String COLOR = "InSampler";
  private static final String DEPTH = "DepthSampler";
  private static final int UNIFORM_SIZE =
      new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().get();
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final int SCRATCH_USAGE =
      GpuTexture.USAGE_TEXTURE_BINDING
          | GpuTexture.USAGE_COPY_DST
          | GpuTexture.USAGE_RENDER_ATTACHMENT;

  private static final RenderPipeline PIPELINE =
      RenderPipeline.builder()
          .withBindGroupLayout(BindGroupLayouts.GLOBALS)
          .withBindGroupLayout(
              BindGroupLayout.builder()
                  .withSampler(COLOR)
                  .withSampler(DEPTH)
                  .withUniform(UNIFORMS, UniformType.UNIFORM_BUFFER)
                  .build())
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/motion_blur"))
          .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/motion_blur"))
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .build();

  private static final Reprojection REPROJECTION = new Reprojection();
  private static final Matrix4f PREVIOUS_VIEW = new Matrix4f();

  private static MotionBlurModule module;
  private static double previousX;
  private static double previousY;
  private static double previousZ;
  private static int previousLevel;
  private static long previousNanos;
  private static boolean primed;
  private static GpuTexture scratch;
  private static GpuTextureView scratchView;
  private static GpuBuffer uniforms;
  private static int width;
  private static int height;
  private static boolean compiled;
  private static boolean broken;
  private static int passes;

  private MotionBlur() {}

  public static MotionBlurModule create(McKeyBinder keys) {
    module = new MotionBlurModule(keys);
    return module;
  }

  public static MotionBlurModule module() {
    return module;
  }

  public static int passes() {
    return passes;
  }

  public static void apply(GameRenderer renderer) {
    if (module == null || broken || !module.enabled.get()) {
      primed = false;
      return;
    }
    CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
    Vec3 position = camera.pos;
    long nanos = System.nanoTime();
    float frame = (nanos - previousNanos) / 1.0e9F;
    int level = System.identityHashCode(Minecraft.getInstance().level);
    double dx = position.x - previousX;
    double dy = position.y - previousY;
    double dz = position.z - previousZ;

    boolean moving =
        primed
            && level == previousLevel
            && !camera.isPanoramicMode
            && module.steady(frame, Math.sqrt(dx * dx + dy * dy + dz * dz))
            && REPROJECTION.compute(
                camera.projectionMatrix,
                camera.viewRotationMatrix,
                PREVIOUS_VIEW,
                (float) dx,
                (float) dy,
                (float) dz,
                module.turnScale(frame),
                module.moveScale(frame));

    PREVIOUS_VIEW.set(camera.viewRotationMatrix);
    previousX = position.x;
    previousY = position.y;
    previousZ = position.z;
    previousLevel = level;
    previousNanos = nanos;
    primed = true;

    if (moving) {
      try {
        draw(renderer.mainRenderTarget());
      } catch (RuntimeException e) {
        fail(e);
      }
    }
  }

  private static void draw(RenderTarget main) {
    GpuDevice device = RenderSystem.getDevice();
    if (!prepare(device, main)) {
      return;
    }
    CommandEncoder encoder = device.createCommandEncoder();
    encoder.copyTextureToTexture(main.getColorTexture(), scratch, 0, 0, 0, 0, 0, width, height);

    boolean zeroToOne = device.getDeviceInfo().isZZeroToOne();
    boolean handInScene = IrisCompat.handInScene();
    try (MemoryStack stack = MemoryStack.stackPush()) {
      ByteBuffer data =
          Std140Builder.onStack(stack, UNIFORM_SIZE)
              .putMat4f(REPROJECTION.matrix())
              .putVec4(
                  module.samples(),
                  module.centreInner(),
                  module.centreOuter(),
                  MotionBlurModule.MAX_STREAK)
              .putVec4(
                  zeroToOne ? 1.0F : 2.0F, zeroToOne ? 0.0F : -1.0F, (float) width / height, 0.0F)
              .putVec4(
                  IrisCompat.handDepthLow(zeroToOne),
                  IrisCompat.handDepthHigh(zeroToOne),
                  module.handBlur(),
                  handInScene ? 1.0F : 0.0F)
              .get();
      encoder.writeToBuffer(uniforms.slice(), data);
    }

    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals motion blur", main.getColorTextureView(), Optional.empty())) {
      pass.setPipeline(PIPELINE);
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform(UNIFORMS, uniforms);
      pass.bindTexture(
          COLOR, scratchView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      pass.bindTexture(
          DEPTH,
          main.getDepthTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      pass.draw(3, 1, 0, 0);
    }
    passes++;
  }

  private static boolean prepare(GpuDevice device, RenderTarget main) {
    if (main.width <= 0 || main.height <= 0 || main.getDepthTextureView() == null) {
      return false;
    }
    if (!compiled) {
      compiled = true;
      if (!device.precompilePipeline(PIPELINE).isValid()) {
        broken = true;
        CxVisualsClient.LOG.warn("Motion blur shader failed to compile, the module does nothing");
        return false;
      }
    }
    if (uniforms == null) {
      uniforms = device.createBuffer(() -> "cxvisuals_motion_blur", UNIFORM_USAGE, UNIFORM_SIZE);
    }
    if (scratch == null || main.width != width || main.height != height) {
      release();
      width = main.width;
      height = main.height;
      scratch =
          device.createTexture(
              () -> "cxvisuals_motion_blur",
              SCRATCH_USAGE,
              GpuFormat.RGBA8_UNORM,
              width,
              height,
              1,
              1);
      scratchView = device.createTextureView(scratch);
    }
    return true;
  }

  private static void fail(RuntimeException e) {
    broken = true;
    release();
    CxVisualsClient.LOG.error("Motion blur failed, the module does nothing", e);
  }

  private static void release() {
    if (scratchView != null) {
      scratchView.close();
      scratchView = null;
    }
    if (scratch != null) {
      scratch.close();
      scratch = null;
    }
  }
}
