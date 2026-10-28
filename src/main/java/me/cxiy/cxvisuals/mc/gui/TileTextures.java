package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.HashMap;
import java.util.Map;
import me.cxiy.cxvisuals.gui.TileSheet;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.system.MemoryUtil;

final class TileTextures {
  private static final class Slot {
    final DynamicTexture texture;
    final TextureSetup setup;
    int revision = -1;

    Slot(DynamicTexture texture, TextureSetup setup) {
      this.texture = texture;
      this.setup = setup;
    }
  }

  private final Map<Integer, Slot> slots = new HashMap<>(4);

  TextureSetup setup(TileSheet sheet) {
    Slot slot = slots.get(sheet.id());
    if (slot == null) {
      NativeImage image =
          new NativeImage(NativeImage.Format.RGBA, sheet.width(), sheet.height(), true);
      int id = sheet.id();
      DynamicTexture texture = new DynamicTexture(() -> "cxvisuals_tiles_" + id, image);
      slot =
          new Slot(
              texture,
              TextureSetup.singleTexture(
                  texture.getTextureView(),
                  RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
      slots.put(sheet.id(), slot);
    }
    if (slot.revision != sheet.revision()) {
      slot.revision = sheet.revision();
      NativeImage image = slot.texture.getPixels();
      int[] pixels = sheet.pixels();
      MemoryUtil.memIntBuffer(image.getPointer(), pixels.length).put(0, pixels);
      slot.texture.upload();
    }
    return slot.setup;
  }
}
