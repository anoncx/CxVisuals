package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

public record FaceRenderState(
    Matrix3x2fc pose,
    float x,
    float y,
    float size,
    float radius,
    int color,
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    float z,
    Cut cut)
    implements GuiElementRenderState {
  private static final float FIXED_POINT = 8.0F;

  @Override
  public void buildVertices(VertexConsumer consumer) {
    float left = cut.x(x);
    float right = cut.x(x + size);
    float top = cut.y(y);
    float bottom = cut.y(y + size);
    float u0 = (left - x) / size;
    float u1 = (right - x) / size;
    float v0 = (top - y) / size;
    float v1 = (bottom - y) / size;
    vertex(consumer, left, top, u0, v0);
    vertex(consumer, left, bottom, u0, v1);
    vertex(consumer, right, bottom, u1, v1);
    vertex(consumer, right, top, u1, v0);
  }

  private void vertex(VertexConsumer consumer, float px, float py, float u, float v) {
    GuiVertex.at(consumer, pose, px, py, z)
        .setColor(color)
        .setUv(u, v)
        .setUv1(Math.round(size * FIXED_POINT), Math.round(radius * FIXED_POINT))
        .setUv2(0, 0)
        .setNormal(0.0F, 0.0F, 1.0F);
  }
}
