package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;
import me.cxiy.cxvisuals.modules.hud.media.LyricTracker;
import me.cxiy.cxvisuals.modules.hud.media.Lyrics;

public final class LyricsElement extends HudElement {
  public enum Align {
    LEFT,
    CENTRE,
    RIGHT
  }

  private static final float PAD = 8.0F;
  private static final float ROW_HEIGHT = 20.0F;
  private static final float SIDE_HEIGHT = 16.0F;
  private static final float SIDE_ALPHA = 0.45F;
  private static final float SLIDE = 0.25F;
  private static final float SLIDE_RISE = 6.0F;
  private static final float RESPONSE = 16.0F;
  private static final float SETTLED = 0.05F;
  private static final float DOT_GAP = 6.0F;
  private static final int MAX_ROWS = 2;
  private static final int INITIAL = 16;
  private static final String SAMPLE = Lang.key("Lyrics follow the music here");

  public final NumberSetting rows;
  public final NumberSetting width;
  public final EnumSetting<Align> align;
  public final NumberSetting offset;
  public final BoolSetting plate;

  private final LyricTracker tracker = new LyricTracker();
  private final float[] rowWidths = new float[MAX_ROWS];
  private String[] words = new String[INITIAL];
  private float[] lefts = new float[INITIAL];
  private int[] wordRows = new int[INITIAL];
  private int[] owners = new int[INITIAL];
  private float[] opens = new float[INITIAL];
  private float[] closes = new float[INITIAL];
  private Lyrics source;
  private int line = Integer.MIN_VALUE;
  private int placed;
  private int used = 1;
  private int key = Integer.MIN_VALUE;
  private float laid = -1.0F;
  private boolean active;
  private boolean found;
  private String before = "";
  private String after = "";
  private float beforeWidth;
  private float afterWidth;
  private float sampleWidth;
  private float slide = 1.0F;
  private float height = PAD * 2.0F + ROW_HEIGHT;
  private float target = PAD * 2.0F + ROW_HEIGHT;

  public LyricsElement(Settings settings) {
    super(settings, "lyrics", "Lyrics", Category.INFO, false, Anchor.BOTTOM, 0.0, -90.0);
    this.rows = group().number("rows", "Lines", 3.0, 1.0, 3.0, 1.0, "");
    this.width = group().number("width", "Width", 320.0, 160.0, 520.0, 10.0, "");
    this.align = group().enumOf("align", "Alignment", Align.CENTRE, "Left", "Centre", "Right");
    this.offset =
        group()
            .number("offset", "Sync offset", 0.0, -3000.0, 3000.0, 50.0, "ms")
            .describe("Moves the lyrics earlier or later when they do not match the music.");
    this.plate = group().bool("plate", "Background", false);
  }

  public int activeLine() {
    return tracker.line();
  }

  public int activeWord() {
    return tracker.word();
  }

  @Override
  public float radius() {
    return 14.0F;
  }

  @Override
  public boolean elastic() {
    return true;
  }

  @Override
  public boolean present(HudData data) {
    return data.media.playing()
        && data.media.present()
        && data.lyrics.state() == Lyrics.State.FOUND;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    Lyrics lyrics = data.lyrics;
    float total = width.get().floatValue();
    found = present(data);
    long position = data.media.livePositionMs(data.mediaNanos) + offset.get().longValue();
    active = found && tracker.update(lyrics, position, data.media.durationMs());
    if (!found) {
      tracker.reset();
    }
    int current = found ? tracker.line() : Integer.MIN_VALUE;
    if (current != line || lyrics != source || total != laid || c.styleKey() != key) {
      boolean moved = current != line || lyrics != source;
      source = lyrics;
      line = current;
      laid = total;
      key = c.styleKey();
      layout(c, lyrics, total - PAD * 2.0F);
      if (moved) {
        slide = data.still ? 1.0F : 0.0F;
      }
    }
    int sides = rows.get().intValue();
    float wanted =
        PAD * 2.0F
            + used * ROW_HEIGHT
            + (sides >= 2 ? SIDE_HEIGHT : 0.0F)
            + (sides >= 3 ? SIDE_HEIGHT : 0.0F);
    if (appear <= 0.0F || data.still) {
      height = wanted;
    }
    w = total;
    h = height;
    target = wanted;
  }

