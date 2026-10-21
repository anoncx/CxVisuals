package me.cxiy.cxvisuals.gui.accounts;

import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.accounts.Entry;
import me.cxiy.cxvisuals.modules.accounts.Faces;
import me.cxiy.cxvisuals.modules.accounts.Hub;
import me.cxiy.cxvisuals.modules.accounts.Ids;
import me.cxiy.cxvisuals.modules.accounts.Kind;
import me.cxiy.cxvisuals.modules.accounts.Links;
import me.cxiy.cxvisuals.modules.accounts.Platform;
import me.cxiy.cxvisuals.modules.accounts.Profiles;

final class DetailPane {
  private static final float TILE_W = 132.0F;
  private static final float TILE_H = 172.0F;
  private static final float GAP = 18.0F;
  private static final float STAT_H = 54.0F;
  private static final float CHIP_H = 22.0F;
  private static final float ROW_GAP = 8.0F;
  private static final long ARM_NANOS = 3_000_000_000L;
  private static final long COPIED_NANOS = 1_600_000_000L;
  private static final int HISTORY_ROWS = 4;

  private final Hub hub;
  private final Runnable used;
  private final Pill use = new Pill(Lang.key("Use account"), Pill.Tone.PRIMARY).minimum(132.0F);
  private final Pill refresh = new Pill(Lang.key("Refresh"), Pill.Tone.QUIET);
  private final Pill pin = new Pill(Lang.key("Pin"), Pill.Tone.QUIET);
  private final Pill link = new Pill(Lang.key("Link server"), Pill.Tone.QUIET);
  private final Pill remove = new Pill(Lang.key("Remove"), Pill.Tone.DANGER);
  private final Pill copy = new Pill(Lang.key("Copy"), Pill.Tone.QUIET).height(CHIP_H);
  private final Pill rejoin = new Pill(Lang.key("Switch and rejoin"), Pill.Tone.PRIMARY);
  private final Pill stay = new Pill(Lang.key("Switch only"), Pill.Tone.QUIET);
  private final Pill back = new Pill(Lang.key("Back"), Pill.Tone.QUIET);
  private Entry entry;
  private boolean confirming;
  private boolean working;
  private long armedAt;
  private long copiedAt;
  private float x;
  private float y;
  private float w;
  private float h;

  DetailPane(Hub hub, Runnable used) {
    this.hub = hub;
    this.used = used;
  }

  boolean confirming() {
    return confirming;
  }

  void cancel() {
    confirming = false;
    armedAt = 0L;
  }

  void bind(Entry next) {
    if (entry == null || next == null || !entry.id.equals(next.id)) {
      cancel();
    }
    entry = next;
  }

  void request(Entry target) {
    entry = target;
    Platform platform = hub.platform();
    boolean onServer = platform.inGame() && !platform.server().isEmpty();
    if (!onServer) {
      begin(Platform.Rejoin.NO);
      return;
    }
    switch (hub.module().rejoin.get()) {
      case ALWAYS -> begin(Platform.Rejoin.YES);
      case NEVER -> begin(Platform.Rejoin.NO);
      case ASK -> confirming = true;
    }
  }

  void remove(Entry target) {
    entry = target;
    removeNow();
  }

  private void begin(Platform.Rejoin mode) {
    confirming = false;
    if (entry == null || working) {
      return;
    }
    working = true;
    hub.use(
        entry,
        mode,
        ok -> {
          working = false;
          if (ok && hub.module().closeOnUse.get()) {
            used.run();
          }
        });
  }

  private void removeNow() {
    if (entry == null) {
      return;
    }
    if (hub.module().confirmRemove.get() && System.nanoTime() - armedAt > ARM_NANOS) {
      armedAt = System.nanoTime();
      return;
    }
    armedAt = 0L;
    hub.remove(entry);
  }

  void draw(
      Canvas c, Theme t, float px, float py, float pw, float ph, float mx, float my, float dt) {
    x = px;
    y = py;
    w = pw;
    h = ph;
    if (entry == null) {
      return;
    }
    if (confirming) {
      drawConfirm(c, t, mx, my, dt);
      return;
    }
    drawHero(c, t, mx, my, dt);
    drawHistory(c, t);
    drawActions(c, t, mx, my, dt);
  }

