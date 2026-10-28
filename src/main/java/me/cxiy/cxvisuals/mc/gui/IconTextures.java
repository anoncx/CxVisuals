package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.io.IOException;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;

final class IconTextures {
  private static final String NAME = "cxvisuals_hud_";
  private static final int SLOTS = 32;

  private final byte[][] sources = new byte[SLOTS][];
  private final DynamicTexture[] textures = new DynamicTexture[SLOTS];
  private final TextureSetup[] setups = new TextureSetup[SLOTS];
  private final long[] used = new long[SLOTS];
  private long clock;

  TextureSetup setup(byte[] png) {
    int oldest = 0;
    for (int i = 0; i < SLOTS; i++) {
      if (sources[i] == png) {
        used[i] = ++clock;
        return setups[i];
      }
      if (used[i] < used[oldest]) {
        oldest = i;
      }
    }
    release(oldest);
    sources[oldest] = png;
    used[oldest] = ++clock;
    int slot = oldest;
    try {
      DynamicTexture texture = new DynamicTexture(() -> NAME + slot, NativeImage.read(png));
      textures[oldest] = texture;
      setups[oldest] =
          TextureSetup.singleTexture(
              texture.getTextureView(),
              RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("HUD image could not be loaded: {}", e.toString());
      release(oldest);
      sources[oldest] = png;
    }
    return setups[oldest];
  }

  private void release(int slot) {
    if (textures[slot] != null) {
      textures[slot].close();
      textures[slot] = null;
    }
    setups[slot] = null;
    sources[slot] = null;
  }
}
