package me.cxiy.cxvisuals.gui.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.Field;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Sound;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.WorldPins;
import me.cxiy.cxvisuals.gui.map.MapChrome.Glyph;
import me.cxiy.cxvisuals.gui.spatial.Spring;
import me.cxiy.cxvisuals.modules.friends.Note;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.map.MapShare;
import me.cxiy.cxvisuals.modules.map.MapSource;
import me.cxiy.cxvisuals.modules.map.TileKey;
import me.cxiy.cxvisuals.modules.waypoints.Waypoints;

public final class MapScreenView {
  public static final int KEY_ESCAPE = 256;
  public static final int KEY_EQUAL = 61;
  public static final int KEY_MINUS = 45;
  public static final int KEY_ADD = 334;
  public static final int KEY_SUBTRACT = 333;
  public static final int KEY_RIGHT = 262;
  public static final int KEY_LEFT = 263;
  public static final int KEY_DOWN = 264;
  public static final int KEY_UP = 265;

  private enum Pop {
    NONE,
    MENU,
    NAME,
    PICK,
    MARK
  }

  private enum Row {
    COORDS,
    WAYPOINT,
    FRIEND
  }

  private record Entry(
      Row kind,
      String name,
      String meta,
      double x,
      double z,
      int color,
      int entity,
      Waypoints.Waypoint waypoint,
      boolean located) {}

  private static final float MARGIN = 16.0F;
  private static final float PANEL = 300.0F;
  private static final float RADIUS = 20.0F;
  private static final float ROW = 46.0F;
  private static final float LIST_TOP = 104.0F;
  private static final float MENU_W = 210.0F;
  private static final float MENU_ROW = 34.0F;
  private static final float PICK_W = 230.0F;
  private static final float CARD_H = 58.0F;
  private static final float WAYPOINT = 10.0F;
  private static final float FRIEND = 20.0F;
  private static final float OTHER = 7.0F;
  private static final float ARROW = 10.0F;
  private static final float HOVER_REACH = 10.0F;
  private static final float ZOOM_NOTCH = 0.22F;
  private static final float FRICTION = 7.5F;
  private static final float GRID_FROM = 3.0F;
  private static final float DRAG_SLOP = 4.0F;
  private static final long DOUBLE_NANOS = 320_000_000L;
  private static final float NOTICE_SECONDS = 2.6F;
  private static final float START_SCALE = 2.0F;
  private static final float SCALE_BAR = 110.0F;
  private static final int CHUNK = 16;

  private final MapHost host;
  private final Viewport view = new Viewport();
  private final ChartBatch batch = new ChartBatch();
  private final Anim open = Anim.reveal(0.0F);
  private final Anim panel = Anim.move(1.0F);
  private final Anim popAnim = Anim.pop(0.0F);
  private final Anim[] hovers = new Anim[8];
  private final Spring zoom = Spring.critical(240.0F);
  private final Spring glideX = Spring.critical(140.0F);
  private final Field search = new Field(Lang.key("Search places, friends or x z"), 48);
  private final Field naming = new Field(Lang.key("Waypoint name"), Waypoints.NAME_LIMIT);
  private final List<Entry> entries = new ArrayList<>(96);
  private final double[] otherX = new double[32];
  private final double[] otherZ = new double[32];

  private float screenW = 1280.0F;
  private float screenH = 720.0F;
  private double camX;
  private double camZ;
  private float zoomGoal = (float) Math.log(START_SCALE);
  private float anchorSX;
  private float anchorSY;
  private double anchorWX;
  private double anchorWZ;
  private boolean anchored;
  private boolean follow = true;
  private boolean gliding;
  private double glideFromX;
  private double glideFromZ;
  private double glideToX;
  private double glideToZ;
  private float velX;
  private float velZ;
  private boolean closing;
  private boolean started;
  private boolean dragging;
  private boolean moved;
  private boolean selecting;
  private boolean selectArmed;
  private boolean hasSelection;
  private float downX;
  private float downY;
  private float lastX;
  private float lastY;
  private long lastNanos;
  private long clickNanos;
  private double selFromX;
  private double selFromZ;
  private double selToX;
  private double selToZ;
  private float mouseX;
  private float mouseY;
  private int tab;
  private float scroll;
  private int hoveredRow = -1;
  private String builtQuery;
  private int builtTab = -1;
  private int builtRevision = -1;
  private long builtAt;
  private Pop pop = Pop.NONE;
  private float popX;
  private float popY;
  private double popWX;
  private double popWZ;
  private boolean pickArea;
  private Waypoints.Waypoint focused;
  private List<Waypoints.Target> picks = List.of();
  private String notice = "";
  private float noticeLeft;
  private int hoveredMark = -1;
  private String copy;

  public MapScreenView(MapHost host) {
    this.host = host;
    for (int i = 0; i < hovers.length; i++) {
      hovers[i] = Anim.hover(0.0F);
    }
  }

  public void open() {
    closing = false;
    open.snap(0.0F);
    open.set(1.0F);
    follow = true;
    started = false;
    Sound.play(Sound.Cue.OPEN);
  }

  public void close() {
    if (!closing) {
      closing = true;
      open.set(0.0F);
      Sound.play(Sound.Cue.CLOSE);
    }
  }

  public boolean closed() {
    return closing && open.get() <= 0.01F;
  }

  public void resize(float width, float height) {
    screenW = width;
    screenH = height;
  }

  private float panelW() {
    return Math.min(PANEL, screenW * 0.34F) * panel.get();
  }

  private float originX() {
    return (screenW - panelW() - MARGIN) * 0.5F;
  }

  private float originY() {
    return screenH * 0.5F;
  }

  private float scale() {
    return (float) Math.exp(zoom.value());
  }

