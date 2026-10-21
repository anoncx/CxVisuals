package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import me.cxiy.cxvisuals.core.settings.Palette;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.shaders.trails.TrailMesh;
import me.cxiy.cxvisuals.modules.shaders.trails.TrailSink;
import me.cxiy.cxvisuals.modules.shaders.trails.Wake;
import me.cxiy.cxvisuals.modules.shaders.trails.Wakes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

final class TrailBatch implements TrailSink {
  static final String BLOCK = "Trail";
  static final RenderPipeline PIPELINE =
      RenderPipeline.builder()
          .withBindGroupLayout(
              BindGroupLayout.builder().withUniform(BLOCK, UniformType.UNIFORM_BUFFER).build())
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/entity_trail"))
          .withVertexShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/trail"))
          .withFragmentShader(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/trail"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
          .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
          .withCull(false)
          .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .build();
  static final Vector4fc WHOLE = new Vector4f(0.0F, 0.0F, 0.0F, -1.0F);

  private static final int VERTEX = 28;
  private static final int QUAD = VERTEX * 6;
  private static final int UNIFORM_SIZE =
      new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().putVec4().get();
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final float CHANNEL = 1.0F / 255.0F;
  private static final float CLOCK_WRAP = 3600.0F;
  private static final float EXTRA_UNITS = 1000.0F;

  private final String label;
  private final TrailMesh mesh = new TrailMesh();
  private final ByteBuffer data =
      ByteBuffer.allocateDirect(TrailMesh.QUADS * QUAD).order(ByteOrder.nativeOrder());
  private GpuBuffer vertices;
  private GpuBuffer uniforms;
  private int quads;

  TrailBatch(String label) {
    this.label = label;
  }

  static boolean compiles(GpuDevice device) {
    return device.precompilePipeline(PIPELINE).isValid();
  }

  int build(Wakes wakes, View view, ShadersModule module, long nanos) {
    data.clear();
    quads = mesh.emit(wakes, view, module, nanos, this);
    data.flip();
    return quads;
  }

  int build(Wake wake, View view, ShadersModule module, long nanos) {
    data.clear();
    quads = mesh.emit(wake, view, module, nanos, this);
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
              (long) TrailMesh.QUADS * QUAD);
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
    Palette palette = module.trailPalette.get();
    int first = palette.first();
    int second = palette.second();
    try (MemoryStack memory = MemoryStack.stackPush()) {
      encoder.writeToBuffer(
          uniforms.slice(),
          Std140Builder.onStack(memory, UNIFORM_SIZE)
              .putMat4f(viewProjection)
              .putVec4(red(first), green(first), blue(first), module.trailIntensityScale())
              .putVec4(red(second), green(second), blue(second), clock % CLOCK_WRAP)
              .putVec4(
                  module.trailGlowScale(), module.trailSpeedScale(), module.trailSizeScale(), 0.0F)
              .putVec4(cut)
              .get());
    }
  }

  void draw(RenderPass pass) {
    pass.setPipeline(PIPELINE);
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
      float aux,
      float tone,
      int seed,
      float extra) {
    data.putFloat(x).putFloat(y).putFloat(z);
    data.put(unit(progress)).put(unit(alpha)).put(unit(aux)).put(unit(tone));
    data.putFloat(u).putFloat(v);
    data.putShort((short) (kind << 12 | seed & 0xFFF))
        .putShort((short) Math.min(Short.MAX_VALUE, Math.round(extra * EXTRA_UNITS)));
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
