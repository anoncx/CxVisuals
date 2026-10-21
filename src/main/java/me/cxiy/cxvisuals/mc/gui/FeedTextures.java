package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.system.MemoryUtil;

final class FeedTextures {
  private static final class Slot {
    final int id;
    DynamicTexture texture;
    TextureSetup setup;
    int width;
    int height;
    long revision;

    Slot(int id) {
      this.id = id;
    }

    void create(int newWidth, int newHeight) {
      release();
      NativeImage image = new NativeImage(NativeImage.Format.RGBA, newWidth, newHeight, false);
      texture = new DynamicTexture(() -> "cxvisuals_feed_" + id, image);
      setup =
          TextureSetup.singleTexture(
              texture.getTextureView(),
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      width = newWidth;
      height = newHeight;
    }

    void release() {
      if (texture != null) {
        texture.close();
        texture = null;
        setup = null;
      }
    }
  }

  private final Map<FrameStore, Slot> slots = new IdentityHashMap<>(2);

  TextureSetup setup(FrameStore store) {
    Slot slot = slots.computeIfAbsent(store, key -> new Slot(slots.size()));
    store.read(
        slot.setup == null ? 0L : slot.revision,
        (ByteBuffer pixels, int width, int height, long revision) -> {
          if (slot.texture == null || slot.width != width || slot.height != height) {
            slot.create(width, height);
          }
          MemoryUtil.memCopy(
              MemoryUtil.memAddress(pixels),
              slot.texture.getPixels().getPointer(),
              (long) width * height * 4L);
          slot.texture.upload();
          slot.revision = revision;
        });
    return slot.setup;
  }
}
