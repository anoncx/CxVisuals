package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.shaders.circles.Circles;
import me.cxiy.cxvisuals.modules.shaders.circles.Rehearsal;
import org.joml.Vector4fc;

public final class CirclePreview extends StagePreview {
  private static final CirclePreview INSTANCE = new CirclePreview();

  private final CircleBatch batch = new CircleBatch("jump_preview");
  private final Circles circles = new Circles();

  private CirclePreview() {
    super("Jump circle");
  }

  public static void install() {
    McCanvas.stages(Canvas.CIRCLES, INSTANCE);
  }

  public static void flush() {
    INSTANCE.present();
  }

  private static ShadersModule module() {
    return Shaders.module();
  }

  @Override
  boolean ready() {
    return module() != null;
  }

  @Override
  float distance() {
    return 3.6F + module().ringRadius() * 1.6F;
  }

  @Override
  float lift() {
    return Rehearsal.height(clock);
  }

  @Override
  float figureX() {
    return 0.0F;
  }

  @Override
  float figureZ() {
    return 0.0F;
  }

  @Override
  float turn(float yaw) {
    return -(float) Math.toDegrees(yaw);
  }

  @Override
  float stride() {
    return 0.0F;
  }

  @Override
  int accent() {
    return module().ringPalette.get().first();
  }

  @Override
  float glow() {
    return module().ringGlowScale() * module().ringIntensityScale();
  }

  @Override
  boolean compiles(GpuDevice device) {
    return CircleBatch.compiles(device);
  }

  @Override
  int build() {
    Rehearsal.stage(circles, module(), clock);
    return batch.build(circles, view, module(), Rehearsal.nanos(clock));
  }

  @Override
  void light(CommandEncoder encoder, Vector4fc cut) {
    batch.look(encoder, lens, module(), clock, cut);
  }

  @Override
  void upload(CommandEncoder encoder) {
    batch.upload(encoder);
  }

  @Override
  void drawBatch(RenderPass pass) {
    batch.draw(pass, false);
  }

  @Override
  void releaseBatch() {
    batch.release();
  }
}
