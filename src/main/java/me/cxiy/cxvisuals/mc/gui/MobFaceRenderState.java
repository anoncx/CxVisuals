package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

public record MobFaceRenderState(
    Matrix3x2fc pose,
    float x,
    float y,
    float size,
    float radius,
    float u0,
    float v0,
    float u1,
    float v1,
    int color,
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    float z)
    implements GuiElementRenderState {
  private static final float FIXED_POINT = 8.0F;

  @Override
  public void buildVertices(VertexConsumer consumer) {
    vertex(consumer, x, y, u0, v0, 0, 0);
    vertex(consumer, x, y + size, u0, v1, 0, 1);
    vertex(consumer, x + size, y + size, u1, v1, 1, 1);
    vertex(consumer, x + size, y, u1, v0, 1, 0);
  }

  private void vertex(
      VertexConsumer consumer, float px, float py, float u, float v, int localX, int localY) {
    GuiVertex.at(consumer, pose, px, py, z)
        .setColor(color)
        .setUv(u, v)
        .setUv1(Math.round(size * FIXED_POINT), Math.round(radius * FIXED_POINT))
        .setUv2(localX, localY)
        .setNormal(0.0F, 0.0F, 1.0F);
  }
}
