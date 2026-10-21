package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.Detail;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;
import me.cxiy.cxvisuals.modules.hud.media.MediaCommand;
import me.cxiy.cxvisuals.modules.hud.media.MediaGlyphs;
import me.cxiy.cxvisuals.modules.hud.media.MediaSnapshot;

public final class MediaElement extends HudElement {
  private static final float HEIGHT = 58.0F;
  private static final float COVER = 36.0F;
  private static final float COVER_RADIUS = 9.0F;
  private static final float NOTE = 16.0F;
  private static final float GAP = 10.0F;
  private static final float TRACK = 2.0F;
  private static final float GLYPH = 16.0F;
  private static final float GLYPH_GAP = 10.0F;
  private static final float GLYPH_TOP = 10.0F;
  private static final float TITLE_TOP = 9.0F;
  private static final float SUBTITLE_TOP = 25.0F;
  private static final float PROGRESS_Y = 46.0F;
  private static final float LOOSE_SHIFT = 7.0F;
  private static final float HIT_TOP = 8.0F;
  private static final float HIT_BOTTOM = 28.0F;
  private static final float SEEK_TOP = 38.0F;
  private static final float SEEK_BOTTOM = 54.0F;
  private static final float SCROLL_SPEED = 22.0F;
  private static final float SCROLL_HOLD = 1.6F;
  private static final float CROSSFADE = 0.25F;
  private static final float PULSE = 1.6F;
  private static final float PULSE_FLOOR = 0.55F;
  private static final float RESPONSE = 14.0F;
  private static final float SETTLED = 0.01F;
  private static final String IDLE = Lang.key("Nothing playing");
  private static final String JOIN = " · ";
  private static final MediaGlyphs.Glyph[] ORDER = {
    MediaGlyphs.Glyph.PREVIOUS, MediaGlyphs.Glyph.PLAY, MediaGlyphs.Glyph.NEXT
  };
  private static final MediaCommand[] COMMANDS = {
    MediaCommand.PREVIOUS, MediaCommand.PLAY_PAUSE, MediaCommand.NEXT
  };

  public final NumberSetting width;
  public final EnumSetting<Detail> detail;
  public final BoolSetting paused;

  private final byte[][] glyphs = new byte[MediaGlyphs.Glyph.values().length][];
  private int glyphInk;
  private int noteInk;
  private byte[] note;
  private String title = "";
  private String subtitle = "";
  private String oldTitle = "";
  private String oldSubtitle = "";
  private String artist = "";
  private String app = "";
  private String time = "";
  private boolean full;
  private boolean stale = true;
  private int key = Integer.MIN_VALUE;
  private long second = Long.MIN_VALUE;
  private long duration;
  private float titleWidth;
  private float subtitleWidth;
  private float timeWidth;
  private float swap = 1.0F;
  private float scroll;
  private float overflow;
  private float pulse;
  private float controls;
  private float coverFade = 1.0F;
  private byte[] cover;
  private float trackLeft;
  private float trackRight;
  private boolean seekable;
  private long seekTarget;

  public MediaElement(Settings settings) {
    super(
        settings, "media", "Now playing", Category.INFO, false, Anchor.BOTTOM_RIGHT, -10.0, -10.0);
    this.width = group().number("width", "Width", 240.0, 180.0, 360.0, 10.0, "");
    this.detail = detail();
    this.paused = group().bool("paused", "Hide when paused", false);
  }

  public boolean scrolling() {
    return overflow > 0.0F;
  }

  public long seekTarget() {
    return seekTarget;
  }

  public MediaCommand hit(float localX, float localY) {
    if (controls < 0.5F) {
      return null;
    }
    float shift = seekable ? 0.0F : LOOSE_SHIFT;
    if (localY >= HIT_TOP + shift && localY <= HIT_BOTTOM + shift) {
      float reach = (GLYPH + GLYPH_GAP) * 0.5F;
      for (int i = 0; i < COMMANDS.length; i++) {
        if (Math.abs(localX - glyphCentre(i)) <= reach) {
          return COMMANDS[i];
        }
      }
      return null;
    }
    if (seekable
        && localY >= SEEK_TOP
        && localY <= SEEK_BOTTOM
        && localX >= trackLeft
        && localX <= trackRight) {
      seekTarget = (long) ((localX - trackLeft) / (trackRight - trackLeft) * duration);
      return MediaCommand.SEEK;
    }
    return null;
  }

