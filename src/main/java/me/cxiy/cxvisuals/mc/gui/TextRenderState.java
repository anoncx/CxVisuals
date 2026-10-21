package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cxiy.cxvisuals.gui.text.GlyphAtlas;
import me.cxiy.cxvisuals.gui.text.TextRun;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

public record TextRenderState(
    Matrix3x2fc pose,
    TextRun run,
    int page,
    float originX,
    float originY,
    float pixelsPerUnit,
    int color,
    boolean adaptive,
    int plate,
    TextureSetup textureSetup,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    float z,
    Cut cut)
    implements GuiElementRenderState {
  private static final float SNAP_EPSILON = 0.001F;
  private static final float TEXEL = 1.0F / GlyphAtlas.PAGE_SIZE;

  @Override
  public void buildVertices(VertexConsumer consumer) {
    float unitsPerPixel = 1.0F / pixelsPerUnit;
    float scale = run.glyphScale;
    for (int i = 0; i < run.count; i++) {
      GlyphAtlas.Glyph glyph = run.glyphs[i];
      if (glyph.page() != page) {
        continue;
      }
      float left =
          (float) Math.floor((originX + run.xs[i]) * pixelsPerUnit + SNAP_EPSILON) * unitsPerPixel;
      float top =
          (float) Math.floor((originY + run.ys[i]) * pixelsPerUnit + SNAP_EPSILON) * unitsPerPixel;
      float right = left + glyph.width() * scale;
      float bottom = top + glyph.height() * scale;
      float u0 = glyph.u() * TEXEL;
      float v0 = glyph.v() * TEXEL;
      float u1 = (glyph.u() + glyph.width()) * TEXEL;
      float v1 = (glyph.v() + glyph.height()) * TEXEL;
      if (cut != Cut.NONE) {
        if (cut.misses(left, top, right, bottom)) {
          continue;
        }
        float cutLeft = cut.x(left);
        float cutRight = cut.x(right);
        float cutTop = cut.y(top);
        float cutBottom = cut.y(bottom);
        float nu0 = Cut.slide(u0, u1, left, right, cutLeft);
        float nu1 = Cut.slide(u0, u1, left, right, cutRight);
        float nv0 = Cut.slide(v0, v1, top, bottom, cutTop);
        float nv1 = Cut.slide(v0, v1, top, bottom, cutBottom);
        left = cutLeft;
        right = cutRight;
        top = cutTop;
        bottom = cutBottom;
        u0 = nu0;
        u1 = nu1;
        v0 = nv0;
        v1 = nv1;
      }
      vertex(consumer, left, top, u0, v0);
      vertex(consumer, left, bottom, u0, v1);
      vertex(consumer, right, bottom, u1, v1);
      vertex(consumer, right, top, u1, v0);
    }
  }

  private void vertex(VertexConsumer consumer, float x, float y, float u, float v) {
    VertexConsumer vertex = GuiVertex.at(consumer, pose, x, y, z).setUv(u, v).setColor(color);
    if (adaptive) {
      vertex
          .setUv1((plate >> 16) & 0xFF, (plate >> 8) & 0xFF)
          .setUv2(plate & 0xFF, plate >>> 24)
          .setNormal(0.0F, 0.0F, 1.0F);
    }
  }

  @Override
  public RenderPipeline pipeline() {
    return adaptive ? TextPipeline.ADAPTIVE : RenderPipelines.GUI_TEXTURED;
  }
}
