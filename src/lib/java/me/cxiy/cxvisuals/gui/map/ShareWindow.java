package me.cxiy.cxvisuals.gui.map;

import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.map.share.LocationMode;

public final class ShareWindow {
  private enum Tab {
    INVITE,
    PEOPLE,
    LOCATION
  }

  public static final float WIDTH = 304.0F;
  private static final float PAD = 16.0F;
  private static final float ROW = 42.0F;
  private static final float BUTTON = SheetDraw.BUTTON;
  private static final float HEADER = 80.0F;
  private static final float RADIUS = 16.0F;
  private static final float CHECK = 18.0F;
  private static final float CHIP = 26.0F;
  private static final int MAX_ROWS = 6;
  private static final int OPAQUE = SheetDraw.OPAQUE;
  private static final int ONLINE = 0xFF30D158;

  private final ShareHost host;
  private final MapChrome.Frost frost;
  private final Runnable close;
  private final Zones zones = new Zones();
  private final Anim thumb = Anim.pop(0.0F);
  private ShareModel model;
  private LocationModel location;
  private Tab tab = Tab.INVITE;
  private boolean leaveArmed;
  private String removing = "";
  private boolean sent;
  private int scroll;
  private float left;
  private float top;
  private float height;

  public ShareWindow(ShareHost host, MapChrome.Frost frost, Runnable close) {
    this.host = host;
    this.frost = frost;
    this.close = close;
    rebuild();
  }

  private void rebuild() {
    String server = host.server().replaceFirst("^server:", "");
    model = new ShareModel(server, host.friends());
    location = new LocationModel(server, host.locationRows(), host::locate);
    scroll = 0;
  }

  public float height(Canvas c) {
    if (!host.available()) {
      return 112.0F;
    }
    if (sent) {
      return 118.0F;
    }
    if (!host.live()) {
      return invite(c, null, 0.0F, 0.0F, 0.0F, 0.0F, false);
    }
    float body =
        switch (tab) {
          case INVITE -> invite(c, null, 0.0F, 0.0F, 0.0F, 0.0F, false);
          case PEOPLE -> people(c, null, 0.0F, 0.0F, 0.0F, 0.0F, false);
          case LOCATION -> locate(c, null, 0.0F, 0.0F, 0.0F, 0.0F, false);
        };
    return HEADER + body;
  }

  public void draw(Canvas c, Theme t, float x, float y, float mx, float my, float dt) {
    left = x;
    top = y;
    height = height(c);
    zones.clear();
    MapChrome.panel(c, t, frost, x, y, WIDTH, height, RADIUS);
    if (!host.available()) {
      notice(
          c,
          t,
          x,
          y,
          Lang.tr("Shared map"),
          Lang.tr("Join a multiplayer server and sign in to CxVisuals to share a map."));
    } else if (sent) {
      notice(
          c,
          t,
          x,
          y,
          Lang.tr("Invitation sent"),
          Lang.tr("Your map will start syncing as soon as they accept."));
      pill(
          c,
          t,
          Lang.tr("Done"),
          x + WIDTH - PAD,
          y + height - PAD - BUTTON,
          true,
          false,
          mx,
          my,
          close);
    } else if (!host.live()) {
      invite(c, t, x, y, mx, my, true);
    } else {
      tabs(c, t, x, y, dt);
      switch (tab) {
        case INVITE -> invite(c, t, x, y + HEADER, mx, my, true);
        case PEOPLE -> people(c, t, x, y + HEADER, mx, my, true);
        case LOCATION -> locate(c, t, x, y + HEADER, mx, my, true);
      }
    }
  }

  public boolean mouseDown(float mx, float my) {
    if (!ShareWindowMath.inside(mx, my, left, top, WIDTH, height)) {
      return false;
    }
    zones.press(mx, my);
    return true;
  }

  public void scroll(float delta) {
    int spare = Math.max(0, model.candidates().size() - MAX_ROWS);
    scroll = Math.max(0, Math.min(spare, scroll - (int) Math.signum(delta)));
  }

