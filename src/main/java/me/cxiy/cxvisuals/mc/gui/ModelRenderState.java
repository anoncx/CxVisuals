package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

public record ModelRenderState(
    Matrix3x2fc pose,
    float x0,
    float y0,
    float x1,
    float y1,
    int color,
    TextureSetup textureSetup,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    float z,
    Cut cut)
    implements GuiElementRenderState {
  @Override
  public void buildVertices(VertexConsumer consumer) {
    float left = cut.x(x0);
    float right = cut.x(x1);
    float top = cut.y(y0);
    float bottom = cut.y(y1);
    float u0 = Cut.slide(0.0F, 1.0F, x0, x1, left);
    float u1 = Cut.slide(0.0F, 1.0F, x0, x1, right);
    float v0 = Cut.slide(1.0F, 0.0F, y0, y1, top);
    float v1 = Cut.slide(1.0F, 0.0F, y0, y1, bottom);
    GuiVertex.at(consumer, pose, left, top, z).setUv(u0, v0).setColor(color);
    GuiVertex.at(consumer, pose, left, bottom, z).setUv(u0, v1).setColor(color);
    GuiVertex.at(consumer, pose, right, bottom, z).setUv(u1, v1).setColor(color);
    GuiVertex.at(consumer, pose, right, top, z).setUv(u1, v0).setColor(color);
  }

  @Override
  public RenderPipeline pipeline() {
    return RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA;
  }
}
