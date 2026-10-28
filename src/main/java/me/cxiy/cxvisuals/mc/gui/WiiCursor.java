package me.cxiy.cxvisuals.mc.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import me.cxiy.cxvisuals.mc.CxVisuals;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Cursors;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;

public final class WiiCursor {
  private static final int SIZE = 48;
  private static final int DEFAULT = 0;
  private static final int OPEN = 1;
  private static final int GRAB = 2;
  private static final int OPEN_HOT_X = 14;
  private static final int OPEN_HOT_Y = 3;
  private static final int GRAB_HOT_X = 23;
  private static final int GRAB_HOT_Y = 17;
  private static final float OUTLINE = 2.4F;
  private static final float ACCENT_SIZE = 5.0F;
  private static final float OPEN_DOT_X = 12.5F;
  private static final float OPEN_DOT_Y = 30.0F;
  private static final float GRAB_DOT_X = 21.0F;
  private static final float GRAB_DOT_Y = 27.0F;
  private static final float SHADOW_SHIFT = 1.8F;
  private static final Color INK = new Color(0x0A0A0A);
  private static final Color SHADOW = new Color(0, 0, 0, 90);
  private static final Color GLOVE_TOP = new Color(0xFFFFFF);
  private static final Color GLOVE_BOTTOM = new Color(0xDADADA);
  private static final Color ACCENT = new Color(0xD71921);

  private static final int TILTS = 9;
  private static final float TILT_STEP = 6.0F;
  private static final float TILT_PER_SPEED = 0.012F;
  private static final float TILT_FOLLOW = 9.0F;
  private static final float MAX_FRAME = 0.1F;
  private static final long[][] CURSORS = new long[3][TILTS];

  private static boolean broken;
  private static int current = DEFAULT;
  private static int currentTilt = TILTS / 2;
  private static double lastX;
  private static long lastNanos;
  private static float sway;

  private WiiCursor() {}

  public static void update(Minecraft mc) {
    int wanted = wanted(mc);
    int tilt = wanted == DEFAULT ? TILTS / 2 : tilt(mc);
    if ((wanted == current && tilt == currentTilt) || broken) {
      return;
    }
    try {
      long cursor = wanted == DEFAULT ? 0L : cursor(wanted, tilt);
      Cursors.select(cursor);
      current = wanted;
      currentTilt = tilt;
    } catch (RuntimeException | LinkageError e) {
      broken = true;
      CxVisualsClient.LOG.warn("Wii cursor unavailable, the system cursor stays", e);
    }
  }

  private static int wanted(Minecraft mc) {
    if (mc.level != null
        || mc.gui.screen() == null
        || mc.mouseHandler.isMouseGrabbed()
        || !CxVisuals.registry().client().wiiCursor.get()) {
      return DEFAULT;
    }
    return mc.mouseHandler.isLeftPressed() ? GRAB : OPEN;
  }

  private static int tilt(Minecraft mc) {
    long now = System.nanoTime();
    double x = mc.mouseHandler.xpos();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME);
    if (dt > 0.0F) {
      float target =
          (float)
              Math.max(
                  -(TILTS / 2) * TILT_STEP,
                  Math.min((TILTS / 2) * TILT_STEP, (x - lastX) / dt * TILT_PER_SPEED));
      sway += (target - sway) * Math.min(1.0F, dt * TILT_FOLLOW);
    }
    lastX = x;
    lastNanos = now;
    return TILTS / 2 + Math.round(sway / TILT_STEP);
  }

  private static long cursor(int kind, int tilt) {
    if (CURSORS[kind][tilt] == 0L) {
      CURSORS[kind][tilt] =
          build(kind == GRAB ? fist() : hand(), kind == GRAB, (tilt - TILTS / 2) * TILT_STEP);
    }
    return CURSORS[kind][tilt];
  }

  static BufferedImage paint(
      Shape glove, float dotX, float dotY, int hotX, int hotY, float degrees) {
    BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.rotate(Math.toRadians(degrees), hotX, hotY);
    g.translate(SHADOW_SHIFT, SHADOW_SHIFT);
    g.setColor(SHADOW);
    g.fill(glove);
    g.setStroke(new BasicStroke(OUTLINE + 1.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(glove);
    g.translate(-SHADOW_SHIFT, -SHADOW_SHIFT);
    g.setColor(INK);
    g.setStroke(new BasicStroke(OUTLINE * 2.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(glove);
    g.setPaint(new GradientPaint(0.0F, 0.0F, GLOVE_TOP, 0.0F, SIZE, GLOVE_BOTTOM));
    g.fill(glove);
    g.setColor(ACCENT);
    g.fill(new Ellipse2D.Float(dotX, dotY, ACCENT_SIZE, ACCENT_SIZE));
    g.dispose();
    return image;
  }

  private static long build(Shape glove, boolean grab, float degrees) {
    int hotX = grab ? GRAB_HOT_X : OPEN_HOT_X;
    int hotY = grab ? GRAB_HOT_Y : OPEN_HOT_Y;
    BufferedImage image =
        paint(
            glove,
            grab ? GRAB_DOT_X : OPEN_DOT_X,
            grab ? GRAB_DOT_Y : OPEN_DOT_Y,
            hotX,
            hotY,
            degrees);
    ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
    try {
      for (int y = 0; y < SIZE; y++) {
        for (int x = 0; x < SIZE; x++) {
          int argb = image.getRGB(x, y);
          pixels
              .put((byte) (argb >> 16))
              .put((byte) (argb >> 8))
              .put((byte) argb)
              .put((byte) (argb >>> 24));
        }
      }
      pixels.flip();
      return Cursors.create(pixels, SIZE, hotX, hotY);
    } finally {
      MemoryUtil.memFree(pixels);
    }
  }

  private static Shape hand() {
    Area area = new Area(new RoundRectangle2D.Float(9.0F, 3.0F, 10.0F, 28.0F, 10.0F, 10.0F));
    area.add(new Area(new RoundRectangle2D.Float(9.0F, 19.0F, 27.0F, 24.0F, 12.0F, 12.0F)));
    area.add(new Area(new RoundRectangle2D.Float(20.0F, 16.0F, 13.0F, 14.0F, 8.0F, 8.0F)));
    area.add(new Area(new RoundRectangle2D.Float(29.0F, 19.0F, 11.0F, 15.0F, 8.0F, 8.0F)));
    area.add(new Area(new RoundRectangle2D.Float(2.0F, 25.0F, 15.0F, 9.0F, 9.0F, 9.0F)));
    return area;
  }

  private static Shape fist() {
    Area area = new Area(new RoundRectangle2D.Float(9.0F, 15.0F, 30.0F, 26.0F, 12.0F, 12.0F));
    area.add(new Area(new Ellipse2D.Float(9.0F, 9.0F, 10.0F, 13.0F)));
    area.add(new Area(new Ellipse2D.Float(17.0F, 6.0F, 10.0F, 14.0F)));
    area.add(new Area(new Ellipse2D.Float(25.0F, 7.0F, 10.0F, 14.0F)));
    area.add(new Area(new Ellipse2D.Float(31.0F, 11.0F, 9.0F, 13.0F)));
    area.add(new Area(new RoundRectangle2D.Float(5.0F, 25.0F, 16.0F, 9.0F, 9.0F, 9.0F)));
    return area;
  }
}