  private void drawHero(Canvas c, Theme t, float mx, float my, float dt) {
    Depth.inset(c, t, x, y, TILE_W, TILE_H, 20.0F, t.surface2());
    c.pushClip(x, y, TILE_W, TILE_H);
    Portrait.figure(c, t, hub, entry, x + (TILE_W - TILE_H) * 0.5F, y, TILE_H, 20.0F);
    c.popClip();

    float left = x + TILE_W + GAP;
    float room = x + w - left;
    float top = y - 2.0F;
    c.text(FontStyle.DISPLAY, c.fit(FontStyle.DISPLAY, entry.username, room), left, top, t.ink());
    float chipsY = top + c.lineHeight(FontStyle.DISPLAY) + 4.0F;
    float cursor = left;
    cursor += chip(c, t, Lang.tr(entry.kind().label()), cursor, chipsY, false) + 6.0F;
    if (hub.isActive(entry)) {
      cursor += chip(c, t, Lang.tr("Active"), cursor, chipsY, true) + 6.0F;
    }
    if (entry.pinned) {
      chip(c, t, Lang.tr("Pinned"), cursor, chipsY, false);
    }
    float idY = chipsY + CHIP_H + 12.0F;
    c.text(FontStyle.CAPTION, Lang.tr("UUID"), left, idY, t.grey());
    float copyW = copy.width(c);
    float uuidRoom = room - copyW - 10.0F;
    String uuid = Ids.dashed(entry.uuid.isEmpty() ? Ids.offline(entry.username) : entry.uuid);
    c.text(
        FontStyle.MONO,
        c.fit(FontStyle.MONO, uuid, uuidRoom),
        left,
        idY + c.lineHeight(FontStyle.CAPTION) + 2.0F,
        t.ink2());
    boolean copied = System.nanoTime() - copiedAt < COPIED_NANOS;
    copy.label(copied ? Lang.key("Copied") : Lang.key("Copy")).enabled(!copied);
    copy.draw(c, t, left + room - copyW, idY + 2.0F, mx, my, dt);

    float statY = y + TILE_H - STAT_H;
    float statW = (room - 16.0F) / 3.0F;
    stat(c, t, left, statY, statW, Lang.tr("Added"), Words.date(entry.added));
    stat(
        c,
        t,
        left + statW + 8.0F,
        statY,
        statW,
        Lang.tr("Last used"),
        Words.ago(entry.used, System.currentTimeMillis()));
    stat(c, t, left + (statW + 8.0F) * 2.0F, statY, statW, Lang.tr("Servers"), servers());
  }

  private String servers() {
    int count = hub.links().count(entry.id);
    if (count == 0) {
      return Lang.tr("None");
    }
    String first = hub.links().first(entry.id);
    String shown =
        first == null
            ? ""
            : first.endsWith(":" + Links.DEFAULT_PORT)
                ? first.substring(0, first.length() - 6)
                : first;
    return count == 1 ? shown : shown + " +" + (count - 1);
  }

