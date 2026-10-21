package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class CompassElement extends HudElement {
  public static final float WIDTH = 280.0F;
  public static final float HEIGHT = 50.0F;
  public static final float SPAN = 120.0F;
  private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
  private static final String[] POINT_KEYS =
      Lang.keys(
          "#compass.N",
          "#compass.NE",
          "#compass.E",
          "#compass.SE",
          "#compass.S",
          "#compass.SW",
          "#compass.W",
          "#compass.NW");
  private static final float PAD = 14.0F;
  private static final float STRIP_TOP = 7.0F;
  private static final float TICK = 4.0F;
  private static final float TICK_TALL = 7.0F;
  private static final float STEP = 15.0F;
  private static final float DOT = 6.0F;
  private static final float FOCUS = 7.0F;
  private static final float EDGE_FADE = 0.3F;

  public final BoolSetting friends;
  public final BoolSetting waypoints;

  private String foot = "";
  private int footHeading = Integer.MIN_VALUE;
  private int footMark = HudData.NONE;
  private int footDistance = -1;

  public CompassElement(Settings settings) {
    super(settings, "compass", "Compass", Category.INFO, false, Anchor.TOP, 0.0, 10.0);
    this.friends =
        group()
            .bool("friends", "Friends", true)
            .describe("Friends on your server who share their location.");
    this.waypoints = group().bool("waypoints", "Waypoints", true);
  }

  @Override
  public float radius() {
    return 16.0F;
  }

  public static float heading(float yaw) {
    float heading = (yaw + 180.0F) % 360.0F;
    return heading < 0.0F ? heading + 360.0F : heading;
  }

  public static float bearing(double fromX, double fromZ, double toX, double toZ) {
    float bearing = (float) Math.toDegrees(Math.atan2(toX - fromX, fromZ - toZ));
    return bearing < 0.0F ? bearing + 360.0F : bearing;
  }

  public static float turn(float bearing, float heading) {
    float turn = (bearing - heading) % 360.0F;
    if (turn > 180.0F) {
      turn -= 360.0F;
    } else if (turn < -180.0F) {
      turn += 360.0F;
    }
    return turn;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    w = WIDTH;
    h = HEIGHT;
  }

  private boolean wanted(HudData data, int index) {
    return data.markFriend[index] ? friends.get() : waypoints.get();
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    HudPaint.plate(c, t, this, x, y, w, h, radius());
    float heading = heading(data.yaw);
    float centre = x + w * 0.5F;
    float reach = w * 0.5F - PAD;
    float scale = reach / (SPAN * 0.5F);
    float stripTop = y + STRIP_TOP;
    float monoLine = c.lineHeight(FontStyle.MONO);
    float labelTop = stripTop + TICK_TALL + 2.0F;

    c.pushClip(x + PAD, y, w - PAD * 2.0F, h);
    float first = (float) Math.floor((heading - SPAN * 0.5F) / STEP) * STEP;
    for (float angle = first; angle <= heading + SPAN * 0.5F + STEP; angle += STEP) {
      float offset = (angle - heading) * scale;
      float fade = fade(offset, reach);
      if (fade <= 0.0F) {
        continue;
      }
      int index = Math.floorMod(Math.round(angle / 45.0F), 8);
      boolean point = Math.floorMod(Math.round(angle), 45) == 0;
      boolean cardinal = point && index % 2 == 0;
      float tall = point ? TICK_TALL : TICK;
      c.roundRect(
          centre + offset - 0.5F,
          stripTop,
          1.0F,
          tall,
          0.0F,
          Theme.withAlpha(cardinal ? t.ink() : t.grey(), fade * (point ? 1.0F : 0.6F)));
      if (point) {
        String label = Lang.tr(POINT_KEYS[index], POINTS[index]);
        c.text(
            cardinal ? FontStyle.CAPTION : FontStyle.MONO,
            label,
            centre
                + offset
                - c.textWidth(cardinal ? FontStyle.CAPTION : FontStyle.MONO, label) * 0.5F,
            labelTop,
            Theme.withAlpha(index == 0 ? t.red() : cardinal ? t.ink() : t.grey(), fade));
      }
    }
    c.popClip();

    int focused = HudData.NONE;
    float nearest = FOCUS;
    float dotTop = stripTop + (TICK_TALL - DOT) * 0.5F;
    for (int i = 0; i < data.markCount; i++) {
      if (!wanted(data, i)) {
        continue;
      }
      float turn = turn(bearing(data.x, data.z, data.markX[i], data.markZ[i]), heading);
      float offset = Math.max(-reach, Math.min(reach, turn * scale));
      boolean inside = Math.abs(turn) <= SPAN * 0.5F;
      if (Math.abs(turn) < nearest) {
        nearest = Math.abs(turn);
        focused = i;
      }
      float left = centre + offset - DOT * 0.5F;
      int color = Theme.withAlpha(data.markColor[i], inside ? 1.0F : 0.45F);
      if (data.markFriend[i]) {
        c.roundRect(left, dotTop, DOT, DOT, DOT * 0.5F, color);
      } else {
        c.roundRect(left, dotTop, DOT, DOT, 1.5F, color);
      }
    }
    c.roundRect(centre - 0.5F, y + 2.0F, 1.0F, 4.0F, 0.0F, t.red());

    int degrees = Math.round(heading) % 360;
    int distance =
        focused == HudData.NONE
            ? -1
            : (int)
                Math.round(Math.hypot(data.markX[focused] - data.x, data.markZ[focused] - data.z));
    if (degrees != footHeading || focused != footMark || distance != footDistance) {
      footHeading = degrees;
      footMark = focused;
      footDistance = distance;
      foot =
          focused == HudData.NONE
              ? degrees + "°"
              : data.markLabel[focused] + " · " + distance + " m";
    }
    float footWidth = c.textWidth(FontStyle.MONO, foot);
    c.text(
        FontStyle.MONO,
        foot,
        centre - footWidth * 0.5F,
        y + h - monoLine - 5.0F,
        focused == HudData.NONE ? t.grey() : t.ink());
  }

  private static float fade(float offset, float reach) {
    float edge = 1.0F - Math.abs(offset) / reach;
    return edge <= 0.0F ? 0.0F : Math.min(1.0F, edge / EDGE_FADE);
  }
}
