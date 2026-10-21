package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.gui.hud.Rolling;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public abstract class ReadoutElement extends HudElement {
  public enum Variant {
    PILL,
    TILE
  }

  private static final float PILL_PAD = 13.0F;
  private static final float GAP = 8.0F;

  public final EnumSetting<Variant> variant;

  private final String caption;
  private final Rolling rolling = new Rolling();
  private String value = "";
  private String note = "";
  private Variant measured;
  private float captionWidth = -1.0F;
  private float valueWidth = -1.0F;
  private float noteWidth;

  protected ReadoutElement(
      Settings settings,
      String id,
      String name,
      String caption,
      Anchor home,
      double homeX,
      double homeY) {
    super(settings, id, name, Category.INFO, false, home, homeX, homeY);
    this.caption = caption;
    this.variant = group().enumOf("variant", "Style", Variant.PILL, "Pill", "Tile");
  }

  public final String value() {
    return value;
  }

  protected final void show(String nextValue, String nextNote) {
    if (value.isEmpty()) {
      rolling.snap(nextValue);
    } else {
      rolling.set(nextValue);
    }
    value = nextValue;
    note = nextNote;
    valueWidth = -1.0F;
  }

  @Override
  public float radius() {
    return variant.get() == Variant.PILL ? HudPaint.PILL * 0.5F : 18.0F;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    if (captionWidth < 0.0F || reworded()) {
      captionWidth = c.textWidth(FontStyle.CAPTION, Lang.tr(caption));
    }
    Variant style = variant.get();
    if (style != measured) {
      measured = style;
      valueWidth = -1.0F;
    }
    if (valueWidth < 0.0F) {
      valueWidth = rolling.width(c, style == Variant.TILE ? FontStyle.DOTS : FontStyle.ROW);
      noteWidth = note.isEmpty() ? 0.0F : c.textWidth(FontStyle.SMALL, note);
    }
    if (style == Variant.PILL) {
      w = (float) Math.ceil(PILL_PAD * 2.0F + captionWidth + GAP + valueWidth);
      h = HudPaint.PILL;
    } else {
      float widest = Math.max(valueWidth, Math.max(captionWidth, noteWidth));
      w = Math.max(HudPaint.TILE, (float) Math.ceil(HudPaint.INSET * 2.0F + widest));
      h = HudPaint.TILE;
    }
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    if (variant.get() == Variant.PILL) {
      HudPaint.plate(c, t, this, x, y, w, h, h * 0.5F);
      c.text(
          FontStyle.CAPTION,
          Lang.tr(caption),
          x + PILL_PAD,
          HudPaint.centred(c, FontStyle.CAPTION, y, h),
          t.grey());
      rolling.draw(
          c,
          FontStyle.ROW,
          x + PILL_PAD + captionWidth + GAP,
          HudPaint.centred(c, FontStyle.ROW, y, h),
          false,
          t.ink(),
          dt,
          data.still);
      return;
    }
    HudPaint.plate(c, t, this, x, y, w, h, 18.0F);
    c.text(FontStyle.CAPTION, Lang.tr(caption), x + HudPaint.INSET, y + 8.0F, t.grey());
    rolling.draw(
        c, FontStyle.DOTS, x + HudPaint.INSET - 1.0F, y + 25.0F, false, t.ink(), dt, data.still);
    if (!note.isEmpty()) {
      c.text(
          FontStyle.SMALL,
          note,
          x + HudPaint.INSET,
          y + h - 9.0F - c.lineHeight(FontStyle.SMALL),
          t.grey());
    }
  }
}
