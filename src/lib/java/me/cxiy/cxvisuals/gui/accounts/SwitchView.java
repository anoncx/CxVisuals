package me.cxiy.cxvisuals.gui.accounts;

import java.util.List;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Presence;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.WindowFrame;
import me.cxiy.cxvisuals.modules.accounts.Entry;
import me.cxiy.cxvisuals.modules.accounts.Hub;

public final class SwitchView {
  public enum Result {
    WAITING,
    CANCELLED,
    PROCEED
  }

  public static final int KEY_ESCAPE = 256;

  private static final float WIDTH = 420.0F;
  private static final float HEIGHT = 262.0F;
  private static final float MARGIN = 16.0F;
  private static final float DOT = 6.0F;

  private final Hub hub;
  private final Entry entry;
  private final String server;
  private final Supplier<WindowMotion> motion;
  private final Presence presence = new Presence();
  private final WindowFrame frame = new WindowFrame();
  private final Pill cancel = new Pill(Lang.key("Cancel"), Pill.Tone.QUIET);
  private final Pill proceed = new Pill(Lang.key("Connect anyway"), Pill.Tone.PRIMARY);
  private final Pill back = new Pill(Lang.key("Back"), Pill.Tone.QUIET);
  private String failure = "";
  private Result result = Result.WAITING;
  private boolean closing;
  private float clock;
  private float screenW = WIDTH;
  private float screenH = HEIGHT;
  private float fit = 1.0F;
  private float originX;
  private float originY;

  public SwitchView(Hub hub, Entry entry, String server, Supplier<WindowMotion> motion) {
    this.hub = hub;
    this.entry = entry;
    this.server = server;
    this.motion = motion;
    presence.snap(0.0F);
  }

  public void fail(String message) {
    failure = message == null ? "" : message;
  }

  public Result result() {
    return result;
  }

  public void close() {
    closing = true;
  }

  public boolean closed() {
    return closing && presence.gone();
  }

  public void resize(float width, float height) {
    screenW = width;
    screenH = height;
    fit =
        Math.max(
            0.1F,
            Math.min(
                1.0F,
                Math.min((width - MARGIN * 2.0F) / WIDTH, (height - MARGIN * 2.0F) / HEIGHT)));
    originX = (width - WIDTH) * 0.5F;
    originY = (height - HEIGHT) * 0.5F;
  }

  private float localX(float mx) {
    return screenW * 0.5F + (mx - screenW * 0.5F) / fit;
  }

  private float localY(float my) {
    return screenH * 0.5F + (my - screenH * 0.5F) / fit;
  }

  public void draw(Canvas c, Theme t, float rawX, float rawY, float dt) {
    float mx = localX(rawX);
    float my = localY(rawY);
    clock += dt;
    WindowMotion style = motion.get();
    presence.step(style, !closing, dt);
    if (presence.phase() < 0.01F) {
      return;
    }
    if (!frame.open(
        c, t, presence, style, originX, originY, WIDTH, HEIGHT, screenW, screenH, fit)) {
      return;
    }
    body(c, t, mx, my, dt);
    frame.close(c);
  }

  private void body(Canvas c, Theme t, float mx, float my, float dt) {
    float centre = originX + WIDTH * 0.5F;
    float top = originY + 28.0F;
    Portrait.head(c, t, hub, entry, centre - 32.0F, top, 64.0F, 18.0F);
    String title = Lang.tr(failure.isEmpty() ? "Switching account" : "Could not switch");
    float titleY = top + 64.0F + 16.0F;
    c.text(
        FontStyle.DISPLAY,
        title,
        centre - c.textWidth(FontStyle.DISPLAY, title) * 0.5F,
        titleY,
        t.ink());
    String sub =
        failure.isEmpty()
            ? Lang.format("Signing in as {0} to join {1}", entry.username, server)
            : failure;
    List<String> lines = Words.wrap(c, FontStyle.SMALL, sub, WIDTH - 64.0F);
    float line = c.lineHeight(FontStyle.SMALL) + 2.0F;
    float subY = titleY + c.lineHeight(FontStyle.DISPLAY) + 6.0F;
    for (int i = 0; i < lines.size(); i++) {
      c.text(
          FontStyle.SMALL,
          lines.get(i),
          centre - c.textWidth(FontStyle.SMALL, lines.get(i)) * 0.5F,
          subY + i * line,
          failure.isEmpty() ? t.ink2() : t.alert());
    }
    float rowY = originY + HEIGHT - 28.0F - Pill.HEIGHT;
    if (failure.isEmpty()) {
      float dotsY = rowY - 26.0F;
      for (int i = 0; i < 3; i++) {
        float wave = 0.5F + 0.5F * (float) Math.sin(clock * 5.0F - i * 0.9F);
        int ink = i == 0 ? Theme.mix(t.ink(), t.red(), wave) : t.ink();
        c.roundRect(
            centre - 24.0F * 0.5F + i * (DOT + 5.0F) - 3.0F,
            dotsY,
            DOT,
            DOT,
            DOT * 0.5F,
            Theme.withAlpha(ink, 0.25F + 0.75F * wave));
      }
      float width = cancel.width(c);
      cancel.draw(c, t, centre - width * 0.5F, rowY, width, mx, my, dt);
      return;
    }
    float a = proceed.width(c);
    float b = back.width(c);
    float start = centre - (a + b + 8.0F) * 0.5F;
    proceed.draw(c, t, start, rowY, a, mx, my, dt);
    back.draw(c, t, start + a + 8.0F, rowY, b, mx, my, dt);
  }

  public boolean outside(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    return mx < originX || mx >= originX + WIDTH || my < originY || my >= originY + HEIGHT;
  }

  public boolean mouseDown(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    return failure.isEmpty() ? cancel.down(mx, my) : proceed.down(mx, my) || back.down(mx, my);
  }

  public void mouseUp(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    if (failure.isEmpty()) {
      if (cancel.up(mx, my)) {
        finish(Result.CANCELLED);
      }
      return;
    }
    if (proceed.up(mx, my)) {
      finish(Result.PROCEED);
    }
    if (back.up(mx, my)) {
      finish(Result.CANCELLED);
    }
  }

  public boolean keyDown(int key) {
    if (key == KEY_ESCAPE) {
      finish(Result.CANCELLED);
      return true;
    }
    return false;
  }

  private void finish(Result next) {
    result = next;
    closing = true;
  }
}
