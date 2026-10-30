package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
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
import java.nio.ByteBuffer;
import java.util.Optional;
import me.cxiy.cxvisuals.core.settings.Palette;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.mc.modules.animations.BlockPopEffects;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public final class OutlineShaders {
  private static final String BLOCK = "Outline";
  private static final String DEPTH = "DepthSampler";
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
          .putMat4f()
          .putVec4()
          .get();
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final float CLOCK_WRAP = 3600.0F;
  private static final float LONGEST_FRAME = 0.1F;
  private static final float CHANNEL = 1.0F / 255.0F;
  private static final float NEAREST = 0.05F;
  private static final int MARGIN = 3;
  private static final RenderPipeline PIPELINE =
      FieldPasses.pass(
          "block_outline",
          new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA),
          BindGroupLayout.builder()
              .withUniform(DEPTH, UniformType.COMBINED_IMAGE_SAMPLER)
              .withUniform(BLOCK, UniformType.UNIFORM_BUFFER)
              .build());
  private static final Matrix4f LENS = new Matrix4f();
  private static final Matrix4f UNPROJECT = new Matrix4f();
  private static final Vector4f CORNER = new Vector4f();
  private static final Matrix4f POSE = new Matrix4f();
  private static final Matrix4f UNPOSE = new Matrix4f();
  private static final float[] SPAN = new float[6];

  private static GpuBuffer uniforms;
  private static int passes;
  private static float clock;
  private static long lastNanos;
  private static boolean compiled;
  private static boolean broken;
  private static int left;
  private static int bottom;
  private static int right;
  private static int top;

  private OutlineShaders() {}

  public static int passes() {
    return passes;
  }

  public static boolean replaces() {
    ShadersModule module = Shaders.module();
    return !broken && module != null && module.outlineLive();
  }

  public static void render(GameRenderer renderer) {
    if (!replaces()) {
      lastNanos = 0L;
      return;
    }
    ShadersModule module = Shaders.module();
    long now = System.nanoTime();
    float frame = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, LONGEST_FRAME);
    lastNanos = now;
    if (!Minecraft.getInstance().isPaused()) {
      clock = (clock + frame * module.outlineSpeedScale()) % CLOCK_WRAP;
    }
    LevelRenderState level = renderer.gameRenderState().levelRenderState;
    BlockOutlineRenderState state = level.blockOutlineRenderState;
    if (state == null || state.shape().isEmpty()) {
      return;
    }
    try {
      draw(renderer.mainRenderTarget(), module, level.cameraRenderState, state);
    } catch (RuntimeException e) {
      broken = true;
      CxVisualsClient.LOG.error(
          "Outline shader failed, the vanilla outline is back for this session", e);
    }
  }

  private static void span(
      float lowX, float lowY, float lowZ, float highX, float highY, float highZ) {
    SPAN[0] = Float.MAX_VALUE;
    SPAN[1] = Float.MAX_VALUE;
    SPAN[2] = Float.MAX_VALUE;
    SPAN[3] = -Float.MAX_VALUE;
    SPAN[4] = -Float.MAX_VALUE;
    SPAN[5] = -Float.MAX_VALUE;
    for (int corner = 0; corner < 8; corner++) {
      CORNER.set(
          (corner & 1) == 0 ? lowX : highX,
          (corner & 2) == 0 ? lowY : highY,
          (corner & 4) == 0 ? lowZ : highZ,
          1.0F);
      POSE.transform(CORNER);
      SPAN[0] = Math.min(SPAN[0], CORNER.x);
      SPAN[1] = Math.min(SPAN[1], CORNER.y);
      SPAN[2] = Math.min(SPAN[2], CORNER.z);
      SPAN[3] = Math.max(SPAN[3], CORNER.x);
      SPAN[4] = Math.max(SPAN[4], CORNER.y);
      SPAN[5] = Math.max(SPAN[5], CORNER.z);
    }
  }

  private static boolean frame(
      RenderTarget main,
      float lowX,
      float lowY,
      float lowZ,
      float highX,
      float highY,
      float highZ) {
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    boolean whole = false;
    for (int corner = 0; corner < 8 && !whole; corner++) {
      CORNER.set(
          (corner & 1) == 0 ? lowX : highX,
          (corner & 2) == 0 ? lowY : highY,
          (corner & 4) == 0 ? lowZ : highZ,
          1.0F);
      LENS.transform(CORNER);
      if (CORNER.w < NEAREST) {
        whole = true;
      } else {
        float x = CORNER.x / CORNER.w;
        float y = CORNER.y / CORNER.w;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
      }
    }
    if (whole) {
      left = 0;
      bottom = 0;
      right = main.width;
      top = main.height;
      return true;
    }
    left = Math.max(0, (int) Math.floor((minX * 0.5F + 0.5F) * main.width) - MARGIN);
    bottom = Math.max(0, (int) Math.floor((minY * 0.5F + 0.5F) * main.height) - MARGIN);
    right = Math.min(main.width, (int) Math.ceil((maxX * 0.5F + 0.5F) * main.width) + MARGIN);
    top = Math.min(main.height, (int) Math.ceil((maxY * 0.5F + 0.5F) * main.height) + MARGIN);
    return right > left && top > bottom;
  }

  private static void draw(
      RenderTarget main,
      ShadersModule module,
      CameraRenderState camera,
      BlockOutlineRenderState state) {
    if (main.width <= 0 || main.height <= 0 || main.getDepthTextureView() == null) {
      return;
    }
    GpuDevice device = RenderSystem.getDevice();
    if (!compiled && Gpu.ready()) {
      compiled = true;
      if (!Gpu.usable(PIPELINE)) {
        broken = true;
        CxVisualsClient.LOG.warn("Outline shader failed to compile, the vanilla outline stays");
        return;
      }
    }
    AABB box = state.shape().bounds();
    float lowX = (float) (state.pos().getX() + box.minX - camera.pos.x);
    float lowY = (float) (state.pos().getY() + box.minY - camera.pos.y);
    float lowZ = (float) (state.pos().getZ() + box.minZ - camera.pos.z);
    float highX = (float) (state.pos().getX() + box.maxX - camera.pos.x);
    float highY = (float) (state.pos().getY() + box.maxY - camera.pos.y);
    float highZ = (float) (state.pos().getZ() + box.maxZ - camera.pos.z);
    Matrix4f projection = EntityShaders.lens();
    LENS.set(projection).mul(camera.viewRotationMatrix);
    float originX = (float) (state.pos().getX() - camera.pos.x);
    float originY = (float) (state.pos().getY() - camera.pos.y);
    float originZ = (float) (state.pos().getZ() - camera.pos.z);
    boolean popping = BlockPopEffects.outlinePose(state.pos().asLong(), UNPOSE);
    float unit = 1.0F;
    boolean visible;
    if (popping) {
      POSE.translation(originX, originY, originZ)
          .mul(UNPOSE)
          .translate(-originX, -originY, -originZ);
      unit = (float) Math.cbrt(Math.abs(POSE.determinant3x3()));
      POSE.invert(UNPOSE);
      span(lowX, lowY, lowZ, highX, highY, highZ);
      visible = frame(main, SPAN[0], SPAN[1], SPAN[2], SPAN[3], SPAN[4], SPAN[5]);
    } else {
      UNPOSE.identity();
      visible = frame(main, lowX, lowY, lowZ, highX, highY, highZ);
    }
    if (!visible) {
      return;
    }
    UNPROJECT.set(LENS).invert();
    if (uniforms == null) {
      uniforms = device.createBuffer(() -> "cxvisuals_outline", UNIFORM_USAGE, UNIFORM_SIZE);
    }
    boolean zeroToOne = device.getDeviceInfo().isZZeroToOne();
    Palette palette = module.outlinePalette.get();
    int first = palette.first();
    int second = palette.second();
    CommandEncoder encoder = device.createCommandEncoder();
    try (MemoryStack memory = MemoryStack.stackPush()) {
      ByteBuffer data =
          Std140Builder.onStack(memory, UNIFORM_SIZE)
              .putMat4f(UNPROJECT)
              .putVec4(lowX, lowY, lowZ, 2.0F / (Math.abs(projection.m11()) * main.height))
              .putVec4(highX, highY, highZ, clock)
              .putVec4(
                  module.outlineWidthBlocks(),
                  module.outlineGlowScale(),
                  module.outlineFillShare(),
                  module.outlineStyle.get().ordinal())
              .putVec4(zeroToOne ? 1.0F : 2.0F, zeroToOne ? 0.0F : -1.0F, 0.0F, 0.0F)
              .putVec4(red(first), green(first), blue(first), 1.0F)
              .putVec4(red(second), green(second), blue(second), 1.0F)
              .putMat4f(UNPOSE)
              .putVec4(unit, 0.0F, 0.0F, 0.0F)
              .get();
      encoder.writeToBuffer(uniforms.slice(), data);
    }
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals block outline", main.getColorTextureView(), Optional.empty())) {
      Gpu.use(pass, PIPELINE);
      pass.enableScissor(left, bottom, right - left, top - bottom);
      pass.setUniform(BLOCK, uniforms);
      Gpu.bind(
          pass,
          DEPTH,
          SceneDepth.view(main),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      pass.draw(3, 1, 0, 0);
    }
    passes++;
  }

  private static float red(int argb) {
    return (argb >> 16 & 0xFF) * CHANNEL;
  }

  private static float green(int argb) {
    return (argb >> 8 & 0xFF) * CHANNEL;
  }

  private static float blue(int argb) {
    return (argb & 0xFF) * CHANNEL;
  }
}