  public void draw(Canvas c, Theme t, float mx, float my, float dt) {
    mouseX = mx;
    mouseY = my;
    HudData data = host.data();
    MapSource source = host.source();
    if (!started) {
      started = true;
      camX = data.x;
      camZ = data.z;
      zoom.snap(zoomGoal);
    }
    open.update(dt);
    panel.update(dt);
    popAnim.update(dt);
    zoom.step(dt, zoomGoal);
    step(data, dt);
    float scale = scale();
    view.set(camX, camZ, scale, 0.0F);
    view.origin(originX(), originY());
    if (anchored) {
      camX = anchorWX - (anchorSX - originX()) / scale;
      camZ = anchorWZ - (anchorSY - originY()) / scale;
      view.set(camX, camZ, scale, 0.0F);
      if (Math.abs(zoom.value() - zoomGoal) < 0.002F) {
        anchored = false;
      }
    }

    float shown = open.get();
    c.pushAlpha(shown);
    c.pushTransform(0.97F + 0.03F * shown, screenW * 0.5F, screenH * 0.5F);
    c.pushClip(0.0F, 0.0F, screenW, screenH);
    batch.build(source.atlas(), view, TileKey.SURFACE, 0.0F, 0.0F, screenW, screenH, 4);
    batch.masked = false;
    batch.roundness = 0.0F;
    batch.clouds = source.clouds() ? 1.0F : 0.0F;
    batch.fog = MapStyle.fog(t);
    batch.alpha = 1.0F;
    if (!c.chart(batch)) {
      c.roundRect(0.0F, 0.0F, screenW, screenH, 0.0F, t.paper() | 0xFF000000);
    }
    grid(c, t, scale);
    selection(c, t);
    marks(c, t, data, source);
    c.popClip();
    c.popTransform();

    vignette(c, t);
    float chromeIn = shown;
    titleCard(c, t, data, chromeIn);
    footer(c, t, chromeIn);
    controls(c, t, chromeIn, dt);
    sidePanel(c, t, data, chromeIn, dt);
    popovers(c, t, dt);
    toast(c, t, dt);
    c.popAlpha();
  }

  private void step(HudData data, float dt) {
    if (follow && !dragging) {
      float ease = Math.min(1.0F, dt * 10.0F);
      camX += (data.x - camX) * ease;
      camZ += (data.z - camZ) * ease;
    } else if (gliding) {
      glideX.step(dt, 1.0F);
      float t = Math.min(1.0F, glideX.value());
      camX = glideFromX + (glideToX - glideFromX) * t;
      camZ = glideFromZ + (glideToZ - glideFromZ) * t;
      if (glideX.settled(1.0F)) {
        gliding = false;
      }
    } else if (!dragging && (Math.abs(velX) > 0.01F || Math.abs(velZ) > 0.01F)) {
      camX += velX * dt;
      camZ += velZ * dt;
      float decay = (float) Math.exp(-FRICTION * dt);
      velX *= decay;
      velZ *= decay;
    }
  }

  private void glide(double x, double z) {
    follow = false;
    gliding = true;
    velX = 0.0F;
    velZ = 0.0F;
    glideFromX = camX;
    glideFromZ = camZ;
    glideToX = x;
    glideToZ = z;
    glideX.snap(0.0F);
  }

  private void zoomAt(float sx, float sy, float notches) {
    float next =
        Math.max(
            (float) Math.log(Viewport.MIN_SCALE),
            Math.min((float) Math.log(Viewport.MAX_SCALE), zoomGoal + notches * ZOOM_NOTCH));
    if (next == zoomGoal) {
      return;
    }
    zoomGoal = next;
    if (!follow) {
      anchored = true;
      anchorSX = sx;
      anchorSY = sy;
      anchorWX = view.worldX(sx, sy);
      anchorWZ = view.worldZ(sx, sy);
    }
  }

  private void grid(Canvas c, Theme t, float scale) {
    if (scale < GRID_FROM) {
      return;
    }
    float fade = Math.min(1.0F, (scale - GRID_FROM) / 2.0F);
    int line = Theme.withAlpha(MapStyle.dark(t) ? 0xFFFFFFFF : 0xFF000000, 0.06F * fade);
    double left = view.worldX(0.0F, 0.0F);
    double top = view.worldZ(0.0F, 0.0F);
    double right = view.worldX(screenW, screenH);
    double bottom = view.worldZ(screenW, screenH);
    for (long x = (long) Math.floor(left / CHUNK) * CHUNK; x <= right; x += CHUNK) {
      c.roundRect(view.screenX(x, 0.0), 0.0F, 1.0F, screenH, 0.0F, line);
    }
    for (long z = (long) Math.floor(top / CHUNK) * CHUNK; z <= bottom; z += CHUNK) {
      c.roundRect(0.0F, view.screenY(0.0, z), screenW, 1.0F, 0.0F, line);
    }
  }

  private void selection(Canvas c, Theme t) {
    if (!selecting && !hasSelection) {
      return;
    }
    int[] box = chunks();
    float x0 = view.screenX(box[0] * CHUNK, box[1] * CHUNK);
    float y0 = view.screenY(box[0] * CHUNK, box[1] * CHUNK);
    float x1 = view.screenX((box[2] + 1) * CHUNK, (box[3] + 1) * CHUNK);
    float y1 = view.screenY((box[2] + 1) * CHUNK, (box[3] + 1) * CHUNK);
    c.roundRect(x0, y0, x1 - x0, y1 - y0, 6.0F, Theme.withAlpha(t.red(), 0.14F));
    c.roundRectBorder(x0, y0, x1 - x0, y1 - y0, 6.0F, 1.5F, t.red());
    String size = (box[2] - box[0] + 1) + " × " + (box[3] - box[1] + 1);
    MapMarks.label(c, t, size + " " + Lang.tr("chunks"), (x0 + x1) * 0.5F, y1 + 8.0F, 1.0F);
  }

  private int[] chunks() {
    int ax = (int) Math.floor(Math.min(selFromX, selToX)) >> 4;
    int az = (int) Math.floor(Math.min(selFromZ, selToZ)) >> 4;
    int bx = (int) Math.floor(Math.max(selFromX, selToX)) >> 4;
    int bz = (int) Math.floor(Math.max(selFromZ, selToZ)) >> 4;
    return new int[] {ax, az, bx, bz};
  }

