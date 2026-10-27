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
import me.cxiy.cxvisuals.gui.map.ChartBatch;
import me.cxiy.cxvisuals.gui.map.MapMarks;
import me.cxiy.cxvisuals.gui.map.MapStyle;
import me.cxiy.cxvisuals.gui.map.Viewport;
import me.cxiy.cxvisuals.gui.spatial.Spring;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;
import me.cxiy.cxvisuals.modules.map.MapAtlas;
import me.cxiy.cxvisuals.modules.map.MapSource;
import me.cxiy.cxvisuals.modules.map.TileKey;
import me.cxiy.cxvisuals.modules.waypoints.Waypoints;

public final class MinimapElement extends HudElement {
  public enum Shape {
    CIRCLE,
    SQUARE
  }

  public static final float SIZE = 148.0F;
  public static final double MIN_ZOOM = 0.5;
  public static final double MAX_ZOOM = 10.0;

  private static final float INSET = 5.0F;
  private static final float SQUARE_RADIUS = 20.0F;
  private static final float FOOT_GAP = 6.0F;
  private static final float FOOT = 22.0F;
  private static final float ARROW = 7.0F;
  private static final float WAYPOINT = 7.0F;
  private static final float FRIEND = 13.0F;
  private static final float OTHER = 5.0F;
  private static final float EDGE = 8.0F;
  private static final float AWAY = 0.55F;
  private static final float NORTH = 12.0F;
  private static final int MAX_PLAYERS = 24;
  private static final float ZOOM_STEP = 1.5F;
  private static final String CAVE = Lang.key("Cave");

  public final EnumSetting<Shape> shape;
  public final BoolSetting rotate;
  public final NumberSetting zoom;
  public final BoolSetting coordinates;

  private final Viewport view = new Viewport();
  private final ChartBatch batch = new ChartBatch();
  private final Spring zoomGlide = Spring.critical(260.0F);
  private final double[] otherX = new double[MAX_PLAYERS];
  private final double[] otherZ = new double[MAX_PLAYERS];
  private MapSource source;
  private MapAtlas idle;
  private boolean glided;
  private int footX = Integer.MIN_VALUE;
  private int footY;
  private int footZ;
  private String foot = "";
  private String caveLabel = "";

  public MinimapElement(Settings settings) {
    super(settings, "minimap", "Minimap", Category.INFO, true, Anchor.TOP_RIGHT, -10.0, 10.0);
    shape = group().enumOf("shape", "Shape", Shape.CIRCLE, "Circle", "Rounded square");
    rotate =
        group()
            .bool("rotate", "Turn with you", true)
            .describe("Keeps the way you face at the top. Off keeps north up.");
    zoom = group().number("zoom", "Zoom", 1.5, MIN_ZOOM, MAX_ZOOM, 0.25, "x");
    coordinates = group().bool("coordinates", "Coordinates", true);
  }

  public void source(MapSource next) {
    source = next;
  }

