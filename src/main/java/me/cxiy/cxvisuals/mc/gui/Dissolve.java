package me.cxiy.cxvisuals.mc.gui;

import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

final class Dissolve {
  private static final int LIMIT = 192;
  private static final int STRIDE = 5;
  private static final float[] REGIONS = new float[LIMIT * STRIDE];
  private static int count;

  private Dissolve() {}

  static void add(float x0, float y0, float x1, float y1, float erosion) {
    if (count == LIMIT) {
      return;
    }
    int at = count * STRIDE;
    REGIONS[at] = x0;
    REGIONS[at + 1] = y0;
    REGIONS[at + 2] = x1;
    REGIONS[at + 3] = y1;
    REGIONS[at + 4] = erosion;
    count++;
  }

  static boolean pending() {
    return count > 0 && EvaporatePipeline.usable();
  }

  static GuiElementRenderState element(int guiWidth, int guiHeight) {
    float[] copy = new float[count * STRIDE];
    System.arraycopy(REGIONS, 0, copy, 0, copy.length);
    GuiElementRenderState state =
        new EvaporateRenderState(
            copy,
            count,
            guiWidth,
            guiHeight,
            ScreenLayer.setup(),
            null,
            new net.minecraft.client.gui.navigation.ScreenRectangle(0, 0, guiWidth, guiHeight));
    count = 0;
    return state;
  }

  static void clear() {
    count = 0;
  }
}
