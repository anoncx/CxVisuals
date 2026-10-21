package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class PositionElement extends HudElement {
  public enum Variant {
    LINE,
    TILE
  }

  private static final String[] AXES = {"X", "Y", "Z"};
  private static final String[] FACING = {"S", "W", "N", "E"};
  private static final String[] FACING_KEYS =
      Lang.keys("#compass.S", "#compass.W", "#compass.N", "#compass.E");
  private static final float PAD = 13.0F;
  private static final float LABEL_GAP = 5.0F;
  private static final float AXIS_GAP = 12.0F;
  private static final float ROW = 17.0F;
  private static final float TILE_WIDTH = 118.0F;
  private static final float ROWS_TOP = 31.0F;

  public final EnumSetting<Variant> variant;
  public final BoolSetting facing;
  public final BoolSetting biome;

  private final int[] block = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
  private final String[] text = {"0", "0", "0"};
  private final float[] widths = new float[3];
  private final float[] labelWidths = new float[3];
  private boolean stale = true;
  private boolean labelled;
  private String heading = FACING[0];
  private String place = "";
  private float headingWidth;
  private float placeWidth;

  public PositionElement(Settings settings, double homeY) {
    super(settings, "position", "Position", Category.INFO, false, Anchor.TOP_LEFT, 10.0, homeY);
    this.variant = group().enumOf("variant", "Style", Variant.LINE, "Line", "Tile");
    this.facing = group().bool("facing", "Facing", true);
    this.biome = group().bool("biome", "Biome", true).describe("Shown on the tile.");
  }

  public String axis(int index) {
    return text[index];
  }

  public String heading() {
    return heading;
  }

  @Override
  public float radius() {
    return variant.get() == Variant.LINE ? HudPaint.PILL * 0.5F : 18.0F;
  }

  @Override
  public void sample(HudData data) {
    update(0, (int) Math.floor(data.x));
    update(1, (int) Math.floor(data.y));
    update(2, (int) Math.floor(data.z));
    int quarter = Math.floorMod(Math.round(data.yaw / 90.0F), 4);
    String next = Lang.tr(FACING_KEYS[quarter], FACING[quarter]);
    if (!next.equals(heading)) {
      heading = next;
      stale = true;
    }
    if (!data.biome.equals(place)) {
      place = data.biome;
      stale = true;
    }
  }

  private void update(int axis, int value) {
    if (block[axis] != value) {
      block[axis] = value;
      text[axis] = Integer.toString(value);
      stale = true;
    }
  }

  @Override
  public void measure(Canvas c, HudData data) {
    if (!labelled) {
      labelled = true;
      for (int i = 0; i < 3; i++) {
        labelWidths[i] = c.textWidth(FontStyle.CAPTION, AXES[i]);
      }
    }
    if (stale) {
      stale = false;
      for (int i = 0; i < 3; i++) {
        widths[i] = c.textWidth(FontStyle.MONO, text[i]);
      }
      headingWidth = c.textWidth(FontStyle.DOTS, heading);
      placeWidth = c.textWidth(FontStyle.SMALL, place);
    }
    if (variant.get() == Variant.LINE) {
      float width = PAD * 2.0F;
      for (int i = 0; i < 3; i++) {
        width += labelWidths[i] + LABEL_GAP + widths[i] + (i < 2 ? AXIS_GAP : 0.0F);
      }
      if (facing.get()) {
        width += AXIS_GAP + c.textWidth(FontStyle.CAPTION, heading);
      }
      w = (float) Math.ceil(width);
      h = HudPaint.PILL;
      return;
    }
    boolean named = biome.get() && !place.isEmpty();
    w = Math.max(TILE_WIDTH, named ? (float) Math.ceil(placeWidth + HudPaint.INSET * 2.0F) : 0.0F);
    h = ROWS_TOP + ROW * 3.0F + (named ? 24.0F : 9.0F);
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    if (variant.get() == Variant.LINE) {
      HudPaint.plate(c, t, this, x, y, w, h, h * 0.5F);
      float captionTop = HudPaint.centred(c, FontStyle.CAPTION, y, h);
      float valueTop = HudPaint.centred(c, FontStyle.MONO, y, h);
      float cursor = x + PAD;
      for (int i = 0; i < 3; i++) {
        c.text(FontStyle.CAPTION, AXES[i], cursor, captionTop, t.grey());
        cursor += labelWidths[i] + LABEL_GAP;
        c.text(FontStyle.MONO, text[i], cursor, valueTop, t.ink());
        cursor += widths[i] + AXIS_GAP;
      }
      if (facing.get()) {
        c.text(FontStyle.CAPTION, heading, cursor, captionTop, t.alert());
      }
      return;
    }
    HudPaint.plate(c, t, this, x, y, w, h, 18.0F);
    c.text(FontStyle.CAPTION, Lang.tr("POSITION"), x + HudPaint.INSET, y + 8.0F, t.grey());
    if (facing.get()) {
      c.text(FontStyle.DOTS, heading, x + w - HudPaint.INSET - headingWidth, y + 3.0F, t.alert());
    }
    float right = x + w - HudPaint.INSET;
    for (int i = 0; i < 3; i++) {
      float rowTop = y + ROWS_TOP + i * ROW;
      c.text(
          FontStyle.CAPTION,
          AXES[i],
          x + HudPaint.INSET,
          HudPaint.centred(c, FontStyle.CAPTION, rowTop, ROW),
          t.grey());
      c.text(
          FontStyle.MONO,
          text[i],
          right - widths[i],
          HudPaint.centred(c, FontStyle.MONO, rowTop, ROW),
          t.ink());
    }
    if (biome.get() && !place.isEmpty()) {
      float lineTop = y + ROWS_TOP + ROW * 3.0F + 3.0F;
      c.roundRect(x + HudPaint.INSET, lineTop, w - HudPaint.INSET * 2.0F, 1.0F, 0.0F, t.hair());
      c.text(FontStyle.SMALL, place, x + HudPaint.INSET, lineTop + 3.0F, t.grey());
    }
  }
}
