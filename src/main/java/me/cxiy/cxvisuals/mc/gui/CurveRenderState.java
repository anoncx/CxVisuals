package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

record CurveRenderState(
    Matrix3x2fc pose,
    float[] xs,
    float[] ys,
    int count,
    boolean fill,
    float halfWidth,
    float softness,
    float extent,
    float base,
    int color,
    int colorBottom,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    float z)
    implements GuiElementRenderState {
  private static final float FIXED_POINT = 8.0F;
  private static final float REACH = 2000.0F;
  private static final float MITER_FLOOR = 0.45F;
  private static final float EPSILON = 1.0e-6F;

  @Override
  public void buildVertices(VertexConsumer consumer) {
    if (fill) {
      buildFill(consumer);
    } else {
      buildStroke(consumer);
    }
  }

  private void buildFill(VertexConsumer consumer) {
    for (int i = 0; i < count - 1; i++) {
      vertex(consumer, xs[i], ys[i], 0.0F, REACH, color);
      vertex(consumer, xs[i], base, 0.0F, REACH, colorBottom);
      vertex(consumer, xs[i + 1], base, 0.0F, REACH, colorBottom);
      vertex(consumer, xs[i + 1], ys[i + 1], 0.0F, REACH, color);
    }
  }

  private void buildStroke(VertexConsumer consumer) {
    float[] normalX = new float[count];
    float[] normalY = new float[count];
    float[] reach = new float[count];
    float previousX = 0.0F;
    float previousY = -1.0F;
    for (int i = 0; i < count; i++) {
      float nextX = previousX;
      float nextY = previousY;
      if (i < count - 1) {
        float dx = xs[i + 1] - xs[i];
        float dy = ys[i + 1] - ys[i];
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > EPSILON) {
          nextX = -dy / length;
          nextY = dx / length;
        }
      }
      float joinX = i == 0 ? nextX : previousX + nextX;
      float joinY = i == 0 ? nextY : previousY + nextY;
      float joinLength = (float) Math.sqrt(joinX * joinX + joinY * joinY);
      if (joinLength < EPSILON) {
        joinX = nextX;
        joinY = nextY;
      } else {
        joinX /= joinLength;
        joinY /= joinLength;
      }
      normalX[i] = joinX;
      normalY[i] = joinY;
      reach[i] = extent / Math.max(MITER_FLOOR, joinX * nextX + joinY * nextY);
      previousX = nextX;
      previousY = nextY;
    }
    for (int i = 0; i < count - 1; i++) {
      int j = i + 1;
      vertex(
          consumer,
          xs[i] - normalX[i] * reach[i],
          ys[i] - normalY[i] * reach[i],
          -extent,
          halfWidth,
          color);
      vertex(
          consumer,
          xs[i] + normalX[i] * reach[i],
          ys[i] + normalY[i] * reach[i],
          extent,
          halfWidth,
          color);
      vertex(
          consumer,
          xs[j] + normalX[j] * reach[j],
          ys[j] + normalY[j] * reach[j],
          extent,
          halfWidth,
          color);
      vertex(
          consumer,
          xs[j] - normalX[j] * reach[j],
          ys[j] - normalY[j] * reach[j],
          -extent,
          halfWidth,
          color);
    }
  }

  private void vertex(
      VertexConsumer consumer, float x, float y, float across, float half, int argb) {
    GuiVertex.at(consumer, pose, x, y, z)
        .setColor(argb)
        .setUv(0.0F, across)
        .setUv1(Math.round(REACH * FIXED_POINT), Math.round(half * FIXED_POINT))
        .setUv2(0, Math.round(softness * FIXED_POINT))
        .setNormal(0.0F, 0.0F, 0.0F);
  }

  @Override
  public RenderPipeline pipeline() {
    return ShapePipeline.SHAPE;
  }

  @Override
  public TextureSetup textureSetup() {
    return TextureSetup.noTexture();
  }
}