  private void marks(Canvas c, Theme t, HudData data, MapSource source) {
    hoveredMark = -1;
    float best = HOVER_REACH;
    if (pop == Pop.NONE && !dragging) {
      for (int i = 0; i < data.markCount; i++) {
        if (!wanted(data, source, i)) {
          continue;
        }
        float d =
            (float)
                Math.hypot(
                    view.screenX(data.markX[i], data.markZ[i]) - mouseX,
                    view.screenY(data.markX[i], data.markZ[i]) - mouseY);
        if (d < best) {
          best = d;
          hoveredMark = i;
        }
      }
    }
    int others = source.players(otherX, otherZ, otherX.length);
    for (int i = 0; i < others; i++) {
      MapMarks.other(
          c, t, view.screenX(otherX[i], otherZ[i]), view.screenY(otherX[i], otherZ[i]), OTHER);
    }
    for (int i = 0; i < data.markCount; i++) {
      if (!wanted(data, source, i) || data.markFriend[i]) {
        continue;
      }
      float sx = view.screenX(data.markX[i], data.markZ[i]);
      float sy = view.screenY(data.markX[i], data.markZ[i]);
      float size = WAYPOINT * (i == hoveredMark ? 1.25F : 1.0F);
      if (data.markColor[i] == Waypoints.DEATH_COLOR) {
        MapMarks.death(c, sx, sy, size + 1.0F, data.markColor[i], 1.0F);
      } else {
        MapMarks.waypoint(c, sx, sy, size, data.markColor[i], 1.0F);
      }
    }
    for (int i = 0; i < data.markCount; i++) {
      if (!wanted(data, source, i) || !data.markFriend[i]) {
        continue;
      }
      float sx = view.screenX(data.markX[i], data.markZ[i]);
      float sy = view.screenY(data.markX[i], data.markZ[i]);
      MapMarks.friend(c, t, data.markEntity[i], data.markLabel[i], sx, sy, FRIEND, 1.0F);
      MapMarks.label(c, t, data.markLabel[i], sx, sy + FRIEND * 0.5F + 5.0F, 0.92F);
    }
    float px = view.screenX(data.x, data.z);
    float py = view.screenY(data.x, data.z);
    MapMarks.player(c, px, py, ARROW, (float) Math.toRadians(data.yaw + 180.0F), t.red());
    if (hoveredMark >= 0 && !data.markFriend[hoveredMark]) {
      int metres =
          (int)
              Math.round(
                  Math.hypot(data.markX[hoveredMark] - data.x, data.markZ[hoveredMark] - data.z));
      String text = data.markLabel[hoveredMark] + " · " + WorldPins.range(metres);
      MapMarks.label(
          c,
          t,
          text,
          view.screenX(data.markX[hoveredMark], data.markZ[hoveredMark]),
          view.screenY(data.markX[hoveredMark], data.markZ[hoveredMark]) + WAYPOINT + 4.0F,
          1.0F);
    }
  }

  private static boolean wanted(HudData data, MapSource source, int i) {
    return data.markFriend[i] ? source.friends() : source.waypoints();
  }

  private void vignette(Canvas c, Theme t) {
    int edge = Theme.withAlpha(t.paper() | 0xFF000000, 0.42F);
    int clear = Theme.withAlpha(t.paper() | 0xFF000000, 0.0F);
    c.roundRectGradient(0.0F, 0.0F, screenW, 96.0F, 0.0F, edge, clear);
    c.roundRectGradient(0.0F, screenH - 80.0F, screenW, 80.0F, 0.0F, clear, edge);
  }

  private void titleCard(Canvas c, Theme t, HudData data, float shown) {
    String title = host.title();
    String dimension = Note.dimensionName(host.dimension());
    float width =
        Math.max(
                c.textWidth(FontStyle.ROW, title),
                c.textWidth(FontStyle.CAPTION, dimension) + 18.0F + 70.0F)
            + 32.0F;
    width = Math.min(width, Math.max(200.0F, screenW - panelW() - MARGIN * 4.0F - 200.0F));
    float x = MARGIN;
    float y = MARGIN - (1.0F - shown) * 12.0F;
    MapChrome.panel(c, t, x, y, width, CARD_H, RADIUS * 0.8F);
    c.text(
        FontStyle.ROW, c.fit(FontStyle.ROW, title, width - 32.0F), x + 16.0F, y + 11.0F, t.ink());
    float chipY = y + 33.0F;
    c.roundRect(x + 16.0F, chipY + 4.0F, 7.0F, 7.0F, 3.5F, Note.dimensionColor(host.dimension()));
    c.text(FontStyle.CAPTION, dimension, x + 29.0F, chipY + 1.0F, t.ink2());
    if (host.source().layer() == TileKey.CAVE) {
      float cx = x + 29.0F + c.textWidth(FontStyle.CAPTION, dimension) + 10.0F;
      c.text(FontStyle.CAPTION, "· " + Lang.tr("Cave view on minimap"), cx, chipY + 1.0F, t.grey());
    }
  }

