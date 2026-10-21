package me.cxiy.cxvisuals.modules.hud;

import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.Setting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.spatial.Layer;

public abstract class HudElement {
  public enum Category {
    INFO,
    GAME
  }

  public static final double MIN_SCALE = 50.0;
  public static final double MAX_SCALE = 300.0;
  public static final double SCALE_STEP = 5.0;

  private static final double REACH = 8192.0;
  private static final String[] ANCHORS = {
    "Native",
    "Top left",
    "Top",
    "Top right",
    "Left",
    "Centre",
    "Right",
    "Bottom left",
    "Bottom",
    "Bottom right"
  };

  private final String id;
  private final String name;
  private final Category category;
  private final Group options;

  public final BoolSetting shown;
  public final EnumSetting<Anchor> anchor;
  public final NumberSetting offsetX;
  public final NumberSetting offsetY;
  public final NumberSetting scale;

  private int worded = Lang.generation();

  public float w;
  public float h;
  public float left;
  public float top;
  public float pushX;
  public float pushY;
  public float shiftX;
  public float shiftY;
  public float appear;
  public float foldW = 1.0F;
  public float foldH = 1.0F;
  public float reveal = 1.0F;
  public boolean veiled;

  protected HudElement(
      Settings settings,
      String id,
      String name,
      Category category,
      boolean shownByDefault,
      Anchor home,
      double homeX,
      double homeY) {
    this.id = id;
    this.name = name;
    this.category = category;
    Page page = settings.hidden(id, name);
    Group placement = page.group("Placement");
    this.shown = placement.bool("shown", "Shown", shownByDefault);
    this.anchor = placement.enumOf("anchor", "Anchor", home, ANCHORS);
    this.offsetX = placement.number("x", "X", homeX, -REACH, REACH, 0.5, "");
    this.offsetY = placement.number("y", "Y", homeY, -REACH, REACH, 0.5, "");
    this.scale = placement.number("scale", "Size", 100.0, MIN_SCALE, MAX_SCALE, SCALE_STEP, "%");
    this.options = page.group(name);
  }

  public float depth() {
    return Layer.HUD_MID;
  }

  public final String id() {
    return id;
  }

  public final String name() {
    return Lang.tr(name);
  }

  protected final boolean reworded() {
    int now = Lang.generation();
    if (now == worded) {
      return false;
    }
    worded = now;
    return true;
  }

  public final Category category() {
    return category;
  }

  public final List<Setting<?>> options() {
    return options.settings();
  }

  protected final Group group() {
    return options;
  }

  protected final EnumSetting<Detail> detail() {
    return options
        .enumOf("detail", "Detail", Detail.VISIBLE, "Visible", "Full")
        .describe("Visible shows only what you could see in the game. Full adds exact values.");
  }

  public float factor(float global) {
    return (float) (scale.get() / 100.0) * global;
  }

  public float radius() {
    return 14.0F;
  }

  protected float inset() {
    return 0.0F;
  }

  public boolean elastic() {
    return false;
  }

  public boolean instant() {
    return false;
  }

  public boolean present(HudData data) {
    return true;
  }

  public void sample(HudData data) {}

  public abstract void measure(Canvas c, HudData data);

  public abstract void draw(Canvas c, Theme t, HudData data, float x, float y, float dt);

  protected float originX(Anchor point, float screenW, float width) {
    return point.fx * (screenW - width);
  }

  protected float originY(Anchor point, float screenH, float height) {
    return point.fy * (screenH - height);
  }

  public final void resolve(float screenW, float screenH, float factor) {
    Anchor point = anchor.get();
    float width = w * factor;
    float height = h * factor;
    float x = originX(point, screenW, width) + offsetX.get().floatValue();
    float y = originY(point, screenH, height) + offsetY.get().floatValue();
    if (point == Anchor.NATIVE) {
      float edge = inset();
      left = edge > 0.0F ? Math.max(edge, Math.min(x, screenW - width - edge)) : x;
      top = edge > 0.0F ? Math.max(edge, Math.min(y, screenH - height - edge)) : y;
      return;
    }
    left = clamp(x, screenW - width);
    top = clamp(y, screenH - height);
  }

  public final void moveTo(
      float newLeft, float newTop, float screenW, float screenH, float factor) {
    float width = w * factor;
    float height = h * factor;
    float x = clamp(newLeft, screenW - width);
    float y = clamp(newTop, screenH - height);
    Anchor point = Anchor.nearest((x + width * 0.5F) / screenW, (y + height * 0.5F) / screenH);
    anchor.set(point);
    offsetX.set((double) (x - point.fx * (screenW - width)));
    offsetY.set((double) (y - point.fy * (screenH - height)));
    resolve(screenW, screenH, factor);
  }

  public final void resize(double percent, float screenW, float screenH, float global) {
    float x = left;
    float y = top;
    scale.set(percent);
    moveTo(x, y, screenW, screenH, factor(global));
  }

  public final void resetPlacement() {
    anchor.reset();
    offsetX.reset();
    offsetY.reset();
    scale.reset();
  }

  private static float clamp(float value, float max) {
    return value < 0.0F ? 0.0F : Math.min(value, Math.max(0.0F, max));
  }
}
