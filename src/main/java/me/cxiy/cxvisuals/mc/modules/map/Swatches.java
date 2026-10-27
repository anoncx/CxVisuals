package me.cxiy.cxvisuals.mc.modules.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.mc.mixin.SpriteContentsAccessor;
import me.cxiy.cxvisuals.modules.map.BlockPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

final class Swatches {
  static final int NO_TINT = -1;
  private static final float SEE_THROUGH = 0.42F;
  private static final int STEP_LIMIT = 16;

  record Swatch(int rgb, int tint, boolean skip, int sprite, String key) {}

  private static final int VALID = 1 << 29;
  private static final int EDGE_LIMIT = 8191;

  private final Map<BlockState, Swatch> cache = new IdentityHashMap<>(1024);
  private final List<BlockStateModelPart> parts = new ArrayList<>(4);
  private final RandomSource random = RandomSource.create(42L);
  private BlockStateModelSet models;
  private int resolvedUpTo;
  private boolean resolvedLate;

  Swatch of(BlockState state) {
    BlockStateModelSet current = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
    if (current != models) {
      models = current;
      cache.clear();
      resolvedUpTo = 0;
    }
    Swatch known = cache.get(state);
    if (known == null) {
      known = measure(state);
      cache.put(state, known);
    }
    return known;
  }

  boolean reloaded() {
    return Minecraft.getInstance().getModelManager().getBlockStateModelSet() != models;
  }

  int color(Swatch swatch, BlockState state, ClientLevel level, BlockPos pos) {
    if (swatch.tint == NO_TINT) {
      return swatch.rgb;
    }
    BlockColors colors = Minecraft.getInstance().getBlockColors();
    BlockTintSource source = colors.getTintSource(state, swatch.tint);
    if (source == null) {
      return swatch.rgb;
    }
    return multiply(swatch.rgb, source.colorInWorld(state, level, pos));
  }

  private Swatch measure(BlockState state) {
    boolean fluid = !state.getFluidState().isEmpty();
    boolean passable =
        !fluid
            && !(state.getBlock() instanceof SnowLayerBlock)
            && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    BlockStateModel model = models.get(state);
    TextureAtlasSprite sprite = null;
    int tint = NO_TINT;
    if (!fluid) {
      parts.clear();
      random.setSeed(42L);
      model.collectParts(random, parts);
      for (int i = 0; i < parts.size() && sprite == null; i++) {
        List<BakedQuad> up = parts.get(i).getQuads(Direction.UP);
        List<BakedQuad> quads = up.isEmpty() ? parts.get(i).getQuads(null) : up;
        if (!quads.isEmpty()) {
          BakedQuad quad = quads.get(0);
          sprite = quad.materialInfo().sprite();
          tint = quad.materialInfo().tintIndex();
        }
      }
    }
    if (sprite == null) {
      sprite = model.particleMaterial().sprite();
      tint = fluid ? 0 : NO_TINT;
    }
    int[] average = average(sprite);
    int rgb = average[0];
    float coverage = average[1] / 255.0F;
    if (coverage <= 0.01F) {
      rgb = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
      tint = NO_TINT;
    }
    boolean skip = passable || (!fluid && coverage < SEE_THROUGH);
    return new Swatch(
        rgb & 0xFFFFFF,
        tint < 0 ? NO_TINT : tint,
        skip,
        code(sprite),
        BlockStateParser.serialize(state));
  }

  static int code(TextureAtlasSprite sprite) {
    int width = sprite.contents().width();
    float spanU = sprite.getU1() - sprite.getU0();
    float spanV = sprite.getV1() - sprite.getV0();
    if (spanU <= 0.0F || spanV <= 0.0F) {
      return 0;
    }
    int x = Math.round(sprite.getU0() * width / spanU);
    int y = Math.round(sprite.getV0() * sprite.contents().height() / spanV);
    if (x < 0 || y < 0 || x > EDGE_LIMIT || y > EDGE_LIMIT || width < 8) {
      return 0;
    }
    int size = Math.max(0, Math.min(7, 31 - Integer.numberOfLeadingZeros(width) - 3));
    return VALID | (y << 16) | (size << 13) | x;
  }

  int block(BlockState state, BlockPalette palette) {
    Swatch swatch = of(state);
    int id = palette.id(swatch.key);
    if (palette.sprite(id) != swatch.sprite) {
      palette.sprite(id, swatch.sprite);
    }
    return id;
  }

  void resolve(BlockPalette palette) {
    for (int id = resolvedUpTo + 1; id < palette.size(); id++) {
      if (palette.sprite(id) != 0) {
        continue;
      }
      try {
        BlockState state =
            BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, palette.key(id), false)
                .blockState();
        palette.sprite(id, of(state).sprite);
        resolvedLate = true;
      } catch (CommandSyntaxException | RuntimeException ignored) {
        palette.sprite(id, 0);
      }
    }
    resolvedUpTo = Math.max(resolvedUpTo, palette.size() - 1);
  }

  boolean takeLate() {
    boolean late = resolvedLate;
    resolvedLate = false;
    return late;
  }

  void reset() {
    resolvedUpTo = 0;
  }

  private static int[] average(TextureAtlasSprite sprite) {
    SpriteContents contents = sprite.contents();
    NativeImage image = ((SpriteContentsAccessor) contents).cxvisuals$originalImage();
    int width = Math.min(contents.width(), image.getWidth());
    int height = Math.min(contents.height(), image.getHeight());
    int stepX = Math.max(1, width / STEP_LIMIT);
    int stepY = Math.max(1, height / STEP_LIMIT);
    long r = 0L;
    long g = 0L;
    long b = 0L;
    long a = 0L;
    int samples = 0;
    for (int y = 0; y < height; y += stepY) {
      for (int x = 0; x < width; x += stepX) {
        int argb = image.getPixel(x, y);
        int alpha = argb >>> 24;
        r += (long) ((argb >> 16) & 0xFF) * alpha;
        g += (long) ((argb >> 8) & 0xFF) * alpha;
        b += (long) (argb & 0xFF) * alpha;
        a += alpha;
        samples++;
      }
    }
    if (a == 0L || samples == 0) {
      return new int[] {0, 0};
    }
    int rgb = (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
    return new int[] {rgb, (int) (a / samples)};
  }

  static int multiply(int rgb, int tint) {
    int r = ((rgb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
    int g = ((rgb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
    int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
    return (r << 16) | (g << 8) | b;
  }

  static int mix(int from, int to, float t) {
    int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
    int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
    int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
    return (r << 16) | (g << 8) | b;
  }
}
