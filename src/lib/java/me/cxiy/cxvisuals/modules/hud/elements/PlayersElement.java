package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class PlayersElement extends HudElement {
  private static final int ROWS = HudData.PLAYER_SLOTS * 2;
  private static final int FREE = Integer.MIN_VALUE;
  private static final float ROW = 24.0F;
  private static final float FACE = 16.0F;
  private static final float FACE_RADIUS = 4.5F;
  private static final float FOOT = 7.0F;
  private static final float MIN_WIDTH = 132.0F;
  private static final float GAP = 14.0F;
  private static final float SLIDE = 10.0F;
  private static final float RESPONSE = 14.0F;
  private static final float FADE_IN = 6.0F;
  private static final float FADE_OUT = 8.0F;
  private static final float CLOSE = 8.0F;
  private static final float GONE = 0.01F;
  private static final String CAPTION = Lang.key("NEARBY");
  private static final String EMPTY = Lang.key("No one in sight");
  private static final int FAR = 512;
  private static final String[] METRES = new String[FAR + 1];
  private static final String[] COUNTS = new String[HudData.PLAYER_SLOTS + 1];

  public final NumberSetting limit;
  public final BoolSetting sight;
  public final BoolSetting range;
  public final BoolSetting formatted;
  public final BoolSetting music;

  private final int[] ids = new int[ROWS];
  private final String[] names = new String[ROWS];
  private final String[] tracks = new String[ROWS];
  private final String[] metres = new String[ROWS];
  private final float[] nameWidths = new float[ROWS];
  private final float[] metreWidths = new float[ROWS];
  private final float[] offsets = new float[ROWS];
  private final float[] targets = new float[ROWS];
  private final float[] presence = new float[ROWS];
  private final boolean[] here = new boolean[ROWS];
  private final boolean[] close = new boolean[ROWS];
  private final boolean[] styled = new boolean[ROWS];
  private int listed;
  private float height = HudPaint.HEAD + ROW + FOOT;
  private float width = MIN_WIDTH;
  private float captionWidth = -1.0F;
  private float emptyWidth;
  private boolean stale;

  public PlayersElement(Settings settings) {
    super(settings, "players", "Nearby players", Category.INFO, false, Anchor.RIGHT, -10.0, 120.0);
    this.limit = group().number("limit", "Rows", 6.0, 3.0, HudData.PLAYER_SLOTS, 1.0, "");
    this.sight = group().bool("sight", "Only in sight", true);
    this.range = group().bool("range", "Distance", true);
    this.formatted =
        group()
            .bool("formatted", "Server names", true)
            .describe("Ranks, colours and effects as the server sends them.");
    this.music =
        group()
            .bool("music", "Shared music", true)
            .describe("Show what nearby players are listening to.");
    for (int i = 0; i < ROWS; i++) {
      ids[i] = FREE;
      names[i] = "";
      tracks[i] = "";
      metres[i] = "";
    }
  }

  public int listed() {
    return listed;
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
    if (data.playerCount > 0) {
      return true;
    }
    for (int i = 0; i < ROWS; i++) {
      if (ids[i] != FREE) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void sample(HudData data) {
    for (int i = 0; i < ROWS; i++) {
      here[i] = false;
    }
    int shown = Math.min(data.playerCount, limit.get().intValue());
    boolean songs = music.get();
    for (int order = 0; order < shown; order++) {
      int row = find(data.playerIds[order]);
      if (row < 0) {
        row = find(FREE);
        if (row < 0) {
          continue;
        }
        ids[row] = data.playerIds[order];
        names[row] = data.playerNames[order];
        tracks[row] = "";
        presence[row] = 0.0F;
        offsets[row] = order * ROW;
        metres[row] = "";
        stale = true;
      }
      here[row] = true;
      targets[row] = order * ROW;
      String song =
          songs && order < data.playerTracks.length && data.playerTracks[order] != null
              ? data.playerTracks[order]
              : "";
      if (!song.equals(tracks[row])) {
        tracks[row] = song;
        stale = true;
      }
      float distance = data.playerDistance[order];
      close[row] = distance < CLOSE;
      String text = metres(distance);
      if (!text.equals(metres[row])) {
        metres[row] = text;
        stale = true;
      }
    }
    listed = shown;
  }

  private int find(int id) {
    for (int i = 0; i < ROWS; i++) {
      if (ids[i] == id) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    if (captionWidth < 0.0F || reworded()) {
      captionWidth = c.textWidth(FontStyle.CAPTION, Lang.tr(CAPTION));
      emptyWidth = c.textWidth(FontStyle.SMALL, Lang.tr(EMPTY));
      stale = true;
    }
    if (stale) {
      stale = false;
      float widest = Math.max(MIN_WIDTH, emptyWidth + HudPaint.INSET * 2.0F);
      boolean ranged = range.get();
      for (int i = 0; i < ROWS; i++) {
        if (ids[i] == FREE) {
          continue;
        }
        float server = tracks[i].isEmpty() && formatted.get() ? c.nameWidth(ids[i]) : -1.0F;
        styled[i] = server >= 0.0F;
        nameWidths[i] = styled[i] ? server : c.textWidth(FontStyle.LABEL, shown(i));
        metreWidths[i] = c.textWidth(FontStyle.MONO, metres[i]);
        float row =
            HudPaint.INSET * 2.0F
                + FACE
                + 8.0F
                + nameWidths[i]
                + (ranged ? GAP + metreWidths[i] : 0.0F);
        widest = Math.max(widest, row);
      }
      width = (float) Math.ceil(widest);
    }
    w = width;
    h = height;
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    boolean still = data.still;
    float blend = still ? 1.0F : 1.0F - (float) Math.exp(-dt * RESPONSE);
    float wanted = HudPaint.HEAD + Math.max(1, listed) * ROW + FOOT;
    height += (wanted - height) * blend;
    if (Math.abs(wanted - height) < 0.05F) {
      height = wanted;
    }
    h = height;

    HudPaint.plate(c, t, this, x, y, w, h, radius());
    HudPaint.header(c, t, x, y, w, Lang.tr(CAPTION), count(listed));

    boolean ranged = range.get();
    boolean any = false;
    float labelTop = HudPaint.centred(c, FontStyle.LABEL, 0.0F, ROW);
    float monoTop = HudPaint.centred(c, FontStyle.MONO, 0.0F, ROW);
    float floor = h - HudPaint.HEAD - FOOT;
    for (int i = 0; i < ROWS; i++) {
      if (ids[i] == FREE) {
        continue;
      }
      float amount =
          still ? (here[i] ? 1.0F : 0.0F) : presence[i] + (here[i] ? dt * FADE_IN : -dt * FADE_OUT);
      amount = amount < 0.0F ? 0.0F : Math.min(amount, 1.0F);
      presence[i] = amount;
      if (!here[i] && amount <= GONE) {
        ids[i] = FREE;
        stale = true;
        continue;
      }
      offsets[i] += (targets[i] - offsets[i]) * blend;
      any = true;
      float room = Math.max(0.0F, Math.min(1.0F, (floor - offsets[i]) / ROW));
      float eased = amount * amount * (3.0F - 2.0F * amount) * room;
      if (eased <= GONE) {
        continue;
      }
      float rowTop = y + HudPaint.HEAD + offsets[i];
      float rowLeft = x + HudPaint.INSET + (1.0F - eased) * SLIDE;
      float faceTop = rowTop + (ROW - FACE) * 0.5F;
      c.pushAlpha(eased);
      if (!c.face(ids[i], rowLeft, faceTop, FACE, FACE_RADIUS)) {
        c.roundRect(rowLeft, faceTop, FACE, FACE, FACE_RADIUS, t.surface2());
      }
      c.roundRectBorder(
          rowLeft, faceTop, FACE, FACE, FACE_RADIUS, 1.0F, close[i] ? t.red() : t.hair());
      if (!styled[i] || !c.name(ids[i], rowLeft + FACE + 8.0F, rowTop, ROW)) {
        c.text(FontStyle.LABEL, shown(i), rowLeft + FACE + 8.0F, rowTop + labelTop, t.ink());
      }
      if (ranged) {
        c.text(
            FontStyle.MONO,
            metres[i],
            x + w - HudPaint.INSET - metreWidths[i],
            rowTop + monoTop,
            t.grey());
      }
      c.popAlpha();
    }
    if (!any) {
      c.text(
          FontStyle.SMALL,
          Lang.tr(EMPTY),
          x + HudPaint.INSET,
          y + HudPaint.HEAD + HudPaint.centred(c, FontStyle.SMALL, 0.0F, ROW),
          t.grey());
    }
  }

  private String shown(int row) {
    return tracks[row].isEmpty() ? names[row] : names[row] + " ♪ " + tracks[row];
  }

  private static String metres(float distance) {
    int whole = Math.max(0, Math.min(FAR, Math.round(distance)));
    String cached = METRES[whole];
    if (cached == null) {
      cached = whole + " m";
      METRES[whole] = cached;
    }
    return cached;
  }

  private static String count(int amount) {
    String cached = COUNTS[amount];
    if (cached == null) {
      cached = Integer.toString(amount);
      COUNTS[amount] = cached;
    }
    return cached;
  }
}