  public boolean back() {
    if (location.pending() != null) {
      location.cancel();
      return true;
    }
    if (model.step() == ShareModel.Step.REVIEW) {
      model.back();
      return true;
    }
    return false;
  }

  private void notice(Canvas c, Theme t, float x, float y, String title, String body) {
    c.text(FontStyle.ROW, title, x + PAD, y + PAD, t.ink());
    paragraph(c, FontStyle.LABEL, body, x + PAD, y + PAD + 30.0F, WIDTH - PAD * 2.0F, t.grey());
  }

  private void tabs(Canvas c, Theme t, float x, float y, float dt) {
    String name = host.roomName().isEmpty() ? Lang.tr("Shared map") : host.roomName();
    c.text(
        FontStyle.ROW, c.fit(FontStyle.ROW, name, WIDTH - PAD * 2.0F), x + PAD, y + PAD, t.ink());
    Tab[] order = Tab.values();
    String[] labels = {Lang.tr("Map"), Lang.tr("People"), Lang.tr("Location")};
    float segment = (WIDTH - PAD * 2.0F) / labels.length;
    float rail = y + 42.0F - 4.0F;
    Depth.inset(c, t, x + PAD, rail, segment * labels.length, 30.0F, 15.0F, t.surface2());
    thumb.set(tab.ordinal());
    thumb.update(dt);
    c.roundRect(
        x + PAD + 2.0F + thumb.get() * segment,
        rail + 2.0F,
        segment - 4.0F,
        26.0F,
        13.0F,
        t.raised());
    for (int i = 0; i < labels.length; i++) {
      Tab target = order[i];
      float labelWidth = c.textWidth(FontStyle.CAPTION, labels[i]);
      c.text(
          FontStyle.CAPTION,
          labels[i],
          x + PAD + i * segment + (segment - labelWidth) * 0.5F,
          rail + (30.0F - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          tab == target ? t.ink() : t.grey());
      zones.add(
          x + PAD + i * segment,
          rail,
          segment,
          30.0F,
          () -> {
            tab = target;
            leaveArmed = false;
            removing = "";
            rebuild();
          });
    }
  }

  private float invite(Canvas c, Theme t, float x, float y, float mx, float my, boolean paint) {
    float room = WIDTH - PAD * 2.0F;
    float cursor = y + PAD;
    if (model.step() == ShareModel.Step.REVIEW) {
      ShareModel.Warning warning = model.warning();
      cursor = sheet(c, t, x, cursor, warning.title(), warning.lines(), paint);
      if (paint) {
        float edge = x + WIDTH - PAD;
        float action =
            pill(c, t, warning.action(), edge, cursor, true, false, mx, my, this::confirmSend);
        pill(
            c, t, Lang.tr("Back"), edge - action - 8.0F, cursor, false, false, mx, my, model::back);
      }
      return cursor + BUTTON + PAD - y;
    }
    if (paint) {
      c.text(
          FontStyle.ROW,
          host.live() ? Lang.tr("Add friends") : Lang.tr("Share this map"),
          x + PAD,
          cursor,
          t.ink());
      if (!host.live()) {
        c.text(
            FontStyle.CAPTION,
            c.fit(FontStyle.CAPTION, model.server(), room),
            x + PAD,
            cursor + 22.0F,
            t.grey());
      }
    }
    cursor += host.live() ? 32.0F : 46.0F;
    List<ShareModel.Friend> people = model.candidates();
    if (people.isEmpty()) {
      if (paint) {
        String why =
            host.friends().isEmpty()
                ? Lang.tr("Add friends to share a map with them.")
                : Lang.tr("Everyone you know is already in this map.");
        paragraph(c, FontStyle.LABEL, why, x + PAD, cursor, room, t.ink2());
      }
      return cursor + 44.0F + PAD - y;
    }
    int shown = Math.min(MAX_ROWS, people.size());
    for (int i = 0; paint && i < shown; i++) {
      friendRow(
          c,
          t,
          people.get(i + Math.min(scroll, people.size() - shown)),
          x,
          cursor + i * ROW,
          mx,
          my);
    }
    cursor += shown * ROW + 4.0F;
    if (paint) {
      c.roundRect(x + PAD, cursor, room, 1.0F, 0.0F, t.hair());
      backfillRow(c, t, x, cursor + 6.0F);
    }
    cursor += 52.0F;
    if (paint) {
      pill(
          c,
          t,
          Lang.tr("Continue"),
          x + WIDTH - PAD,
          cursor,
          true,
          !model.canContinue(),
          mx,
          my,
          model::review);
    }
    return cursor + BUTTON + PAD - y;
  }

  private float sheet(
      Canvas c, Theme t, float x, float y, String title, List<String> lines, boolean paint) {
    float room = WIDTH - PAD * 2.0F;
    float cursor = y;
    if (paint) {
      paragraph(c, FontStyle.ROW, title, x + PAD, cursor, room, t.ink());
    }
    cursor += paragraphHeight(c, FontStyle.ROW, title, room) + 10.0F;
    for (String line : lines) {
      if (paint) {
        c.roundRect(x + PAD, cursor + 6.0F, 4.0F, 4.0F, 2.0F, t.grey());
        paragraph(c, FontStyle.LABEL, line, x + PAD + 14.0F, cursor, room - 14.0F, t.ink2());
      }
      cursor += paragraphHeight(c, FontStyle.LABEL, line, room - 14.0F) + 6.0F;
    }
    return cursor + 10.0F;
  }

  private void friendRow(
      Canvas c, Theme t, ShareModel.Friend friend, float x, float y, float mx, float my) {
    if (ShareWindowMath.inside(mx, my, x + 6.0F, y, WIDTH - 12.0F, ROW)) {
      c.roundRect(x + 6.0F, y, WIDTH - 12.0F, ROW, 10.0F, Theme.withAlpha(t.ink(), 0.07F));
    }
    MapMarks.friend(
        c, t, friend.entityId(), friend.name(), x + PAD + 14.0F, y + ROW * 0.5F, 24.0F, 1.0F);
    c.text(
        FontStyle.LABEL,
        c.fit(FontStyle.LABEL, friend.name(), WIDTH - 112.0F),
        x + PAD + 36.0F,
        y + (ROW - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.ink());
    float boxX = x + WIDTH - PAD - CHECK;
    c.roundRect(
        boxX - 22.0F,
        y + ROW * 0.5F - 3.0F,
        6.0F,
        6.0F,
        3.0F,
        friend.online() ? ONLINE : t.dotOff());
    check(c, t, boxX, y + (ROW - CHECK) * 0.5F, model.isSelected(friend.uuid()));
    zones.add(x + 6.0F, y, WIDTH - 12.0F, ROW, () -> model.toggle(friend.uuid()));
  }

  private void check(Canvas c, Theme t, float x, float y, boolean on) {
    if (!on) {
      c.roundRectBorder(x, y, CHECK, CHECK, CHECK * 0.5F, 1.4F, t.ink2());
      return;
    }
    c.roundRect(x, y, CHECK, CHECK, CHECK * 0.5F, t.ink());
    float cx = x + CHECK * 0.5F;
    float cy = y + CHECK * 0.5F;
    float[] xs = {cx - 4.0F, cx - 1.0F, cx + 4.5F};
    float[] ys = {cy, cy + 3.2F, cy - 3.2F};
    c.stroke(xs, ys, 3, 1.8F, 0.4F, t.paper() | OPAQUE);
  }

  private void backfillRow(Canvas c, Theme t, float x, float y) {
    String label = Lang.tr("Include areas I've already explored");
    float switchW = 38.0F;
    float switchH = 22.0F;
    c.text(
        FontStyle.LABEL,
        c.fit(FontStyle.LABEL, label, WIDTH - PAD * 2.0F - switchW - 14.0F),
        x + PAD,
        y + (40.0F - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.ink());
    float switchX = x + WIDTH - PAD - switchW;
    float switchY = y + (40.0F - switchH) * 0.5F;
    boolean on = model.backfill();
    float knob = switchH - 6.0F;
    c.roundRect(switchX, switchY, switchW, switchH, switchH * 0.5F, on ? t.ink() : t.surface2());
    c.roundRect(
        switchX + 3.0F + (on ? switchW - knob - 6.0F : 0.0F),
        switchY + 3.0F,
        knob,
        knob,
        knob * 0.5F,
        on ? t.paper() | OPAQUE : t.ink2());
    zones.add(x + 6.0F, y, WIDTH - 12.0F, 40.0F, () -> model.backfill(!model.backfill()));
  }

  private void confirmSend() {
    List<String> chosen = model.selected();
    boolean everything = model.backfill();
    if (model.send()) {
      host.share(chosen, everything);
      sent = true;
    }
  }

  private float locate(Canvas c, Theme t, float x, float y, float mx, float my, boolean paint) {
    LocationModel.Pending pending = location.pending();
    if (pending != null) {
      float cursor = sheet(c, t, x, y + PAD * 0.5F, pending.title(), pending.lines(), paint);
      if (paint) {
        float edge = x + WIDTH - PAD;
        float action =
            pill(c, t, pending.action(), edge, cursor, true, false, mx, my, location::confirm);
        pill(
            c,
            t,
            Lang.tr("Cancel"),
            edge - action - 8.0F,
            cursor,
            false,
            false,
            mx,
            my,
            location::cancel);
      }
      return cursor + BUTTON + PAD - y;
    }
    List<LocationModel.Row> rows = location.rows();
    int shown = Math.min(MAX_ROWS, rows.size());
    float cursor = y + 6.0F;
    if (paint && rows.isEmpty()) {
      String hint = Lang.tr("Invite someone to share your location with them.");
      paragraph(c, FontStyle.LABEL, hint, x + PAD, cursor + 6.0F, WIDTH - PAD * 2.0F, t.ink2());
    }
    for (int i = 0; paint && i < shown; i++) {
      locationRow(c, t, rows.get(i), x, cursor + i * ROW, mx, my);
    }
    return cursor + Math.max(1, shown) * ROW + PAD - y;
  }

  private float people(Canvas c, Theme t, float x, float y, float mx, float my, boolean paint) {
    List<ShareModel.Friend> members = new ArrayList<>();
    for (ShareModel.Friend friend : host.friends()) {
      if (friend.member()) {
        members.add(friend);
      }
    }
    int shown = Math.min(MAX_ROWS, members.size());
    float cursor = y + 6.0F;
    if (paint && members.isEmpty()) {
      String hint = Lang.tr("Nobody else is in this map yet.");
      paragraph(c, FontStyle.LABEL, hint, x + PAD, cursor + 6.0F, WIDTH - PAD * 2.0F, t.ink2());
    }
    for (int i = 0; paint && i < shown; i++) {
      personRow(c, t, members.get(i), x, cursor + i * ROW, mx, my);
    }
    float footer = cursor + Math.max(1, shown) * ROW + 12.0F;
    if (paint) {
      c.roundRect(x + PAD, footer - 6.0F, WIDTH - PAD * 2.0F, 1.0F, 0.0F, t.hair());
      leaveRow(c, t, x, footer);
    }
    return footer + 28.0F + PAD - y;
  }

  private void personRow(
      Canvas c, Theme t, ShareModel.Friend friend, float x, float y, float mx, float my) {
    MapMarks.friend(
        c, t, friend.entityId(), friend.name(), x + PAD + 14.0F, y + ROW * 0.5F, 24.0F, 1.0F);
    boolean owner = host.owner();
    c.text(
        FontStyle.LABEL,
        c.fit(FontStyle.LABEL, friend.name(), WIDTH - (owner ? 164.0F : 96.0F)),
        x + PAD + 36.0F,
        y + (ROW - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.ink());
    if (!owner) {
      return;
    }
    boolean armed = friend.uuid().equals(removing);
    String label = armed ? Lang.tr("Tap to confirm") : Lang.tr("Remove");
    float chipW = c.textWidth(FontStyle.CAPTION, label) + 22.0F;
    float chipX = x + WIDTH - PAD - chipW;
    float chipY = y + (ROW - CHIP) * 0.5F;
    boolean over = ShareWindowMath.inside(mx, my, chipX, chipY, chipW, CHIP);
    c.roundRect(
        chipX,
        chipY,
        chipW,
        CHIP,
        CHIP * 0.5F,
        armed ? t.red() : Theme.withAlpha(t.ink(), over ? 0.14F : 0.07F));
    c.text(
        FontStyle.CAPTION,
        label,
        chipX + 11.0F,
        chipY + (CHIP - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        armed ? OPAQUE | 0xFFFFFF : t.ink2());
    zones.add(
        chipX,
        chipY,
        chipW,
        CHIP,
        () -> {
          if (armed) {
            host.remove(friend.uuid());
            removing = "";
          } else {
            removing = friend.uuid();
          }
        });
  }

  private void locationRow(
      Canvas c, Theme t, LocationModel.Row row, float x, float y, float mx, float my) {
    MapMarks.friend(c, t, row.entityId(), row.name(), x + PAD + 14.0F, y + ROW * 0.5F, 24.0F, 1.0F);
    c.text(
        FontStyle.LABEL,
        c.fit(FontStyle.LABEL, row.name(), WIDTH - 164.0F),
        x + PAD + 36.0F,
        y + (ROW - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.ink());
    String label = LocationModel.label(row.mode());
    float chipW = c.textWidth(FontStyle.CAPTION, label) + 22.0F;
    float chipX = x + WIDTH - PAD - chipW;
    float chipY = y + (ROW - CHIP) * 0.5F;
    boolean on = row.mode() != LocationMode.OFF;
    boolean over = ShareWindowMath.inside(mx, my, chipX, chipY, chipW, CHIP);
    c.roundRect(
        chipX,
        chipY,
        chipW,
        CHIP,
        CHIP * 0.5F,
        on ? t.ink() : Theme.withAlpha(t.ink(), over ? 0.14F : 0.07F));
    c.text(
        FontStyle.CAPTION,
        label,
        chipX + 11.0F,
        chipY + (CHIP - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        on ? t.paper() | OPAQUE : t.ink2());
    LocationMode next = LocationModel.next(row.mode());
    zones.add(chipX, chipY, chipW, CHIP, () -> location.choose(row.uuid(), next));
  }

  private void leaveRow(Canvas c, Theme t, float x, float y) {
    String label =
        leaveArmed ? Lang.tr("Tap again to leave this map") : Lang.tr("Leave shared map");
    c.text(
        FontStyle.LABEL,
        label,
        x + PAD,
        y + (28.0F - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        leaveArmed ? t.red() : t.ink2());
    zones.add(
        x + PAD,
        y,
        Math.max(c.textWidth(FontStyle.LABEL, label), 120.0F),
        28.0F,
        () -> {
          if (leaveArmed) {
            host.leave();
            close.run();
          } else {
            leaveArmed = true;
          }
        });
  }

  private float pill(
      Canvas c,
      Theme t,
      String label,
      float right,
      float y,
      boolean primary,
      boolean disabled,
      float mx,
      float my,
      Runnable action) {
    return SheetDraw.pill(c, t, zones, label, right, y, primary, disabled, mx, my, action);
  }

  private static void paragraph(
      Canvas c, FontStyle font, String text, float x, float y, float max, int color) {
    SheetDraw.paragraph(c, font, text, x, y, max, color);
  }

  private static float paragraphHeight(Canvas c, FontStyle font, String text, float max) {
    return SheetDraw.paragraphHeight(c, font, text, max);
  }
}