  private float glyphCentre(int index) {
    return w - HudPaint.INSET - GLYPH * 0.5F - (COMMANDS.length - 1 - index) * (GLYPH + GLYPH_GAP);
  }

  @Override
  public float radius() {
    return 18.0F;
  }

  @Override
  public boolean present(HudData data) {
    MediaSnapshot media = data.media;
    return media.present() && !(paused.get() && !media.playing());
  }

  private void refresh(HudData data) {
    MediaSnapshot media = data.media;
    boolean wide = detail.get() == Detail.FULL;
    String nextTitle =
        !media.present() ? Lang.tr(IDLE) : media.title().isEmpty() ? media.artist() : media.title();
    String nextArtist = media.title().isEmpty() ? "" : media.artist();
    boolean renamed = !nextTitle.equals(title);
    if (renamed || wide != full || !nextArtist.equals(artist) || !media.app().equals(app)) {
      if (renamed) {
        oldTitle = title;
        oldSubtitle = subtitle;
        swap = title.isEmpty() ? 1.0F : 0.0F;
        scroll = 0.0F;
        title = nextTitle;
      }
      full = wide;
      artist = nextArtist;
      app = media.app();
      String source = full ? source(app) : "";
      subtitle = artist.isEmpty() ? source : source.isEmpty() ? artist : artist + JOIN + source;
      second = Long.MIN_VALUE;
      stale = true;
    }
    duration = media.durationMs();
    long now = full && duration > 0L ? media.livePositionMs(data.mediaNanos) / 1000L : -1L;
    if (now != second) {
      second = now;
      time = now < 0L ? "" : clock(now) + " / " + clock(duration / 1000L);
      stale = true;
    }
    if (data.cover != cover) {
      cover = data.cover;
      coverFade = cover == null || data.still ? 1.0F : 0.0F;
    }
  }