  private void footer(Canvas c, Theme t, float shown) {
    double wx = view.worldX(mouseX, mouseY);
    double wz = view.worldZ(mouseX, mouseY);
    int bx = (int) Math.floor(wx);
    int bz = (int) Math.floor(wz);
    int height = host.height(bx, bz);
    String place =
        height == Integer.MIN_VALUE
            ? "X " + bx + "   Z " + bz
            : "X " + bx + "   Y " + height + "   Z " + bz;
    float scale = scale();
    float metres = SCALE_BAR / scale;
    float nice = nice(metres);
    float bar = nice * scale;
    String barLabel = WorldPins.range(Math.round(nice));
    float textW = c.textWidth(FontStyle.MONO, place);
    float width = textW + bar + c.textWidth(FontStyle.CAPTION, barLabel) + 64.0F;
    float h = 34.0F;
    float x = MARGIN;
    float y = screenH - MARGIN - h + (1.0F - shown) * 12.0F;
    MapChrome.panel(c, t, x, y, width, h, h * 0.5F);
    c.text(
        FontStyle.MONO, place, x + 16.0F, y + (h - c.lineHeight(FontStyle.MONO)) * 0.5F, t.ink());
    float barX = x + 16.0F + textW + 18.0F;
    float barY = y + h * 0.5F;
    c.roundRect(barX, barY - 0.75F, bar, 1.5F, 0.75F, t.ink2());
    c.roundRect(barX, barY - 4.0F, 1.5F, 8.0F, 0.75F, t.ink2());
    c.roundRect(barX + bar - 1.5F, barY - 4.0F, 1.5F, 8.0F, 0.75F, t.ink2());
    c.text(
        FontStyle.CAPTION,
        barLabel,
        barX + bar + 8.0F,
        y + (h - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        t.grey());
  }

  private static float nice(float value) {
    float power = (float) Math.pow(10.0, Math.floor(Math.log10(Math.max(1.0F, value))));
    float unit = value / power;
    float step = unit >= 5.0F ? 5.0F : unit >= 2.0F ? 2.0F : 1.0F;
    return step * power;
  }

  private float controlsX() {
    return screenW
        - panelW()
        - (panel.get() > 0.01F ? MARGIN * 2.0F : MARGIN)
        - MapChrome.BUTTON
        - 6.0F;
  }

  private void controls(Canvas c, Theme t, float shown, float dt) {
    float size = MapChrome.BUTTON;
    float x = controlsX();
    float h = size * 3.0F + 12.0F;
    float y = screenH - MARGIN - h + (1.0F - shown) * 12.0F;
    MapChrome.panel(c, t, x, y, size + 6.0F, h, (size + 6.0F) * 0.5F);
    Glyph[] glyphs = {Glyph.PLUS, Glyph.MINUS, Glyph.LOCATE};
    for (int i = 0; i < glyphs.length; i++) {
      float by = y + 3.0F + i * (size + 3.0F);
      boolean over = MapChrome.hit(mouseX, mouseY, x + 3.0F, by, size, size) && pop == Pop.NONE;
      hovers[i].set(over ? 1.0F : 0.0F);
      hovers[i].update(dt);
      boolean lit = i == 2 && follow;
      MapChrome.button(
          c, t, glyphs[i], x + 3.0F, by, size, Math.max(hovers[i].get(), lit ? 1.0F : 0.0F), false);
      if (lit) {
        c.roundRect(x + 3.0F + size * 0.5F - 2.0F, by + size - 7.0F, 4.0F, 4.0F, 2.0F, t.red());
      }
    }
    float sy = MARGIN - (1.0F - shown) * 12.0F;
    float sx = x;
    MapChrome.panel(c, t, sx, sy, size + 6.0F, size + 6.0F, (size + 6.0F) * 0.5F);
    boolean over = MapChrome.hit(mouseX, mouseY, sx + 3.0F, sy + 3.0F, size, size);
    hovers[3].set(over ? 1.0F : 0.0F);
    hovers[3].update(dt);
    MapChrome.button(c, t, Glyph.SIDEBAR, sx + 3.0F, sy + 3.0F, size, hovers[3].get(), false);
  }

  private boolean controlsDown(float mx, float my) {
    float size = MapChrome.BUTTON;
    float x = controlsX();
    float h = size * 3.0F + 12.0F;
    float y = screenH - MARGIN - h;
    for (int i = 0; i < 3; i++) {
      float by = y + 3.0F + i * (size + 3.0F);
      if (MapChrome.hit(mx, my, x + 3.0F, by, size, size)) {
        switch (i) {
          case 0 -> zoomAt(originX(), originY(), 3.0F);
          case 1 -> zoomAt(originX(), originY(), -3.0F);
          default -> {
            follow = true;
            gliding = false;
            velX = 0.0F;
            velZ = 0.0F;
          }
        }
        Sound.play(Sound.Cue.STEP);
        return true;
      }
    }
    if (MapChrome.hit(mx, my, x, MARGIN, size + 6.0F, size + 6.0F)) {
      panel.set(panel.target() > 0.5F ? 0.0F : 1.0F);
      Sound.play(Sound.Cue.TAB);
      return true;
    }
    return MapChrome.hit(mx, my, x, y, size + 6.0F, h);
  }

  private float panelX() {
    return screenW - MARGIN - Math.min(PANEL, screenW * 0.34F) + (1.0F - panel.get()) * 40.0F;
  }

  private void sidePanel(Canvas c, Theme t, HudData data, float shown, float dt) {
    float amount = panel.get() * shown;
    if (amount <= 0.01F) {
      return;
    }
    float w = Math.min(PANEL, screenW * 0.34F);
    float x = panelX();
    float y = MARGIN;
    float h = screenH - MARGIN * 2.0F;
    c.pushAlpha(amount);
    MapChrome.panel(c, t, x, y, w, h, RADIUS);
    search.draw(c, t, x + 14.0F, y + 14.0F, w - 28.0F, dt);
    String[] tabs = {Lang.tr("Places"), Lang.tr("Friends")};
    float tabW = (w - 28.0F) / 2.0F;
    float tabY = y + 54.0F;
    Depth.inset(c, t, x + 14.0F, tabY, w - 28.0F, 30.0F, 15.0F, t.surface2());
    float knob = x + 16.0F + tab * tabW;
    c.roundRect(knob, tabY + 2.0F, tabW - 4.0F, 26.0F, 13.0F, t.raised());
    for (int i = 0; i < tabs.length; i++) {
      float tw = c.textWidth(FontStyle.CAPTION, tabs[i]);
      c.text(
          FontStyle.CAPTION,
          tabs[i],
          x + 14.0F + i * tabW + (tabW - tw) * 0.5F,
          tabY + (30.0F - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          i == tab ? t.ink() : t.grey());
    }
    rebuild(data);
    float listTop = y + LIST_TOP;
    float listH = h - LIST_TOP - 14.0F;
    float content = entries.size() * ROW;
    scroll = Math.max(0.0F, Math.min(scroll, Math.max(0.0F, content - listH)));
    c.pushClip(x, listTop, w, listH);
    hoveredRow = -1;
    for (int i = 0; i < entries.size(); i++) {
      float ry = listTop + i * ROW - scroll;
      if (ry + ROW < listTop || ry > listTop + listH) {
        continue;
      }
      boolean over =
          pop == Pop.NONE
              && MapChrome.hit(mouseX, mouseY, x + 8.0F, ry, w - 16.0F, ROW)
              && mouseY >= listTop
              && mouseY < listTop + listH;
      if (over) {
        hoveredRow = i;
        c.roundRect(
            x + 8.0F, ry + 2.0F, w - 16.0F, ROW - 4.0F, 12.0F, Theme.withAlpha(t.ink(), 0.06F));
      }
      row(c, t, entries.get(i), x + 8.0F, ry, w - 16.0F, over);
    }
    if (entries.isEmpty()) {
      String empty =
          tab == 0
              ? Lang.tr("No waypoints in this dimension yet. Right-click the map to add one.")
              : Lang.tr("No friends are online.");
      wrap(c, t, empty, x + 22.0F, listTop + 12.0F, w - 44.0F);
    }
    c.popClip();
    String hint = Lang.tr("Shift-drag to share or save an area");
    c.text(
        FontStyle.SMALL,
        hint,
        x + (w - c.textWidth(FontStyle.SMALL, hint)) * 0.5F,
        y + h - 24.0F,
        t.grey());
    c.popAlpha();
  }

  private void wrap(Canvas c, Theme t, String text, float x, float y, float width) {
    String[] words = text.split(" ");
    StringBuilder line = new StringBuilder();
    float lineY = y;
    for (String word : words) {
      String next = line.isEmpty() ? word : line + " " + word;
      if (c.textWidth(FontStyle.SMALL, next) > width && !line.isEmpty()) {
        c.text(FontStyle.SMALL, line.toString(), x, lineY, t.grey());
        lineY += c.lineHeight(FontStyle.SMALL) + 3.0F;
        line.setLength(0);
        line.append(word);
      } else {
        line.setLength(0);
        line.append(next);
      }
    }
    if (!line.isEmpty()) {
      c.text(FontStyle.SMALL, line.toString(), x, lineY, t.grey());
    }
  }

  private void row(Canvas c, Theme t, Entry entry, float x, float y, float w, boolean over) {
    float iconX = x + 22.0F;
    float iconY = y + ROW * 0.5F;
    switch (entry.kind) {
      case COORDS -> MapChrome.glyph(c, Glyph.PIN, iconX, iconY, 7.0F, t.ink());
      case WAYPOINT -> {
        if (entry.color == Waypoints.DEATH_COLOR) {
          MapMarks.death(c, iconX, iconY, 11.0F, entry.color, 1.0F);
        } else {
          MapMarks.waypoint(c, iconX, iconY, 10.0F, entry.color, 1.0F);
        }
      }
      case FRIEND -> {
        MapMarks.friend(
            c, t, entry.entity, entry.name, iconX, iconY, 22.0F, entry.located ? 1.0F : 0.45F);
      }
    }
    float textX = x + 44.0F;
    float actions = over && entry.kind == Row.WAYPOINT ? 64.0F : 0.0F;
    float room = w - 44.0F - 12.0F - actions;
    c.text(FontStyle.LABEL, c.fit(FontStyle.LABEL, entry.name, room), textX, y + 7.0F, t.ink());
    c.text(FontStyle.MONO, c.fit(FontStyle.MONO, entry.meta, room), textX, y + 25.0F, t.grey());
    if (actions > 0.0F) {
      float bx = x + w - 64.0F;
      float by = y + (ROW - 30.0F) * 0.5F;
      MapChrome.button(
          c,
          t,
          Glyph.SHARE,
          bx,
          by,
          30.0F,
          MapChrome.hit(mouseX, mouseY, bx, by, 30.0F, 30.0F) ? 1.0F : 0.0F,
          false);
      MapChrome.button(
          c,
          t,
          Glyph.TRASH,
          bx + 32.0F,
          by,
          30.0F,
          MapChrome.hit(mouseX, mouseY, bx + 32.0F, by, 30.0F, 30.0F) ? 1.0F : 0.0F,
          true);
    }
  }

  private void rebuild(HudData data) {
    String query = search.text().strip().toLowerCase(Locale.ROOT);
    Waypoints waypoints = host.waypoints();
    long now = System.nanoTime();
    if (query.equals(builtQuery)
        && tab == builtTab
        && waypoints.revision() == builtRevision
        && now - builtAt < 500_000_000L) {
      return;
    }
    builtQuery = query;
    builtTab = tab;
    builtRevision = waypoints.revision();
    builtAt = now;
    entries.clear();
    double[] typed = coordinates(query);
    if (typed != null) {
      entries.add(
          new Entry(
              Row.COORDS,
              Lang.format("Go to {0}, {1}", (long) typed[0], (long) typed[1]),
              Lang.tr("Coordinates"),
              typed[0],
              typed[1],
              0,
              -1,
              null,
              true));
    }
    if (tab == 0) {
      for (Waypoints.Waypoint waypoint : waypoints.list()) {
        if (!waypoint.dimension().equals(host.dimension())
            || (!query.isEmpty()
                && typed == null
                && !waypoint.name().toLowerCase(Locale.ROOT).contains(query))) {
          continue;
        }
        int metres =
            (int) Math.round(Math.hypot(waypoint.x() + 0.5 - data.x, waypoint.z() + 0.5 - data.z));
        String meta = WorldPins.range(metres) + "   " + waypoint.place();
        entries.add(
            new Entry(
                Row.WAYPOINT,
                waypoint.name(),
                meta,
                waypoint.x() + 0.5,
                waypoint.z() + 0.5,
                waypoint.color(),
                -1,
                waypoint,
                true));
      }
      return;
    }
    List<String> seen = new ArrayList<>();
    for (int i = 0; i < data.markCount; i++) {
      if (!data.markFriend[i]) {
        continue;
      }
      String name = data.markLabel[i];
      if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)) {
        continue;
      }
      seen.add(name);
      int metres = (int) Math.round(Math.hypot(data.markX[i] - data.x, data.markZ[i] - data.z));
      entries.add(
          new Entry(
              Row.FRIEND,
              name,
              Lang.tr("Here") + " · " + WorldPins.range(metres),
              data.markX[i],
              data.markZ[i],
              0,
              data.markEntity[i],
              null,
              true));
    }
    for (Waypoints.Target friend : host.friends()) {
      if (seen.contains(friend.name())
          || (!query.isEmpty() && !friend.name().toLowerCase(Locale.ROOT).contains(query))) {
        continue;
      }
      entries.add(
          new Entry(
              Row.FRIEND,
              friend.name(),
              friend.online() ? Lang.tr("Online") : Lang.tr("Offline"),
              0.0,
              0.0,
              0,
              friend.entityId(),
              null,
              false));
    }
  }

