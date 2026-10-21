package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class GearElement extends HudElement {
  public enum Flow {
    COLUMN,
    ROW
  }

  private static final float PAD = 4.0F;

  public final EnumSetting<Flow> flow;
  public final BoolSetting wear;

  private final int first;
  private final int slots;
  private int visible;
  private boolean placeholder;

  public GearElement(
      Settings settings,
      String id,
      String name,
      int first,
      int slots,
      Flow flow,
      Anchor home,
      double homeX,
      double homeY) {
    super(settings, id, name, Category.INFO, false, home, homeX, homeY);
    this.first = first;
    this.slots = slots;
    this.flow = group().enumOf("flow", "Layout", flow, "Column", "Row");
    this.wear = group().bool("wear", "Durability", true);
  }

  @Override
  public boolean elastic() {
    return true;
  }

  @Override
  public boolean present(HudData data) {
    for (int i = first; i < first + slots; i++) {
      if (data.gear[i]) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    int filled = 0;
    for (int i = first; i < first + slots; i++) {
      if (data.gear[i]) {
        filled++;
      }
    }
    placeholder = filled == 0;
    visible = placeholder ? slots : filled;
    boolean column = flow.get() == Flow.COLUMN;
    w = PAD * 2.0F + HudPaint.CELL_WIDTH * (column ? 1 : visible);
    h = PAD * 2.0F + HudPaint.CELL_HEIGHT * (column ? visible : 1);
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    HudPaint.plate(c, t, this, x, y, w, h, radius());
    boolean column = flow.get() == Flow.COLUMN;
    int placed = 0;
    for (int i = first; i < first + slots; i++) {
      if (!placeholder && !data.gear[i]) {
        continue;
      }
      float cellX = x + PAD + (column ? 0.0F : placed * HudPaint.CELL_WIDTH);
      float cellY = y + PAD + (column ? placed * HudPaint.CELL_HEIGHT : 0.0F);
      placed++;
      if (placeholder) {
        HudPaint.dot(
            c,
            cellX + (HudPaint.CELL_WIDTH - HudPaint.DOT) * 0.5F,
            cellY + (HudPaint.CELL_HEIGHT - HudPaint.DOT) * 0.5F,
            t.dotOff());
        continue;
      }
      HudPaint.gear(c, t, data, i, cellX, cellY, wear.get());
    }
  }
}
