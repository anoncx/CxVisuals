package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

final class LaunchCard {
  static final int OUTSIDE = 0;
  static final int INSIDE = 1;
  static final int START = 2;
  static final int BACK = 3;
  static final int PREVIOUS = 4;
  static final int NEXT = 5;

  private static final float FOOTER = 76.0F;
  private static final float PILL_HEIGHT = 40.0F;
  private static final float PILL_MAX = 220.0F;
  private static final float PILL_GAP = 24.0F;
  private static final float ARROW = 38.0F;
  private static final float ARROW_INSET = 18.0F;
  private static final float ART_MAX = 120.0F;
  private static final float ART_SHARE = 0.34F;
  private static final float SHIFT = 40.0F;
  private static final float DIM = 0.62F;
  private static final float SHOW_AT = 0.55F;
  private static final float WAVE_SPEED = 5.0F;
  private static final int SCRIM = 0xFF000000;
  private static final String START_LABEL = Lang.key("Start");
  private static final String BACK_LABEL = Lang.key("Menu");
  private static final String PROMPT = Lang.key("Start this channel?");
  private static final String BACK_GLYPH = "‹";
  private static final String NEXT_GLYPH = "›";

  private final Anim open = Anim.move(0.0F);
  private final Anim depart = Anim.reveal(0.0F);
  private final Anim swap = Anim.reveal(1.0F);
  private final Anim startHover = Anim.hover(0.0F);
  private final Anim backHover = Anim.hover(0.0F);
  private final Anim previousHover = Anim.hover(0.0F);
  private final Anim nextHover = Anim.hover(0.0F);
  private Channel channel;
  private Channel before;
  private Channel arrived;
  private boolean closing;
  private boolean departing;
  private boolean browsable;
  private int direction;
  private int pressed = OUTSIDE;
  private float clock;
  private float fromX;
  private float fromY;
  private float fromW;
  private float fromH;
  private float x;
  private float y;
  private float w;
  private float h;
  private float startX;
  private float backX;
  private float pillY;
  private float pillW;
  private float arrowY;
  private float previousX;
  private float nextX;

  boolean active() {
    return channel != null;
  }

  boolean interactive() {
    return channel != null && !closing && !departing;
  }

  Channel channel() {
    return channel;
  }

  void show(Channel target, float tileX, float tileY, float tileW, float tileH, boolean others) {
    channel = target;
    before = null;
    closing = false;
    departing = false;
    browsable = others;
    depart.snap(0.0F);
    swap.snap(1.0F);
    fromX = tileX;
    fromY = tileY;
    fromW = tileW;
    fromH = tileH;
  }

  void browse(Channel next, int step) {
    before = channel;
    channel = next;
    direction = step;
    swap.snap(0.0F);
    swap.set(1.0F);
  }

  void close() {
    closing = true;
  }

  void depart() {
    departing = true;
  }

  Channel arrived() {
    Channel done = arrived;
    arrived = null;
    return done;
  }

  float hidden(Channel tile) {
    return tile == channel ? open.get() : 0.0F;
  }

  void place(float px, float py, float pw, float ph) {
    x = px;
    y = py;
    w = pw;
    h = ph;
    pillW = Math.min(PILL_MAX, (pw - 80.0F - PILL_GAP) * 0.5F);
    pillY = py + ph - (FOOTER + PILL_HEIGHT) * 0.5F;
    float centre = px + pw * 0.5F;
    backX = centre - PILL_GAP * 0.5F - pillW;
    startX = centre + PILL_GAP * 0.5F;
    arrowY = py + (ph - FOOTER) * 0.5F - ARROW * 0.5F;
    previousX = px + ARROW_INSET;
    nextX = px + pw - ARROW_INSET - ARROW;
  }

  int hit(float mx, float my) {
    if (Kit.inside(mx, my, startX, pillY, pillW, PILL_HEIGHT)) {
      return START;
    }
    if (Kit.inside(mx, my, backX, pillY, pillW, PILL_HEIGHT)) {
      return BACK;
    }
    if (browsable && Kit.inside(mx, my, previousX, arrowY, ARROW, ARROW)) {
      return PREVIOUS;
    }
    if (browsable && Kit.inside(mx, my, nextX, arrowY, ARROW, ARROW)) {
      return NEXT;
    }
    return Kit.inside(mx, my, x, y, w, h) ? INSIDE : OUTSIDE;
  }

  int press(float mx, float my) {
    pressed = hit(mx, my);
    return pressed;
  }

  int release(float mx, float my) {
    int began = pressed;
    pressed = OUTSIDE;
    return began == hit(mx, my) ? began : OUTSIDE;
  }

