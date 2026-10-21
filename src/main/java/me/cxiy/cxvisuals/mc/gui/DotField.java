package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

public final class DotField {
  private static final String UNIFORMS = "Dots";
  private static final RenderPipeline PIPELINE =
      RenderPipeline.builder()
          .withBindGroupLayout(
              BindGroupLayout.builder().withUniform(UNIFORMS, UniformType.UNIFORM_BUFFER).build())
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/menu_dots"))
          .withVertexShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/menu_dots"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/menu_dots"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION)
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .withCull(false)
          .build();
  private static final int UNIFORM_SIZE =
      new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putVec4().get();
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final float[] TRIANGLE = {
    -1.0F, -1.0F, 0.0F, 3.0F, -1.0F, 0.0F, -1.0F, 3.0F, 0.0F
  };
  private static final float CHANNEL = 1.0F / 255.0F;

  private static GpuBuffer triangle;
  private static GpuBuffer uniforms;
  private static boolean checked;
  private static boolean usable;

  private DotField() {}

  public static boolean usable() {
    if (!checked) {
      checked = true;
      try {
        usable = RenderSystem.getDevice().precompilePipeline(PIPELINE).isValid();
      } catch (RuntimeException e) {
        usable = false;
        CxVisualsClient.LOG.error("Menu backdrop shader failed to compile", e);
      }
      if (!usable) {
        CxVisualsClient.LOG.warn("Menu backdrop unavailable, the menu falls back to a flat fill");
      }
    }
    return usable;
  }

  private static void prepare(GpuDevice device) {
    if (triangle != null) {
      return;
    }
    try (MemoryStack stack = MemoryStack.stackPush()) {
      ByteBuffer data = stack.malloc(TRIANGLE.length * Float.BYTES).order(ByteOrder.nativeOrder());
      for (float value : TRIANGLE) {
        data.putFloat(value);
      }
      data.flip();
      triangle = device.createBuffer(() -> "cxvisuals_menu_dots", GpuBuffer.USAGE_VERTEX, data);
    }
    uniforms =
        device.createBuffer(() -> "cxvisuals_menu_dots_uniforms", UNIFORM_USAGE, UNIFORM_SIZE);
  }

  private static float red(int argb) {
    return (argb >> 16 & 0xFF) * CHANNEL;
  }

  private static float green(int argb) {
    return (argb >> 8 & 0xFF) * CHANNEL;
  }

  private static float blue(int argb) {
    return (argb & 0xFF) * CHANNEL;
  }

  public static void draw(
      RenderTarget main,
      Theme t,
      float cursorX,
      float cursorY,
      float seconds,
      float pixelsPerUnit) {
    GpuTextureView color = main.getColorTextureView();
    if (color == null || main.width <= 0 || main.height <= 0 || !usable()) {
      return;
    }
    try {
      GpuDevice device = RenderSystem.getDevice();
      prepare(device);
      CommandEncoder encoder = device.createCommandEncoder();
      float topLeft = device.getDeviceInfo().isZZeroToOne() ? 0.0F : 1.0F;
      try (MemoryStack stack = MemoryStack.stackPush()) {
        ByteBuffer data =
            Std140Builder.onStack(stack, UNIFORM_SIZE)
                .putVec4(red(t.paper()), green(t.paper()), blue(t.paper()), 1.0F)
                .putVec4(red(t.ink()), green(t.ink()), blue(t.ink()), 1.0F)
                .putVec4(red(t.red()), green(t.red()), blue(t.red()), 1.0F)
                .putVec4(main.width, main.height, seconds, pixelsPerUnit)
                .putVec4(cursorX, cursorY, topLeft, 0.0F)
                .get();
        encoder.writeToBuffer(uniforms.slice(), data);
      }
      try (RenderPass pass =
          encoder.createRenderPass(() -> "CxVisuals menu dots", color, Optional.empty())) {
        pass.setPipeline(PIPELINE);
        pass.setUniform(UNIFORMS, uniforms);
        pass.setVertexBuffer(0, triangle.slice());
        pass.draw(3, 1, 0, 0);
      }
    } catch (RuntimeException e) {
      usable = false;
      CxVisualsClient.LOG.error(
          "Menu backdrop failed to draw, the menu falls back to a flat fill", e);
    }
  }
}
