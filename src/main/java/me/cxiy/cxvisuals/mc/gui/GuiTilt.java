package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import java.util.BitSet;
import me.cxiy.cxvisuals.gui.spatial.Warp;
import me.cxiy.cxvisuals.mc.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;

public final class GuiTilt {
  public interface Strata {
    int cxvisuals$strata();
  }

  private static final float MODEL_DEPTH = -11000.0F;
  private static final int NONE = -1;
  public static final int FLAT = 0;
  public static final int TILTED = 1;
  public static final int CAPTURED = 2;
  private static final Warp WARP = new Warp();
  private static final Matrix4f MATRIX = new Matrix4f();
  private static final Matrix4f SCRATCH = new Matrix4f();
  private static final Matrix4f PLACED = new Matrix4f();
  private static final float OPAQUE = 1.0F;
  private static final BitSet DRAWS = new BitSet();
  private static final BitSet LAYERED = new BitSet();

  private static ProjectionMatrixBuffer buffer;
  private static GpuBufferSlice slice;
  private static int from = NONE;
  private static int to = NONE;
  private static int stratum;
  private static boolean scissors;
  private static boolean capture;
  private static boolean placing;
  private static float opacity = OPAQUE;
  private static int guiWidth;
  private static int guiHeight;

  private GuiTilt() {}

  private static int strata(GuiGraphicsExtractor graphics) {
    return ((Strata) ((GuiGraphicsExtractorAccessor) graphics).cxvisuals$guiRenderState())
        .cxvisuals$strata();
  }

  public static boolean begin(GuiGraphicsExtractor graphics, Warp warp, boolean projectScissors) {
    if (from != NONE || warp.flat()) {
      return false;
    }
    graphics.nextStratum();
    from = strata(graphics) - 1;
    to = Integer.MAX_VALUE;
    capture = projectScissors && ScreenLayer.prepare();
    scissors = projectScissors && !capture;
    guiWidth = graphics.guiWidth();
    guiHeight = graphics.guiHeight();
    WARP.copy(warp);
    return true;
  }

  public static boolean place(GuiGraphicsExtractor graphics, float[] rows, float alpha) {
    if (from != NONE || !ScreenLayer.prepare()) {
      return false;
    }
    graphics.nextStratum();
    from = strata(graphics) - 1;
    to = Integer.MAX_VALUE;
    capture = true;
    scissors = false;
    placing = true;
    opacity = alpha;
    guiWidth = graphics.guiWidth();
    guiHeight = graphics.guiHeight();
    PLACED.set(
        rows[0], rows[3], 0.0F, rows[6], rows[1], rows[4], 0.0F, rows[7], 0.0F, 0.0F, 0.0F, 0.0F,
        rows[2], rows[5], 0.0F, rows[8]);
    return true;
  }

  public static void end(GuiGraphicsExtractor graphics) {
    if (from != NONE && to == Integer.MAX_VALUE) {
      graphics.nextStratum();
      to = strata(graphics) - 1;
      if (capture) {
        ((GuiGraphicsExtractorAccessor) graphics)
            .cxvisuals$guiRenderState()
            .addGuiElement(
                Dissolve.pending()
                    ? Dissolve.element(guiWidth, guiHeight)
                    : ScreenLayer.element(guiWidth, guiHeight, opacity));
        graphics.nextStratum();
      }
    }
  }

  public static boolean active() {
    return from != NONE;
  }

  public static void stratum(int index) {
    stratum = index;
  }

  public static int kind() {
    if (from == NONE) {
      return FLAT;
    }
    if (stratum >= from && stratum < to) {
      return capture ? CAPTURED : TILTED;
    }
    return capture && stratum == to ? TILTED : FLAT;
  }

  public static void mark(int draw, int kind) {
    DRAWS.set(draw, kind == TILTED);
    LAYERED.set(draw, kind == CAPTURED);
  }

  public static boolean marked(int draw) {
    return DRAWS.get(draw);
  }

  public static boolean captured(int draw) {
    return LAYERED.get(draw);
  }

  public static boolean capturing() {
    return capture && !LAYERED.isEmpty();
  }

  public static void upload(Projection ortho) {
    if (buffer == null) {
      buffer = new ProjectionMatrixBuffer("cxvisuals tilt");
    }
    ortho
        .getMatrix(MATRIX)
        .translate(0.0F, 0.0F, MODEL_DEPTH)
        .mul(placing ? PLACED : WARP.matrix(SCRATCH))
        .translate(0.0F, 0.0F, -MODEL_DEPTH);
    slice = buffer.getBuffer(MATRIX);
  }

  public static GpuBufferSlice slice() {
    return slice;
  }

  public static ScreenRectangle scissor(ScreenRectangle area) {
    if (!scissors) {
      return area;
    }
    WARP.bounds(area.left(), area.top(), area.right(), area.bottom(), 0.0F, guiWidth, guiHeight);
    return new ScreenRectangle(
        WARP.left(), WARP.top(), WARP.right() - WARP.left(), WARP.bottom() - WARP.top());
  }

  public static void endFrame() {
    Dissolve.clear();
    from = NONE;
    to = NONE;
    DRAWS.clear();
    LAYERED.clear();
    capture = false;
    placing = false;
    opacity = OPAQUE;
    ScreenLayer.endFrame();
  }
}
