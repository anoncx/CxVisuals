package me.cxiy.cxvisuals.mc.modules.deaths;

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
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.modules.deaths.Corpses;
import me.cxiy.cxvisuals.modules.deaths.DeathMesh;
import me.cxiy.cxvisuals.modules.deaths.FxSink;
import me.cxiy.cxvisuals.modules.environment.View;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

final class DeathBatch implements FxSink {
  private static final String BLOCK = "DeathFx";
  private static final RenderPipeline PIPELINE =
      RenderPipeline.builder()
          .withBindGroupLayout(
              BindGroupLayout.builder().withUniform(BLOCK, UniformType.UNIFORM_BUFFER).build())
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/death_fx"))
          .withVertexShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/death_fx"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/death_fx"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
          .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
          .withCull(false)
          .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .build();

  private static final int VERTEX = 28;
  private static final int QUAD = VERTEX * 6;
  private static final int UNIFORM_SIZE =
      new Std140SizeCalculator().putMat4f().putVec4().putVec4().get();
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final float CHANNEL = 1.0F / 255.0F;
  private static final float SIZE_UNITS = 1000.0F;

  private final DeathMesh mesh = new DeathMesh();
  private final ByteBuffer data =
      ByteBuffer.allocateDirect(DeathMesh.QUADS * QUAD).order(ByteOrder.nativeOrder());
  private GpuBuffer vertices;
  private GpuBuffer uniforms;
  private int quads;

  static boolean compiles(GpuDevice device) {
    return Gpu.usable(PIPELINE);
  }

  int build(Corpses corpses, View view, float seconds, long nanos) {
    data.clear();
    quads = mesh.emit(corpses, view, seconds, nanos, this);
    data.flip();
    return quads;
  }

  void upload(
      CommandEncoder encoder,
      Matrix4fc viewProjection,
      int first,
      int second,
      float intensity,
      float clock) {
    GpuDevice device = RenderSystem.getDevice();
    if (vertices == null) {
      vertices =
          device.createBuffer(
              () -> "cxvisuals_death_fx",
              GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
              (long) DeathMesh.QUADS * QUAD);
      uniforms =
          device.createBuffer(() -> "cxvisuals_death_fx_uniforms", UNIFORM_USAGE, UNIFORM_SIZE);
    }
    encoder.writeToBuffer(vertices.slice(0, data.remaining()), data);
    try (MemoryStack memory = MemoryStack.stackPush()) {
      encoder.writeToBuffer(
          uniforms.slice(),
          Std140Builder.onStack(memory, UNIFORM_SIZE)
              .putMat4f(viewProjection)
              .putVec4(red(first), green(first), blue(first), intensity)
              .putVec4(red(second), green(second), blue(second), clock)
              .get());
    }
  }

  void draw(RenderPass pass) {
    Gpu.use(pass, PIPELINE);
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
      float size) {
    data.putFloat(x).putFloat(y).putFloat(z);
    data.put(unit(progress)).put(unit(alpha)).put((byte) 0).put((byte) -1);
    data.putFloat(u).putFloat(v);
    data.putShort((short) (kind << 12 | seed & 0xFFF))
        .putShort((short) Math.min(Short.MAX_VALUE, Math.round(size * SIZE_UNITS)));
  }

  private static byte unit(float value) {
    return (byte) Math.round(Math.clamp(value, 0.0F, 1.0F) * 255.0F);
  }

  static float red(int argb) {
    return (argb >> 16 & 0xFF) * CHANNEL;
  }

  static float green(int argb) {
    return (argb >> 8 & 0xFF) * CHANNEL;
  }

  static float blue(int argb) {
    return (argb & 0xFF) * CHANNEL;
  }
}
