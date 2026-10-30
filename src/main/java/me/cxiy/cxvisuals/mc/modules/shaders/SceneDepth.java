package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import me.cxiy.cxvisuals.mc.compat.IrisCompat;
import me.cxiy.cxvisuals.mc.compat.Targets;
import me.cxiy.cxvisuals.mc.modules.deaths.DeathEffects;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import net.minecraft.client.Minecraft;

public final class SceneDepth {
  private static RenderTarget snapshot;
  private static boolean armed;
  private static boolean captured;
  private static GpuTexture irisTexture;
  private static GpuTextureView irisView;

  private SceneDepth() {}

  private static boolean wanted() {
    ShadersModule shaders = Shaders.module();
    return (shaders != null && shaders.enabled.get())
        || (DeathEffects.module() != null && DeathEffects.module().enabled.get());
  }

  public static void arm() {
    armed = true;
    captured = false;
  }

  public static void capture() {
    if (!armed) {
      return;
    }
    armed = false;
    captured = false;
    if (!wanted() || IrisCompat.cleanDepth() != null) {
      return;
    }
    RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    if (main == null || main.width <= 0 || main.height <= 0 || main.getDepthTextureView() == null) {
      return;
    }
    if (snapshot == null) {
      snapshot = Targets.create("CxVisuals scene depth", main.width, main.height);
    } else if (snapshot.width != main.width || snapshot.height != main.height) {
      snapshot.resize(main.width, main.height);
    }
    snapshot.copyDepthFrom(main);
    captured = true;
  }

  public static GpuTextureView view(RenderTarget main) {
    Object clean = IrisCompat.cleanDepth();
    if (clean instanceof GpuTexture texture) {
      if (texture != irisTexture) {
        if (irisView != null) {
          irisView.close();
        }
        irisView = RenderSystem.getDevice().createTextureView(texture);
        irisTexture = texture;
      }
      return irisView;
    }
    return attachment(main);
  }

  public static GpuTextureView attachment(RenderTarget main) {
    return captured && snapshot != null ? snapshot.getDepthTextureView() : main.getDepthTextureView();
  }
}
