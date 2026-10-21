package me.cxiy.cxvisuals.modules.hud;

import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

public final class HostedElement extends HudElement {
  private static final float GHOST_ALPHA = 0.6F;
  private static final float GHOST_FILL = 0.4F;
  private static final float NORMAL_FLOOR = 0.999F;
  private static final float SAFE_INSET = 10.0F;

  private final BoolSetting themed;
  private final boolean solid;
  private final BoolSetting backed;
  private float norm = 1.0F;
  private float nativeX;
  private float nativeY;
  private boolean live;

  public HostedElement(Settings settings, String id, String name, boolean themable, boolean solid) {
    this(settings, id, name, themable, solid, false);
  }

  public HostedElement(
      Settings settings,
      String id,
      String name,
      boolean themable,
      boolean solid,
      boolean backable) {
    super(settings, id, name, Category.GAME, true, Anchor.NATIVE, 0.0, 0.0);
    this.solid = solid;
    this.backed =
        backable
            ? group()
                .bool("background", "Background", true)
                .describe("Puts the bars and names on one card.")
            : null;
    this.themed =
        themable
            ? group()
                .bool("themed", "Themed", true)
                .describe("Draws it on a card in the interface style.")
            : null;
  }

  public void natural(float x, float y, float width, float height, boolean drawn) {
    nativeX = x;
    nativeY = y;
    w = width;
    h = height;
    live = drawn;
  }

  public float nativeX() {
    return nativeX;
  }

  public float nativeY() {
    return nativeY;
  }

  public boolean solid() {
    return solid && live;
  }

  public boolean live() {
    return live;
  }

  public boolean themed() {
    return themed != null && themed.get();
  }

  public boolean backed() {
    return backed != null && backed.get();
  }

  public void normalize(float value) {
    norm = value;
  }

  public boolean normalized() {
    return themed() && norm < NORMAL_FLOOR;
  }

  public boolean transformed() {
    return displaced() || normalized();
  }

  public boolean displaced() {
    return anchor.get() != Anchor.NATIVE
        || offsetX.get() != 0.0
        || offsetY.get() != 0.0
        || scale.get() != 100.0;
  }

  @Override
  public float factor(float global) {
    return (float) (scale.get() / 100.0) * (themed() ? norm : 1.0F);
  }

  @Override
  protected float inset() {
    return normalized() ? SAFE_INSET : 0.0F;
  }

  @Override
  public float radius() {
    return 10.0F;
  }

  @Override
  protected float originX(Anchor point, float screenW, float width) {
    if (point != Anchor.NATIVE) {
      return super.originX(point, screenW, width);
    }
    return nativeX + Anchor.pivot((nativeX + w * 0.5F) / screenW) * (w - width);
  }

  @Override
  protected float originY(Anchor point, float screenH, float height) {
    if (point != Anchor.NATIVE) {
      return super.originY(point, screenH, height);
    }
    return nativeY + Anchor.pivot((nativeY + h * 0.5F) / screenH) * (h - height);
  }

  @Override
  public void measure(Canvas c, HudData data) {}

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    float corner = Math.min(radius(), Math.min(w, h) * 0.5F);
    c.pushAlpha(GHOST_ALPHA);
    c.roundRect(x, y, w, h, corner, Theme.withAlpha(t.paper(), GHOST_FILL));
    c.roundRectBorder(x, y, w, h, corner, 1.0F, t.focus());
    String label = name();
    float width = c.textWidth(FontStyle.CAPTION, label);
    if (width + 8.0F <= w && c.lineHeight(FontStyle.CAPTION) <= h) {
      c.text(
          FontStyle.CAPTION,
          label,
          x + (w - width) * 0.5F,
          y + (h - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          t.grey());
    }
    c.popAlpha();
  }
}
