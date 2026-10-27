package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.Map;
import me.cxiy.cxvisuals.modules.map.BlockPalette;
import me.cxiy.cxvisuals.modules.map.MapAtlas;
import me.cxiy.cxvisuals.modules.map.TileBaker;
import me.cxiy.cxvisuals.modules.map.TileKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.system.MemoryUtil;

final class MapTextures {
  private static final int UPLOADS_PER_FRAME = 6;

  private static final class Slot {
    final DynamicTexture colors;
    final DynamicTexture ids;
    GpuTextureView blocks;
    TextureSetup setup;
    int table = -1;

    Slot(DynamicTexture colors, DynamicTexture ids) {
      this.colors = colors;
      this.ids = ids;
    }
  }

  private final Map<MapAtlas, Slot> slots = new IdentityHashMap<>(2);
  private ByteBuffer staging;
  private ByteBuffer zeros;
  private final int[] sprites = new int[BlockPalette.TABLE];

  TextureSetup setup(MapAtlas atlas) {
    Slot slot = slots.get(atlas);
    if (slot == null) {
      slot =
          new Slot(
              new DynamicTexture(
                  () -> "cxvisuals_map",
                  new NativeImage(NativeImage.Format.RGBA, MapAtlas.SIZE, MapAtlas.SIZE, true)),
              new DynamicTexture(
                  () -> "cxvisuals_map_blocks",
                  new NativeImage(NativeImage.Format.RGBA, MapAtlas.SIZE, MapAtlas.SIZE, true)));
      slots.put(atlas, slot);
    }
    GpuTextureView blocks =
        Minecraft.getInstance()
            .getTextureManager()
            .getTexture(TextureAtlas.LOCATION_BLOCKS)
            .getTextureView();
    if (slot.setup == null || slot.blocks != blocks) {
      slot.blocks = blocks;
      slot.setup =
          new TextureSetup(
              slot.colors.getTextureView(),
              slot.ids.getTextureView(),
              blocks,
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true));
    }
    if (staging == null) {
      staging = MemoryUtil.memAlloc(TileBaker.PIXELS * 4).order(ByteOrder.nativeOrder());
      zeros = MemoryUtil.memCalloc(TileBaker.PIXELS * 4).order(ByteOrder.nativeOrder());
    }
    BlockPalette palette = atlas.palette();
    if (palette != null && palette.revision() != slot.table) {
      slot.table = palette.revision();
      palette.table(sprites);
      write(
          RenderSystem.getDevice().createCommandEncoder(),
          slot.ids.getTexture(),
          MapAtlas.TABLE,
          sprites);
    }
    if (atlas.busy()) {
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      GpuTexture colors = slot.colors.getTexture();
      GpuTexture ids = slot.ids.getTexture();
      atlas.drain(
          (index, pixels, details) -> {
            write(encoder, colors, index, pixels);
            write(encoder, ids, index, details);
          },
          UPLOADS_PER_FRAME);
    }
    return slot.setup;
  }

  private void write(CommandEncoder encoder, GpuTexture texture, int index, int[] values) {
    ByteBuffer source;
    if (values == null) {
      source = zeros;
      source.clear();
    } else {
      source = staging;
      source.clear();
      source.asIntBuffer().put(values);
      source.limit(TileBaker.PIXELS * 4);
    }
    encoder.writeToTexture(
        texture,
        source,
        0,
        0,
        (index % MapAtlas.GRID) * TileKey.EDGE,
        (index / MapAtlas.GRID) * TileKey.EDGE,
        TileKey.EDGE,
        TileKey.EDGE);
  }
}
