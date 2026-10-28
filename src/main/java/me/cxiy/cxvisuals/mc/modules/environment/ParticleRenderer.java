package me.cxiy.cxvisuals.mc.modules.environment;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.environment.particles.Atmosphere;
import me.cxiy.cxvisuals.modules.environment.particles.Climate;
import me.cxiy.cxvisuals.modules.environment.particles.Downpour;
import me.cxiy.cxvisuals.modules.environment.particles.Field;
import me.cxiy.cxvisuals.modules.environment.particles.Fireworks;
import me.cxiy.cxvisuals.modules.environment.particles.Lanterns;
import me.cxiy.cxvisuals.modules.environment.particles.Meteors;
import me.cxiy.cxvisuals.modules.environment.particles.Ripples;
import me.cxiy.cxvisuals.modules.environment.particles.Shape;
import me.cxiy.cxvisuals.modules.environment.particles.Sink;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class ParticleRenderer implements Sink {
  private static final int MAX_QUADS =
      Field.CAPACITY
          + Lanterns.QUADS
          + Meteors.QUADS
          + Fireworks.QUADS
          + Ripples.CAPACITY
          + Downpour.CAPACITY;
  private static final int VERTEX = 28;
  private static final int QUAD = VERTEX * 6;
  private static final int SEEDS = 0x7FFF;

  private final ByteBuffer data =
      ByteBuffer.allocateDirect(MAX_QUADS * QUAD).order(ByteOrder.nativeOrder());
  private final Matrix4f modelView = new Matrix4f();
  private GpuBuffer vertices;
  private int quads;

  void release() {
    if (vertices != null) {
      vertices.close();
      vertices = null;
    }
  }

  int draw(
      RenderTarget main,
      Matrix4fc viewRotation,
      Atmosphere atmosphere,
      View view,
      Climate climate) {
    GpuTextureView color = main.getColorTextureView();
    GpuTextureView depth = main.getDepthTextureView();
    if (color == null || depth == null) {
      return 0;
    }
    data.clear();
    quads = 0;
    atmosphere.emit(view, climate, this);
    if (quads == 0) {
      return 0;
    }
    data.flip();
    GpuDevice device = RenderSystem.getDevice();
    if (vertices == null) {
      vertices =
          device.createBuffer(
              () -> "cxvisuals_environment_particles",
              GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
              (long) MAX_QUADS * QUAD);
    }
    CommandEncoder encoder = device.createCommandEncoder();
    encoder.writeToBuffer(vertices.slice(0, data.remaining()), data);
    GpuBufferSlice transforms =
        RenderSystem.getDynamicUniforms().writeTransform(modelView.set(viewRotation));
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals environment particles",
            color,
            Optional.empty(),
            depth,
            OptionalDouble.empty())) {
      Gpu.use(pass, ParticlePipeline.pipeline);
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("DynamicTransforms", transforms);
      pass.setVertexBuffer(0, vertices.slice());
      pass.draw(quads * 6, 1, 0, 0);
    }
    return quads;
  }

  @Override
  public void quad(
      float x,
      float y,
      float z,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      int argb,
      Shape shape,
      float detail,
      int seed) {
    if (quads >= MAX_QUADS) {
      return;
    }
    short look = (short) (shape.ordinal() << 8 | Math.clamp(Math.round(detail * 255.0F), 0, 255));
    short grain = (short) (seed & SEEDS);
    byte r = (byte) (argb >> 16);
    byte g = (byte) (argb >> 8);
    byte b = (byte) argb;
    byte a = (byte) (argb >>> 24);
    vertex(x - ax - bx, y - ay - by, z - az - bz, r, g, b, a, -1.0F, -1.0F, look, grain);
    vertex(x + ax - bx, y + ay - by, z + az - bz, r, g, b, a, 1.0F, -1.0F, look, grain);
    vertex(x + ax + bx, y + ay + by, z + az + bz, r, g, b, a, 1.0F, 1.0F, look, grain);
    vertex(x - ax - bx, y - ay - by, z - az - bz, r, g, b, a, -1.0F, -1.0F, look, grain);
    vertex(x + ax + bx, y + ay + by, z + az + bz, r, g, b, a, 1.0F, 1.0F, look, grain);
    vertex(x - ax + bx, y - ay + by, z - az + bz, r, g, b, a, -1.0F, 1.0F, look, grain);
    quads++;
  }

  private void vertex(
      float x,
      float y,
      float z,
      byte r,
      byte g,
      byte b,
      byte a,
      float u,
      float v,
      short look,
      short grain) {
    data.putFloat(x).putFloat(y).putFloat(z);
    data.put(r).put(g).put(b).put(a);
    data.putFloat(u).putFloat(v);
    data.putShort(look).putShort(grain);
  }
}
