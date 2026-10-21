package me.cxiy.cxvisuals.gui;

final class Stage {
  private static final float DOT = 2.0F;
  private static final float SPACING = 11.0F;
  private static final int ROWS = 7;
  private static final float DEPTH = 0.42F;

  private Stage() {}

  static void floor(Canvas c, Theme t, float sx, float sy, float sw, float sh) {
    float horizon = sy + sh * (1.0F - DEPTH);
    float centre = sx + sw * 0.5F;
    float bottom = sy + sh;
    for (int row = 0; row < ROWS; row++) {
      float depth = (row + 1.0F) / ROWS;
      float rowY = horizon + (bottom - horizon) * depth * depth;
      float spacing = SPACING * (0.55F + 0.9F * depth);
      float dot = DOT * (0.6F + 0.6F * depth);
      int across = (int) (sw * 0.5F / spacing);
      for (int k = -across; k <= across; k++) {
        float dx = k * spacing;
        float fade = 1.0F - Math.abs(dx) / (sw * 0.5F);
        float alpha = Math.max(0.0F, Math.min(1.0F, fade * fade * (0.25F + 0.75F * depth)));
        if (alpha < 0.05F) {
          continue;
        }
        c.roundRect(
            centre + dx - dot * 0.5F,
            rowY - dot * 0.5F,
            dot,
            dot,
            dot * 0.5F,
            Theme.withAlpha(t.dotOff(), alpha * 2.2F));
      }
    }
  }
}
