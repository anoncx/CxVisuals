package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.text.FontFace;
import me.cxiy.cxvisuals.gui.text.TextRun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public final class HudText {
  private static final FontStyle STYLE = FontStyle.LABEL;
  private static final float CAP = 6.7F;
  private static final float BASELINE = 7.0F;
  private static final float SHADOW = 0.6F;
  private static final float SHADOW_ALPHA = 0.55F;
  private static final float RULE = 0.75F;
  private static final float UNDERLINE = 0.9F;
  private static final float STRIKE = 0.45F;
  private static final float SLANT = -0.2F;
  private static final long SCRAMBLE_NANOS = 50_000_000L;
  private static final String POOL =
      "ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghkmnopqrstuvwxyz0123456789#$%&?@";
  private static final String[] SCRAMBLE = new String[POOL.length()];
  private static final Line LINE = new Line();
  private static final Matrix3x2f MATRIX = new Matrix3x2f();

  private static int depth;
  private static boolean enabled;
  private static boolean bypass;
  private static float unit;

  static {
    for (int i = 0; i < SCRAMBLE.length; i++) {
      SCRAMBLE[i] = String.valueOf(POOL.charAt(i));
    }
  }

  private HudText() {}

  private static final class Line implements FormattedCharSink {
    private static final int ROOM = 64;

    final String[] text = new String[ROOM];
    final Style[] style = new Style[ROOM];
    final boolean[] vanilla = new boolean[ROOM];
    final StringBuilder run = new StringBuilder(64);
    int count;
    Style current;
    boolean currentVanilla;

    void begin() {
      for (int i = 0; i < count; i++) {
        text[i] = null;
        style[i] = null;
      }
      count = 0;
      run.setLength(0);
      current = null;
    }

    @Override
    public boolean accept(int position, Style next, int codepoint) {
      boolean foreign =
          !next.getFont().equals(FontDescription.DEFAULT) || !regular().covers(codepoint);
      if (run.length() > 0
          && (next != current && !next.equals(current) || foreign != currentVanilla)) {
        flush();
      }
      current = next;
      currentVanilla = foreign;
      run.appendCodePoint(codepoint);
      return true;
    }

    void flush() {
      if (run.length() == 0) {
        return;
      }
      if (count < ROOM) {
        text[count] = run.toString();
        style[count] = current;
        vanilla[count] = currentVanilla;
        count++;
      }
      run.setLength(0);
    }
  }

  public static void configure(boolean on) {
    enabled = on;
    depth = 0;
  }

  public static void reset() {
    depth = 0;
  }

  public static void enter() {
    depth++;
  }

  public static void exit() {
    if (depth > 0) {
      depth--;
    }
  }

  public static boolean active() {
    return depth > 0 && enabled && !bypass && RenderSystem.isOnRenderThread();
  }

  private static FontFace regular() {
    return McCanvas.engine().face(STYLE);
  }

  private static FontFace face(Style style) {
    return style.isBold() ? McCanvas.engine().bold(STYLE) : regular();
  }

  private static float unit() {
    if (unit == 0.0F) {
      unit = CAP / regular().capHeight();
    }
    return unit;
  }

  private static int vanillaWidth(Font font, String text, Style style) {
    bypass = true;
    try {
      return font.width(FormattedCharSequence.forward(text, style));
    } finally {
      bypass = false;
    }
  }

  private static int measure(Font font) {
    LINE.flush();
    float width = 0.0F;
    for (int i = 0; i < LINE.count; i++) {
      width +=
          LINE.vanilla[i]
              ? vanillaWidth(font, LINE.text[i], LINE.style[i])
              : face(LINE.style[i]).width(LINE.text[i]) * unit();
    }
    return (int) Math.ceil(width - 0.01F);
  }

  public static int width(Font font, FormattedCharSequence text) {
    LINE.begin();
    text.accept(LINE);
    return measure(font);
  }

  public static int width(Font font, String text) {
    LINE.begin();
    StringDecomposer.iterateFormatted(text, Style.EMPTY, LINE);
    return measure(font);
  }

  public static int width(Font font, FormattedText text) {
    LINE.begin();
    StringDecomposer.iterateFormatted(text, Style.EMPTY, LINE);
    return measure(font);
  }

  public static void submit(
      GuiRenderState state,
      Font font,
      FormattedCharSequence text,
      Matrix3x2fc pose,
      int x,
      int y,
      int color,
      boolean shadow,
      ScreenRectangle scissor) {
    LINE.begin();
    text.accept(LINE);
    LINE.flush();
    float scale = unit();
    float raster = scale * Minecraft.getInstance().getWindow().getGuiScale() * Math.abs(pose.m00());
    float cursor = x;
    for (int i = 0; i < LINE.count; i++) {
      String part = LINE.text[i];
      Style style = LINE.style[i];
      if (LINE.vanilla[i]) {
        state.addText(
            new GuiTextRenderState(
                font,
                FormattedCharSequence.forward(part, style),
                pose,
                Math.round(cursor),
                y,
                color,
                0,
                shadow,
                false,
                scissor));
        cursor += vanillaWidth(font, part, style);
        continue;
      }
      FontFace face = face(style);
      float width = face.width(part) * scale;
      TextColor tint = style.getColor();
      int argb =
          (color & 0xFF000000) | (tint == null ? color & 0x00FFFFFF : tint.getValue() & 0x00FFFFFF);
      int dark = shade(argb);
      if (style.isObfuscated()) {
        int beat = (int) (System.nanoTime() / SCRAMBLE_NANOS);
        float at = cursor;
        for (int k = 0; k < part.length(); k++) {
          String single = part.substring(k, k + 1);
          float slot = face.width(single) * scale;
          if (part.charAt(k) != ' ') {
            int mix = (k + 1) * 0x9E3779B1 ^ beat * 0x85EBCA6B;
            mix = (mix ^ (mix >>> 15)) * 0x2C1B3C6D;
            String glyph = SCRAMBLE[((mix ^ (mix >>> 12)) >>> 1) % SCRAMBLE.length];
            float left = at + (slot - face.width(glyph) * scale) * 0.5F;
            glyphs(
                state,
                face,
                glyph,
                pose,
                left,
                y,
                scale,
                raster,
                argb,
                dark,
                shadow,
                style.isItalic(),
                scissor);
          }
          at += slot;
        }
      } else {
        glyphs(
            state,
            face,
            part,
            pose,
            cursor,
            y,
            scale,
            raster,
            argb,
            dark,
            shadow,
            style.isItalic(),
            scissor);
      }
      if (style.isUnderlined()) {
        rule(state, pose, cursor, y + BASELINE + UNDERLINE, width, argb, scissor);
      }
      if (style.isStrikethrough()) {
        rule(state, pose, cursor, y + BASELINE - CAP * STRIKE, width, argb, scissor);
      }
      cursor += width;
    }
  }

  private static int shade(int argb) {
    int alpha = Math.round((argb >>> 24) * SHADOW_ALPHA);
    return (alpha << 24) | ((argb >> 2) & 0x003F3F3F);
  }

  private static void glyphs(
      GuiRenderState state,
      FontFace face,
      String text,
      Matrix3x2fc pose,
      float x,
      float y,
      float scale,
      float raster,
      int argb,
      int dark,
      boolean shadow,
      boolean italic,
      ScreenRectangle scissor) {
    TextRun run = face.layout(text, raster);
    if (run.isEmpty()) {
      return;
    }
    if (shadow && ShapePipeline.usable()) {
      place(state, run, pose, x + SHADOW, y + SHADOW, scale, raster, dark, italic, scissor);
    }
    place(state, run, pose, x, y, scale, raster, argb, italic, scissor);
  }

  private static void place(
      GuiRenderState state,
      TextRun run,
      Matrix3x2fc pose,
      float x,
      float y,
      float scale,
      float raster,
      int argb,
      boolean italic,
      ScreenRectangle scissor) {
    Matrix3x2f matrix = new Matrix3x2f(pose).translate(x, y + BASELINE);
    if (italic) {
      matrix.mul(MATRIX.set(1.0F, 0.0F, SLANT, 1.0F, 0.0F, 0.0F));
    }
    matrix.scale(scale);
    int x0 = (int) Math.floor(run.minX - 2.0F);
    int y0 = (int) Math.floor(run.minY - 2.0F);
    int x1 = (int) Math.ceil(run.maxX + 2.0F);
    int y1 = (int) Math.ceil(run.maxY + 2.0F);
    ScreenRectangle bounds =
        new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(matrix);
    if (scissor != null) {
      bounds = scissor.intersection(bounds);
    }
    if (bounds == null) {
      return;
    }
    for (int page = run.firstPage; page <= run.lastPage; page++) {
      state.addGuiElement(
          new TextRenderState(
              matrix,
              run,
              page,
              0.0F,
              0.0F,
              raster / scale,
              argb,
              false,
              0,
              McCanvas.fontTextures().setup(page),
              scissor,
              bounds,
              0.0F,
              Cut.NONE));
    }
  }

  private static void rule(
      GuiRenderState state,
      Matrix3x2fc pose,
      float x,
      float y,
      float width,
      int argb,
      ScreenRectangle scissor) {
    if (!ShapePipeline.usable() || width <= 0.0F) {
      return;
    }
    ScreenRectangle bounds =
        new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(width) + 1, 2)
            .transformMaxBounds(pose);
    if (scissor != null) {
      bounds = scissor.intersection(bounds);
    }
    if (bounds != null) {
      state.addGuiElement(
          new ShapeRenderState(
              pose,
              x + width * 0.5F,
              y + RULE * 0.5F,
              width * 0.5F,
              RULE * 0.5F,
              0.5F,
              0.0F,
              0.0F,
              0.0F,
              0.0F,
              argb,
              argb,
              scissor,
              bounds,
              0.0F,
              Cut.NONE));
    }
  }
}