  private void layout(Canvas c, Lyrics lyrics, float inner) {
    placed = 0;
    used = 1;
    rowWidths[0] = 0.0F;
    rowWidths[1] = 0.0F;
    before = "";
    after = "";
    sampleWidth = c.textWidth(FontStyle.ROW, Lang.tr(SAMPLE));
    if (!found) {
      return;
    }
    for (int i = line - 1; i >= 0; i--) {
      if (!lyrics.line(i).isEmpty()) {
        before = lyrics.line(i);
        break;
      }
    }
    for (int i = line + 1; i < lyrics.size(); i++) {
      if (!lyrics.line(i).isEmpty()) {
        after = lyrics.line(i);
        break;
      }
    }
    beforeWidth = c.textWidth(FontStyle.SMALL, before);
    afterWidth = c.textWidth(FontStyle.SMALL, after);
    if (line < 0) {
      return;
    }
    String text = lyrics.line(line);
    float space = c.textWidth(FontStyle.ROW, " ");
    int row = 0;
    float cursor = 0.0F;
    for (int i = 0; i < tracker.words() && row < MAX_ROWS; i++) {
      int from = tracker.wordStart(i);
      int to = tracker.wordEnd(i);
      int at = from;
      while (at < to && row < MAX_ROWS) {
        float start = cursor == 0.0F ? 0.0F : cursor + space;
        int end = to;
        float span = c.textWidth(FontStyle.ROW, text.substring(at, end));
        if (start + span > inner && cursor > 0.0F) {
          row++;
          cursor = 0.0F;
          continue;
        }
        if (span > inner) {
          end = fit(c, text, at, to, inner);
          span = c.textWidth(FontStyle.ROW, text.substring(at, end));
        }
        if (placed == words.length) {
          words = java.util.Arrays.copyOf(words, placed * 2);
          lefts = java.util.Arrays.copyOf(lefts, placed * 2);
          wordRows = java.util.Arrays.copyOf(wordRows, placed * 2);
          owners = java.util.Arrays.copyOf(owners, placed * 2);
          opens = java.util.Arrays.copyOf(opens, placed * 2);
          closes = java.util.Arrays.copyOf(closes, placed * 2);
        }
        words[placed] = text.substring(at, end);
        lefts[placed] = start;
        wordRows[placed] = row;
        owners[placed] = i;
        opens[placed] = (at - from) / (float) (to - from);
        closes[placed] = (end - from) / (float) (to - from);
        placed++;
        rowWidths[row] = start + span;
        used = row + 1;
        at = end;
        if (at < to) {
          row++;
          cursor = 0.0F;
        } else {
          cursor = start + span;
        }
      }
    }
  }

  private static int fit(Canvas c, String text, int from, int to, float room) {
    int end = from + Character.charCount(text.codePointAt(from));
    while (end < to) {
      int next = end + Character.charCount(text.codePointAt(end));
      if (c.textWidth(FontStyle.ROW, text.substring(from, next)) > room) {
        break;
      }
      end = next;
    }
    return end;
  }

  private float aligned(float left, float inner, float span) {
    return switch (align.get()) {
      case LEFT -> left;
      case CENTRE -> left + (inner - span) * 0.5F;
      case RIGHT -> left + inner - span;
    };
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    boolean still = data.still;
    height += (target - height) * (still ? 1.0F : 1.0F - (float) Math.exp(-dt * RESPONSE));
    if (Math.abs(target - height) < SETTLED) {
      height = target;
    }
    h = height;
    slide = still ? 1.0F : Math.min(1.0F, slide + dt / SLIDE);
    float eased = (float) Easing.snappy(slide);
    float rise = (1.0F - eased) * SLIDE_RISE;
    if (plate.get()) {
      HudPaint.plate(c, t, this, x, y, w, h, radius());
    }
    float left = x + PAD;
    float inner = w - PAD * 2.0F;
    int sides = rows.get().intValue();
    float top = y + PAD + rise;

    if (!found) {
      c.text(
          FontStyle.ROW,
          Lang.tr(SAMPLE),
          aligned(left, inner, Math.min(inner, sampleWidth)),
          HudPaint.centred(c, FontStyle.ROW, y + (h - ROW_HEIGHT) * 0.5F, ROW_HEIGHT),
          t.grey());
      return;
    }
    c.pushClip(x, y, w, h);
    if (sides >= 3) {
      side(c, t, before, beforeWidth, left, inner, top);
      top += SIDE_HEIGHT;
    }
    float labelTop = HudPaint.centred(c, FontStyle.ROW, 0.0F, ROW_HEIGHT);
    if (active && placed > 0) {
      int sung = tracker.word();
      float fill = tracker.wordProgress();
      c.pushAlpha(eased);
      for (int i = 0; i < placed; i++) {
        int row = wordRows[i];
        float wordLeft = aligned(left, inner, rowWidths[row]) + lefts[i];
        float wordTop = top + row * ROW_HEIGHT + labelTop;
        int owner = owners[i];
        c.text(FontStyle.ROW, words[i], wordLeft, wordTop, owner < sung ? t.ink() : t.grey());
        if (owner == sung && fill > opens[i]) {
          c.pushAlpha(Math.min(1.0F, (fill - opens[i]) / (closes[i] - opens[i])));
          c.text(FontStyle.ROW, words[i], wordLeft, wordTop, t.ink());
          c.popAlpha();
        }
      }
      c.popAlpha();
    } else {
      float span = HudPaint.DOT * 3.0F + DOT_GAP * 2.0F;
      float dotLeft = aligned(left, inner, span);
      float dotTop = top + (ROW_HEIGHT - HudPaint.DOT) * 0.5F;
      for (int i = 0; i < 3; i++) {
        HudPaint.dot(c, dotLeft + i * (HudPaint.DOT + DOT_GAP), dotTop, t.dotOff());
      }
    }
    top += used * ROW_HEIGHT;
    if (sides >= 2) {
      side(c, t, after, afterWidth, left, inner, top);
    }
    c.popClip();
  }

  private void side(
      Canvas c, Theme t, String text, float span, float left, float inner, float top) {
    if (text.isEmpty()) {
      return;
    }
    c.pushAlpha(SIDE_ALPHA);
    c.text(
        FontStyle.SMALL,
        text,
        aligned(left, inner, Math.min(inner, span)),
        HudPaint.centred(c, FontStyle.SMALL, top, SIDE_HEIGHT),
        t.ink());
    c.popAlpha();
  }
}
