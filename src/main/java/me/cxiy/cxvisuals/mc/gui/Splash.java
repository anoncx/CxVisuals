package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.Window;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.mc.CxVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

public final class Splash {
  public static final int UNAVAILABLE = -1;
  public static final int RUNNING = 0;
  public static final int FINISHED = 1;

  private static final String WORD = "CXVISUALS";
  private static final float LETTER_STAGGER = 0.045F;
  private static final float LETTER_SECONDS = 0.42F;
  private static final float LETTER_RISE = 14.0F;
  private static final float INTRO_SECONDS = 0.55F;
  private static final float BAR_WIDTH = 240.0F;
  private static final float BAR_HEIGHT = 3.0F;
  private static final float BAR_GAP = 34.0F;
  private static final float CHASE = 7.0F;
  private static final float DOT = 5.0F;
  private static final float OUTRO_DISSOLVE = 0.6F;
  private static final float OUTRO_VEIL_FROM = 0.3F;
  private static final float OUTRO_TOTAL = 1.1F;
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final float SMOOTHING = 5.5F;
  private static final float DISSOLVE_PAD = 90.0F;
  private static final float CENTER_LIFT = 18.0F;

  private static final McCanvas CANVAS = new McCanvas();

  private static Themes themes;
  private static long lastNanos;
  private static float clock;
  private static float displayed;

  private Splash() {}

  public static void reset() {
    lastNanos = 0L;
    clock = 0.0F;
    displayed = 0.0F;
  }

  public static int draw(
      GuiGraphicsExtractor graphics,
      Minecraft mc,
      float progress,
      boolean fadeIn,
      float intro,
      float outro,
      String version) {
    if (CxVisuals.registry() == null) {
      return UNAVAILABLE;
    }
    if (themes == null) {
      themes = new Themes(CxVisuals.registry().client());
    }
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;
    clock += dt;
    float target = outro >= 0.0F ? 1.0F : Math.max(displayed, Math.min(progress, 1.0F));
    displayed += (target - displayed) * (1.0F - (float) Math.exp(-dt * SMOOTHING));
    if (target - displayed < 0.002F) {
      displayed = target;
    }

    Theme theme = themes.get();
    int paper = theme.paper() | 0xFF000000;
    Window window = mc.getWindow();
    int guiScale = Math.max(1, window.getGuiScale());
    float scale = McCanvas.interfaceScale(window.getHeight());
    float width = window.getWidth() / scale;
    float height = window.getHeight() / scale;

    boolean outroing = outro >= 0.0F;
    float veil =
        outroing
            ? 1.0F - smooth((outro - OUTRO_VEIL_FROM) / (OUTRO_TOTAL - OUTRO_VEIL_FROM))
            : fadeIn ? smooth(intro) : 1.0F;
    if ((outroing || (fadeIn && intro < 1.0F)) && mc.gui.screen() != null) {
      mc.gui.screen().extractRenderStateWithTooltipAndSubtitles(graphics, 0, 0, 0.0F);
    } else if (!fadeIn && !outroing) {
      ARGB.setVector4fFromARGB32(
          mc.gameRenderer.gameRenderState().guiRenderState.clearColorOverride, paper);
    }
    graphics.nextStratum();
    CANVAS.begin(graphics, scale, guiScale);
    CANVAS.roundRect(0.0F, 0.0F, width, height, 0.0F, Theme.withAlpha(paper, veil));

    float cx = width * 0.5F;
    float cy = height * 0.5F - CENTER_LIFT;
    float fade = fadeIn ? 1.0F : smooth(clock / INTRO_SECONDS);
    float erosion = outroing ? Math.min(1.0F, outro / OUTRO_DISSOLVE) : 0.0F;
    CANVAS.pushAlpha(fade);
    CANVAS.beginDissolve(
        cx - BAR_WIDTH * 0.5F - DISSOLVE_PAD,
        cy - 70.0F - DISSOLVE_PAD,
        BAR_WIDTH + DISSOLVE_PAD * 2.0F,
        190.0F + DISSOLVE_PAD * 2.0F,
        erosion);
    wordmark(CANVAS, theme, cx, cy);
    bar(CANVAS, theme, cx, cy + BAR_GAP);
    caption(CANVAS, theme, cx, cy + BAR_GAP + 22.0F, version);
    CANVAS.endDissolve();
    CANVAS.popAlpha();
    CANVAS.end();

    return outroing && outro >= OUTRO_TOTAL ? FINISHED : RUNNING;
  }

  private static void wordmark(McCanvas c, Theme t, float cx, float cy) {
    float total = c.textWidth(FontStyle.HERO, WORD);
    float left = cx - total * 0.5F;
    float top = cy - c.lineHeight(FontStyle.HERO) * 0.5F - 6.0F;
    float x = left;
    for (int i = 0; i < WORD.length(); i++) {
      String letter = String.valueOf(WORD.charAt(i));
      float local = (clock - i * LETTER_STAGGER) / LETTER_SECONDS;
      float in = smooth(local);
      if (in > 0.0F) {
        c.pushAlpha(in);
        c.text(FontStyle.HERO, letter, x, top + (1.0F - in) * LETTER_RISE, t.ink());
        c.popAlpha();
      }
      x += c.textWidth(FontStyle.HERO, letter);
    }
    float lit = smooth((clock - WORD.length() * LETTER_STAGGER) / LETTER_SECONDS);
    float pulse = 0.55F + 0.45F * (float) Math.sin(clock * 3.2F);
    c.pushAlpha(lit * pulse);
    c.roundRect(left + total + 8.0F, top + 6.0F, DOT, DOT, DOT * 0.5F, t.red());
    c.popAlpha();
  }

  private static void bar(McCanvas c, Theme t, float cx, float y) {
    float left = cx - BAR_WIDTH * 0.5F;
    int track = Theme.withAlpha(t.ink(), 0.12F);
    c.roundRect(left, y, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT * 0.5F, track);
    float fill = Math.max(BAR_HEIGHT, BAR_WIDTH * displayed);
    c.pushClip(left, y - 6.0F, fill, BAR_HEIGHT + 12.0F);
    c.roundRect(left, y, Math.max(fill, BAR_HEIGHT), BAR_HEIGHT, BAR_HEIGHT * 0.5F, t.ink());
    c.popClip();
    float head = left + fill;
    float breathe = 0.5F + 0.5F * (float) Math.sin(clock * CHASE);
    c.shadow(
        head - 4.0F,
        y - 1.0F,
        8.0F,
        BAR_HEIGHT + 2.0F,
        4.0F,
        12.0F,
        0.0F,
        Theme.withAlpha(t.ink(), 0.25F + 0.25F * breathe));
  }

  private static void caption(McCanvas c, Theme t, float cx, float y, String version) {
    String percent = Math.round(displayed * 100.0F) + "%";
    float pw = c.textWidth(FontStyle.MONO, percent);
    c.text(FontStyle.MONO, percent, cx - pw * 0.5F, y, t.ink2());
    String label = "CxVisuals " + version;
    float lw = c.textWidth(FontStyle.CAPTION, label);
    c.text(FontStyle.CAPTION, label, cx - lw * 0.5F, y + 22.0F, t.grey());
  }

  private static float smooth(float t) {
    return (float) Easing.smoothstep(Math.max(0.0F, Math.min(1.0F, t)));
  }
}