  private static String source(String app) {
    int bang = app.lastIndexOf('!');
    String name = bang >= 0 ? app.substring(bang + 1) : app;
    if (name.toLowerCase().endsWith(".exe")) {
      name = name.substring(0, name.length() - 4);
    }
    return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private static String clock(long seconds) {
    long minutes = seconds / 60L;
    long rest = seconds % 60L;
    return minutes + (rest < 10L ? ":0" : ":") + rest;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    refresh(data);
    if (stale || c.styleKey() != key) {
      stale = false;
      key = c.styleKey();
      titleWidth = c.textWidth(FontStyle.LABEL, title);
      subtitleWidth = c.textWidth(FontStyle.SMALL, subtitle);
      timeWidth = time.isEmpty() ? 0.0F : c.textWidth(FontStyle.MONO, time);
    }
    w = width.get().floatValue();
    h = HEIGHT;
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    boolean still = data.still;
    MediaSnapshot media = data.media;
    float follow = still ? 1.0F : 1.0F - (float) Math.exp(-dt * RESPONSE);
    controls += ((data.pointer ? 1.0F : 0.0F) - controls) * follow;
    if (Math.abs((data.pointer ? 1.0F : 0.0F) - controls) < SETTLED) {
      controls = data.pointer ? 1.0F : 0.0F;
    }
    swap = still ? 1.0F : Math.min(1.0F, swap + dt / CROSSFADE);
    coverFade = still ? 1.0F : Math.min(1.0F, coverFade + dt / CROSSFADE);

    HudPaint.plate(c, t, this, x, y, w, h, radius());

    float coverLeft = x + HudPaint.INSET;
    float coverTop = y + (HEIGHT - COVER) * 0.5F;
    if (cover == null || coverFade < 1.0F) {
      c.roundRect(coverLeft, coverTop, COVER, COVER, COVER_RADIUS, t.surface2());
      if (note == null || noteInk != t.grey()) {
        noteInk = t.grey();
        note = MediaGlyphs.png(MediaGlyphs.Glyph.NOTE, noteInk);
      }
      c.icon(note, coverLeft + (COVER - NOTE) * 0.5F, coverTop + (COVER - NOTE) * 0.5F, NOTE, 0.0F);
    }
    c.pushAlpha(coverFade);
    c.icon(cover, coverLeft, coverTop, COVER, COVER_RADIUS);
    c.popAlpha();
    c.roundRectBorder(coverLeft, coverTop, COVER, COVER, COVER_RADIUS, 1.0F, t.hair());

    float textLeft = coverLeft + COVER + GAP;
    float right = x + w - HudPaint.INSET;
    float room = right - textLeft;
    seekable = duration > 0L;
    float shift = seekable ? 0.0F : LOOSE_SHIFT;
    float block = (COMMANDS.length * GLYPH + (COMMANDS.length - 1) * GLYPH_GAP + GAP) * controls;
    float titleRoom = Math.max(0.0F, room - block);
    overflow = Math.max(0.0F, titleWidth - titleRoom);

    float offset = 0.0F;
    if (overflow > 0.0F && !still) {
      scroll += dt;
      float travel = overflow / SCROLL_SPEED;
      float lap = SCROLL_HOLD + travel;
      float at = scroll % (lap * 2.0F);
      float forward = at < lap ? at : lap * 2.0F - at;
      offset = Math.max(0.0F, Math.min(overflow, (forward - SCROLL_HOLD * 0.5F) * SCROLL_SPEED));
    }
    c.pushClip(textLeft, y, titleRoom, HEIGHT);
    if (swap < 1.0F) {
      c.pushAlpha(1.0F - swap);
      c.text(FontStyle.LABEL, oldTitle, textLeft, y + TITLE_TOP + shift, t.ink());
      c.popAlpha();
    }
    c.pushAlpha(swap);
    c.text(FontStyle.LABEL, title, textLeft - offset, y + TITLE_TOP + shift, t.ink());
    c.popAlpha();
    c.popClip();

    boolean tight = subtitleWidth > room;
    if (tight) {
      c.pushClip(textLeft, y, room, HEIGHT);
    }
    if (swap < 1.0F && !oldSubtitle.isEmpty()) {
      c.pushAlpha(1.0F - swap);
      c.text(FontStyle.SMALL, oldSubtitle, textLeft, y + SUBTITLE_TOP + shift, t.grey());
      c.popAlpha();
    }
    if (!subtitle.isEmpty()) {
      c.pushAlpha(swap);
      c.text(FontStyle.SMALL, subtitle, textLeft, y + SUBTITLE_TOP + shift, t.grey());
      c.popAlpha();
    }
    if (tight) {
      c.popClip();
    }

    if (controls > 0.0F) {
      if (glyphs[0] == null || glyphInk != t.ink()) {
        glyphInk = t.ink();
        for (MediaGlyphs.Glyph glyph : MediaGlyphs.Glyph.values()) {
          glyphs[glyph.ordinal()] = MediaGlyphs.png(glyph, glyphInk);
        }
      }
      c.pushAlpha(controls);
      for (int i = 0; i < ORDER.length; i++) {
        MediaGlyphs.Glyph glyph = i == 1 && media.playing() ? MediaGlyphs.Glyph.PAUSE : ORDER[i];
        c.icon(
            glyphs[glyph.ordinal()],
            x + glyphCentre(i) - GLYPH * 0.5F,
            y + GLYPH_TOP + shift,
            GLYPH,
            0.0F);
      }
      c.popAlpha();
    }

    if (!seekable) {
      return;
    }
    float lineLeft = textLeft;
    float lineRight = time.isEmpty() ? right : right - timeWidth - GAP;
    trackLeft = lineLeft - x;
    trackRight = lineRight - x;
    float line = lineRight - lineLeft;
    float lineTop = y + PROGRESS_Y - TRACK * 0.5F;
    float filled = line * media.progress(data.mediaNanos);
    boolean playing = media.playing();
    c.roundRect(lineLeft, lineTop, line, TRACK, TRACK * 0.5F, t.hair());
    c.roundRect(lineLeft, lineTop, filled, TRACK, TRACK * 0.5F, playing ? t.ink() : t.grey());
    float glow = 1.0F;
    if (!playing && !still) {
      pulse += dt;
      glow =
          PULSE_FLOOR
              + (1.0F - PULSE_FLOOR)
                  * (0.5F + 0.5F * (float) Math.sin(pulse * (Math.PI * 2.0) / PULSE));
    }
    c.pushAlpha(glow);
    HudPaint.dot(
        c, lineLeft + filled - HudPaint.DOT * 0.5F, y + PROGRESS_Y - HudPaint.DOT * 0.5F, t.ink());
    c.popAlpha();
    if (!time.isEmpty()) {
      c.text(
          FontStyle.MONO,
          time,
          right - timeWidth,
          y + PROGRESS_Y - c.lineHeight(FontStyle.MONO) * 0.5F,
          t.ink2());
    }
  }
}
