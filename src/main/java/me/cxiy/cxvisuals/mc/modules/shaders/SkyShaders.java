package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
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
import java.nio.ByteBuffer;
import java.util.Optional;
import me.cxiy.cxvisuals.core.settings.Palette;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.IrisCompat;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.shaders.Sky;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public final class SkyShaders {
  private static final String BLOCK = "Sky";
  private static final String DEPTH = "DepthSampler";
  private static final int UNIFORM_SIZE =
      new Std140SizeCalculator()
          .putMat4f()
          .putMat4f()
          .putVec4()
          .putVec4()
          .putVec4()
          .putVec4()
          .get();
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final float CLOCK_WRAP = 3600.0F;
  private static final float LONGEST_FRAME = 0.1F;
  private static final float CHANNEL = 1.0F / 255.0F;
  private static final float FULL_TURN = (float) (Math.PI * 2.0);
  private static final float TILT = (float) Math.toRadians(24.0);
  private static final float STORM = 0.35F;
  private static final float END_HAZE = 0.4F;
  private static final Sky[] SKIES = Sky.values();
  private static final RenderPipeline[] PIPELINES = new RenderPipeline[SKIES.length];
  private static final boolean[] COMPILED = new boolean[SKIES.length];
  private static final boolean[] BROKEN = new boolean[SKIES.length];
  private static final Matrix4f RAYS = new Matrix4f();
  private static final Matrix4f TURN = new Matrix4f();
  private static final Matrix4f VIEW = new Matrix4f();
  private static final Quaternionf FACING = new Quaternionf();

  private static GpuBuffer uniforms;
  private static int passes;
  private static float clock;
  private static float angle;
  private static long lastNanos;
  private static boolean broken;

  static {
    ColorTargetState blend = new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
    for (int i = 0; i < SKIES.length; i++) {
      PIPELINES[i] =
          FieldPasses.pass(
              SKIES[i].shader(),
              blend,
              BindGroupLayout.builder()
                  .withSampler(DEPTH)
                  .withUniform(BLOCK, UniformType.UNIFORM_BUFFER)
                  .build());
    }
  }

  private SkyShaders() {}

  public static int passes() {
    return passes;
  }

  public static void render(GameRenderer renderer) {
    ShadersModule module = Shaders.module();
    if (broken || module == null || !module.skyLive()) {
      lastNanos = 0L;
      return;
    }
    tick(module);
    LevelRenderState level = renderer.gameRenderState().levelRenderState;
    CameraRenderState camera = level.cameraRenderState;
    SkyRenderState vanilla = level.skyRenderState;
    if (vanilla.skybox == DimensionType.Skybox.NONE
        || camera.fogType != FogType.NONE
        || camera.entityRenderState.doesMobEffectBlockSky
        || camera.isPanoramicMode) {
      return;
    }
    boolean end = vanilla.skybox == DimensionType.Skybox.END;
    float shown = end ? 1.0F : module.skyShown(vanilla.starBrightness);
    if (shown <= 0.0F) {
      return;
    }
    float clear = end ? 1.0F : STORM + (1.0F - STORM) * vanilla.rainBrightness;
    float haze = IrisCompat.handInScene() ? 0.0F : end ? END_HAZE : 1.0F;
    try {
      draw(renderer.mainRenderTarget(), module, camera, shown, clear, haze);
    } catch (RuntimeException e) {
      broken = true;
      CxVisualsClient.LOG.error("Sky shaders failed and do nothing for this session", e);
    }
  }

  private static void tick(ShadersModule module) {
    long now = System.nanoTime();
    float frame = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, LONGEST_FRAME);
    lastNanos = now;
    if (!Minecraft.getInstance().isPaused()) {
      clock = (clock + frame * module.skySpeedScale()) % CLOCK_WRAP;
      angle = (angle + frame * module.skyDriftRate()) % FULL_TURN;
    }
  }

  private static boolean ready(GpuDevice device, Sky sky) {
    int index = sky.ordinal();
    if (!COMPILED[index]) {
      COMPILED[index] = true;
      if (!device.precompilePipeline(PIPELINES[index]).isValid()) {
        BROKEN[index] = true;
        CxVisualsClient.LOG.warn("Sky shader {} failed to compile and does nothing", sky.shader());
      }
    }
    return !BROKEN[index];
  }

  private static void draw(
      RenderTarget main,
      ShadersModule module,
      CameraRenderState camera,
      float shown,
      float clear,
      float haze) {
    if (main.width <= 0 || main.height <= 0 || main.getDepthTextureView() == null) {
      return;
    }
    GpuDevice device = RenderSystem.getDevice();
    Sky sky = module.scene.get();
    if (!ready(device, sky)) {
      return;
    }
    if (uniforms == null) {
      uniforms = device.createBuffer(() -> "cxvisuals_sky", UNIFORM_USAGE, UNIFORM_SIZE);
    }
    Matrix4f lens = camera.projectionMatrix;
    RAYS.set(lens).mul(VIEW.rotation(camera.orientation.conjugate(FACING))).invert();
    TURN.rotationY(angle).rotateZ(TILT);
    Palette palette = module.skyPalette.get();
    int first = palette.first();
    int second = palette.second();
    Vector4f fog = camera.fogData.color;
    CommandEncoder encoder = device.createCommandEncoder();
    try (MemoryStack memory = MemoryStack.stackPush()) {
      ByteBuffer data =
          Std140Builder.onStack(memory, UNIFORM_SIZE)
              .putMat4f(RAYS)
              .putMat4f(TURN)
              .putVec4(
                  2.0F / (Math.abs(lens.m11()) * main.height),
                  clock,
                  module.skyBrightnessScale() * clear,
                  shown)
              .putVec4(fog.x, fog.y, fog.z, haze)
              .putVec4(red(first), green(first), blue(first), 1.0F)
              .putVec4(red(second), green(second), blue(second), 1.0F)
              .get();
      encoder.writeToBuffer(uniforms.slice(), data);
    }
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals sky shader", main.getColorTextureView(), Optional.empty())) {
      pass.setPipeline(PIPELINES[sky.ordinal()]);
      pass.setUniform(BLOCK, uniforms);
      pass.bindTexture(
          DEPTH,
          main.getDepthTextureView(),
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
