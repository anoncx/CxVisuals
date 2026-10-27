package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cxiy.cxvisuals.gui.map.ChartBatch;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

public record ChartRenderState(
    Matrix3x2fc pose,
    float[] corners,
    int[] packed,
    int count,
    int color,
    float roundness,
    float masked,
    float clouds,
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    float z)
    implements GuiElementRenderState {
  private static final float MASK_FIXED = 8192.0F;
  private static final float CLOUD_FIXED = 16.0F;
  private static final int STRIDE = 4;

  static ChartRenderState of(
      Matrix3x2fc pose,
      ChartBatch batch,
      int color,
      RenderPipeline pipeline,
      TextureSetup setup,
      @Nullable ScreenRectangle scissor,
      @Nullable ScreenRectangle bounds,
      float z) {
    int vertices = batch.count * 4;
    float[] corners = new float[vertices * STRIDE];
    int[] packed = new int[vertices * 2];
    for (int q = 0; q < batch.count; q++) {
      for (int i = 0; i < 4; i++) {
        int at = q * 4 + i;
        boolean right = i >= 2;
        boolean bottom = i == 1 || i == 2;
        corners[at * STRIDE] = batch.px[at];
        corners[at * STRIDE + 1] = batch.py[at];
        corners[at * STRIDE + 2] = right ? batch.u1[q] : batch.u0[q];
        corners[at * STRIDE + 3] = bottom ? batch.v1[q] : batch.v0[q];
        packed[at * 2] = pack(batch.mx[at] * MASK_FIXED, batch.my[at] * MASK_FIXED);
        packed[at * 2 + 1] = pack(batch.cx[at] * CLOUD_FIXED, batch.cy[at] * CLOUD_FIXED);
      }
    }
    return new ChartRenderState(
        pose,
        corners,
        packed,
        vertices,
        color,
        batch.roundness,
        batch.masked ? 1.0F : 0.0F,
        batch.clouds,
        pipeline,
        setup,
        scissor,
        bounds,
        z);
  }

  private static int pack(float a, float b) {
    int x = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(a)));
    int y = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(b)));
    return (x << 16) | (y & 0xFFFF);
  }

  @Override
  public void buildVertices(VertexConsumer consumer) {
    for (int at = 0; at < count; at++) {
      int mask = packed[at * 2];
      int cloud = packed[at * 2 + 1];
      GuiVertex.at(consumer, pose, corners[at * STRIDE], corners[at * STRIDE + 1], z)
          .setColor(color)
          .setUv(corners[at * STRIDE + 2], corners[at * STRIDE + 3])
          .setUv1(mask >> 16, (short) mask)
          .setUv2(cloud >> 16, (short) cloud)
          .setNormal(roundness, masked, clouds);
    }
  }
}
