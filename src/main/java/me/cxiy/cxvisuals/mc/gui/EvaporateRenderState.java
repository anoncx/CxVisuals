package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jspecify.annotations.Nullable;

public record EvaporateRenderState(
    float[] regions,
    int count,
    int guiWidth,
    int guiHeight,
    TextureSetup textureSetup,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds)
    implements GuiElementRenderState {
  private static final int COLUMNS = 96;
  private static final int ROWS = 48;
  private static final int POINTS = (COLUMNS + 1) * (ROWS + 1);
  private static final int STRIDE = 5;
  private static final float STAGGER = 0.45F;
  private static final float LIFT = 9.0F;
  private static final float DRIFT = 2.6F;
  private static final float DRIFT_WAVES = 7.0F;
  private static final float FIXED_POINT = 1000.0F;
  private static final float[] EROSION = new float[POINTS];
  private static final float[] FADE = new float[POINTS];
  private static final float[] LIFTS = new float[POINTS];
  private static final float[] DRIFTS = new float[POINTS];

  @Override
  public void buildVertices(VertexConsumer consumer) {
    float tileW = (float) guiWidth / COLUMNS;
    float tileH = (float) guiHeight / ROWS;
    for (int row = 0; row <= ROWS; row++) {
      float py = row * tileH;
      for (int col = 0; col <= COLUMNS; col++) {
        sample(row * (COLUMNS + 1) + col, col * tileW, py, tileW, tileH);
      }
    }
    for (int row = 0; row < ROWS; row++) {
      for (int col = 0; col < COLUMNS; col++) {
        int a = row * (COLUMNS + 1) + col;
        vertex(consumer, a, col * tileW, row * tileH);
        vertex(consumer, a + COLUMNS + 1, col * tileW, (row + 1) * tileH);
        vertex(consumer, a + COLUMNS + 2, (col + 1) * tileW, (row + 1) * tileH);
        vertex(consumer, a + 1, (col + 1) * tileW, row * tileH);
      }
    }
  }

  private void sample(int index, float px, float py, float marginX, float marginY) {
    EROSION[index] = 0.0F;
    FADE[index] = 1.0F;
    LIFTS[index] = 0.0F;
    DRIFTS[index] = 0.0F;
    for (int r = count - 1; r >= 0; r--) {
      int at = r * STRIDE;
      if (px >= regions[at] - marginX
          && px <= regions[at + 2] + marginX
          && py >= regions[at + 1] - marginY
          && py <= regions[at + 3] + marginY) {
        float e = regions[at + 4];
        float across =
            Math.max(
                0.0F,
                Math.min(1.0F, (px - regions[at]) / Math.max(1.0F, regions[at + 2] - regions[at])));
        float l = Math.max(0.0F, Math.min(1.0F, e * (1.0F + STAGGER) - across * STAGGER));
        EROSION[index] = l * l * (3.0F - 2.0F * l);
        FADE[index] = 1.0F - l * l;
        LIFTS[index] = l * l * LIFT;
        DRIFTS[index] = (float) Math.sin(across * DRIFT_WAVES) * l * DRIFT;
        return;
      }
    }
  }

  private void vertex(VertexConsumer consumer, int index, float x, float y) {
    consumer
        .addVertex(x + DRIFTS[index], y - LIFTS[index], 0.0F)
        .setColor(0xFFFFFFFF)
        .setUv(x / guiWidth, 1.0F - y / guiHeight)
        .setUv1(Math.round(EROSION[index] * FIXED_POINT), Math.round(FADE[index] * FIXED_POINT))
        .setUv2(0, 0)
        .setNormal(0.0F, 0.0F, 1.0F);
  }

  @Override
  public RenderPipeline pipeline() {
    return EvaporatePipeline.EVAPORATE;
  }
}
