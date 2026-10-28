package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class ScreenLayer {
  private static final int USAGE =
      GpuTexture.USAGE_TEXTURE_BINDING
          | GpuTexture.USAGE_RENDER_ATTACHMENT
          | GpuTexture.USAGE_COPY_SRC;
  private static final Vector4fc CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
  private static final Matrix3x2f IDENTITY = new Matrix3x2f();
  private static final int OPAQUE = 0xFFFFFFFF;
  private static final double EMPTY_DEPTH = 0.0;

  private static GpuTexture color;
  private static GpuTextureView colorView;
  private static GpuTexture depth;
  private static GpuTextureView depthView;
  private static TextureSetup setup;
  private static MethodHandle execute;
  private static int width;
  private static int height;
  private static boolean cleared;
  private static boolean broken;

  private ScreenLayer() {}

  static boolean prepare() {
    if (broken) {
      return false;
    }
    try {
      if (execute == null) {
        Class<?> draw = Class.forName("net.minecraft.client.gui.render.GuiRenderer$Draw");
        Method method = GuiRenderer.class.getDeclaredMethod("executeDraw", draw, RenderPass.class);
        method.setAccessible(true);
        execute = MethodHandles.lookup().unreflect(method);
      }
      WindowRenderState window =
          Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
      if (window.width <= 0 || window.height <= 0) {
        return false;
      }
      if (color == null || window.width != width || window.height != height) {
        release();
        width = window.width;
        height = window.height;
        color =
            RenderSystem.getDevice()
                .createTexture(
                    () -> "cxvisuals_screen", USAGE, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        colorView = RenderSystem.getDevice().createTextureView(color);
        depth =
            RenderSystem.getDevice()
                .createTexture(
                    () -> "cxvisuals_screen_depth",
                    USAGE,
                    GpuFormat.D32_FLOAT,
                    width,
                    height,
                    1,
                    1);
        depthView = RenderSystem.getDevice().createTextureView(depth);
        setup =
            TextureSetup.singleTexture(
                colorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      }
      return true;
    } catch (ReflectiveOperationException | RuntimeException e) {
      fail(e);
      return false;
    }
  }

  static GuiElementRenderState element(int guiWidth, int guiHeight, float alpha) {
    int a = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, alpha)));
    int tint = a >= 255 ? OPAQUE : a << 24 | a << 16 | a << 8 | a;
    return new ModelRenderState(
        IDENTITY,
        0.0F,
        0.0F,
        guiWidth,
        guiHeight,
        tint,
        setup,
        null,
        new ScreenRectangle(0, 0, guiWidth, guiHeight),
        0.0F,
        Cut.NONE);
  }

  static TextureSetup setup() {
    return setup;
  }

  public static boolean usable() {
    return !broken && color != null && execute != null;
  }

  public static RenderPass pass(Supplier<String> label, boolean freshDepth) {
    Optional<Vector4fc> wipe = cleared ? Optional.empty() : Optional.of(CLEAR);
    cleared = true;
    return RenderSystem.getDevice()
        .createCommandEncoder()
        .createRenderPass(
            label,
            colorView,
            wipe,
            depthView,
            freshDepth ? OptionalDouble.of(EMPTY_DEPTH) : OptionalDouble.empty());
  }

  public static void execute(Object renderer, Object draw, RenderPass pass) {
    try {
      execute.invoke((GuiRenderer) renderer, draw, pass);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new IllegalStateException(e);
    }
  }

  static void endFrame() {
    cleared = false;
  }

  private static void fail(Exception e) {
    broken = true;
    release();
    CxVisualsClient.LOG.error("Screen layer unavailable, tilted game screens draw directly", e);
  }

  private static void release() {
    if (colorView != null) {
      colorView.close();
      colorView = null;
    }
    if (color != null) {
      color.close();
      color = null;
    }
    if (depthView != null) {
      depthView.close();
      depthView = null;
    }
    if (depth != null) {
      depth.close();
      depth = null;
    }
    setup = null;
  }
}
