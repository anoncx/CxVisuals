package me.cxiy.cxvisuals.mc.modules.swings;

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
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.swings.SwingMesh;
import me.cxiy.cxvisuals.modules.swings.SwingSink;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

final class SwingBatch implements SwingSink {
  private static final String BLOCK = "Swing";
  private static final RenderPipeline PIPELINE =
      RenderPipeline.builder()
          .withBindGroupLayout(
              BindGroupLayout.builder().withUniform(BLOCK, UniformType.UNIFORM_BUFFER).build())
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/swing_trail"))
          .withVertexShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/swing_trail"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/swing_trail"))
          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
          .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
          .withCull(false)
          .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .build();

  private static final int VERTEX = 28;
  private static final int QUAD = VERTEX * 6;
  private static final int UNIFORM_SIZE =
      new Std140SizeCalculator().putMat4f().putVec4().putVec4().putVec4().get();
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final float CHANNEL = 1.0F / 255.0F;
  private static final float CLOCK_WRAP = 3600.0F;

  private final String label;
  private final ByteBuffer data =
      ByteBuffer.allocateDirect(SwingMesh.QUADS * QUAD).order(ByteOrder.nativeOrder());
  private GpuBuffer vertices;
  private GpuBuffer uniforms;
  private int quads;

  SwingBatch(String label) {
    this.label = label;
  }

  static boolean compiles(GpuDevice device) {
    return device.precompilePipeline(PIPELINE).isValid();
  }

  void begin() {
    data.clear();
    quads = 0;
  }

  void added(int count) {
    quads += count;
  }

  int quads() {
    return quads;
  }

  void upload(
      CommandEncoder encoder,
      Matrix4fc viewProjection,
      int first,
      int second,
      float intensity,
      float clock,
      float style,
      float glow,
      float halo,
      float width) {
    GpuDevice device = RenderSystem.getDevice();
    if (vertices == null) {
      vertices =
          device.createBuffer(
              () -> "cxvisuals_" + label,
              GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
              (long) SwingMesh.QUADS * QUAD);
      uniforms =
          device.createBuffer(
              () -> "cxvisuals_" + label + "_uniforms", UNIFORM_USAGE, UNIFORM_SIZE);
    }
    data.flip();
    encoder.writeToBuffer(vertices.slice(0, data.remaining()), data);
    try (MemoryStack memory = MemoryStack.stackPush()) {
      encoder.writeToBuffer(
          uniforms.slice(),
          Std140Builder.onStack(memory, UNIFORM_SIZE)
              .putMat4f(viewProjection)
              .putVec4(red(first), green(first), blue(first), intensity)
              .putVec4(red(second), green(second), blue(second), clock % CLOCK_WRAP)
              .putVec4(style, glow, halo, width)
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
      float across,
      float arc,
      float along,
      float alpha,
      float ends,
      float tip,
      int seed) {
    data.putFloat(x).putFloat(y).putFloat(z);
    data.put(unit(along)).put(unit(alpha)).put(unit(ends)).put(unit(tip));
    data.putFloat(across).putFloat(arc);
    data.putShort((short) (seed & 0xFFF)).putShort((short) 0);
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
