package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import me.cxiy.cxvisuals.core.settings.Palette;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.shaders.circles.CircleMesh;
import me.cxiy.cxvisuals.modules.shaders.circles.CircleSink;
import me.cxiy.cxvisuals.modules.shaders.circles.Circles;
import me.cxiy.cxvisuals.modules.shaders.circles.Contour;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

final class CircleBatch implements CircleSink {
  static final String BLOCK = "Circle";
  static final RenderPipeline PIPELINE = pipeline("jump_circle", CompareOp.GREATER_THAN_OR_EQUAL);
  static final RenderPipeline THROUGH_WALLS =
      pipeline("jump_circle_through_walls", CompareOp.ALWAYS_PASS);

  private static final int VERTEX = 28;
  private static final int QUAD = VERTEX * 6;
  private static final int UNIFORM_SIZE;
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final float CHANNEL = 1.0F / 255.0F;
  private static final float CLOCK_WRAP = 3600.0F;
  private static final float RADIUS_UNITS = 1000.0F;
  private static final int RIM_POINTS = 128;
  private static final double TURN = Math.PI * 2.0;
  static final Vector4fc WHOLE = new Vector4f(0.0F, 0.0F, 0.0F, -1.0F);

  static {
    Std140SizeCalculator size = new Std140SizeCalculator().putMat4f();
    for (int i = 0; i < RIM_POINTS / 2 + 4; i++) {
      size.putVec4();
    }
    UNIFORM_SIZE = size.get();
  }

  private final String label;
  private final CircleMesh mesh = new CircleMesh();
  private final float[] rim = new float[RIM_POINTS * 2];
  private Contour traced;
  private final ByteBuffer data =
      ByteBuffer.allocateDirect(CircleMesh.QUADS * QUAD).order(ByteOrder.nativeOrder());
  private GpuBuffer vertices;
  private GpuBuffer uniforms;
  private int quads;

  CircleBatch(String label) {
    this.label = label;
  }

  private static RenderPipeline pipeline(String name, CompareOp depth) {
    return RenderPipeline.builder()
        .withBindGroupLayout(
            BindGroupLayout.builder().withUniform(BLOCK, UniformType.UNIFORM_BUFFER).build())
        .withLocation(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/" + name))
        .withVertexShader(
            Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/jump_circle"))
        .withFragmentShader(
            Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/jump_circle"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
        .withDepthStencilState(new DepthStencilState(depth, false))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
        .build();
  }

  static boolean compiles(GpuDevice device) {
    return Gpu.usable(PIPELINE) && Gpu.usable(THROUGH_WALLS);
  }

  int build(Circles circles, View view, ShadersModule module, long nanos) {
    data.clear();
    quads = mesh.emit(circles, view, module, nanos, this);
    data.flip();
    return quads;
  }

  void upload(CommandEncoder encoder) {
    GpuDevice device = RenderSystem.getDevice();
    if (vertices == null) {
      vertices =
          device.createBuffer(
              () -> "cxvisuals_" + label,
              GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
              (long) CircleMesh.QUADS * QUAD);
      uniforms =
          device.createBuffer(
              () -> "cxvisuals_" + label + "_uniforms", UNIFORM_USAGE, UNIFORM_SIZE);
    }
    encoder.writeToBuffer(vertices.slice(0, data.remaining()), data);
  }

  void look(
      CommandEncoder encoder,
      Matrix4fc viewProjection,
      ShadersModule module,
      float clock,
      Vector4fc cut) {
    trace(module.ringContour());
    Palette palette = module.ringPalette.get();
    int first = palette.first();
    int second = palette.second();
    try (MemoryStack memory = MemoryStack.stackPush()) {
      Std140Builder builder = Std140Builder.onStack(memory, UNIFORM_SIZE).putMat4f(viewProjection);
      for (int k = 0; k < rim.length; k += 4) {
        builder.putVec4(rim[k], rim[k + 1], rim[k + 2], rim[k + 3]);
      }
      builder
          .putVec4(
              module.ring.get().ordinal(),
              module.ringLineBlocks(),
              module.ringGlowScale(),
              module.ringIntensityScale())
          .putVec4(red(first), green(first), blue(first), module.floorGlowShare())
          .putVec4(red(second), green(second), blue(second), clock % CLOCK_WRAP)
          .putVec4(cut);
      encoder.writeToBuffer(uniforms.slice(), builder.get());
    }
  }

  private void trace(Contour contour) {
    if (contour == traced) {
      return;
    }
    traced = contour;
    for (int i = 0; i < RIM_POINTS; i++) {
      float turn = (float) i / RIM_POINTS;
      float radius = contour.at(turn);
      rim[i * 2] = (float) Math.cos(turn * TURN) * radius;
      rim[i * 2 + 1] = (float) Math.sin(turn * TURN) * radius;
    }
  }

  void draw(RenderPass pass, boolean throughWalls) {
    Gpu.use(pass, throughWalls ? THROUGH_WALLS : PIPELINE);
    pass.setUniform(BLOCK, uniforms);
    pass.setVertexBuffer(0, vertices.slice());
    pass.draw(quads * 6, 1, 0, 0);
  }

  void release() {
    if (vertices != null) {
      vertices.close();
      uniforms.close();
      vertices = null;
      uniforms = null;
    }
  }

  @Override
  public void vertex(
      float x,
      float y,
      float z,
      float u,
      float v,
      int kind,
      float progress,
      float alpha,
      int seed,
      float radius) {
    data.putFloat(x).putFloat(y).putFloat(z);
    data.put(unit(progress)).put(unit(alpha)).put((byte) 0).put((byte) -1);
    data.putFloat(u).putFloat(v);
    data.putShort((short) (kind << 12 | seed & 0xFFF))
        .putShort((short) Math.min(Short.MAX_VALUE, Math.round(radius * RADIUS_UNITS)));
  }

  private static byte unit(float value) {
    return (byte) Math.round(Math.clamp(value, 0.0F, 1.0F) * 255.0F);
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
