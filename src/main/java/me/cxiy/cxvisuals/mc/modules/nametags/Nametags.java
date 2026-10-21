package me.cxiy.cxvisuals.mc.modules.nametags;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.text.FontFace;
import me.cxiy.cxvisuals.gui.text.GlyphAtlas;
import me.cxiy.cxvisuals.gui.text.TextRun;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.compat.IrisCompat;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.modules.environment.WorldTextPipeline;
import me.cxiy.cxvisuals.modules.nametags.NametagsModule;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Nametags {
  private static final FontStyle STYLE = FontStyle.LABEL;
  private static final int CAPACITY = 256;
  private static final int MAX_QUADS = 8192;
  private static final int VERTEX = 28;
  private static final int QUAD = VERTEX * 6;
  private static final int MAX_PAGES = 16;
  private static final int MAX_PARSED = 512;
  private static final int SEGMENTS = 12;
  private static final float RASTER = 2.0F;
  private static final float TEXEL = 1.0F / GlyphAtlas.PAGE_SIZE;
  private static final float CAP = 6.7F;
  private static final float BASELINE = 7.0F;
  private static final float PAD_X = 4.5F;
  private static final float PLATE_TOP = -2.5F;
  private static final float PLATE_HEIGHT = 12.5F;
  private static final float DIM = 0.5F;
  private static final int DISC = 48;
  private static final long SCRAMBLE_NANOS = 50_000_000L;
  private static final String POOL =
      "ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghkmnopqrstuvwxyz0123456789#$%&?@";

  private static final ByteBuffer DATA =
      ByteBuffer.allocateDirect(MAX_QUADS * QUAD).order(ByteOrder.nativeOrder());
  private static final Matrix4f MODEL_VIEW = new Matrix4f();
  private static final Vector3f POINT = new Vector3f();
  private static final Matrix4f[] POSES = new Matrix4f[CAPACITY];
  private static final Parsed[] TAGS = new Parsed[CAPACITY];
  private static final float[] OFFSETS = new float[CAPACITY];
  private static final boolean[] THROUGH = new boolean[CAPACITY];
  private static final Map<Component, Parsed> PARSED = new HashMap<>();
  private static final Scan SCAN = new Scan();
  private static final int[] FIRST = new int[MAX_PAGES * 2];
  private static final int[] COUNT = new int[MAX_PAGES * 2];
  private static final int[] TEXT_FIRST = new int[MAX_PAGES * 2];
  private static final int[] TEXT_COUNT = new int[MAX_PAGES * 2];

  private static NametagsModule module;
  private static GpuBuffer vertices;
  private static GlyphAtlas.Glyph disc;
  private static float unit;
  private static int count;
  private static boolean subject;
  private static int quads;

  private Nametags() {}

  private static final class Parsed {
    final String[] text = new String[SEGMENTS];
    final int[] color = new int[SEGMENTS];
    final boolean[] bold = new boolean[SEGMENTS];
    final boolean[] scrambled = new boolean[SEGMENTS];
    int segments;
    float width;
  }

  private static final class Scan implements FormattedCharSink {
    final StringBuilder run = new StringBuilder(48);
    Parsed target;
    Style current;
    boolean foreign;

    @Override
    public boolean accept(int position, Style style, int codepoint) {
      if (!style.getFont().equals(FontDescription.DEFAULT) || !face(false).covers(codepoint)) {
        foreign = true;
        return false;
      }
      if (run.length() > 0 && style != current && !style.equals(current)) {
        flush();
      }
      current = style;
      run.appendCodePoint(codepoint);
      return true;
    }

    void flush() {
      if (run.length() == 0) {
        return;
      }
      if (target.segments == SEGMENTS) {
        foreign = true;
        run.setLength(0);
        return;
      }
      int index = target.segments++;
      TextColor tint = current.getColor();
      target.text[index] = run.toString();
      target.color[index] = tint == null ? 0xFFFFFF : tint.getValue() & 0xFFFFFF;
      target.bold[index] = current.isBold();
      target.scrambled[index] = current.isObfuscated();
      target.width += face(current.isBold()).width(target.text[index]) * unit();
      run.setLength(0);
    }
  }

  public static NametagsModule create(McKeyBinder keys) {
    module = new NametagsModule(keys);
    for (int i = 0; i < CAPACITY; i++) {
      POSES[i] = new Matrix4f();
    }
    return module;
  }

  public static NametagsModule module() {
    return module;
  }

  public static void subject(boolean player) {
    subject = player;
  }

  private static FontFace face(boolean bold) {
    return bold ? McCanvas.engine().bold(STYLE) : McCanvas.engine().face(STYLE);
  }

  private static float unit() {
    if (unit == 0.0F) {
      unit = CAP / face(false).capHeight();
    }
    return unit;
  }

  private static Parsed parse(Component name) {
    Parsed known = PARSED.get(name);
    if (known != null) {
      return known.segments == 0 ? null : known;
    }
    if (PARSED.size() >= MAX_PARSED) {
      PARSED.clear();
    }
    Parsed parsed = new Parsed();
    SCAN.target = parsed;
    SCAN.current = null;
    SCAN.foreign = false;
    SCAN.run.setLength(0);
    name.getVisualOrderText().accept(SCAN);
    SCAN.flush();
    if (SCAN.foreign) {
      parsed.segments = 0;
    }
    PARSED.put(name, parsed);
    return parsed.segments == 0 ? null : parsed;
  }

  public static boolean capture(
      PoseStack poseStack,
      Vec3 attachment,
      int offset,
      Component name,
      boolean seeThrough,
      CameraRenderState camera) {
    if (IrisCompat.isRenderingShadowPass()) {
      return true;
    }
    if (module == null
        || !module.enabled.get()
        || count == CAPACITY
        || !RenderSystem.isOnRenderThread()) {
      return false;
    }
    if (!subject && !module.everything.get()) {
      return false;
    }
    if (!WorldTextPipeline.usable()) {
      return false;
    }
    Parsed parsed = parse(name);
    if (parsed == null) {
      return false;
    }
    poseStack.pushPose();
    poseStack.translate(attachment.x, attachment.y + 0.5, attachment.z);
    poseStack.mulPose(camera.orientation);
    poseStack.scale(0.025F, -0.025F, 0.025F);
    POSES[count].set(poseStack.last().pose());
    poseStack.popPose();
    TAGS[count] = parsed;
    OFFSETS[count] = offset;
    THROUGH[count] = seeThrough;
    count++;
    return true;
  }

  public static void render(GameRenderer renderer) {
    int total = count;
    count = 0;
    if (total == 0 || module == null) {
      return;
    }
    if (IrisCompat.isRenderingShadowPass()) {
      clear(total);
      return;
    }
    RenderTarget main = renderer.mainRenderTarget();
    GpuTextureView color = main.getColorTextureView();
    GpuTextureView depth = main.getDepthTextureView();
    if (color == null || depth == null) {
      clear(total);
      return;
    }
    CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
    if (disc == null) {
      disc = disc();
    }
    float plate = module.plate();
    for (int i = 0; i < total; i++) {
      Parsed tag = TAGS[i];
      for (int s = 0; s < tag.segments; s++) {
        if (!tag.scrambled[s]) {
          face(tag.bold[s]).layout(tag.text[s], RASTER);
        }
      }
    }
    int pages = Math.min(MAX_PAGES, McCanvas.engine().atlas().pageCount());
    DATA.clear();
    quads = 0;
    for (int pass = 0; pass < 2; pass++) {
      boolean flat = pass == 0;
      for (int page = 0; page < pages; page++) {
        int slot = pass * MAX_PAGES + page;
        FIRST[slot] = quads * 6;
        if (plate > 0.0F && disc.page() == page) {
          for (int i = 0; i < total; i++) {
            if (THROUGH[i] == flat) {
              plate(POSES[i], TAGS[i], OFFSETS[i], plate);
            }
          }
        }
        COUNT[slot] = quads * 6 - FIRST[slot];
      }
    }
    for (int pass = 0; pass < 2; pass++) {
      boolean flat = pass == 0;
      for (int page = 0; page < pages; page++) {
        int slot = pass * MAX_PAGES + page;
        TEXT_FIRST[slot] = quads * 6;
        for (int i = 0; i < total; i++) {
          if (flat && !THROUGH[i]) {
            continue;
          }
          float alpha = flat || !THROUGH[i] ? DIM : 1.0F;
          letters(POSES[i], TAGS[i], OFFSETS[i], page, alpha);
        }
        TEXT_COUNT[slot] = quads * 6 - TEXT_FIRST[slot];
      }
    }
    clear(total);
    if (quads == 0) {
      return;
    }
    DATA.flip();
    McCanvas.syncFonts();
    GpuDevice device = RenderSystem.getDevice();
    if (vertices == null) {
      vertices =
          device.createBuffer(
              () -> "cxvisuals_nametags",
              GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
              (long) MAX_QUADS * QUAD);
    }
    CommandEncoder encoder = device.createCommandEncoder();
    encoder.writeToBuffer(vertices.slice(0, DATA.remaining()), DATA);
    GpuBufferSlice transforms =
        RenderSystem.getDynamicUniforms().writeTransform(MODEL_VIEW.set(camera.viewRotationMatrix));
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals nametags", color, Optional.empty(), depth, OptionalDouble.empty())) {
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("DynamicTransforms", transforms);
      pass.setVertexBuffer(0, vertices.slice());
      for (int stage = 0; stage < 2; stage++) {
        pass.setPipeline(stage == 0 ? WorldTextPipeline.flat : WorldTextPipeline.depth);
        for (int layer = 0; layer < 2; layer++) {
          int[] first = layer == 0 ? FIRST : TEXT_FIRST;
          int[] amount = layer == 0 ? COUNT : TEXT_COUNT;
          for (int page = 0; page < pages; page++) {
            int slot = stage * MAX_PAGES + page;
            if (amount[slot] > 0) {
              pass.bindTexture(
                  "Sampler0",
                  McCanvas.fontView(page),
                  RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
              pass.draw(amount[slot], 1, first[slot], 0);
            }
          }
        }
      }
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.warn("Nametag pass failed: {}", e.toString());
    }
  }

  private static void clear(int total) {
    for (int i = 0; i < total; i++) {
      TAGS[i] = null;
    }
  }

  private static GlyphAtlas.Glyph disc() {
    byte[] coverage = new byte[DISC * DISC];
    float centre = DISC * 0.5F;
    float radius = centre - 1.0F;
    for (int y = 0; y < DISC; y++) {
      for (int x = 0; x < DISC; x++) {
        float distance = (float) Math.hypot(x + 0.5F - centre, y + 0.5F - centre);
        coverage[y * DISC + x] =
            (byte) Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, radius - distance + 0.5F)));
      }
    }
    return McCanvas.engine().atlas().place(coverage, DISC, DISC, 0.0F, 0.0F);
  }

  private static void plate(Matrix4f pose, Parsed tag, float offset, float alpha) {
    if (quads + 3 > MAX_QUADS) {
      return;
    }
    float left = -tag.width * 0.5F - PAD_X;
    float right = tag.width * 0.5F + PAD_X;
    float top = offset + PLATE_TOP;
    float bottom = top + PLATE_HEIGHT;
    float radius = Math.min(PLATE_HEIGHT * 0.5F, (right - left) * 0.5F);
    float u0 = (disc.u() + 1.0F) * TEXEL;
    float u1 = (disc.u() + disc.width() - 1.0F) * TEXEL;
    float middle = (u0 + u1) * 0.5F;
    float v0 = (disc.v() + 1.0F) * TEXEL;
    float v1 = (disc.v() + disc.height() - 1.0F) * TEXEL;
    int shade = Math.round(alpha * 255.0F);
    quad(pose, left, top, left + radius, bottom, u0, v0, middle, v1, 0, shade);
    quad(pose, left + radius, top, right - radius, bottom, middle, v0, middle, v1, 0, shade);
    quad(pose, right - radius, top, right, bottom, middle, v0, u1, v1, 0, shade);
  }

  private static void letters(Matrix4f pose, Parsed tag, float offset, int page, float alpha) {
    float scale = unit();
    float cursor = -tag.width * 0.5F;
    float baseline = offset + BASELINE;
    int shade = Math.round(alpha * 255.0F);
    for (int s = 0; s < tag.segments; s++) {
      FontFace face = face(tag.bold[s]);
      String text = tag.scrambled[s] ? scramble(tag.text[s]) : tag.text[s];
      TextRun run = face.layout(text, RASTER);
      if (run.lastPage >= page && run.firstPage <= page) {
        int rgb = tag.color[s];
        for (int g = 0; g < run.count && quads < MAX_QUADS; g++) {
          GlyphAtlas.Glyph glyph = run.glyphs[g];
          if (glyph.page() != page) {
            continue;
          }
          float left = cursor + run.xs[g] * scale;
          float top = baseline + run.ys[g] * scale;
          float right = left + glyph.width() * run.glyphScale * scale;
          float bottom = top + glyph.height() * run.glyphScale * scale;
          quad(
              pose,
              left,
              top,
              right,
              bottom,
              glyph.u() * TEXEL,
              glyph.v() * TEXEL,
              (glyph.u() + glyph.width()) * TEXEL,
              (glyph.v() + glyph.height()) * TEXEL,
              rgb,
              shade);
        }
      }
      cursor += face.width(tag.text[s]) * scale;
    }
  }

  private static String scramble(String text) {
    int beat = (int) (System.nanoTime() / SCRAMBLE_NANOS);
    StringBuilder out = new StringBuilder(text.length());
    for (int k = 0; k < text.length(); k++) {
      int mix = (k + 1) * 0x9E3779B1 ^ beat * 0x85EBCA6B;
      mix = (mix ^ (mix >>> 15)) * 0x2C1B3C6D;
      out.append(
          text.charAt(k) == ' ' ? ' ' : POOL.charAt(((mix ^ (mix >>> 12)) >>> 1) % POOL.length()));
    }
    return out.toString();
  }

  private static void quad(
      Matrix4f pose,
      float left,
      float top,
      float right,
      float bottom,
      float u0,
      float v0,
      float u1,
      float v1,
      int rgb,
      int alpha) {
    if (quads >= MAX_QUADS) {
      return;
    }
    vertex(pose, left, top, u0, v0, rgb, alpha);
    vertex(pose, left, bottom, u0, v1, rgb, alpha);
    vertex(pose, right, bottom, u1, v1, rgb, alpha);
    vertex(pose, left, top, u0, v0, rgb, alpha);
    vertex(pose, right, bottom, u1, v1, rgb, alpha);
    vertex(pose, right, top, u1, v0, rgb, alpha);
    quads++;
  }

  private static void vertex(
      Matrix4f pose, float x, float y, float u, float v, int rgb, int alpha) {
    pose.transformPosition(POINT.set(x, y, 0.0F));
    DATA.putFloat(POINT.x).putFloat(POINT.y).putFloat(POINT.z);
    DATA.put((byte) (rgb >> 16)).put((byte) (rgb >> 8)).put((byte) rgb).put((byte) alpha);
    DATA.putFloat(u).putFloat(v);
    DATA.putShort((short) 0).putShort((short) 0);
  }
}
