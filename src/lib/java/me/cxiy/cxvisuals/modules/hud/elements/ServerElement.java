package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.StyledText;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.Detail;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class ServerElement extends HudElement {
  private static final String MISSING = "--";
  private static final String[] LABELS = Lang.keys("Players", "World", "Ping", "TPS", "Software");
  private static final int PLAYERS = 0;
  private static final int WORLD = 1;
  private static final int PING = 2;
  private static final int TPS = 3;
  private static final int SOFTWARE = 4;
  private static final int VISIBLE_ROWS = 2;
  private static final float MIN_WIDTH = 176.0F;
  private static final float MAX_WIDTH = 300.0F;
  private static final float ICON = 28.0F;
  private static final float ICON_RADIUS = 8.0F;
  private static final float HEADER = 44.0F;
  private static final float TEXT_GAP = 9.0F;
  private static final float GAP = 14.0F;
  private static final float FOOT = 7.0F;

  public final EnumSetting<Detail> detail = detail();

  private final String[] values = new String[LABELS.length];
  private final float[] valueWidths = new float[LABELS.length];
  private final float[] labelWidths = new float[LABELS.length];
  private String title = "";
  private String subtitle = "";
  private String brand = "";
  private StyledText titleText = StyledText.EMPTY;
  private StyledText subtitleText = StyledText.EMPTY;
  private String initial = MISSING;
  private int online = -1;
  private int capacity = -1;
  private int latency = -1;
  private int tenths = Integer.MIN_VALUE;
  private int signal;
  private boolean stale = true;
  private boolean labelled;
  private float titleWidth;
  private float subtitleWidth;
  private float textRoom;

  public ServerElement(Settings settings) {
    super(settings, "server", "Server info", Category.INFO, false, Anchor.TOP_RIGHT, -10.0, 10.0);
    for (int i = 0; i < values.length; i++) {
      values[i] = "";
    }
  }

  public String value(int row) {
    return values[row];
  }

  @Override
  public float radius() {
    return 18.0F;
  }

  @Override
  public void sample(HudData data) {
    if (!data.serverName.equals(title)) {
      title = data.serverName;
      titleText = StyledText.of(title);
      String name = titleText.plain.strip();
      initial =
          name.isEmpty() ? MISSING : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase();
      stale = true;
    }
    if (!data.serverAddress.equals(subtitle)) {
      subtitle = data.serverAddress;
      subtitleText = StyledText.of(subtitle);
      stale = true;
    }
    set(WORLD, data.dimension);
    if (!data.serverBrand.equals(brand)) {
      brand = data.serverBrand;
      set(SOFTWARE, StyledText.strip(brand));
    }
    if (data.onlinePlayers != online || data.maxPlayers != capacity) {
      online = data.onlinePlayers;
      capacity = data.maxPlayers;
      set(PLAYERS, capacity > 0 ? online + " / " + capacity : Integer.toString(online));
    }
    int ms = Math.round(data.ping);
    if (ms != latency) {
      latency = ms;
      set(PING, ms + " ms");
    }
    int rate = data.tps < 0.0F ? -1 : Math.round(data.tps * 10.0F);
    if (rate != tenths) {
      tenths = rate;
      set(TPS, rate < 0 ? MISSING : rate / 10 + "." + rate % 10);
    }
    signal = HudPaint.signal(data.ping);
  }

  private void set(int row, String value) {
    if (!value.equals(values[row])) {
      values[row] = value;
      stale = true;
    }
  }

  @Override
  public void measure(Canvas c, HudData data) {
    if (!labelled || reworded()) {
      labelled = true;
      for (int i = 0; i < LABELS.length; i++) {
        labelWidths[i] = c.textWidth(FontStyle.CAPTION, Lang.tr(LABELS[i]));
      }
    }
    if (stale) {
      stale = false;
      for (int i = 0; i < LABELS.length; i++) {
        valueWidths[i] = c.textWidth(FontStyle.MONO, values[i]);
      }
      titleWidth = c.styledWidth(FontStyle.LABEL, titleText);
      subtitleWidth = c.styledWidth(FontStyle.SMALL, subtitleText);
    }
    int rows = rows();
    float widest =
        HudPaint.INSET * 2.0F
            + ICON
            + TEXT_GAP
            + Math.max(titleWidth, subtitleWidth)
            + GAP
            + HudPaint.SIGNAL_WIDTH;
    for (int i = 0; i < rows; i++) {
      widest = Math.max(widest, HudPaint.INSET * 2.0F + labelWidths[i] + GAP + valueWidths[i]);
    }
    w = (float) Math.ceil(Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, widest)));
    textRoom = w - HudPaint.INSET * 2.0F - ICON - TEXT_GAP - GAP - HudPaint.SIGNAL_WIDTH;
    h = HEADER + rows * HudPaint.ROW + FOOT;
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    HudPaint.plate(c, t, this, x, y, w, h, radius());
    float iconLeft = x + HudPaint.INSET;
    float iconTop = y + (HEADER - ICON) * 0.5F;
    if (!c.icon(data.serverIcon, iconLeft, iconTop, ICON, ICON_RADIUS)) {
      c.roundRect(iconLeft, iconTop, ICON, ICON, ICON_RADIUS, t.surface2());
      c.text(
          FontStyle.ROW,
          initial,
          iconLeft + (ICON - c.textWidth(FontStyle.ROW, initial)) * 0.5F,
          HudPaint.centred(c, FontStyle.ROW, iconTop, ICON),
          t.ink2());
    }
    c.roundRectBorder(iconLeft, iconTop, ICON, ICON, ICON_RADIUS, 1.0F, t.hair());
    boolean weak = signal <= 2;
    HudPaint.bars(
        c,
        x + w - HudPaint.INSET - HudPaint.SIGNAL_WIDTH,
        y + (HEADER - HudPaint.SIGNAL_HEIGHT) * 0.5F,
        signal,
        weak ? t.red() : t.ink(),
        t.dotOff());

    float textLeft = iconLeft + ICON + TEXT_GAP;
    boolean clipped = Math.max(titleWidth, subtitleWidth) > textRoom;
    if (clipped) {
      c.pushClip(textLeft, y, textRoom, HEADER);
    }
    boolean lines = subtitleText.count > 0;
    float titleTop = lines ? iconTop : HudPaint.centred(c, FontStyle.LABEL, iconTop, ICON);
    c.styled(FontStyle.LABEL, titleText, textLeft, titleTop, t.ink());
    if (lines) {
      c.styled(
          FontStyle.SMALL,
          subtitleText,
          textLeft,
          iconTop + ICON - c.lineHeight(FontStyle.SMALL),
          t.grey());
    }
    if (clipped) {
      c.popClip();
    }
    c.roundRect(
        x + HudPaint.INSET, y + HEADER - 2.0F, w - HudPaint.INSET * 2.0F, 1.0F, 0.0F, t.hair());

    int rows = rows();
    for (int i = 0; i < rows; i++) {
      int color = i == PING && weak ? t.red() : t.ink();
      HudPaint.pair(
          c,
          t,
          x,
          y + HEADER + i * HudPaint.ROW,
          w,
          Lang.tr(LABELS[i]),
          values[i],
          valueWidths[i],
          color);
    }
  }

  private int rows() {
    return detail.get() == Detail.FULL ? LABELS.length : VISIBLE_ROWS;
  }
}
