package me.cxiy.cxvisuals.gui.hud;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.hud.HudData;

public final class WorldPins {
  public static final float FOCUS = 70.0F;
  private static final float PILL = 20.0F;
  private static final float PAD = 9.0F;
  private static final float DOT = 6.0F;
  private static final float GAP = 6.0F;
  private static final float STEM = 7.0F;
  private static final float POINT = 5.0F;
  private static final float MARGIN = 6.0F;
  private static final float NEAR = 2.0F;
  private static final float NEAR_FADE = 4.0F;
  private static final float REST = 0.72F;
  private static final int KILOMETRE = 1000;

  private WorldPins() {}

  public static String range(int metres) {
    if (metres < KILOMETRE) {
      return metres + " m";
    }
    return metres / KILOMETRE + "." + metres % KILOMETRE / 100 + " km";
  }

  public static void draw(Canvas c, Theme t, HudData data, float screenW, float screenH) {
    float labelLine = c.lineHeight(FontStyle.LABEL);
    float monoLine = c.lineHeight(FontStyle.MONO);
    for (int i = 0; i < data.markCount; i++) {
      if (!data.pinOn[i]) {
        continue;
      }
      float x = data.pinX[i];
      float y = data.pinY[i];
      float distance = data.pinDistance[i];
      float near = Math.max(0.0F, Math.min(1.0F, (distance - NEAR) / NEAR_FADE));
      if (near <= 0.004F) {
        continue;
      }
      boolean focused = Math.hypot(x - screenW * 0.5F, y - screenH * 0.5F) < FOCUS;
      int metres = Math.round(distance);
      String note = data.markNote[i];
      if (metres != data.pinMetres[i] || data.pinRange[i] == null || note != data.pinNote[i]) {
        data.pinMetres[i] = metres;
        data.pinNote[i] = note;
        data.pinRange[i] =
            note == null || note.isEmpty() ? range(metres) : range(metres) + " · " + note;
      }
      String name = data.markLabel[i];
      String range = data.pinRange[i];
      float nameWidth = c.textWidth(FontStyle.LABEL, name);
      float rangeWidth = focused ? GAP + c.textWidth(FontStyle.MONO, range) : 0.0F;
      float width = PAD + DOT + GAP + nameWidth + rangeWidth + PAD;
      float left = Math.max(MARGIN, Math.min(screenW - MARGIN - width, x - width * 0.5F));
      float top = y - STEM - PILL;
      int color = data.markColor[i];
      c.pushAlpha(near * (focused ? 1.0F : REST));
      c.roundRect(x - 0.5F, y - STEM, 1.0F, STEM, 0.0F, Theme.withAlpha(t.ink(), 0.5F));
      c.roundRect(
          x - POINT * 0.5F - 1.0F,
          y - POINT * 0.5F - 1.0F,
          POINT + 2.0F,
          POINT + 2.0F,
          data.markFriend[i] ? (POINT + 2.0F) * 0.5F : 2.0F,
          t.paper());
      c.roundRect(
          x - POINT * 0.5F,
          y - POINT * 0.5F,
          POINT,
          POINT,
          data.markFriend[i] ? POINT * 0.5F : 1.5F,
          color);
      HudPaint.plate(c, t, left, top, width, PILL, PILL * 0.5F);
      c.roundRect(
          left + PAD,
          top + (PILL - DOT) * 0.5F,
          DOT,
          DOT,
          data.markFriend[i] ? DOT * 0.5F : 1.5F,
          color);
      c.pushContrast(t.paper());
      float textX = left + PAD + DOT + GAP;
      c.text(FontStyle.LABEL, name, textX, top + (PILL - labelLine) * 0.5F, t.ink());
      if (focused) {
        c.text(
            FontStyle.MONO,
            range,
            textX + nameWidth + GAP,
            top + (PILL - monoLine) * 0.5F,
            t.grey());
      }
      c.popContrast();
      c.popAlpha();
    }
  }
}
