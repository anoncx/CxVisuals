package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class BakedShape implements VertexConsumer, SubmitNodeCollector.CustomGeometryRenderer {
  private static final int STRIDE = 5;
  private static final int LIGHT = 15728880;

  private float[] data = new float[STRIDE * 256];
  private int size;

  void reset() {
    size = 0;
  }

  @Override
  public VertexConsumer addVertex(float x, float y, float z) {
    if (size + STRIDE > data.length) {
      data = Arrays.copyOf(data, data.length * 2);
    }
    data[size] = x;
    data[size + 1] = y;
    data[size + 2] = z;
    size += STRIDE;
    return this;
  }

  @Override
  public VertexConsumer setColor(int r, int g, int b, int a) {
    return this;
  }

  @Override
  public VertexConsumer setColor(int color) {
    return this;
  }

  @Override
  public VertexConsumer setUv(float u, float v) {
    if (size >= STRIDE) {
      data[size - 2] = u;
      data[size - 1] = v;
    }
    return this;
  }

  @Override
  public VertexConsumer setUv1(int u, int v) {
    return this;
  }

  @Override
  public VertexConsumer setUv2(int u, int v) {
    return this;
  }

  @Override
  public VertexConsumer setNormal(float x, float y, float z) {
    return this;
  }

  @Override
  public VertexConsumer setLineWidth(float width) {
    return this;
  }

  @Override
  public void render(PoseStack.Pose pose, VertexConsumer consumer) {
    for (int i = 0; i < size; i += STRIDE) {
      consumer.addVertex(
          data[i],
          data[i + 1],
          data[i + 2],
          -1,
          data[i + 3],
          data[i + 4],
          OverlayTexture.NO_OVERLAY,
          LIGHT,
          0.0F,
          1.0F,
          0.0F);
    }
  }
}
