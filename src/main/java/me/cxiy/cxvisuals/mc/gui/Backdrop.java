package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;

public final class Backdrop {
  private static final int USAGE =
      GpuTexture.USAGE_TEXTURE_BINDING
          | GpuTexture.USAGE_COPY_DST
          | GpuTexture.USAGE_RENDER_ATTACHMENT;

  private static GpuTexture texture;
  private static GpuTextureView view;
  private static TextureSetup setup;
  private static int width;
  private static int height;
  private static final int RELAXED_INTERVAL = 3;

  private static boolean requested;
  private static boolean relaxed;
  private static int skipped = RELAXED_INTERVAL;
  private static boolean broken;

  private Backdrop() {}

  static TextureSetup request() {
    return request(true);
  }

  static TextureSetup request(boolean exact) {
    if (broken) {
      return null;
    }
    try {
      RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      if (main.width <= 0 || main.height <= 0) {
        return null;
      }
      if (texture == null || main.width != width || main.height != height) {
        release();
        width = main.width;
        height = main.height;
        texture =
            RenderSystem.getDevice()
                .createTexture(
                    () -> "cxvisuals_backdrop", USAGE, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        view = RenderSystem.getDevice().createTextureView(texture);
        setup =
            TextureSetup.singleTexture(
                view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      }
      if (exact) {
        requested = true;
      } else {
        relaxed = true;
      }
      return setup;
    } catch (RuntimeException e) {
      fail(e);
      return null;
    }
  }

  static GpuTextureView view() {
    return view;
  }

  public static void captureIfRequested() {
    boolean due = requested;
    if (relaxed && !due) {
      due = ++skipped >= RELAXED_INTERVAL;
    }
    requested = false;
    relaxed = false;
    if (!due) {
      return;
    }
    skipped = 0;
    try {
      RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      if (texture == null || main.width != width || main.height != height) {
        return;
      }
      RenderSystem.getDevice()
          .createCommandEncoder()
          .copyTextureToTexture(main.getColorTexture(), texture, 0, 0, 0, 0, 0, width, height);
    } catch (RuntimeException e) {
      fail(e);
    }
  }

  private static void fail(RuntimeException e) {
    broken = true;
    requested = false;
    release();
    CxVisualsClient.LOG.error(
        "Backdrop capture failed, glass style falls back to a tinted panel", e);
  }

  private static void release() {
    if (view != null) {
      view.close();
      view = null;
    }
    if (texture != null) {
      texture.close();
      texture = null;
    }
    setup = null;
  }
}
