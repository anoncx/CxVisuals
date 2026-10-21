package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.PickSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;

public final class MirrorElement extends HudElement {
  public enum Quality {
    SHARP(2.0F),
    BALANCED(1.4F),
    LIGHT(1.0F);

    public final float oversample;

    Quality(float oversample) {
      this.oversample = oversample;
    }
  }

  private static final float PAD = 3.0F;
  private static final float WIDE = 9.0F / 16.0F;
  private static final float MIN_RATIO = 0.4F;
  private static final float MAX_RATIO = 2.4F;
  private static final float FROZEN_DIM = 0.45F;
  private static final float RESPONSE = 12.0F;
  private static final float SETTLED = 0.005F;
  private static final float CHIP_HEIGHT = 20.0F;
  private static final float CHIP_PAD = 7.0F;
  private static final float CHIP_MARGIN = 9.0F;
  private static final float DOT_GAP = 6.0F;
  private static final String WAITING = Lang.key("Connecting");
  private static final String MINIMIZED = Lang.key("Minimized");
  private static final String CLOSED = Lang.key("Window closed");
  private static final String IDLE = Lang.key("Pick a window in the HUD settings");
  private static final String UNSUPPORTED = Lang.key("Window capture is unavailable here");

  public final NumberSetting width;
  public final EnumSetting<Quality> quality;
  public final BoolSetting name;
  public final BoolSetting hide;

  private final FrameStore store;
  private final PickSetting window;
  private float dim = 1.0F;

  public MirrorElement(Settings settings, FrameStore store, PickSetting window) {
    super(settings, "mirror", "Window", Category.INFO, false, Anchor.TOP_RIGHT, -10.0, 120.0);
    this.store = store;
    this.window = window;
    this.width = group().number("width", "Width", 320.0, 160.0, 640.0, 10.0, "");
    this.quality =
        group()
            .enumOf("quality", "Quality", Quality.BALANCED, "Sharp", "Balanced", "Light")
            .describe("Sharp keeps the most detail and uses more video memory bandwidth.");
    this.name = group().bool("name", "Show name", false);
    this.hide =
        group()
            .bool("hide", "Hide when empty", true)
            .describe("Keeps the card away until a window is being mirrored.");
  }

  public FrameStore store() {
    return store;
  }

  @Override
  public float radius() {
    return 16.0F;
  }

  @Override
  public boolean present(HudData data) {
    return !hide.get() || store.has();
  }

  @Override
  public void measure(Canvas c, HudData data) {
    float inner = width.get().floatValue() - PAD * 2.0F;
    float ratio = store.has() ? store.height() / (float) store.width() : WIDE;
    w = inner + PAD * 2.0F;
    h = inner * Math.max(MIN_RATIO, Math.min(MAX_RATIO, ratio)) + PAD * 2.0F;
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    FrameStore.Status status = store.status();
    boolean frozen = status == FrameStore.Status.PAUSED || status == FrameStore.Status.LOST;
    float goal = frozen ? FROZEN_DIM : 1.0F;
    float follow = data.still ? 1.0F : 1.0F - (float) Math.exp(-dt * RESPONSE);
    dim += (goal - dim) * follow;
    if (Math.abs(goal - dim) < SETTLED) {
      dim = goal;
    }

    HudPaint.plate(c, t, this, x, y, w, h, radius());
    float left = x + PAD;
    float top = y + PAD;
    float inner = w - PAD * 2.0F;
    float height = h - PAD * 2.0F;
    float round = Math.max(2.0F, radius() - PAD);
    boolean drawn = store.has() && c.feed(store, left, top, inner, height, round, dim);
    if (!drawn) {
      c.roundRect(left, top, inner, height, round, t.surface2());
      center(c, t, left, top, inner, height, notice(status), false);
      return;
    }
    if (frozen) {
      String notice = status == FrameStore.Status.PAUSED ? MINIMIZED : CLOSED;
      center(c, t, left, top, inner, height, notice, true);
    }
    if (name.get()) {
      String title = title();
      if (!title.isEmpty()) {
        String shown = c.fit(FontStyle.CAPTION, title, inner * 0.6F);
        chip(c, t, left + CHIP_MARGIN, top + CHIP_MARGIN, shown);
      }
    }
  }

  private String notice(FrameStore.Status status) {
    if (window.chosen().isEmpty()) {
      return IDLE;
    }
    return switch (status) {
      case UNSUPPORTED -> UNSUPPORTED;
      case LOST -> CLOSED;
      case PAUSED -> MINIMIZED;
      default -> WAITING;
    };
  }

  private String title() {
    String key = window.chosen();
    int cut = key.indexOf('|');
    return cut < 0 ? key : key.substring(cut + 1);
  }

  private void center(
      Canvas c, Theme t, float x, float y, float w, float h, String text, boolean plate) {
    String shown = c.fit(FontStyle.CAPTION, Lang.tr(text), w - CHIP_PAD * 4.0F);
    float textWidth = c.textWidth(FontStyle.CAPTION, shown);
    float left = x + (w - textWidth) * 0.5F;
    float top = y + (h - c.lineHeight(FontStyle.CAPTION)) * 0.5F;
    if (plate) {
      c.roundRect(
          left - CHIP_PAD - DOT_GAP * 0.5F,
          y + (h - CHIP_HEIGHT) * 0.5F,
          textWidth + CHIP_PAD * 2.0F + DOT_GAP,
          CHIP_HEIGHT,
          CHIP_HEIGHT * 0.5F,
          Theme.withAlpha(t.paper(), 0.85F));
    }
    c.text(FontStyle.CAPTION, shown, left, top, plate ? t.ink() : t.grey());
  }

  private void chip(Canvas c, Theme t, float x, float y, String text) {
    float textWidth = c.textWidth(FontStyle.CAPTION, text);
    c.roundRect(
        x,
        y,
        textWidth + CHIP_PAD * 2.0F,
        CHIP_HEIGHT,
        CHIP_HEIGHT * 0.5F,
        Theme.withAlpha(t.paper(), 0.85F));
    c.text(
        FontStyle.CAPTION,
        text,
        x + CHIP_PAD,
        y + (CHIP_HEIGHT - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        t.ink());
  }
}
