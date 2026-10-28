package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.shaders.trails.Runner;
import me.cxiy.cxvisuals.modules.shaders.trails.Wake;
import org.joml.Vector4fc;

public final class TrailPreview extends StagePreview {
  private static final TrailPreview INSTANCE = new TrailPreview();
  private static final float DISTANCE = 6.2F;
  private static final float BODY_TURN = 180.0F;

  private final TrailBatch batch = new TrailBatch("trail_preview");
  private final Wake wake = new Wake();

  private TrailPreview() {
    super("Entity trail");
    wake.identity(1);
  }

  public static void install() {
    McCanvas.stages(Canvas.TRAILS, INSTANCE);
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
    return DISTANCE;
  }

  @Override
  float lift() {
    return 0.0F;
  }

  @Override
  float figureX() {
    return Runner.x(clock);
  }

  @Override
  float figureZ() {
    return Runner.z(clock);
  }

  @Override
  float turn(float yaw) {
    return Runner.heading(clock) - (float) Math.toDegrees(yaw) - BODY_TURN;
  }

  @Override
  float stride() {
    return Runner.STRIDE;
  }

  @Override
  int accent() {
    return module().trailPalette.get().first();
  }

  @Override
  float glow() {
    return 0.0F;
  }

  @Override
  boolean compiles(GpuDevice device) {
    return TrailBatch.compiles(device);
  }

  @Override
  int build() {
    Runner.fill(wake, clock, module().trailSeconds());
    return batch.build(wake, view, module(), Runner.nanos(clock));
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
    batch.draw(pass);
  }

  @Override
  void releaseBatch() {
    batch.release();
  }
}
