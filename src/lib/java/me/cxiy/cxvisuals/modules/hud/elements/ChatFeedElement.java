package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.gui.spatial.Layer;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.ChatLog;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class ChatFeedElement extends HudElement {
  private static final int ROWS = 128;
  private static final int PARAGRAPH = 12;
  private static final float PAD = 8.0F;
  private static final float FADE = 1.0F;
  private static final float ENTER = 0.18F;
  private static final float SLIDE = 6.0F;
  private static final float RESPONSE = 14.0F;
  private static final float SETTLED = 0.05F;
  private static final float THUMB = 2.0F;
  private static final float THUMB_INSET = 4.0F;
  private static final float THUMB_MIN = 8.0F;
  private static final String EMPTY = Lang.key("No messages yet");

  public final NumberSetting lines;
  public final NumberSetting width;
  public final NumberSetting hold;

  private final String[] rows = new String[ROWS];
  private final boolean[] system = new boolean[ROWS];
  private final float[] born = new float[ROWS];
  private final int[] owner = new int[ROWS];
  private final int[] part = new int[ROWS];
  private final String[] scratch = new String[PARAGRAPH];
  private int first = ROWS;
  private int revision = -1;
  private float wrapped = -1.0F;
  private int key;
  private int visible;
  private int scroll;
  private float grace;
  private float height = PAD * 2.0F + HudPaint.ROW;

  public ChatFeedElement(Settings settings) {
    super(settings, "feed", "Chat feed", Category.INFO, false, Anchor.BOTTOM_LEFT, 10.0, -56.0);
    this.lines = group().number("lines", "Lines", 6.0, 3.0, 10.0, 1.0, "");
    this.width =
        group()
            .number("width", "Width", 280.0, 180.0, 420.0, 10.0, "")
            .describe("Longer messages wrap onto the next line.");
    this.hold =
        group()
            .number("hold", "Visible for", 8.0, 3.0, 30.0, 1.0, "s")
            .describe("How long a message stays before it fades out.");
  }

  @Override
  public float depth() {
    return Layer.HUD_FAR;
  }

  public int rowCount() {
    return visible;
  }

  public String row(int index) {
    return rows[ROWS - scroll - visible + index];
  }

  public void scroll(int amount) {
    scroll += amount;
  }

  public int scrolled() {
    return scroll;
  }

  @Override
  public float radius() {
    return 16.0F;
  }

  @Override
  public boolean elastic() {
    return true;
  }

  @Override
  public boolean present(HudData data) {
    ChatLog log = data.chat;
    return log.size() > 0
        && (data.chatOpen || grace > 0.0F || log.clock() - log.born(log.size() - 1) < span());
  }

  @Override
  public void measure(Canvas c, HudData data) {
    ChatLog log = data.chat;
    float total = width.get().floatValue();
    if (log.revision() != revision || total != wrapped || c.styleKey() != key) {
      wrap(c, log, total);
    }
    float span = span();
    int limit = lines.get().intValue();
    int count = 0;
    if (data.chatOpen) {
      grace = FADE;
    }
    if (grace > 0.0F) {
      int stored = ROWS - first;
      count = Math.min(limit, stored);
      scroll = Math.max(0, Math.min(scroll, stored - count));
    } else {
      scroll = 0;
      for (int i = ROWS - 1; i >= first && count < limit && log.clock() - born[i] < span; i--) {
        count++;
      }
    }
    visible = count;
    w = total;
    h = height;
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    ChatLog log = data.chat;
    log.advance(dt);
    boolean still = data.still;
    float held = grace / FADE;
    if (!data.chatOpen && grace > 0.0F) {
      grace = still ? 0.0F : Math.max(0.0F, grace - dt);
    }
    float wanted = PAD * 2.0F + Math.max(1, visible) * HudPaint.ROW;
    height += (wanted - height) * (still ? 1.0F : 1.0F - (float) Math.exp(-dt * RESPONSE));
    if (Math.abs(wanted - height) < SETTLED) {
      height = wanted;
    }
    h = height;
    HudPaint.plate(c, t, this, x, y, w, h, radius());
    float labelTop = HudPaint.centred(c, FontStyle.SMALL, 0.0F, HudPaint.ROW);
    if (visible == 0) {
      c.text(
          FontStyle.SMALL,
          Lang.tr(EMPTY),
          x + HudPaint.INSET,
          y + h - PAD - HudPaint.ROW + labelTop,
          t.grey());
      return;
    }
    float span = span();
    for (int k = 0; k < visible; k++) {
      int index = ROWS - scroll - visible + k;
      float age = log.clock() - born[index];
      float rowTop = y + h - PAD - (visible - k) * HudPaint.ROW;
      float room = Math.max(0.0F, Math.min(1.0F, (rowTop - y) / HudPaint.ROW));
      float alpha = room;
      float lift = 0.0F;
      if (!still) {
        float entering = Math.min(1.0F, age / ENTER);
        alpha *= Math.max(held, Math.max(0.0F, Math.min(1.0F, (span - age) / FADE)) * entering);
        lift = (1.0F - entering * entering * (3.0F - 2.0F * entering)) * SLIDE;
      }
      if (alpha <= 0.0F) {
        continue;
      }
      c.pushAlpha(alpha);
      int color = system[index] ? t.ink2() : t.ink();
      if (rows[index] == null) {
        c.line(owner[index], part[index], x + HudPaint.INSET, rowTop + lift, HudPaint.ROW, color);
      } else {
        c.text(FontStyle.SMALL, rows[index], x + HudPaint.INSET, rowTop + labelTop + lift, color);
      }
      c.popAlpha();
    }
    int total = ROWS - first;
    if (held > 0.0F && total > visible) {
      float track = h - PAD * 2.0F;
      float thumb = Math.max(THUMB_MIN, track * visible / total);
      float travel = (track - thumb) * (total - visible - scroll) / (total - visible);
      c.pushAlpha(held);
      c.roundRect(
          x + w - THUMB_INSET - THUMB, y + PAD + travel, THUMB, thumb, THUMB * 0.5F, t.grey());
      c.popAlpha();
    }
  }

  private float span() {
    return hold.get().floatValue() + FADE;
  }

  private void wrap(Canvas c, ChatLog log, float total) {
    revision = log.revision();
    wrapped = total;
    key = c.styleKey();
    float limit = total - HudPaint.INSET * 2.0F;
    int slot = ROWS;
    for (int message = log.size() - 1; message >= 0 && slot > 0; message--) {
      int id = log.id(message);
      int styled = c.lines(id, limit);
      int count = styled >= 0 ? styled : split(c, log.text(message), limit);
      float stamp = log.born(message);
      boolean quiet = log.system(message);
      for (int k = count - 1; k >= 0 && slot > 0; k--) {
        slot--;
        rows[slot] = styled >= 0 ? null : scratch[k];
        owner[slot] = id;
        part[slot] = k;
        system[slot] = quiet;
        born[slot] = stamp;
      }
    }
    first = slot;
  }

  private int split(Canvas c, String text, float limit) {
    int count = 0;
    int from = 0;
    int length = text.length();
    while (from < length && count < PARAGRAPH) {
      int newline = text.indexOf('\n', from);
      int end = newline < 0 ? length : newline;
      while (from < end && count < PARAGRAPH) {
        int to = fit(c, text, from, end, limit);
        scratch[count++] = text.substring(from, to);
        from = to;
        while (from < end && text.charAt(from) == ' ') {
          from++;
        }
      }
      from = end + 1;
    }
    return count;
  }

  private int fit(Canvas c, String text, int from, int end, float limit) {
    int good = from;
    int pos = from;
    while (true) {
      int space = text.indexOf(' ', pos);
      int stop = space < 0 || space > end ? end : space;
      if (c.textWidth(FontStyle.SMALL, text.substring(from, stop)) > limit) {
        break;
      }
      good = stop;
      if (stop == end) {
        return end;
      }
      pos = stop + 1;
    }
    if (good > from) {
      return good;
    }
    int to = from + 1;
    while (to < end && c.textWidth(FontStyle.SMALL, text.substring(from, to + 1)) <= limit) {
      to++;
    }
    return to > from + 1 && Character.isHighSurrogate(text.charAt(to - 1)) ? to - 1 : to;
  }
}
