package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

public record GlassRenderState(
    Matrix3x2fc pose,
    float centerX,
    float centerY,
    float halfWidth,
    float halfHeight,
    float radius,
    float blur,
    float fade,
    int tint,
    TextureSetup textureSetup,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    float z,
    Cut cut)
    implements GuiElementRenderState {
  private static final float FIXED_POINT = 8.0F;
  private static final float PAD = 1.0F;

  @Override
  public void buildVertices(VertexConsumer consumer) {
    float extentX = halfWidth + PAD;
    float extentY = halfHeight + PAD;
    float left = cut.x(centerX - extentX) - centerX;
    float right = cut.x(centerX + extentX) - centerX;
    float top = cut.y(centerY - extentY) - centerY;
    float bottom = cut.y(centerY + extentY) - centerY;
    vertex(consumer, left, top);
    vertex(consumer, left, bottom);
    vertex(consumer, right, bottom);
    vertex(consumer, right, top);
  }

  private void vertex(VertexConsumer consumer, float localX, float localY) {
    GuiVertex.at(consumer, pose, centerX + localX, centerY + localY, z)
        .setColor(tint)
        .setUv(localX, localY)
        .setUv1(Math.round(halfWidth * FIXED_POINT), Math.round(halfHeight * FIXED_POINT))
        .setUv2(Math.round(radius * FIXED_POINT), Math.round(blur * FIXED_POINT))
        .setNormal(fade, 0.0F, 0.0F);
  }

  @Override
  public RenderPipeline pipeline() {
    return GlassPipeline.GLASS;
  }
}
