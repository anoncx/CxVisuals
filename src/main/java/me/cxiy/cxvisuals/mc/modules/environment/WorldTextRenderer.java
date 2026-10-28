package me.cxiy.cxvisuals.mc.modules.environment;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.text.FontFace;
import me.cxiy.cxvisuals.gui.text.GlyphAtlas;
import me.cxiy.cxvisuals.gui.text.TextRun;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.modules.environment.EnvironmentModule;
import me.cxiy.cxvisuals.modules.environment.lyrics.Effects;
import me.cxiy.cxvisuals.modules.environment.lyrics.Stanza;
import me.cxiy.cxvisuals.modules.environment.lyrics.WorldLyrics;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class WorldTextRenderer {
  static final FontStyle STYLE = FontStyle.TITLE;

  private static final int MAX_QUADS = 2048;
  private static final int VERTEX = 28;
  private static final int QUAD = VERTEX * 6;
  private static final int MAX_PAGES = 16;
  private static final float RASTER = 2.0F;
  private static final float TEXEL = 1.0F / GlyphAtlas.PAGE_SIZE;
  private static final float UNSUNG = 0.45F;
  private static final float PULSE = 0.08F;
  private static final float GONE = 0.004F;
  private static final float SHADOW = 0.4F;
  private static final float SHADOW_X = 0.8F;
  private static final float SHADOW_Y = 1.2F;

  private static final ByteBuffer DATA =
      ByteBuffer.allocateDirect(MAX_QUADS * QUAD).order(ByteOrder.nativeOrder());
  private static final Effects.Sample SAMPLE = new Effects.Sample();
  private static final Matrix4f MODEL_VIEW = new Matrix4f();
  private static final int[] FIRST = new int[MAX_PAGES];
  private static final int[] COUNT = new int[MAX_PAGES];

  private static GpuBuffer vertices;

  private WorldTextRenderer() {}

  static void release() {
    if (vertices != null) {
      vertices.close();
      vertices = null;
    }
  }

  static int draw(
      RenderTarget main,
      Matrix4fc viewRotation,
      Vec3 camera,
      WorldLyrics world,
      EnvironmentModule module,
      long nowNanos) {
    GpuTextureView color = main.getColorTextureView();
    if (color == null) {
      return 0;
    }
    FontFace face = McCanvas.engine().face(STYLE);
    float cap = face.capHeight() * 0.5F;
    float opacity = (float) (module.opacity.get() / 100.0);
    boolean calm = module.calm.get();
    EnvironmentModule.Arrive arrive = module.arrive.get();
    EnvironmentModule.Leave leave = module.leave.get();

    for (int i = 0; i < WorldLyrics.CAPACITY; i++) {
      Stanza s = world.stanza(i);
      if (s.alive) {
        for (int part = 0; part < s.words; part++) {
          face.layout(s.parts[part], RASTER);
        }
      }
    }
    int pages = Math.min(MAX_PAGES, McCanvas.engine().atlas().pageCount());
    DATA.clear();
    int quads = 0;
    for (int page = 0; page < pages; page++) {
      FIRST[page] = quads * 6;
      for (int i = 0; i < WorldLyrics.CAPACITY && quads < MAX_QUADS; i++) {
        Stanza s = world.stanza(i);
        if (s.alive) {
          quads =
              stanza(
                  s, face, page, true, cap, opacity, calm, arrive, leave, camera, nowNanos, quads);
        }
      }
      for (int i = 0; i < WorldLyrics.CAPACITY && quads < MAX_QUADS; i++) {
        Stanza s = world.stanza(i);
        if (s.alive) {
          quads =
              stanza(
                  s, face, page, false, cap, opacity, calm, arrive, leave, camera, nowNanos, quads);
        }
      }
      COUNT[page] = quads * 6 - FIRST[page];
    }
    if (quads == 0) {
      return 0;
    }
    DATA.flip();
    McCanvas.syncFonts();

    GpuDevice device = RenderSystem.getDevice();
    if (vertices == null) {
      vertices =
          device.createBuffer(
              () -> "cxvisuals_world_text",
              GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
              (long) MAX_QUADS * QUAD);
    }
    CommandEncoder encoder = device.createCommandEncoder();
    encoder.writeToBuffer(vertices.slice(0, DATA.remaining()), DATA);
    GpuBufferSlice transforms =
        RenderSystem.getDynamicUniforms().writeTransform(MODEL_VIEW.set(viewRotation));
    GpuTextureView depth = module.occlude.get() ? main.getDepthTextureView() : null;
    try (RenderPass pass =
        depth == null
            ? encoder.createRenderPass(() -> "CxVisuals world text", color, Optional.empty())
            : encoder.createRenderPass(
                () -> "CxVisuals world text",
                color,
                Optional.empty(),
                depth,
                OptionalDouble.empty())) {
      Gpu.use(pass, depth == null ? WorldTextPipeline.flat : WorldTextPipeline.depth);
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("DynamicTransforms", transforms);
      pass.setVertexBuffer(0, vertices.slice());
      for (int page = 0; page < pages; page++) {
        if (COUNT[page] > 0) {
          Gpu.bind(
              pass,
              "Sampler0",
              McCanvas.fontView(page),
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
          pass.draw(COUNT[page], 1, FIRST[page], 0);
        }
      }
    }
    return quads;
  }

  private static int stanza(
      Stanza s,
      FontFace face,
      int page,
      boolean shadow,
      float cap,
      float opacity,
      boolean calm,
      EnvironmentModule.Arrive arrive,
      EnvironmentModule.Leave leave,
      Vec3 camera,
      long now,
      int quads) {
    float age = (now - s.born) / 1.0e9F;
    float leaving = s.leaving == Long.MAX_VALUE ? -1.0F : (now - s.leaving) / 1.0e9F;
    float baseX = (float) (s.x - camera.x);
    float baseY = (float) (s.y - camera.y);
    float baseZ = (float) (s.z - camera.z);
    float cos = (float) Math.cos(s.roll);
    float sin = (float) Math.sin(s.roll);
    float scale = s.scale;
    for (int part = 0; part < s.words; part++) {
      TextRun run = face.layout(s.parts[part], RASTER);
      if (run.lastPage < page || run.firstPage > page) {
        continue;
      }
      float brightness =
          !s.whole || part < s.sung
              ? 1.0F
              : part == s.sung ? UNSUNG + (1.0F - UNSUNG) * s.fill : UNSUNG;
      float swell =
          s.whole && part == s.sung && !calm
              ? 1.0F + PULSE * (1.0F - s.fill) * (1.0F - s.fill) * (1.0F - s.fill)
              : 1.0F;
      float centreX = s.lefts[part] + s.widths[part] * 0.5F;
      float centreY = s.tops[part];
      for (int g = 0; g < run.count && quads < MAX_QUADS; g++) {
        GlyphAtlas.Glyph glyph = run.glyphs[g];
        if (glyph.page() != page) {
          continue;
        }
        Effects.sample(
            arrive, leave, calm, s.letters[part] + g, s.letterCount, age, leaving, SAMPLE);
        float alpha = opacity * SAMPLE.alpha * brightness;
        if (alpha <= GONE || SAMPLE.erosion >= 1.0F) {
          continue;
        }
        float left = centreX + (s.lefts[part] + run.xs[g] - centreX) * swell + SAMPLE.drift;
        float top = centreY + (cap + run.ys[g]) * swell - SAMPLE.lift;
        float right = left + glyph.width() * run.glyphScale * swell;
        float bottom = top + glyph.height() * run.glyphScale * swell;
        float u0 = glyph.u() * TEXEL;
        float v0 = glyph.v() * TEXEL;
        float u1 = (glyph.u() + glyph.width()) * TEXEL;
        float v1 = (glyph.v() + glyph.height()) * TEXEL;
        int shade = Math.min(255, Math.round(alpha * 255.0F));
        short erosion = (short) Math.round(Math.max(0.0F, SAMPLE.erosion) * 1000.0F);
        short seed = (short) ((s.seed + g * 7) & 0x3FF);
        if (shadow) {
          int dark = Math.round(shade * SHADOW);
          quad(
              s,
              baseX,
              baseY,
              baseZ,
              cos,
              sin,
              scale,
              left + SHADOW_X,
              top + SHADOW_Y,
              right + SHADOW_X,
              bottom + SHADOW_Y,
              u0,
              v0,
              u1,
              v1,
              0,
              dark,
              erosion,
              seed);
        } else {
          quad(
              s, baseX, baseY, baseZ, cos, sin, scale, left, top, right, bottom, u0, v0, u1, v1,
              0xFF, shade, erosion, seed);
        }
        quads++;
      }
    }
    return quads;
  }

  private static void quad(
      Stanza s,
      float baseX,
      float baseY,
      float baseZ,
      float cos,
      float sin,
      float scale,
      float left,
      float top,
      float right,
      float bottom,
      float u0,
      float v0,
      float u1,
      float v1,
      int tone,
      int alpha,
      short erosion,
      short seed) {
    vertex(s, baseX, baseY, baseZ, cos, sin, scale, left, top, u0, v0, tone, alpha, erosion, seed);
    vertex(
        s, baseX, baseY, baseZ, cos, sin, scale, left, bottom, u0, v1, tone, alpha, erosion, seed);
    vertex(
        s, baseX, baseY, baseZ, cos, sin, scale, right, bottom, u1, v1, tone, alpha, erosion, seed);
    vertex(s, baseX, baseY, baseZ, cos, sin, scale, left, top, u0, v0, tone, alpha, erosion, seed);
    vertex(
        s, baseX, baseY, baseZ, cos, sin, scale, right, bottom, u1, v1, tone, alpha, erosion, seed);
    vertex(s, baseX, baseY, baseZ, cos, sin, scale, right, top, u1, v0, tone, alpha, erosion, seed);
  }

  private static void vertex(
      Stanza s,
      float baseX,
      float baseY,
      float baseZ,
      float cos,
      float sin,
      float scale,
      float localX,
      float localY,
      float u,
      float v,
      int tone,
      int alpha,
      short erosion,
      short seed) {
    float x = (localX * cos - localY * sin) * scale;
    float y = (localX * sin + localY * cos) * scale;
    DATA.putFloat(baseX + s.rx * x - s.ux * y);
    DATA.putFloat(baseY + s.ry * x - s.uy * y);
    DATA.putFloat(baseZ + s.rz * x - s.uz * y);
    DATA.put((byte) tone).put((byte) tone).put((byte) tone).put((byte) alpha);
    DATA.putFloat(u).putFloat(v);
    DATA.putShort(erosion).putShort(seed);
  }
}
