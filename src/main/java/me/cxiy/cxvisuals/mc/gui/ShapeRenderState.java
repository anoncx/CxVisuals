package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

public record ShapeRenderState(
    Matrix3x2fc pose,
    float centerX,
    float centerY,
    float halfWidth,
    float halfHeight,
    float pad,
    float radius,
    float softness,
    float border,
    float fade,
    int color,
    int colorBottom,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    float z,
    Cut cut)
    implements GuiElementRenderState {
  private static final float FIXED_POINT = 8.0F;
  private static final float BORDER_RANGE = 16.0F;
  private static final float FADE_RANGE = 96.0F;

  @Override
  public void buildVertices(VertexConsumer consumer) {
    float extentX = halfWidth + pad;
    float extentY = halfHeight + pad;
    float left = cut.x(centerX - extentX) - centerX;
    float right = cut.x(centerX + extentX) - centerX;
    float top = cut.y(centerY - extentY) - centerY;
    float bottom = cut.y(centerY + extentY) - centerY;
    int upper = Cut.blend(color, colorBottom, (top + extentY) / (extentY * 2.0F));
    int lower = Cut.blend(color, colorBottom, (bottom + extentY) / (extentY * 2.0F));
    vertex(consumer, left, top, upper);
    vertex(consumer, left, bottom, lower);
    vertex(consumer, right, bottom, lower);
    vertex(consumer, right, top, upper);
  }

  private void vertex(VertexConsumer consumer, float localX, float localY, int argb) {
    GuiVertex.at(consumer, pose, centerX + localX, centerY + localY, z)
        .setColor(argb)
        .setUv(localX, localY)
        .setUv1(Math.round(halfWidth * FIXED_POINT), Math.round(halfHeight * FIXED_POINT))
        .setUv2(Math.round(radius * FIXED_POINT), Math.round(softness * FIXED_POINT))
        .setNormal(Math.min(border / BORDER_RANGE, 1.0F), Math.min(fade / FADE_RANGE, 1.0F), 0.0F);
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