  static double[] coordinates(String query) {
    String[] parts = query.replace(',', ' ').trim().split("\\s+");
    if (parts.length != 2 && parts.length != 3) {
      return null;
    }
    try {
      double x = Double.parseDouble(parts[0]);
      double z = Double.parseDouble(parts[parts.length - 1]);
      return Math.abs(x) <= Note.WORLD_EDGE && Math.abs(z) <= Note.WORLD_EDGE
          ? new double[] {x, z}
          : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void popovers(Canvas c, Theme t, float dt) {
    if (pop == Pop.NONE) {
      return;
    }
    float amount = popAnim.get();
    c.pushAlpha(Math.min(1.0F, amount * 1.4F));
    c.pushTransform(0.92F + 0.08F * amount, popX, popY);
    switch (pop) {
      case MENU -> menu(c, t);
      case NAME -> name(c, t, dt);
      case PICK -> pick(c, t);
      case MARK -> mark(c, t);
      default -> {}
    }
    c.popTransform();
    c.popAlpha();
  }

  private String[] menuItems() {
    return new String[] {
      Lang.tr("Add waypoint here"), Lang.tr("Share an area from here"), Lang.tr("Copy coordinates")
    };
  }

  private float[] popBox(float w, float h) {
    float x = Math.max(MARGIN, Math.min(popX, screenW - MARGIN - w));
    float y = Math.max(MARGIN, Math.min(popY, screenH - MARGIN - h));
    return new float[] {x, y};
  }

  private void menu(Canvas c, Theme t) {
    String[] items = menuItems();
    float h = items.length * MENU_ROW + 12.0F;
    float[] at = popBox(MENU_W, h);
    MapChrome.panel(c, t, at[0], at[1], MENU_W, h, 14.0F);
    String head = (long) Math.floor(popWX) + "  " + (long) Math.floor(popWZ);
    for (int i = 0; i < items.length; i++) {
      float ry = at[1] + 6.0F + i * MENU_ROW;
      if (MapChrome.hit(mouseX, mouseY, at[0] + 6.0F, ry, MENU_W - 12.0F, MENU_ROW)) {
        c.roundRect(
            at[0] + 6.0F, ry, MENU_W - 12.0F, MENU_ROW, 9.0F, Theme.withAlpha(t.ink(), 0.07F));
      }
      c.text(
          FontStyle.LABEL,
          items[i],
          at[0] + 16.0F,
          ry + (MENU_ROW - c.lineHeight(FontStyle.LABEL)) * 0.5F,
          t.ink());
    }
    c.text(
        FontStyle.MONO,
        head,
        at[0] + MENU_W - 14.0F - c.textWidth(FontStyle.MONO, head),
        at[1] + 6.0F + (MENU_ROW - c.lineHeight(FontStyle.MONO)) * 0.5F,
        t.grey());
  }

  private void name(Canvas c, Theme t, float dt) {
    float w = 260.0F;
    float h = 96.0F;
    float[] at = popBox(w, h);
    MapChrome.panel(c, t, at[0], at[1], w, h, 16.0F);
    c.text(FontStyle.CAPTION, Lang.tr("New waypoint"), at[0] + 16.0F, at[1] + 12.0F, t.grey());
    naming.draw(c, t, at[0] + 12.0F, at[1] + 30.0F, w - 24.0F, dt);
    String hint = Lang.tr("Enter to save");
    c.text(FontStyle.SMALL, hint, at[0] + 16.0F, at[1] + h - 24.0F, t.grey());
    String coords = (long) Math.floor(popWX) + "  " + (long) Math.floor(popWZ);
    c.text(
        FontStyle.MONO,
        coords,
        at[0] + w - 16.0F - c.textWidth(FontStyle.MONO, coords),
        at[1] + h - 24.0F,
        t.grey());
  }

  private int lead() {
    return pickArea ? 1 : 0;
  }

  private float pickHeight() {
    return 40.0F + (Math.min(Math.max(1, picks.size()), 7) + lead()) * MENU_ROW + 8.0F;
  }

  private void pick(Canvas c, Theme t) {
    float h = pickHeight();
    float[] at = popBox(PICK_W, h);
    MapChrome.panel(c, t, at[0], at[1], PICK_W, h, 16.0F);
    float first = at[1] + 8.0F;
    if (pickArea) {
      if (MapChrome.hit(mouseX, mouseY, at[0] + 6.0F, first, PICK_W - 12.0F, MENU_ROW)) {
        c.roundRect(
            at[0] + 6.0F, first, PICK_W - 12.0F, MENU_ROW, 9.0F, Theme.withAlpha(t.ink(), 0.07F));
      }
      MapChrome.glyph(c, Glyph.PIN, at[0] + 26.0F, first + MENU_ROW * 0.5F, 6.0F, t.red());
      c.text(
          FontStyle.LABEL,
          Lang.tr("Save as a world"),
          at[0] + 44.0F,
          first + (MENU_ROW - c.lineHeight(FontStyle.LABEL)) * 0.5F,
          t.ink());
      c.roundRect(at[0] + 16.0F, first + MENU_ROW + 3.0F, PICK_W - 32.0F, 1.0F, 0.0F, t.hair());
    }
    float headY = first + lead() * MENU_ROW + 6.0F;
    String head = pickArea ? Lang.tr("Share this area with") : Lang.tr("Share with");
    c.text(FontStyle.CAPTION, head, at[0] + 16.0F, headY, t.grey());
    float rowsTop = headY + 22.0F;
    if (picks.isEmpty()) {
      c.text(
          FontStyle.LABEL,
          Lang.tr("Add friends to share maps."),
          at[0] + 16.0F,
          rowsTop + 8.0F,
          t.ink2());
      return;
    }
    for (int i = 0; i < picks.size() && i < 7; i++) {
      Waypoints.Target friend = picks.get(i);
      float ry = rowsTop + i * MENU_ROW;
      if (MapChrome.hit(mouseX, mouseY, at[0] + 6.0F, ry, PICK_W - 12.0F, MENU_ROW)) {
        c.roundRect(
            at[0] + 6.0F, ry, PICK_W - 12.0F, MENU_ROW, 9.0F, Theme.withAlpha(t.ink(), 0.07F));
      }
      MapMarks.friend(
          c, t, friend.entityId(), friend.name(), at[0] + 26.0F, ry + MENU_ROW * 0.5F, 20.0F, 1.0F);
      c.text(
          FontStyle.LABEL,
          c.fit(FontStyle.LABEL, friend.name(), PICK_W - 90.0F),
          at[0] + 44.0F,
          ry + (MENU_ROW - c.lineHeight(FontStyle.LABEL)) * 0.5F,
          t.ink());
      c.roundRect(
          at[0] + PICK_W - 22.0F,
          ry + MENU_ROW * 0.5F - 3.0F,
          6.0F,
          6.0F,
          3.0F,
          friend.online() ? 0xFF30D158 : t.dotOff());
    }
  }

  private void mark(Canvas c, Theme t) {
    if (focused == null) {
      return;
    }
    float w = 240.0F;
    float h = 92.0F;
    float[] at = popBox(w, h);
    MapChrome.panel(c, t, at[0], at[1], w, h, 16.0F);
    MapMarks.waypoint(c, at[0] + 22.0F, at[1] + 24.0F, 10.0F, focused.color(), 1.0F);
    c.text(
        FontStyle.LABEL,
        c.fit(FontStyle.LABEL, focused.name(), w - 60.0F),
        at[0] + 38.0F,
        at[1] + 16.0F,
        t.ink());
    HudData data = host.data();
    int metres =
        (int) Math.round(Math.hypot(focused.x() + 0.5 - data.x, focused.z() + 0.5 - data.z));
    c.text(
        FontStyle.MONO,
        WorldPins.range(metres) + "   " + focused.place(),
        at[0] + 16.0F,
        at[1] + 40.0F,
        t.grey());
    float by = at[1] + h - 34.0F;
    float half = (w - 36.0F) / 2.0F;
    pill(c, t, Lang.tr("Share"), at[0] + 12.0F, by, half, false);
    pill(c, t, Lang.tr("Delete"), at[0] + 24.0F + half, by, half, true);
  }

  private void pill(Canvas c, Theme t, String label, float x, float y, float w, boolean danger) {
    boolean over = MapChrome.hit(mouseX, mouseY, x, y, w, 26.0F);
    c.roundRect(
        x, y, w, 26.0F, 13.0F, Theme.withAlpha(danger ? t.red() : t.ink(), over ? 0.2F : 0.09F));
    float tw = c.textWidth(FontStyle.CAPTION, label);
    c.text(
        FontStyle.CAPTION,
        label,
        x + (w - tw) * 0.5F,
        y + (26.0F - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        danger ? t.red() : t.ink());
  }

  private void toast(Canvas c, Theme t, float dt) {
    String fresh = host.notice();
    if (fresh != null && !fresh.isEmpty() && !fresh.equals(notice)) {
      say(fresh);
    }
    if (noticeLeft <= 0.0F) {
      return;
    }
    noticeLeft -= dt;
    float fade = Math.min(1.0F, Math.min(noticeLeft * 3.0F, (NOTICE_SECONDS - noticeLeft) * 6.0F));
    float w = c.textWidth(FontStyle.LABEL, notice) + 32.0F;
    float h = 36.0F;
    float x = (screenW - panelW()) * 0.5F - w * 0.5F;
    float y = MARGIN + (1.0F - fade) * -10.0F;
    c.pushAlpha(Math.max(0.0F, fade));
    MapChrome.panel(c, t, x, y, w, h, h * 0.5F);
    c.text(
        FontStyle.LABEL,
        notice,
        x + 16.0F,
        y + (h - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.ink());
    c.popAlpha();
  }

  private void say(String text) {
    notice = text;
    noticeLeft = NOTICE_SECONDS;
  }

  private void show(Pop next, float x, float y) {
    pop = next;
    popX = x;
    popY = y;
    popAnim.snap(0.0F);
    popAnim.set(1.0F);
  }

  private void dismiss() {
    pop = Pop.NONE;
    naming.focus(false);
    focused = null;
  }

  public boolean mouseDown(float mx, float my, boolean shift) {
    mouseX = mx;
    mouseY = my;
    if (pop != Pop.NONE) {
      popDown(mx, my);
      return true;
    }
    if (controlsDown(mx, my)) {
      return true;
    }
    if (panelDown(mx, my)) {
      return true;
    }
    if (hoveredMark >= 0) {
      HudData data = host.data();
      if (data.markFriend[hoveredMark]) {
        glide(data.markX[hoveredMark], data.markZ[hoveredMark]);
        return true;
      }
      Waypoints.Waypoint waypoint = waypointAt(data.markX[hoveredMark], data.markZ[hoveredMark]);
      if (waypoint != null) {
        focused = waypoint;
        show(Pop.MARK, mx + 12.0F, my + 12.0F);
        return true;
      }
    }
    search.focus(false);
    dragging = true;
    moved = false;
    downX = mx;
    downY = my;
    lastX = mx;
    lastY = my;
    lastNanos = System.nanoTime();
    velX = 0.0F;
    velZ = 0.0F;
    hasSelection = false;
    if (shift || selectArmed) {
      selecting = true;
      selectArmed = false;
      selFromX = view.worldX(mx, my);
      selFromZ = view.worldZ(mx, my);
      selToX = selFromX;
      selToZ = selFromZ;
    }
    long now = System.nanoTime();
    if (!selecting && now - clickNanos < DOUBLE_NANOS) {
      zoomAt(mx, my, 3.0F);
      if (follow) {
        follow = false;
        anchored = true;
        anchorSX = mx;
        anchorSY = my;
        anchorWX = view.worldX(mx, my);
        anchorWZ = view.worldZ(mx, my);
      }
      clickNanos = 0L;
    } else {
      clickNanos = now;
    }
    return true;
  }

  private Waypoints.Waypoint waypointAt(double x, double z) {
    for (Waypoints.Waypoint waypoint : host.waypoints().list()) {
      if (Math.abs(waypoint.x() + 0.5 - x) < 0.01 && Math.abs(waypoint.z() + 0.5 - z) < 0.01) {
        return waypoint;
      }
    }
    return null;
  }

  private boolean panelDown(float mx, float my) {
    if (panel.get() < 0.5F) {
      return false;
    }
    float w = Math.min(PANEL, screenW * 0.34F);
    float x = panelX();
    float y = MARGIN;
    float h = screenH - MARGIN * 2.0F;
    if (!MapChrome.hit(mx, my, x, y, w, h)) {
      return false;
    }
    if (MapChrome.hit(mx, my, x + 14.0F, y + 14.0F, w - 28.0F, Field.HEIGHT)) {
      search.focus(true);
      search.press(mx);
      return true;
    }
    search.focus(false);
    if (MapChrome.hit(mx, my, x + 14.0F, y + 54.0F, w - 28.0F, 30.0F)) {
      int next = mx < x + w * 0.5F ? 0 : 1;
      if (next != tab) {
        tab = next;
        scroll = 0.0F;
        Sound.play(Sound.Cue.TAB);
      }
      return true;
    }
    if (hoveredRow >= 0 && hoveredRow < entries.size()) {
      Entry entry = entries.get(hoveredRow);
      float ry = y + LIST_TOP + hoveredRow * ROW - scroll;
      float bx = x + 8.0F + (w - 16.0F) - 64.0F;
      float by = ry + (ROW - 30.0F) * 0.5F;
      if (entry.kind == Row.WAYPOINT && MapChrome.hit(mx, my, bx, by, 30.0F, 30.0F)) {
        focused = entry.waypoint;
        pickArea = false;
        picks = host.friends();
        show(Pop.PICK, bx - PICK_W + 30.0F, by + 32.0F);
        return true;
      }
      if (entry.kind == Row.WAYPOINT && MapChrome.hit(mx, my, bx + 32.0F, by, 30.0F, 30.0F)) {
        host.waypoints().remove(entry.waypoint.id());
        builtRevision = -1;
        Sound.play(Sound.Cue.OFF);
        return true;
      }
      if (entry.located) {
        glide(entry.x, entry.z);
        Sound.play(Sound.Cue.STEP);
      }
    }
    return true;
  }

  private void popDown(float mx, float my) {
    switch (pop) {
      case MENU -> {
        String[] items = menuItems();
        float h = items.length * MENU_ROW + 12.0F;
        float[] at = popBox(MENU_W, h);
        int index = (int) Math.floor((my - at[1] - 6.0F) / MENU_ROW);
        if (!MapChrome.hit(mx, my, at[0], at[1], MENU_W, h) || index < 0 || index >= 3) {
          dismiss();
          return;
        }
        switch (index) {
          case 0 -> {
            naming.set("");
            show(Pop.NAME, popX, popY);
            naming.focus(true);
          }
          case 1 -> {
            dismiss();
            selectArmed = true;
            say(Lang.tr("Drag across the map to pick the area"));
          }
          default -> {
            dismiss();
            say(Lang.tr("Coordinates copied"));
            copy = (long) Math.floor(popWX) + " " + (long) Math.floor(popWZ);
          }
        }
      }
      case NAME -> {
        float[] at = popBox(260.0F, 96.0F);
        if (MapChrome.hit(mx, my, at[0] + 12.0F, at[1] + 30.0F, 236.0F, Field.HEIGHT)) {
          naming.press(mx);
        } else if (!MapChrome.hit(mx, my, at[0], at[1], 260.0F, 96.0F)) {
          dismiss();
        }
      }
      case PICK -> {
        float h = pickHeight();
        float[] at = popBox(PICK_W, h);
        float first = at[1] + 8.0F;
        if (pickArea && MapChrome.hit(mx, my, at[0], first, PICK_W, MENU_ROW)) {
          int[] box = chunks();
          say(
              host.downloadArea(box[0], box[1], box[2], box[3])
                  ? Lang.tr("Saving this area as a world")
                  : Lang.tr("Only loaded chunks can be saved. Stop the recording first."));
          hasSelection = false;
          Sound.play(Sound.Cue.ON);
          dismiss();
          return;
        }
        float rowsTop = first + lead() * MENU_ROW + 28.0F;
        int index = (int) Math.floor((my - rowsTop) / MENU_ROW);
        if (MapChrome.hit(mx, my, at[0], at[1], PICK_W, h)
            && my >= rowsTop
            && index >= 0
            && index < Math.min(7, picks.size())) {
          Waypoints.Target friend = picks.get(index);
          if (pickArea) {
            int[] box = chunks();
            host.shareArea(box[0], box[1], box[2], box[3], friend);
            hasSelection = false;
          } else if (focused != null) {
            boolean sent = host.waypoints().actions().share(focused, friend.uuid());
            say(
                sent
                    ? Lang.format("Shared with {0}", friend.name())
                    : Lang.tr("Couldn't share the waypoint."));
          }
          Sound.play(Sound.Cue.ON);
        }
        dismiss();
      }
      case MARK -> {
        float[] at = popBox(240.0F, 92.0F);
        float by = at[1] + 92.0F - 34.0F;
        float half = (240.0F - 36.0F) / 2.0F;
        if (MapChrome.hit(mx, my, at[0] + 12.0F, by, half, 26.0F) && focused != null) {
          pickArea = false;
          picks = host.friends();
          Waypoints.Waypoint keep = focused;
          show(Pop.PICK, at[0], at[1] + 92.0F + 6.0F);
          focused = keep;
          return;
        }
        if (MapChrome.hit(mx, my, at[0] + 24.0F + half, by, half, 26.0F) && focused != null) {
          host.waypoints().remove(focused.id());
          builtRevision = -1;
          Sound.play(Sound.Cue.OFF);
        }
        dismiss();
      }
      default -> dismiss();
    }
  }

  public String takeCopy() {
    String out = copy;
    copy = null;
    return out;
  }

  public void mouseUp(float mx, float my) {
    search.release();
    naming.release();
    if (!dragging) {
      return;
    }
    dragging = false;
    if (selecting) {
      selecting = false;
      int[] box = chunks();
      long count = (long) (box[2] - box[0] + 1) * (box[3] - box[1] + 1);
      if (moved && count > 0) {
        if (count > MapShare.MAX_CHUNKS) {
          say(Lang.tr("That area is too large to share. Pick a smaller one."));
          return;
        }
        hasSelection = true;
        pickArea = true;
        picks = host.friends();
        show(Pop.PICK, mx + 10.0F, my + 10.0F);
      }
    }
  }

  public void mouseDrag(float mx, float my) {
    mouseX = mx;
    mouseY = my;
    if (search.focused()) {
      search.drag(mx);
    }
    if (!dragging) {
      return;
    }
    if (Math.hypot(mx - downX, my - downY) > DRAG_SLOP) {
      moved = true;
    }
    if (selecting) {
      selToX = view.worldX(mx, my);
      selToZ = view.worldZ(mx, my);
      return;
    }
    if (!moved) {
      return;
    }
    follow = false;
    gliding = false;
    anchored = false;
    float scale = scale();
    float dx = (mx - lastX) / scale;
    float dz = (my - lastY) / scale;
    camX -= dx;
    camZ -= dz;
    long now = System.nanoTime();
    float seconds = Math.max(0.001F, (now - lastNanos) / 1.0e9F);
    velX = velX * 0.6F + (-dx / seconds) * 0.4F;
    velZ = velZ * 0.6F + (-dz / seconds) * 0.4F;
    lastX = mx;
    lastY = my;
    lastNanos = now;
  }

  public void secondaryDown(float mx, float my) {
    if (pop != Pop.NONE) {
      dismiss();
      return;
    }
    popWX = view.worldX(mx, my);
    popWZ = view.worldZ(mx, my);
    selFromX = popWX;
    selFromZ = popWZ;
    show(Pop.MENU, mx + 6.0F, my + 6.0F);
    Sound.play(Sound.Cue.STEP);
  }

  public void scroll(float mx, float my, float amount) {
    if (pop != Pop.NONE) {
      return;
    }
    if (panel.get() > 0.5F && mx >= panelX()) {
      scroll -= amount * ROW;
      return;
    }
    zoomAt(mx, my, amount);
  }

  public boolean keyDown(int key, boolean control, boolean shift) {
    if (pop == Pop.NAME) {
      if (naming.submits(key)) {
        String name = naming.take();
        double y = host.height((int) Math.floor(popWX), (int) Math.floor(popWZ));
        int height = y == Integer.MIN_VALUE ? (int) Math.floor(host.data().y) : (int) y + 1;
        Waypoints.Waypoint made =
            host.waypoints()
                .add(
                    name,
                    (int) Math.floor(popWX),
                    height,
                    (int) Math.floor(popWZ),
                    host.dimension());
        if (made != null) {
          say(Lang.format("Added {0}", made.name()));
          Sound.play(Sound.Cue.ON);
        } else {
          say(host.waypoints().notice());
        }
        builtRevision = -1;
        dismiss();
        return true;
      }
      if (key == KEY_ESCAPE) {
        dismiss();
        return true;
      }
      return naming.key(key, control, shift);
    }
    if (search.focused()) {
      if (key == KEY_ESCAPE) {
        search.focus(false);
        return true;
      }
      if (search.submits(key) && !entries.isEmpty()) {
        Entry first = entries.get(0);
        if (first.located) {
          glide(first.x, first.z);
        }
        return true;
      }
      return search.key(key, control, shift);
    }
    if (key == KEY_ESCAPE) {
      if (pop != Pop.NONE) {
        dismiss();
      } else if (selecting || hasSelection || selectArmed) {
        selecting = false;
        hasSelection = false;
        selectArmed = false;
      } else {
        close();
      }
      return true;
    }
    float pan = 80.0F / scale();
    switch (key) {
      case KEY_EQUAL, KEY_ADD -> zoomAt(originX(), originY(), 2.0F);
      case KEY_MINUS, KEY_SUBTRACT -> zoomAt(originX(), originY(), -2.0F);
      case KEY_LEFT -> glide(camX - pan, camZ);
      case KEY_RIGHT -> glide(camX + pan, camZ);
      case KEY_UP -> glide(camX, camZ - pan);
      case KEY_DOWN -> glide(camX, camZ + pan);
      default -> {
        return false;
      }
    }
    return true;
  }

  public boolean charTyped(int codepoint) {
    if (pop == Pop.NAME) {
      return naming.type(codepoint);
    }
    if (search.focused()) {
      return search.type(codepoint);
    }
    return false;
  }

  public boolean typing() {
    return search.focused() || pop == Pop.NAME;
  }
}
