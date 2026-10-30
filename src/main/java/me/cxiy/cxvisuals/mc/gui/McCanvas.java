package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Hsv;
import me.cxiy.cxvisuals.gui.StyledText;
import me.cxiy.cxvisuals.gui.TileSheet;
import me.cxiy.cxvisuals.gui.map.ChartBatch;
import me.cxiy.cxvisuals.gui.map.TerrainFrame;
import me.cxiy.cxvisuals.gui.spatial.Warp;
import me.cxiy.cxvisuals.gui.text.FontFace;
import me.cxiy.cxvisuals.gui.text.TextEngine;
import me.cxiy.cxvisuals.gui.text.TextRun;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.mixin.GuiGraphicsExtractorAccessor;
import me.cxiy.cxvisuals.mc.modules.inventory.Stacks;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;
import me.cxiy.cxvisuals.modules.map.MapAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class McCanvas implements Canvas {
  private static final float HUE_BAR = -3.0F;
  private static final float COLOUR_FIELD = -1.0F;
  private static final String FONT_PATH = "/assets/cxvisuals/font/";
  private static final TextEngine ENGINE =
      new TextEngine(file -> McCanvas.class.getResourceAsStream(FONT_PATH + file));
  private static final FontTextures TEXTURES = new FontTextures();
  private static final FaceTextures FACES = new FaceTextures();
  private static final IconTextures ICONS = new IconTextures();
  private static final TileTextures TILES = new TileTextures();
  private static final FeedTextures FEEDS = new FeedTextures();
  private static final MapTextures MAPS = new MapTextures();
  private static final MobFaces MOBS = new MobFaces();
  private static final TerrainRenderer TERRAIN = new TerrainRenderer(MAPS);
  private static final int SPRITE_VALID = 1 << 29;
  private static final float TERRAIN_SAMPLES = 1.5F;
  private static final float TERRAIN_LIMIT = 4096.0F;
  private static final ChatLines CHAT = new ChatLines();
  private static final FontStyle[] FONTS = FontStyle.values();
  private static ItemStack[] held;
  private static MapRenderState[] maps;
  private static final float ITEM_FADE_FLOOR = 0.05F;
  private static final int DISSOLVE_NEST = 8;
  private static final float DISSOLVE_MIN = 0.001F;
  private static final float[] FLAT = {1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F};
  private static final float[] SCALES = {1.0F, 1.5F, 2.0F};
  private static final int STACK = 16;
  private static final float ITEM_EDGE = 1.0F;
  private static final float EDGE_PAD = 1.0F;
  private static final float CURVE_PAD = 1.5F;
  private static final float MITER_REACH = 2.2F;
  private static final float LINE_EXTRA = 3.0F;
  private static final float GLASS_BLUR = 7.0F;
  private static final float GLASS_FALLBACK_TINT = 0.72F;
  private static final float SUPERSAMPLE = 2.0F;
  private static final int LARGEST_PREVIEW = 2048;

  private static final float ITEM_SIZE = 16.0F;
  private static final float NAME_SCALE = 1.3F;
  private static final float NAME_LINE = 8.0F;
  private static final int HITS = 16;
  private static final FormattedCharSequence[] HIT_TEXT = new FormattedCharSequence[HITS];
  private static final Matrix3x2f[] HIT_POSE = new Matrix3x2f[HITS];
  private static final Matrix3x2f SLANT = new Matrix3x2f(1.0F, 0.0F, -0.2F, 1.0F, 0.0F, 0.0F);
  private static final float SLANT_REACH = 4.0F;
  private static final float RULE = 1.0F;
  private static final float UNDERLINE_DROP = 1.5F;
  private static final float STRIKE_RISE = 0.42F;
  private static final long SCRAMBLE_NANOS = 50_000_000L;
  private static final String[] SCRAMBLE =
      singles("ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghkmnopqrstuvwxyz0123456789#$%&?@");

  private static int hits;
  private static float glassBlur = GLASS_BLUR;
  private static boolean interactive;

  private static ModelPreviews previews;
  private static final StagePreviews[] STAGES = new StagePreviews[2];
  private static final float STAND_IN_REACH = 1.3F;
  private static final Quaternionf STAND_IN_POSE = new Quaternionf();
  private static final Quaternionf STAND_IN_TILT = new Quaternionf();
  private static final Vector3f STAND_IN_LIFT = new Vector3f();
  private static final float FIGURE_YAW = 30.0F;
  private static final float FIGURE_PITCH = -5.0F;
  private static final float FIGURE_TURN = 45.0F;
  private static final float FIGURE_NOD = 15.0F;
  private static final float FIGURE_FOLLOW = 0.25F;
  private static final float FIGURE_SCALE = 0.97F;
  private static final float FIGURE_HEIGHT = 2.125F;
  private static final float FIGURE_PIVOT = -1.0625F;
  private static EntityModelSet figureModels;
  private static Model.Simple figureWide;
  private static Model.Simple figureSlim;
  private static Supplier<PlayerSkin> figureSkin;
  private static User figureUser;
  private static ItemStack[] items;

  private final float[] baselines = new float[FONTS.length];
  private final float[] alphas = new float[STACK];
  private final int[] contrasts = new int[STACK];
  private final ScreenRectangle[] clips = new ScreenRectangle[STACK];
  private final float[] cuts = new float[STACK * 4];
  private final float[] layers = new float[STACK];
  private final Warp warp = new Warp();
  private boolean tilting;
  private boolean dissolving;
  private int dissolveDepth;
  private final boolean[] dissolveFallback = new boolean[DISSOLVE_NEST];
  private int layerDepth;
  private float height;
  private float unit = 1.0F;
  private float centreX;
  private float centreY;

  private GuiGraphicsExtractor graphics;
  private GuiRenderState state;
  private Matrix3x2fStack pose;
  private Matrix3x2f snapshot;
  private float uiScale = 1.0F;
  private float alpha = 1.0F;
  private int alphaDepth;
  private int clipDepth;
  private boolean shapes;
  private boolean adaptive;
  private boolean behindResolved;
  private GpuTextureView behindView;
  private ScreenRectangle screen;
  private int contrastDepth;
  private final Consumer<Style> insertions =
      style -> {
        if (style.getInsertion() != null && Minecraft.getInstance().hasShiftDown()) {
          graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
      };

  static {
    for (int i = 0; i < HITS; i++) {
      HIT_POSE[i] = new Matrix3x2f();
    }
  }

  private static String[] singles(String pool) {
    String[] out = new String[pool.length()];
    for (int i = 0; i < out.length; i++) {
      out[i] = String.valueOf(pool.charAt(i));
    }
    return out;
  }

  static FontTextures fontTextures() {
    return TEXTURES;
  }

  public McCanvas() {
    for (int i = 0; i < FONTS.length; i++) {
      baselines[i] = (FONTS[i].size + LINE_EXTRA + ENGINE.face(FONTS[i]).capHeight()) * 0.5F;
    }
  }

  public static void prewarm() {
    try {
      for (FontStyle style : FONTS) {
        ENGINE.face(style);
      }
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.error("Settings window resources failed to load", e);
    }
  }

  public static void previews(ModelPreviews source) {
    previews = source;
  }

  public static void stages(int scene, StagePreviews source) {
    STAGES[scene] = source;
  }

  public static void chat(int messageId, Component message) {
    CHAT.put(messageId, message);
  }

  public static TextEngine engine() {
    return ENGINE;
  }

  public static GpuTextureView fontView(int page) {
    return TEXTURES.view(page);
  }

  public static void syncFonts() {
    TEXTURES.sync(ENGINE.atlas());
  }

  public static void glassBlur(float units) {
    glassBlur = units > 0.0F ? units : GLASS_BLUR;
  }

  public static void interactive(boolean on) {
    interactive = on;
    hits = 0;
  }

  public static void clicks(ActiveTextCollector collector) {
    ActiveTextCollector.Parameters base = collector.defaultParameters();
    for (int i = 0; i < hits; i++) {
      collector.accept(TextAlignment.LEFT, 0, 0, base.withPose(HIT_POSE[i]), HIT_TEXT[i]);
    }
  }

  public static void items(ItemStack[] source) {
    items = source;
  }

  public static float interfaceScale(int windowHeight) {
    if (windowHeight >= 1300) {
      return SCALES[2];
    }
    return windowHeight >= 900 ? SCALES[1] : SCALES[0];
  }

  public void begin(GuiGraphicsExtractor target, float interfaceScale, int guiScale) {
    graphics = target;
    state = ((GuiGraphicsExtractorAccessor) target).cxvisuals$guiRenderState();
    pose = target.pose();
    shapes = ShapePipeline.usable();
    adaptive = shapes && TextPipeline.usable();
    contrastDepth = 0;
    uiScale = interfaceScale;
    alpha = 1.0F;
    alphaDepth = 0;
    clipDepth = 0;
    behindResolved = false;
    if (screen == null
        || screen.width() != target.guiWidth()
        || screen.height() != target.guiHeight()) {
      screen = new ScreenRectangle(0, 0, target.guiWidth(), target.guiHeight());
    }
    unit = uiScale / guiScale;
    centreX = target.guiWidth() * 0.5F;
    centreY = target.guiHeight() * 0.5F;
    tilting = false;
    dissolving = false;
    dissolveDepth = 0;
    layerDepth = 0;
    height = 0.0F;
    pose.pushMatrix();
    pose.scale(unit, unit);
    snapshot = null;
  }

  public void end() {
    while (clipDepth > 0) {
      popClip();
    }
    flatten();
    if (dissolving) {
      dissolving = false;
      GuiTilt.end(graphics);
    }
    pose.popMatrix();
    TEXTURES.sync(ENGINE.atlas());
    graphics = null;
    state = null;
    pose = null;
    snapshot = null;
  }

  private Matrix3x2f pose() {
    if (snapshot == null) {
      snapshot = new Matrix3x2f(pose);
    }
    return snapshot;
  }

  private int tint(int argb) {
    if (alpha >= 1.0F) {
      return argb;
    }
    int a = Math.round((argb >>> 24) * alpha);
    return (a << 24) | (argb & 0x00FFFFFF);
  }

  private float depth() {
    return tilting ? height : 0.0F;
  }

  private void flatten() {
    if (tilting) {
      tilting = false;
      GuiTilt.end(graphics);
    }
  }

  private void project(Matrix3x2f matrix, float x, float y) {
    warp.map(
        matrix.m00() * x + matrix.m10() * y + matrix.m20(),
        matrix.m01() * x + matrix.m11() * y + matrix.m21(),
        height);
  }

  private ScreenRectangle bounds(
      Matrix3x2f matrix, float left, float top, float right, float bottom) {
    int x0 = (int) Math.floor(left);
    int y0 = (int) Math.floor(top);
    int x1 = (int) Math.ceil(right);
    int y1 = (int) Math.ceil(bottom);
    ScreenRectangle rectangle =
        new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(matrix);
    return clipDepth > 0 ? clips[clipDepth - 1].intersection(rectangle) : rectangle;
  }

  private void shape(
      float x,
      float y,
      float w,
      float h,
      float radius,
      float softness,
      float border,
      float fade,
      float pad,
      int argb,
      int bottomArgb) {
    int color = tint(argb);
    int colorBottom = tint(bottomArgb);
    if (((color | colorBottom) >>> 24) == 0 || w <= 0.0F || h <= 0.0F) {
      return;
    }
    if (!shapes) {
      if (softness <= 0.0F && border <= 0.0F) {
        graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
      }
      return;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds = bounds(matrix, x - pad, y - pad, x + w + pad, y + h + pad);
    if (bounds == null) {
      return;
    }
    float halfWidth = w * 0.5F;
    float halfHeight = h * 0.5F;
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    state.addGuiElement(
        new ShapeRenderState(
            matrix,
            x + halfWidth,
            y + halfHeight,
            halfWidth,
            halfHeight,
            pad,
            radius,
            softness,
            border,
            fade,
            color,
            colorBottom,
            scissor,
            bounds,
            depth(),
            cut(matrix)));
  }

  @Override
  public void roundRect(float x, float y, float w, float h, float radius, int argb) {
    shape(x, y, w, h, radius, 0.0F, 0.0F, 0.0F, EDGE_PAD, argb, argb);
  }

  @Override
  public void spectrum(float x, float y, float w, float h, float radius, float hue) {
    int pure = hue < 0.0F ? 0xFFFFFFFF : Hsv.argb(hue, 1.0F, 1.0F);
    shape(
        x, y, w, h, radius, 0.0F, hue < 0.0F ? HUE_BAR : COLOUR_FIELD, 0.0F, EDGE_PAD, pure, pure);
  }

  @Override
  public void roundRectGradient(
      float x, float y, float w, float h, float radius, int topArgb, int bottomArgb) {
    shape(x, y, w, h, radius, 0.0F, 0.0F, 0.0F, EDGE_PAD, topArgb, bottomArgb);
  }

  @Override
  public void rim(
      float x, float y, float w, float h, float radius, float width, float fade, int argb) {
    if (shapes) {
      shape(x, y, w, h, radius, 0.0F, width, fade, EDGE_PAD, argb, argb);
    }
  }

  @Override
  public void glass(float x, float y, float w, float h, float radius, int tintArgb) {
    TextureSetup backdrop = shapes && GlassPipeline.usable() ? Backdrop.request() : null;
    if (backdrop == null) {
      int alpha = Math.max(tintArgb >>> 24, Math.round(255.0F * GLASS_FALLBACK_TINT));
      roundRect(x, y, w, h, radius, (alpha << 24) | (tintArgb & 0x00FFFFFF));
      return;
    }
    if (alpha <= 0.002F || w <= 0.0F || h <= 0.0F) {
      return;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds =
        bounds(matrix, x - EDGE_PAD, y - EDGE_PAD, x + w + EDGE_PAD, y + h + EDGE_PAD);
    if (bounds == null) {
      return;
    }
    float halfWidth = w * 0.5F;
    float halfHeight = h * 0.5F;
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    state.addGuiElement(
        new GlassRenderState(
            matrix,
            x + halfWidth,
            y + halfHeight,
            halfWidth,
            halfHeight,
            radius,
            glassBlur,
            alpha,
            tintArgb,
            backdrop,
            scissor,
            bounds,
            depth(),
            cut(matrix)));
  }

  @Override
  public void roundRectBorder(
      float x, float y, float w, float h, float radius, float borderWidth, int argb) {
    shape(x, y, w, h, radius, 0.0F, borderWidth, 0.0F, EDGE_PAD, argb, argb);
  }

  @Override
  public void shadow(
      float x, float y, float w, float h, float radius, float blur, float offsetY, int argb) {
    shape(x, y + offsetY, w, h, radius, blur, 0.0F, 0.0F, blur, argb, argb);
  }

  @Override
  public void text(FontStyle style, String text, float x, float y, int argb) {
    if (text.indexOf(StyledText.MARK) >= 0) {
      styled(style, StyledText.of(text), x, y, argb);
      return;
    }
    int color = tint(argb);
    if ((color >>> 24) < 4) {
      return;
    }
    float originX = Math.round(x * uiScale) / uiScale;
    float originY = Math.round((y + baselines[style.ordinal()]) * uiScale) / uiScale;
    glyphs(ENGINE.face(style).layout(text, uiScale), pose(), originX, originY, 0.0F, color, true);
  }

  private void glyphs(
      TextRun run,
      Matrix3x2f matrix,
      float originX,
      float originY,
      float reach,
      int color,
      boolean contrast) {
    if (run.isEmpty()) {
      return;
    }
    ScreenRectangle bounds =
        bounds(
            matrix,
            originX + run.minX - 1.0F,
            originY + run.minY - 1.0F,
            originX + run.maxX + 1.0F + reach,
            originY + run.maxY + 1.0F);
    if (bounds == null) {
      return;
    }
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    GpuTextureView behind = contrast && contrastDepth > 0 && adaptive ? behind() : null;
    int plate = behind == null ? 0 : contrasts[contrastDepth - 1];
    for (int page = run.firstPage; page <= run.lastPage; page++) {
      TextureSetup setup = behind == null ? TEXTURES.setup(page) : TEXTURES.paired(page, behind);
      state.addGuiElement(
          new TextRenderState(
              matrix,
              run,
              page,
              originX,
              originY,
              uiScale,
              color,
              behind != null,
              plate,
              setup,
              scissor,
              bounds,
              depth(),
              cut(matrix)));
    }
  }

  @Override
  public float styledWidth(FontStyle style, StyledText text) {
    float width = 0.0F;
    for (int i = 0; i < text.count; i++) {
      width += face(style, text.flags[i]).width(text.parts[i]);
    }
    return width;
  }

  @Override
  public void styled(FontStyle style, StyledText text, float x, float y, int argb) {
    if ((tint(argb) >>> 24) < 4) {
      return;
    }
    float pixel = 1.0F / uiScale;
    float originY = Math.round((y + baselines[style.ordinal()]) * uiScale) / uiScale;
    float cursor = x;
    for (int i = 0; i < text.count; i++) {
      int flags = text.flags[i];
      String part = text.parts[i];
      boolean inherit = text.colors[i] == 0;
      int ink = inherit ? argb : (argb & 0xFF000000) | (text.colors[i] & 0x00FFFFFF);
      int color = tint(ink);
      FontFace face = face(style, flags);
      float width = face.width(part);
      float originX = Math.round(cursor * uiScale) / uiScale;
      boolean slanted = (flags & StyledText.ITALIC) != 0;
      Matrix3x2f matrix =
          slanted
              ? new Matrix3x2f(pose)
                  .translate(originX, originY)
                  .mul(SLANT)
                  .translate(-originX, -originY)
              : pose();
      float reach = slanted ? SLANT_REACH : 0.0F;
      if ((flags & StyledText.OBFUSCATED) != 0) {
        int length = part.length();
        float slot = width / length;
        int beat = (int) (System.nanoTime() / SCRAMBLE_NANOS);
        for (int k = 0; k < length; k++) {
          if (part.charAt(k) == ' ') {
            continue;
          }
          int mix = (k + 1) * 0x9E3779B1 ^ beat * 0x85EBCA6B;
          mix = (mix ^ (mix >>> 15)) * 0x2C1B3C6D;
          String glyph = SCRAMBLE[((mix ^ (mix >>> 12)) >>> 1) % SCRAMBLE.length];
          float left =
              Math.round((cursor + k * slot + (slot - face.width(glyph)) * 0.5F) * uiScale)
                  / uiScale;
          glyphs(face.layout(glyph, uiScale), matrix, left, originY, reach, color, inherit);
        }
      } else {
        TextRun run = face.layout(part, uiScale);
        glyphs(run, matrix, originX, originY, reach, color, inherit);
        if ((flags & StyledText.BOLD) != 0 && face == ENGINE.face(style)) {
          glyphs(run, matrix, originX + pixel, originY, reach, color, inherit);
        }
      }
      if ((flags & StyledText.UNDERLINE) != 0) {
        roundRect(cursor, originY + UNDERLINE_DROP, width, RULE, 0.0F, ink);
      }
      if ((flags & StyledText.STRIKE) != 0) {
        roundRect(cursor, originY - face.capHeight() * STRIKE_RISE, width, RULE, 0.0F, ink);
      }
      cursor += width;
    }
  }

  private static FontFace face(FontStyle style, int flags) {
    return (flags & StyledText.BOLD) != 0 ? ENGINE.bold(style) : ENGINE.face(style);
  }

  private GpuTextureView behind() {
    if (!behindResolved) {
      behindResolved = true;
      behindView = Backdrop.request(false) != null ? Backdrop.view() : null;
    }
    return behindView;
  }

  private float nameScale() {
    return Math.max(1.0F, Math.round(NAME_SCALE * uiScale)) / uiScale;
  }

  @Override
  public float nameWidth(int entityId) {
    Component label = FACES.label(entityId);
    if (label == null) {
      return -1.0F;
    }
    HudText.enter();
    float width = Minecraft.getInstance().font.width(label) * nameScale();
    HudText.exit();
    return width;
  }

  @Override
  public String clipboardText() {
    return Minecraft.getInstance().keyboardHandler.getClipboard();
  }

  @Override
  public void clipboardText(String text) {
    Minecraft.getInstance().keyboardHandler.setClipboard(text);
  }

  @Override
  public boolean name(int entityId, float x, float y, float height) {
    Component label = FACES.label(entityId);
    if (label == null) {
      return false;
    }
    int opacity = Math.round(255.0F * Math.min(1.0F, alpha));
    if (opacity < 8) {
      return true;
    }
    float scale = nameScale();
    pose.pushMatrix();
    pose.translate(x, y + (height - NAME_LINE * scale) * 0.5F);
    pose.scale(scale, scale);
    HudText.enter();
    graphics.text(Minecraft.getInstance().font, label, 0, 0, (opacity << 24) | 0x00FFFFFF, true);
    HudText.exit();
    pose.popMatrix();
    return true;
  }

  @Override
  public boolean face(int entityId, float x, float y, float size, float radius) {
    if (!shapes || alpha <= 0.002F || !FacePipeline.usable()) {
      return false;
    }
    TextureSetup setup = FACES.setup(entityId);
    if (setup == null) {
      return false;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds = bounds(matrix, x, y, x + size, y + size);
    if (bounds == null) {
      return true;
    }
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    int color = (Math.round(255.0F * Math.min(1.0F, alpha)) << 24) | 0x00FFFFFF;
    state.addGuiElement(
        new FaceRenderState(
            matrix,
            x,
            y,
            size,
            radius,
            color,
            FacePipeline.FACE,
            setup,
            scissor,
            bounds,
            depth(),
            cut(matrix)));
    return true;
  }

  @Override
  public boolean blockIcon(int sprite, float x, float y, float size, float radius, int backing) {
    if (!shapes || alpha <= 0.002F || (sprite & SPRITE_VALID) == 0 || !FacePipeline.usable()) {
      return false;
    }
    TextureSetup setup = MAPS.blocks();
    if (setup == null) {
      return false;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds = bounds(matrix, x, y, x + size, y + size);
    if (bounds == null) {
      return true;
    }
    float atlas = MAPS.blockSize();
    float edge = 8 << ((sprite >> 13) & 7);
    float u0 = (sprite & 0x1FFF) / atlas;
    float v0 = ((sprite >> 16) & 0x1FFF) / atlas;
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    int color = (Math.round(255.0F * Math.min(1.0F, alpha)) << 24) | (backing & 0x00FFFFFF);
    state.addGuiElement(
        new MobFaceRenderState(
            matrix,
            x,
            y,
            size,
            radius,
            u0,
            v0,
            u0 + edge / atlas,
            v0 + edge / atlas,
            color,
            FacePipeline.MOB,
            setup,
            scissor,
            bounds,
            depth()));
    return true;
  }

  @Override
  public boolean mobFace(int entityId, float x, float y, float size, float radius, int backing) {
    if (!shapes || alpha <= 0.002F || !FacePipeline.usable()) {
      return false;
    }
    MobFaces.Face face = MOBS.face(entityId);
    if (face == null) {
      return false;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds = bounds(matrix, x, y, x + size, y + size);
    if (bounds == null) {
      return true;
    }
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    int color = (Math.round(255.0F * Math.min(1.0F, alpha)) << 24) | (backing & 0x00FFFFFF);
    state.addGuiElement(
        new MobFaceRenderState(
            matrix,
            x,
            y,
            size,
            radius,
            face.u0(),
            face.v0(),
            face.u1(),
            face.v1(),
            color,
            FacePipeline.MOB,
            face.setup(),
            scissor,
            bounds,
            depth()));
    return true;
  }

  @Override
  public boolean icon(byte[] png, float x, float y, float size, float radius) {
    if (!shapes || alpha <= 0.002F || png == null || !FacePipeline.usable()) {
      return false;
    }
    TextureSetup setup = ICONS.setup(png);
    if (setup == null) {
      return false;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds = bounds(matrix, x, y, x + size, y + size);
    if (bounds == null) {
      return true;
    }
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    int color = (Math.round(255.0F * Math.min(1.0F, alpha)) << 24) | 0x00FFFFFF;
    state.addGuiElement(
        new FaceRenderState(
            matrix,
            x,
            y,
            size,
            radius,
            color,
            FacePipeline.ICON,
            setup,
            scissor,
            bounds,
            depth(),
            cut(matrix)));
    return true;
  }

  @Override
  public boolean tile(TileSheet sheet, int cell, float x, float y, float w, float h, float radius) {
    if (!shapes || alpha <= 0.002F || cell < 0 || !TilePipeline.usable()) {
      return false;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds = bounds(matrix, x, y, x + w, y + h);
    if (bounds == null) {
      return true;
    }
    TextureSetup setup = TILES.setup(sheet);
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    int color = (Math.round(255.0F * Math.min(1.0F, alpha)) << 24) | 0x00FFFFFF;
    int grid = (sheet.width() / sheet.edge()) | ((sheet.height() / sheet.edge()) << 8);
    state.addGuiElement(
        new TileRenderState(
            matrix,
            x,
            y,
            w,
            h,
            radius,
            cell,
            grid,
            color,
            TilePipeline.TILE,
            setup,
            scissor,
            bounds,
            depth(),
            cut(matrix)));
    return true;
  }

  @Override
  public boolean chart(ChartBatch batch) {
    if (!shapes || alpha <= 0.002F || batch.count == 0 || !MapPipeline.usable()) {
      return false;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds =
        bounds(matrix, batch.left, batch.top, batch.left + batch.width, batch.top + batch.height);
    if (bounds == null) {
      return true;
    }
    TextureSetup setup = MAPS.setup(batch.atlas);
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    int color =
        (Math.round(255.0F * Math.min(1.0F, alpha * batch.alpha)) << 24) | (batch.fog & 0xFFFFFF);
    state.addGuiElement(
        ChartRenderState.of(
            matrix, batch, color, MapPipeline.MAP, setup, scissor, bounds, depth()));
    return true;
  }

  public static void prepareMap(MapAtlas atlas) {
    if (MapPipeline.usable()) {
      MAPS.setup(atlas);
    }
  }

  public static void flushTerrain() {
    TERRAIN.flush();
  }

  @Override
  public boolean terrain(TerrainFrame frame) {
    if (!shapes || alpha <= 0.002F || frame.count == 0 || !FeedPipeline.usable()) {
      return false;
    }
    MAPS.setup(frame.atlas);
    float sharp =
        Math.min(TERRAIN_SAMPLES, TERRAIN_LIMIT / Math.max(frame.width, frame.height) / uiScale);
    int pixelW = Math.max(1, Math.round(frame.width * uiScale * sharp));
    int pixelH = Math.max(1, Math.round(frame.height * uiScale * sharp));
    TextureSetup setup = TERRAIN.request(frame, pixelW, pixelH);
    if (setup == null) {
      return false;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds =
        bounds(matrix, frame.left, frame.top, frame.left + frame.width, frame.top + frame.height);
    if (bounds == null) {
      return true;
    }
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    int color = (Math.round(255.0F * Math.min(1.0F, alpha * frame.alpha)) << 24) | 0x00FFFFFF;
    state.addGuiElement(
        new FeedRenderState(
            matrix,
            frame.left,
            frame.top,
            frame.width,
            frame.height,
            0.0F,
            color,
            FeedPipeline.IMAGE,
            setup,
            scissor,
            bounds,
            depth(),
            cut(matrix)));
    return true;
  }

  @Override
  public boolean feed(
      FrameStore frames, float x, float y, float w, float h, float radius, float dim) {
    if (!shapes || alpha <= 0.002F || !FeedPipeline.usable()) {
      return false;
    }
    TextureSetup setup = FEEDS.setup(frames);
    if (setup == null) {
      return false;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds = bounds(matrix, x, y, x + w, y + h);
    if (bounds == null) {
      return true;
    }
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    int level = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, dim)));
    int color =
        (Math.round(255.0F * Math.min(1.0F, alpha)) << 24) | (level << 16) | (level << 8) | level;
    state.addGuiElement(
        new FeedRenderState(
            matrix,
            x,
            y,
            w,
            h,
            radius,
            color,
            FeedPipeline.FEED,
            setup,
            scissor,
            bounds,
            depth(),
            cut(matrix)));
    return true;
  }

  @Override
  public int styleKey() {
    return Float.floatToIntBits(nameScale());
  }

  @Override
  public int lines(int messageId, float width) {
    return CHAT.split(messageId, Math.max(1, (int) (width / nameScale())));
  }

  @Override
  public boolean line(int messageId, int index, float x, float y, float height, int argb) {
    FormattedCharSequence text = CHAT.line(messageId, index);
    if (text == null) {
      return false;
    }
    int opacity = Math.round(255.0F * Math.min(1.0F, alpha));
    if (opacity < 8) {
      return true;
    }
    float scale = nameScale();
    pose.pushMatrix();
    pose.translate(x, y + (height - NAME_LINE * scale) * 0.5F);
    pose.scale(scale, scale);
    HudText.enter();
    graphics.text(
        Minecraft.getInstance().font, text, 0, 0, (opacity << 24) | (argb & 0x00FFFFFF), true);
    HudText.exit();
    if (interactive) {
      if (hits < HITS) {
        HIT_TEXT[hits] = text;
        HIT_POSE[hits++].set(pose);
      }
      ActiveTextCollector hover =
          graphics.textRenderer(
              GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR, insertions);
      hover.accept(TextAlignment.LEFT, 0, 0, hover.defaultParameters().withOpacity(0.0F), text);
    }
    pose.popMatrix();
    return true;
  }

  @Override
  public void pushContrast(int plateArgb) {
    contrasts[contrastDepth++] = plateArgb;
  }

  @Override
  public void popContrast() {
    contrastDepth--;
  }

  @Override
  public float textWidth(FontStyle style, String text) {
    if (text.indexOf(StyledText.MARK) >= 0) {
      return styledWidth(style, StyledText.of(text));
    }
    return ENGINE.face(style).width(text);
  }

  @Override
  public float lineHeight(FontStyle style) {
    return style.size + LINE_EXTRA;
  }

  @Override
  public void pushClip(float x, float y, float w, float h) {
    int left = (int) Math.floor(x);
    int top = (int) Math.floor(y);
    int right = (int) Math.ceil(x + w);
    int bottom = (int) Math.ceil(y + h);
    ScreenRectangle rectangle =
        tilting
            ? enclosing(left, top, right, bottom)
            : new ScreenRectangle(left, top, right - left, bottom - top)
                .transformAxisAligned(pose());
    ScreenRectangle merged =
        (clipDepth > 0 ? clips[clipDepth - 1] : screen).intersection(rectangle);
    Matrix3x2f matrix = pose();
    int slot = clipDepth * 4;
    cuts[slot] = matrix.m00() * x + matrix.m20();
    cuts[slot + 1] = matrix.m11() * y + matrix.m21();
    cuts[slot + 2] = matrix.m00() * (x + w) + matrix.m20();
    cuts[slot + 3] = matrix.m11() * (y + h) + matrix.m21();
    if (clipDepth > 0) {
      cuts[slot] = Math.max(cuts[slot], cuts[slot - 4]);
      cuts[slot + 1] = Math.max(cuts[slot + 1], cuts[slot - 3]);
      cuts[slot + 2] = Math.min(cuts[slot + 2], cuts[slot - 2]);
      cuts[slot + 3] = Math.min(cuts[slot + 3], cuts[slot - 1]);
    }
    clips[clipDepth++] = merged == null ? ScreenRectangle.empty() : merged;
  }

  private Cut cut(Matrix3x2f matrix) {
    if (!tilting || clipDepth == 0) {
      return Cut.NONE;
    }
    int slot = (clipDepth - 1) * 4;
    return Cut.of(
        matrix,
        cuts[slot],
        cuts[slot + 1],
        Math.max(cuts[slot], cuts[slot + 2]),
        Math.max(cuts[slot + 1], cuts[slot + 3]));
  }

  private ScreenRectangle enclosing(float left, float top, float right, float bottom) {
    Matrix3x2f matrix = pose();
    project(matrix, left, top);
    float minX = warp.x();
    float maxX = minX;
    float minY = warp.y();
    float maxY = minY;
    for (int corner = 1; corner < 4; corner++) {
      project(matrix, (corner & 1) == 0 ? left : right, corner < 2 ? top : bottom);
      minX = Math.min(minX, warp.x());
      maxX = Math.max(maxX, warp.x());
      minY = Math.min(minY, warp.y());
      maxY = Math.max(maxY, warp.y());
    }
    int x0 = (int) Math.floor(minX) - 1;
    int y0 = (int) Math.floor(minY) - 1;
    return new ScreenRectangle(
        x0, y0, (int) Math.ceil(maxX) + 1 - x0, (int) Math.ceil(maxY) + 1 - y0);
  }

  @Override
  public void popClip() {
    clips[--clipDepth] = null;
  }

  @Override
  public void pushTransform(float scale, float pivotX, float pivotY) {
    pose.pushMatrix();
    pose.translate(pivotX, pivotY);
    pose.scale(scale, scale);
    pose.translate(-pivotX, -pivotY);
    snapshot = null;
  }

  @Override
  public void popTransform() {
    pose.popMatrix();
    snapshot = null;
  }

  @Override
  public void pushAffine(float scale, float shiftX, float shiftY, float pivotX, float pivotY) {
    pose.pushMatrix();
    pose.translate(pivotX + shiftX, pivotY + shiftY);
    pose.scale(scale, scale);
    pose.translate(-pivotX, -pivotY);
    snapshot = null;
  }

  @Override
  public void perspective(float yaw, float pitch, float distance) {
    perspective(yaw, pitch, distance, centreX / unit, centreY / unit);
  }

  @Override
  public void perspective(float yaw, float pitch, float distance, float pivotX, float pivotY) {
    flatten();
    if (!shapes || (yaw == 0.0F && pitch == 0.0F)) {
      return;
    }
    warp.set(yaw, pitch, pivotX * unit, pivotY * unit, distance * unit);
    tilting = GuiTilt.begin(graphics, warp, false);
  }

  @Override
  public void pushLayer(float elevation) {
    layers[layerDepth++] = height;
    height = elevation * unit;
  }

  @Override
  public void popLayer() {
    height = layers[--layerDepth];
  }

  @Override
  public void pushAlpha(float value) {
    alphas[alphaDepth++] = alpha;
    alpha *= Math.max(0.0F, Math.min(1.0F, value));
  }

  @Override
  public void beginDissolve(float x, float y, float w, float h, float erosion) {
    boolean wanted = erosion > DISSOLVE_MIN;
    boolean captured = false;
    if (wanted && shapes && !tilting) {
      if (!dissolving) {
        dissolving = GuiTilt.place(graphics, FLAT, 1.0F);
      }
      if (dissolving) {
        Matrix3x2f matrix = pose();
        Dissolve.add(
            matrix.m00() * x + matrix.m20(),
            matrix.m11() * y + matrix.m21(),
            matrix.m00() * (x + w) + matrix.m20(),
            matrix.m11() * (y + h) + matrix.m21(),
            erosion);
        captured = true;
      }
    }
    boolean fallback = wanted && !captured;
    if (fallback) {
      pushAlpha(1.0F - erosion);
    }
    dissolveFallback[dissolveDepth++] = fallback;
  }

  @Override
  public void endDissolve() {
    if (dissolveFallback[--dissolveDepth]) {
      popAlpha();
    }
  }

  @Override
  public void popAlpha() {
    alpha = alphas[--alphaDepth];
  }

  @Override
  public void item(int slot, float x, float y, float size) {
    if (items == null || alpha <= 0.002F) {
      return;
    }
    ItemStack stack = items[slot];
    if (stack == null || stack.isEmpty()) {
      return;
    }
    float unit = size / ITEM_SIZE;
    pose.pushMatrix();
    pose.translate(x, y);
    pose.scale(unit, unit);
    item(stack);
    pose.popMatrix();
  }

  @Override
  public boolean curves() {
    return shapes;
  }

  @Override
  public void stroke(float[] xs, float[] ys, int count, float width, float softness, int argb) {
    int color = tint(argb);
    if (!shapes || count < 2 || (color >>> 24) == 0) {
      return;
    }
    float half = width * 0.5F;
    emit(
        xs,
        ys,
        count,
        false,
        half,
        softness,
        half + Math.max(softness, 0.0F) + CURVE_PAD / uiScale,
        0.0F,
        color,
        color);
  }

  @Override
  public void area(float[] xs, float[] ys, int count, float base, int topArgb, int baseArgb) {
    int top = tint(topArgb);
    int bottom = tint(baseArgb);
    if (!shapes || count < 2 || ((top | bottom) >>> 24) == 0) {
      return;
    }
    emit(xs, ys, count, true, 0.0F, 0.0F, 0.0F, base, top, bottom);
  }

  private void emit(
      float[] xs,
      float[] ys,
      int count,
      boolean fill,
      float half,
      float softness,
      float extent,
      float base,
      int color,
      int colorBottom) {
    float minX = Float.MAX_VALUE;
    float maxX = -Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    for (int i = 0; i < count; i++) {
      minX = Math.min(minX, xs[i]);
      maxX = Math.max(maxX, xs[i]);
      minY = Math.min(minY, ys[i]);
      maxY = Math.max(maxY, ys[i]);
    }
    if (fill) {
      maxY = Math.max(maxY, base);
    }
    Matrix3x2f matrix = pose();
    float reach = extent * MITER_REACH;
    ScreenRectangle bounds = bounds(matrix, minX - reach, minY - reach, maxX + reach, maxY + reach);
    if (bounds == null) {
      return;
    }
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    state.addGuiElement(
        new CurveRenderState(
            matrix,
            java.util.Arrays.copyOf(xs, count),
            java.util.Arrays.copyOf(ys, count),
            count,
            fill,
            half,
            softness,
            extent,
            base,
            color,
            colorBottom,
            scissor,
            bounds,
            depth()));
  }

  @Override
  public float pixel() {
    return 1.0F / uiScale;
  }

  public static void held(ItemStack[] source) {
    held = source;
  }

  public static void maps(MapRenderState[] source) {
    maps = source;
  }

  @Override
  public void map(int index, float x, float y, float size) {
    if (maps == null || index < 0 || index >= maps.length || alpha <= 0.002F) {
      return;
    }
    MapRenderState state = maps[index];
    if (state == null || state.texture == null) {
      return;
    }
    float shrink = alpha >= 1.0F ? 1.0F : alpha * alpha * (3.0F - 2.0F * alpha);
    if (shrink <= ITEM_FADE_FLOOR) {
      return;
    }
    float unit = size * shrink / 128.0F;
    float inset = size * (1.0F - shrink) * 0.5F;
    pose.pushMatrix();
    pose.translate(x + inset, y + inset);
    pose.scale(unit, unit);
    graphics.map(state);
    pose.popMatrix();
  }

  @Override
  public void held(int index, float x, float y, float size) {
    if (held == null || index < 0 || index >= held.length || alpha <= 0.002F) {
      return;
    }
    ItemStack stack = held[index];
    if (stack == null || stack.isEmpty()) {
      return;
    }
    float shrink = alpha >= 1.0F ? 1.0F : alpha * alpha * (3.0F - 2.0F * alpha);
    if (shrink <= ITEM_FADE_FLOOR) {
      return;
    }
    float unit = size * shrink / ITEM_SIZE;
    float inset = size * (1.0F - shrink) * 0.5F;
    pose.pushMatrix();
    pose.translate(x + inset, y + inset);
    pose.scale(unit, unit);
    item(stack);
    pose.popMatrix();
  }

  @Override
  public void stack(int handle, float x, float y, float size) {
    if (alpha <= 0.002F) {
      return;
    }
    ItemStack stack = Stacks.get(handle);
    if (stack.isEmpty()) {
      return;
    }
    float shrink = alpha >= 1.0F ? 1.0F : alpha * alpha * (3.0F - 2.0F * alpha);
    if (shrink <= ITEM_FADE_FLOOR) {
      return;
    }
    float unit = size * shrink / ITEM_SIZE;
    float inset = size * (1.0F - shrink) * 0.5F;
    pose.pushMatrix();
    pose.translate(x + inset, y + inset);
    pose.scale(unit, unit);
    item(stack);
    pose.popMatrix();
  }

  private void item(ItemStack stack) {
    if (clipDepth > 0 && !within(clips[clipDepth - 1])) {
      return;
    }
    graphics.item(stack, 0, 0);
  }

  private boolean within(ScreenRectangle clip) {
    Matrix3x2f matrix = pose;
    float left = matrix.m20();
    float top = matrix.m21();
    float right = left + matrix.m00() * ITEM_SIZE;
    float bottom = top + matrix.m11() * ITEM_SIZE;
    return left >= clip.left() - ITEM_EDGE
        && top >= clip.top() - ITEM_EDGE
        && right <= clip.right() + ITEM_EDGE
        && bottom <= clip.bottom() + ITEM_EDGE;
  }

  @Override
  public void player(float x, float y, float w, float h, boolean follow) {
    if (graphics == null || alpha <= 0.002F || w <= 0.0F || h <= 0.0F) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    Matrix3x2f matrix = pose();
    float ax = matrix.m00() * x + matrix.m10() * y + matrix.m20();
    float ay = matrix.m01() * x + matrix.m11() * y + matrix.m21();
    float bx = matrix.m00() * (x + w) + matrix.m10() * (y + h) + matrix.m20();
    float by = matrix.m01() * (x + w) + matrix.m11() * (y + h) + matrix.m21();
    int x0 = Math.round(Math.min(ax, bx));
    int y0 = Math.round(Math.min(ay, by));
    int x1 = Math.round(Math.max(ax, bx));
    int y1 = Math.round(Math.max(ay, by));
    if (x1 <= x0 || y1 <= y0) {
      return;
    }
    if (mc.player == null) {
      figure(mc, x0, y0, x1, y1, follow);
      return;
    }
    int size = Math.max(8, Math.round((y1 - y0) * 0.43F));
    float mouseX;
    float mouseY;
    if (follow) {
      mouseX = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
      mouseY = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
    } else {
      mouseX = (x0 + x1) * 0.5F;
      mouseY = (y0 + y1) * 0.5F;
    }
    try {
      InventoryScreen.extractEntityInInventoryFollowsMouse(
          graphics, x0, y0, x1, y1, size, 0.0625F, mouseX, mouseY, mc.player);
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.warn("Viewmodel preview failed: {}", e.toString());
    }
  }

  private void figure(Minecraft mc, int x0, int y0, int x1, int y1, boolean follow) {
    try {
      EntityModelSet models = mc.getEntityModels();
      if (models != figureModels) {
        figureModels = models;
        figureWide =
            new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
        figureSlim =
            new Model.Simple(
                models.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
      }
      if (figureSkin == null || figureUser != mc.getUser()) {
        figureUser = mc.getUser();
        figureSkin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
      }
      PlayerSkin skin = figureSkin.get();
      float yaw = FIGURE_YAW;
      float pitch = FIGURE_PITCH;
      if (follow) {
        float dx = (float) mc.mouseHandler.getScaledXPos(mc.getWindow()) - (x0 + x1) * 0.5F;
        float dy = (float) mc.mouseHandler.getScaledYPos(mc.getWindow()) - (y0 + y1) * 0.5F;
        yaw = Math.max(-FIGURE_TURN, Math.min(FIGURE_TURN, dx * FIGURE_FOLLOW));
        pitch = Math.max(-FIGURE_NOD, Math.min(FIGURE_NOD, -dy * FIGURE_FOLLOW * 0.5F));
      }
      graphics.skin(
          skin.model() == PlayerModelType.SLIM ? figureSlim : figureWide,
          skin.body().texturePath(),
          FIGURE_SCALE * (y1 - y0) / FIGURE_HEIGHT,
          pitch,
          yaw,
          FIGURE_PIVOT,
          x0,
          y0,
          x1,
          y1);
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.warn("Menu player preview failed: {}", e.toString());
    }
  }

  @Override
  public void model(
      int handle, float x, float y, float w, float h, float yaw, boolean live, int audition) {
    if (previews == null || alpha <= 0.002F || w <= 0.0F || h <= 0.0F) {
      return;
    }
    int width = Math.min(LARGEST_PREVIEW, Math.max(8, Math.round(w * uiScale * SUPERSAMPLE)));
    int height = Math.min(LARGEST_PREVIEW, Math.max(8, Math.round(h * uiScale * SUPERSAMPLE)));
    TextureSetup setup = previews.request(handle, width, height, yaw, live, audition);
    if (setup == null) {
      return;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds = bounds(matrix, x, y, x + w, y + h);
    if (bounds == null) {
      return;
    }
    int a = Math.round(255.0F * Math.min(1.0F, alpha));
    int color = a << 24 | a << 16 | a << 8 | a;
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    state.addGuiElement(
        new ModelRenderState(
            matrix, x, y, x + w, y + h, color, setup, scissor, bounds, depth(), cut(matrix)));
  }

  @Override
  public boolean stage(
      int scene,
      float x,
      float y,
      float w,
      float h,
      float yaw,
      float pitch,
      float clock,
      float radius) {
    StagePreviews stages = STAGES[scene];
    if (stages == null || alpha <= 0.002F || w <= 0.0F || h <= 0.0F) {
      return false;
    }
    Matrix3x2f matrix = pose();
    ScreenRectangle bounds = bounds(matrix, x, y, x + w, y + h);
    if (bounds == null) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    int width = Math.min(LARGEST_PREVIEW, Math.max(8, Math.round(w * uiScale)));
    int height = Math.min(LARGEST_PREVIEW, Math.max(8, Math.round(h * uiScale)));
    StagePreviews.Frame frame =
        stages.request(width, height, yaw, pitch, clock, radius * uiScale, mc.player != null);
    if (frame == null) {
      return false;
    }
    int a = Math.round(255.0F * Math.min(1.0F, alpha));
    int color = a << 24 | a << 16 | a << 8 | a;
    ScreenRectangle scissor = clipDepth > 0 ? clips[clipDepth - 1] : null;
    Cut cut = cut(matrix);
    state.addGuiElement(
        new ModelRenderState(
            matrix, x, y, x + w, y + h, color, frame.back(), scissor, bounds, depth(), cut));
    if (frame.front() != null) {
      standIn(
          mc,
          matrix,
          x + w * frame.figureX(),
          y + h * frame.figureY(),
          h * frame.blockHeight(),
          frame.turn(),
          frame.stride() * clock,
          frame.stride() > 0.0F,
          pitch);
      state.addGuiElement(
          new ModelRenderState(
              matrix, x, y, x + w, y + h, color, frame.front(), scissor, bounds, depth(), cut));
    }
    return true;
  }

  private void standIn(
      Minecraft mc,
      Matrix3x2f matrix,
      float centreX,
      float centreY,
      float block,
      float turn,
      float walk,
      boolean running,
      float pitch) {
    float scale = (float) Math.hypot(matrix.m01(), matrix.m11());
    float gx = matrix.m00() * centreX + matrix.m10() * centreY + matrix.m20();
    float gy = matrix.m01() * centreX + matrix.m11() * centreY + matrix.m21();
    float pixels = block * scale;
    float reach = pixels * STAND_IN_REACH;
    try {
      EntityRenderState figure =
          mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player, 1.0F);
      figure.shadowPieces.clear();
      figure.outlineColor = 0;
      if (figure instanceof LivingEntityRenderState living) {
        living.bodyRot = turn;
        living.walkAnimationPos = walk;
        living.walkAnimationSpeed = running ? 1.0F : living.walkAnimationSpeed;
        living.yRot = 0.0F;
        living.xRot = 0.0F;
      }
      STAND_IN_TILT.rotationX(pitch);
      STAND_IN_POSE.rotationZ((float) Math.PI).mul(STAND_IN_TILT);
      STAND_IN_LIFT.set(0.0F, figure.boundingBoxHeight * 0.5F, 0.0F);
      graphics.entity(
          figure,
          pixels,
          STAND_IN_LIFT,
          STAND_IN_POSE,
          STAND_IN_TILT,
          Math.round(gx - reach),
          Math.round(gy - reach),
          Math.round(gx + reach),
          Math.round(gy + reach));
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.warn("Jump circle stand-in failed: {}", e.toString());
    }
  }
}