  void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    open.set(channel != null && !closing ? 1.0F : 0.0F);
    open.update(dt);
    depart.set(departing ? 1.0F : 0.0F);
    depart.update(dt);
    swap.update(dt);
    clock += dt;
    if (channel == null) {
      return;
    }
    if (closing && open.get() <= 0.001F) {
      channel = null;
      before = null;
      closing = false;
      return;
    }
    if (departing && depart.get() >= 1.0F) {
      arrived = channel;
      channel = null;
      before = null;
      departing = false;
      depart.snap(0.0F);
      return;
    }
    float amount = (float) Easing.snappy(Math.max(0.0F, Math.min(1.0F, open.get())));
    c.roundRect(x, y, w, h, 0.0F, Theme.withAlpha(SCRIM, DIM * amount));
    float cx = fromX + (x - fromX) * amount;
    float cy = fromY + (y - fromY) * amount;
    float cw = fromW + (w - fromW) * amount;
    float ch = fromH + (h - fromH) * amount;
    Kit.window(c, t, cx, cy, cw, ch);
    if (amount > SHOW_AT) {
      c.pushClip(x, y, w, h);
      content(
          c, t, (amount - SHOW_AT) / (1.0F - SHOW_AT) * (1.0F - depart.get()), mouseX, mouseY, dt);
      c.popClip();
    }
  }

  private void content(Canvas c, Theme t, float visible, float mouseX, float mouseY, float dt) {
    c.pushAlpha(visible);
    float centre = x + w * 0.5F;
    float previewH = h - FOOTER;
    float progress = swap.get();
    if (before != null && progress < 1.0F) {
      c.pushAlpha(1.0F - progress);
      preview(c, t, before, centre - direction * SHIFT * progress, previewH);
      c.popAlpha();
      c.pushAlpha(progress);
      preview(c, t, channel, centre + direction * SHIFT * (1.0F - progress), previewH);
      c.popAlpha();
    } else {
      preview(c, t, channel, centre, previewH);
    }
    c.roundRect(x + 24.0F, y + h - FOOTER, w - 48.0F, 1.0F, 0.5F, t.hair());
    int over = hit(mouseX, mouseY);
    startHover.set(over == START ? 1.0F : 0.0F);
    backHover.set(over == BACK ? 1.0F : 0.0F);
    previousHover.set(over == PREVIOUS ? 1.0F : 0.0F);
    nextHover.set(over == NEXT ? 1.0F : 0.0F);
    startHover.update(dt);
    backHover.update(dt);
    previousHover.update(dt);
    nextHover.update(dt);
    Kit.pill(
        c,
        t,
        Lang.tr(START_LABEL),
        startX,
        pillY,
        pillW,
        PILL_HEIGHT,
        startHover.get(),
        pressed == START ? 1.0F : 0.0F,
        true,
        channel.enabled);
    Kit.pill(
        c,
        t,
        Lang.tr(BACK_LABEL),
        backX,
        pillY,
        pillW,
        PILL_HEIGHT,
        backHover.get(),
        pressed == BACK ? 1.0F : 0.0F,
        false,
        true);
    if (browsable) {
      arrow(c, t, BACK_GLYPH, previousX, previousHover.get(), pressed == PREVIOUS);
      arrow(c, t, NEXT_GLYPH, nextX, nextHover.get(), pressed == NEXT);
    }
    c.popAlpha();
  }

  private void preview(Canvas c, Theme t, Channel shown, float centre, float previewH) {
    float art = Math.min(ART_MAX, previewH * ART_SHARE);
    float artY = y + previewH * 0.5F - art * 0.5F - 26.0F;
    float artX = centre - art * 0.5F;
    if (shown.art != null) {
      shown.art.draw(c, t, artX, artY, art, t.ink(), true, clock * WAVE_SPEED);
    } else {
      Pictograms.draw(c, t, shown.glyph, artX, artY, art, t.ink(), true, clock * WAVE_SPEED);
    }
    float inner = w - 160.0F;
    float titleY = artY + art + 18.0F;
    float titleWidth = Math.min(inner, c.textWidth(FontStyle.TITLE, shown.title));
    c.pushClip(centre - inner * 0.5F, titleY, inner, c.lineHeight(FontStyle.TITLE) + 2.0F);
    c.text(FontStyle.TITLE, shown.title, centre - titleWidth * 0.5F, titleY, t.ink());
    c.popClip();
    String prompt = shown.caption.isEmpty() ? Lang.tr(PROMPT) : shown.caption;
    float promptWidth = Math.min(inner, c.textWidth(FontStyle.CAPTION, prompt));
    c.text(
        FontStyle.CAPTION,
        prompt,
        centre - promptWidth * 0.5F,
        titleY + c.lineHeight(FontStyle.TITLE) + 4.0F,
        t.grey());
  }

  private void arrow(Canvas c, Theme t, String glyph, float left, float hover, boolean down) {
    c.pushTransform(
        (down ? 0.94F : 1.0F) * (1.0F + 0.06F * hover), left + ARROW * 0.5F, arrowY + ARROW * 0.5F);
    Depth.raised(
        c,
        t,
        left,
        arrowY,
        ARROW,
        ARROW,
        ARROW * 0.5F,
        Theme.mix(t.surface2(), t.ink(), 0.1F * hover));
    c.text(
        FontStyle.TITLE,
        glyph,
        left + (ARROW - c.textWidth(FontStyle.TITLE, glyph)) * 0.5F,
        arrowY + (ARROW - c.lineHeight(FontStyle.TITLE)) * 0.5F - 2.0F,
        t.ink());
    c.popTransform();
  }
}
