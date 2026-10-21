package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.gui.text.GlyphAtlas;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;

final class FontTextures {
  private record Sheet(NativeImage image, DynamicTexture texture, TextureSetup setup) {}

  private static final int WHITE = 0x00FFFFFF;

  private final List<Sheet> sheets = new ArrayList<>();
  private final List<TextureSetup> paired = new ArrayList<>();
  private GpuTextureView pairedWith;

  TextureSetup paired(int page, GpuTextureView backdrop) {
    if (backdrop != pairedWith) {
      pairedWith = backdrop;
      paired.clear();
    }
    setup(page);
    while (paired.size() <= page) {
      Sheet sheet = sheets.get(paired.size());
      paired.add(
          TextureSetup.doubleTexture(
              sheet.texture().getTextureView(),
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
              backdrop,
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)));
    }
    return paired.get(page);
  }

  GpuTextureView view(int page) {
    setup(page);
    return sheets.get(page).texture().getTextureView();
  }

  TextureSetup setup(int page) {
    while (sheets.size() <= page) {
      int index = sheets.size();
      NativeImage image =
          new NativeImage(
              NativeImage.Format.RGBA, GlyphAtlas.PAGE_SIZE, GlyphAtlas.PAGE_SIZE, true);
      DynamicTexture texture = new DynamicTexture(() -> "cxvisuals_font_" + index, image);
      TextureSetup setup =
          TextureSetup.singleTexture(
              texture.getTextureView(),
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      sheets.add(new Sheet(image, texture, setup));
    }
    return sheets.get(page).setup();
  }

  void sync(GlyphAtlas atlas) {
    for (int i = 0; i < atlas.pageCount(); i++) {
      GlyphAtlas.Page page = atlas.page(i);
      if (!page.dirty()) {
        continue;
      }
      setup(i);
      Sheet sheet = sheets.get(i);
      byte[] alpha = page.alpha();
      NativeImage image = sheet.image();
      for (int y = 0; y < GlyphAtlas.PAGE_SIZE; y++) {
        int row = y * GlyphAtlas.PAGE_SIZE;
        for (int x = 0; x < GlyphAtlas.PAGE_SIZE; x++) {
          image.setPixel(x, y, ((alpha[row + x] & 0xFF) << 24) | WHITE);
        }
      }
      sheet.texture().upload();
      page.clean();
    }
  }
}