  private float chip(Canvas c, Theme t, String label, float cx, float cy, boolean live) {
    float dot = live ? 14.0F : 0.0F;
    float width = c.textWidth(FontStyle.CAPTION, label) + 20.0F + dot;
    c.roundRect(
        cx,
        cy,
        width,
        CHIP_H,
        CHIP_H * 0.5F,
        live ? Theme.withAlpha(t.red(), 0.16F) : t.surface2());
    if (live) {
      c.roundRect(cx + 10.0F, cy + (CHIP_H - 6.0F) * 0.5F, 6.0F, 6.0F, 3.0F, t.red());
    }
    c.text(
        FontStyle.CAPTION,
        label,
        cx + 10.0F + dot,
        cy + (CHIP_H - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        t.ink());
    return width;
  }

  private void stat(Canvas c, Theme t, float sx, float sy, float sw, String label, String value) {
    Depth.inset(c, t, sx, sy, sw, STAT_H, 14.0F, t.surface2());
    c.text(FontStyle.CAPTION, label, sx + 12.0F, sy + 9.0F, t.grey());
    c.text(
        FontStyle.LABEL,
        c.fit(FontStyle.LABEL, value, sw - 20.0F),
        sx + 12.0F,
        sy + 9.0F + c.lineHeight(FontStyle.CAPTION) + 3.0F,
        t.ink());
  }

  private void drawHistory(Canvas c, Theme t) {
    float top = y + TILE_H + 20.0F;
    c.text(FontStyle.CAPTION, Lang.tr("Name history"), x, top, t.grey());
    float rows = top + c.lineHeight(FontStyle.CAPTION) + 8.0F;
    Faces.Face face = hub.faces().of(entry);
    Profiles.Known known = face.known();
    if (known == null || known.names().isEmpty()) {
      String note =
          !hub.module().lookup.get()
              ? Lang.key("Online lookup is off.")
              : face.loading()
                  ? Lang.key("Looking this account up...")
                  : entry.kind() == Kind.OFFLINE
                      ? Lang.key("Offline accounts have no public history.")
                      : Lang.key("No public history found.");
      c.text(FontStyle.SMALL, Lang.tr(note), x, rows, t.ink2());
      return;
    }
    List<Profiles.Name> names = known.names();
    int shown = Math.min(HISTORY_ROWS, names.size());
    float line = c.lineHeight(FontStyle.LABEL) + 8.0F;
    for (int i = 0; i < shown; i++) {
      Profiles.Name name = names.get(names.size() - 1 - i);
      float ry = rows + i * line;
      c.roundRect(
          x, ry + (line - 8.0F) * 0.5F - 1.0F, 4.0F, 4.0F, 2.0F, i == 0 ? t.red() : t.dotOff());
      c.text(
          FontStyle.LABEL,
          c.fit(FontStyle.LABEL, name.name(), w * 0.5F),
          x + 14.0F,
          ry,
          i == 0 ? t.ink() : t.ink2());
      String when =
          name.changedAt() <= 0L ? Lang.tr("Original name") : Words.date(name.changedAt());
      c.text(
          FontStyle.CAPTION,
          when,
          x + w - c.textWidth(FontStyle.CAPTION, when),
          ry + 2.0F,
          t.grey());
    }
  }

  private void drawActions(Canvas c, Theme t, float mx, float my, float dt) {
    Platform platform = hub.platform();
    String server = platform.inGame() ? platform.server() : "";
    boolean linked = !server.isEmpty() && entry.id.equals(hub.links().get(server));
    use.enabled(!working && !hub.isActive(entry))
        .label(
            working
                ? Lang.key("Switching...")
                : hub.isActive(entry) ? Lang.key("In use") : Lang.key("Use account"));
    refresh.enabled(!working && !hub.busy()).label(Lang.key("Refresh"));
    pin.label(entry.pinned ? Lang.key("Unpin") : Lang.key("Pin"));
    link.label(linked ? Lang.key("Unlink server") : Lang.key("Link server"))
        .enabled(!server.isEmpty());
    remove.label(
        System.nanoTime() - armedAt < ARM_NANOS && armedAt != 0L
            ? Lang.key("Click again to remove")
            : Lang.key("Remove"));

    float line = Pill.HEIGHT;
    Pill[] pills = {use, refresh, pin, link, remove};
    float total = ROW_GAP * (pills.length - 1);
    for (Pill pill : pills) {
      total += pill.width(c);
    }
    boolean wrap = total > w;
    float rowsHeight = wrap ? line * 2.0F + ROW_GAP : line;
    float bottom = y + h;
    float top = bottom - rowsHeight;
    if (!server.isEmpty()) {
      String where =
          Lang.format("On {0}", server)
              + (linked ? "  ·  " + Lang.tr("linked to this account") : "");
      c.text(
          FontStyle.CAPTION,
          c.fit(FontStyle.CAPTION, where, w),
          x,
          top - c.lineHeight(FontStyle.CAPTION) - 8.0F,
          t.grey());
    }
    float cursor = x;
    float row = top;
    for (int i = 0; i < pills.length; i++) {
      Pill pill = pills[i];
      float width = pill.width(c);
      if (wrap && i == 3) {
        cursor = x;
        row = top + line + ROW_GAP;
      }
      if (!wrap && i == pills.length - 1) {
        cursor = x + w - width;
      }
      pill.draw(c, t, cursor, row, width, mx, my, dt);
      cursor += width + ROW_GAP;
    }
  }

  private void drawConfirm(Canvas c, Theme t, float mx, float my, float dt) {
    String server = hub.platform().server();
    float centre = x + w * 0.5F;
    float top = y + h * 0.5F - 96.0F;
    Portrait.head(c, t, hub, entry, centre - 28.0F, top, 56.0F, 16.0F);
    String title = Lang.format("Switch to {0}?", entry.username);
    float titleY = top + 56.0F + 16.0F;
    c.text(
        FontStyle.DISPLAY,
        c.fit(FontStyle.DISPLAY, title, w),
        centre - Math.min(c.textWidth(FontStyle.DISPLAY, title), w) * 0.5F,
        titleY,
        t.ink());
    String body =
        Lang.format(
            "You are on {0}. Switching takes you off it, and you can join straight back in with the new account.",
            server);
    List<String> lines = Words.wrap(c, FontStyle.SMALL, body, Math.min(w, 380.0F));
    float lineHeight = c.lineHeight(FontStyle.SMALL) + 2.0F;
    float bodyY = titleY + c.lineHeight(FontStyle.DISPLAY) + 6.0F;
    for (int i = 0; i < lines.size(); i++) {
      c.text(
          FontStyle.SMALL,
          lines.get(i),
          centre - c.textWidth(FontStyle.SMALL, lines.get(i)) * 0.5F,
          bodyY + i * lineHeight,
          t.ink2());
    }
    float rowY = bodyY + lines.size() * lineHeight + 22.0F;
    float a = rejoin.width(c);
    float b = stay.width(c);
    float d = back.width(c);
    float start = centre - (a + b + d + ROW_GAP * 2.0F) * 0.5F;
    rejoin.draw(c, t, start, rowY, a, mx, my, dt);
    stay.draw(c, t, start + a + ROW_GAP, rowY, b, mx, my, dt);
    back.draw(c, t, start + a + b + ROW_GAP * 2.0F, rowY, d, mx, my, dt);
  }

  boolean mouseDown(float mx, float my) {
    if (entry == null) {
      return false;
    }
    if (confirming) {
      return rejoin.down(mx, my) || stay.down(mx, my) || back.down(mx, my);
    }
    return use.down(mx, my)
        || refresh.down(mx, my)
        || pin.down(mx, my)
        || link.down(mx, my)
        || remove.down(mx, my)
        || copy.down(mx, my);
  }

  void mouseUp(Canvas screen, float mx, float my) {
    if (entry == null) {
      return;
    }
    if (confirming) {
      if (rejoin.up(mx, my)) {
        begin(Platform.Rejoin.YES);
      } else if (stay.up(mx, my)) {
        begin(Platform.Rejoin.NO);
      } else if (back.up(mx, my)) {
        confirming = false;
      }
      return;
    }
    if (use.up(mx, my)) {
      request(entry);
    }
    if (refresh.up(mx, my)) {
      hub.refresh(entry);
    }
    if (pin.up(mx, my)) {
      hub.book().pin(entry.id, !entry.pinned);
    }
    if (link.up(mx, my)) {
      String server = hub.platform().server();
      if (entry.id.equals(hub.links().get(server))) {
        hub.unlink(server);
      } else {
        hub.link(entry, server);
      }
    }
    if (remove.up(mx, my)) {
      removeNow();
    }
    if (copy.up(mx, my)) {
      screen.clipboardText(
          Ids.dashed(entry.uuid.isEmpty() ? Ids.offline(entry.username) : entry.uuid));
      copiedAt = System.nanoTime();
    }
  }
}