  public void zoomBy(boolean in) {
    double next = zoom.get() * (in ? ZOOM_STEP : 1.0F / ZOOM_STEP);
    zoom.set(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.round(next * 4.0) / 4.0)));
  }

  @Override
  public float radius() {
    return shape.get() == Shape.CIRCLE ? SIZE * 0.5F : SQUARE_RADIUS;
  }

  @Override
  public boolean present(HudData data) {
    return data.editing || (source != null && source.active());
  }

  @Override
  public void measure(Canvas c, HudData data) {
    w = SIZE;
    h = SIZE + (coordinates.get() ? FOOT_GAP + FOOT : 0.0F);
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    boolean circle = shape.get() == Shape.CIRCLE;
    float plateRadius = circle ? SIZE * 0.5F : SQUARE_RADIUS;
    HudPaint.plate(c, t, this, x, y, SIZE, SIZE, plateRadius);
    float mapX = x + INSET;
    float mapY = y + INSET;
    float mapSize = SIZE - INSET * 2.0F;
    float half = mapSize * 0.5F;
    float centreX = mapX + half;
    float centreY = mapY + half;

    float goal = (float) Math.log(zoom.get());
    if (!glided) {
      glided = true;
      zoomGlide.snap(goal);
    }
    zoomGlide.step(dt, goal);
    float scale = (float) Math.exp(zoomGlide.value());
    float angle = rotate.get() ? Viewport.facing(data.yaw) : 0.0F;
    view.set(data.x, data.z, scale, angle);
    view.origin(centreX, centreY);

    MapAtlas atlas = source != null ? source.atlas() : idle();
    int layer = source != null && source.active() ? source.layer() : TileKey.SURFACE;
    batch.build(atlas, view, layer, mapX, mapY, mapSize, mapSize, 2);
    batch.masked = true;
    batch.roundness = circle ? 1.0F : (SQUARE_RADIUS - INSET) / half;
    batch.clouds = source == null || source.clouds() ? 1.0F : 0.0F;
    batch.fog = MapStyle.fog(t);
    batch.alpha = 1.0F;
    c.chart(batch);

    float reach = half - EDGE;
    boolean waypoints = source == null || source.waypoints();
    boolean friends = source == null || source.friends();
    if (source != null && source.active()) {
      int count = source.players(otherX, otherZ, MAX_PLAYERS);
      for (int i = 0; i < count; i++) {
        float px = view.screenX(otherX[i], otherZ[i]) - centreX;
        float py = view.screenY(otherX[i], otherZ[i]) - centreY;
        if (inside(px, py, reach, circle)) {
          MapMarks.other(c, t, centreX + px, centreY + py, OTHER);
        }
      }
    }
    for (int i = 0; i < data.markCount; i++) {
      boolean friend = data.markFriend[i];
      if (friend ? !friends : !waypoints) {
        continue;
      }
      float px = view.screenX(data.markX[i], data.markZ[i]) - centreX;
      float py = view.screenY(data.markX[i], data.markZ[i]) - centreY;
      float alpha = 1.0F;
      if (!inside(px, py, reach, circle)) {
        float pull = pull(px, py, reach, circle);
        px *= pull;
        py *= pull;
        alpha = AWAY;
      }
      float sx = centreX + px;
      float sy = centreY + py;
      if (friend) {
        MapMarks.friend(c, t, data.markEntity[i], data.markLabel[i], sx, sy, FRIEND, alpha);
      } else if (data.markColor[i] == Waypoints.DEATH_COLOR) {
        MapMarks.death(c, sx, sy, WAYPOINT + 1.0F, data.markColor[i], alpha);
      } else {
        MapMarks.waypoint(c, sx, sy, WAYPOINT, data.markColor[i], alpha);
      }
    }

    float north = rotate.get() ? angle : 0.0F;
    float nx = centreX + (float) Math.sin(north) * (half - NORTH * 0.5F - 2.0F);
    float ny = centreY - (float) Math.cos(north) * (half - NORTH * 0.5F - 2.0F);
    if (!circle) {
      float limit = half - NORTH * 0.5F - 2.0F;
      float stretch = Math.max(Math.abs(nx - centreX), Math.abs(ny - centreY));
      if (stretch > 0.001F) {
        nx = centreX + (nx - centreX) * limit / stretch;
        ny = centreY + (ny - centreY) * limit / stretch;
      }
    }
    c.roundRect(nx - NORTH * 0.5F, ny - NORTH * 0.5F, NORTH, NORTH, NORTH * 0.5F, t.red());
    float nw = c.textWidth(FontStyle.CAPTION, "N");
    c.text(
        FontStyle.CAPTION,
        "N",
        nx - nw * 0.5F,
        ny - c.lineHeight(FontStyle.CAPTION) * 0.5F,
        MapMarks.WHITE);

    float heading = rotate.get() ? 0.0F : (float) Math.toRadians(data.yaw + 180.0F);
    MapMarks.player(c, centreX, centreY, ARROW, heading, t.red());

    if (coordinates.get()) {
      int bx = (int) Math.floor(data.x);
      int by = (int) Math.floor(data.y);
      int bz = (int) Math.floor(data.z);
      if (bx != footX || by != footY || bz != footZ) {
        footX = bx;
        footY = by;
        footZ = bz;
        foot = bx + "  " + by + "  " + bz;
        caveLabel = Lang.tr(CAVE);
      }
      boolean cave = source != null && source.active() && source.layer() == TileKey.CAVE;
      float textW = c.textWidth(FontStyle.MONO, foot);
      float badge = cave ? c.textWidth(FontStyle.CAPTION, caveLabel) + 14.0F : 0.0F;
      float pillW = Math.min(SIZE, textW + badge + 22.0F);
      float pillX = x + (SIZE - pillW) * 0.5F;
      float pillY = y + SIZE + FOOT_GAP;
      HudPaint.plate(c, t, pillX, pillY, pillW, FOOT, FOOT * 0.5F);
      float textX = pillX + (pillW - textW - badge) * 0.5F;
      if (cave) {
        c.roundRect(textX, pillY + FOOT * 0.5F - 3.0F, 6.0F, 6.0F, 3.0F, t.red());
        c.text(
            FontStyle.CAPTION,
            caveLabel,
            textX + 10.0F,
            pillY + (FOOT - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
            t.ink2());
        textX += badge;
      }
      c.text(
          FontStyle.MONO,
          foot,
          textX,
          pillY + (FOOT - c.lineHeight(FontStyle.MONO)) * 0.5F,
          t.ink());
    }
  }

  private MapAtlas idle() {
    if (idle == null) {
      idle = new MapAtlas();
    }
    return idle;
  }

  private static boolean inside(float px, float py, float reach, boolean circle) {
    return circle
        ? px * px + py * py <= reach * reach
        : Math.abs(px) <= reach && Math.abs(py) <= reach;
  }

  private static float pull(float px, float py, float reach, boolean circle) {
    float far =
        circle ? (float) Math.sqrt(px * px + py * py) : Math.max(Math.abs(px), Math.abs(py));
    return far <= 0.001F ? 1.0F : reach / far;
  }
}
