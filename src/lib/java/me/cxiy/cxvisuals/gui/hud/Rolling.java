package me.cxiy.cxvisuals.gui.hud;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;

public final class Rolling {
  private static final int MAX = 12;
  private static final int ASCII = 128;
  private static final float SECONDS = 0.24F;
  private static final float TRAVEL = 0.5F;
  private static final String[] GLYPHS = new String[ASCII];

  private final float[] nowAt = new float[MAX + 1];
  private final float[] thenAt = new float[MAX + 1];
  private String current = "";
  private String previous = "";
  private FontStyle measured;
  private float progress = 1.0F;
  private float width;
  private boolean stale = true;
  private boolean plain;

  public void set(String next) {
    if (next.equals(current)) {
      return;
    }
    previous = current;
    current = next;
    progress = previous.isEmpty() ? 1.0F : 0.0F;
    stale = true;
  }

  public void snap(String next) {
    current = next;
    previous = "";
    progress = 1.0F;
    stale = true;
  }

  public String text() {
    return current;
  }

  public boolean rolling() {
    return progress < 1.0F;
  }

  public float width(Canvas c, FontStyle font) {
    if (stale || font != measured) {
      stale = false;
      measured = font;
      plain = !simple(current) || !simple(previous);
      if (plain) {
        width = c.textWidth(font, current);
        progress = 1.0F;
      } else {
        width = prefix(c, font, current, nowAt);
        prefix(c, font, previous, thenAt);
      }
    }
    return width;
  }

  public void draw(
      Canvas c,
      FontStyle font,
      float x,
      float y,
      boolean alignRight,
      int argb,
      float dt,
      boolean still) {
    float span = width(c, font);
    if (still) {
      progress = 1.0F;
    }
    if (progress >= 1.0F) {
      c.text(font, current, x, y, argb);
      return;
    }
    progress = Math.min(1.0F, progress + dt / SECONDS);
    float eased = progress * progress * (3.0F - 2.0F * progress);
    float travel = c.lineHeight(font) * TRAVEL;
    int nowLength = current.length();
    int thenLength = previous.length();
    float thenLeft = alignRight ? x + span - thenAt[thenLength] : x;
    int longest = Math.max(nowLength, thenLength);
    for (int k = 0; k < longest; k++) {
      int nowIndex = alignRight ? nowLength - 1 - k : k;
      int thenIndex = alignRight ? thenLength - 1 - k : k;
      boolean hasNow = nowIndex >= 0 && nowIndex < nowLength;
      boolean hasThen = thenIndex >= 0 && thenIndex < thenLength;
      char now = hasNow ? current.charAt(nowIndex) : 0;
      char then = hasThen ? previous.charAt(thenIndex) : 0;
      if (hasNow && hasThen && now == then) {
        c.text(font, glyph(now), x + nowAt[nowIndex], y, argb);
        continue;
      }
      if (hasThen) {
        c.pushAlpha(1.0F - eased);
        c.text(font, glyph(then), thenLeft + thenAt[thenIndex], y - eased * travel, argb);
        c.popAlpha();
      }
      if (hasNow) {
        c.pushAlpha(eased);
        c.text(font, glyph(now), x + nowAt[nowIndex], y + (1.0F - eased) * travel, argb);
        c.popAlpha();
      }
    }
  }

  private static boolean simple(String text) {
    if (text.length() > MAX) {
      return false;
    }
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) >= ASCII) {
        return false;
      }
    }
    return true;
  }

  private static float prefix(Canvas c, FontStyle font, String text, float[] at) {
    float cursor = 0.0F;
    for (int i = 0; i < text.length(); i++) {
      at[i] = cursor;
      cursor += c.textWidth(font, glyph(text.charAt(i)));
    }
    at[text.length()] = cursor;
    return cursor;
  }

  private static String glyph(char ch) {
    String cached = GLYPHS[ch];
    if (cached == null) {
      cached = String.valueOf(ch);
      GLYPHS[ch] = cached;
    }
    return cached;
  }
}
