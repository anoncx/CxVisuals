package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import java.util.OptionalDouble;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.shaders.circles.Circles;
import me.cxiy.cxvisuals.modules.shaders.circles.Hops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class JumpCircles {
  private static final double REACH = 48.0;
  private static final float RADIANS = (float) (Math.PI / 180.0);
  private static final long CLOCK_WRAP = 3_600_000_000_000L;
  private static final Circles CIRCLES = new Circles();
  private static final Hops HOPS = new Hops();
  private static final CircleBatch BATCH = new CircleBatch("jump_circles");
  private static final View VIEW = new View();
  private static final Vector3f AXIS = new Vector3f();
  private static final Matrix4f LENS = new Matrix4f();
  private static final Hops.Listener SPAWN =
      (x, y, z, yaw, strength) -> CIRCLES.spawn(x, y, z, yaw, strength, System.nanoTime());

  private static boolean compiled;
  private static boolean broken;
  private static int passes;

  private JumpCircles() {}

  public static int passes() {
    return passes;
  }

  public static int alive() {
    return CIRCLES.count();
  }

  public static void tick(Minecraft mc) {
    ShadersModule module = Shaders.module();
    if (broken
        || module == null
        || !module.circlesLive()
        || mc.level == null
        || mc.player == null) {
      HOPS.clear();
      CIRCLES.clear();
      return;
    }
    boolean jumps = module.onJump.get();
    boolean lands = module.onLand.get();
    boolean self = module.ringSelf.get();
    boolean others = module.ringPlayers.get();
    HOPS.begin();
    for (AbstractClientPlayer player : mc.level.players()) {
      boolean mine = player == mc.player;
      if ((mine ? !self : !others)
          || player.isSpectator()
          || player.isInvisible()
          || player.isPassenger()
          || (!mine && player.distanceToSqr(mc.player) > REACH * REACH)) {
        continue;
      }
      HOPS.step(
          player.getId(),
          player.getX(),
          player.getY(),
          player.getZ(),
          player.getYRot() * RADIANS,
          player.onGround(),
          mine,
          jumps,
          lands,
          SPAWN);
    }
    HOPS.end();
  }

  public static void render(GameRenderer renderer) {
    ShadersModule module = Shaders.module();
    if (broken || module == null || CIRCLES.empty()) {
      return;
    }
    long now = System.nanoTime();
    CIRCLES.prune(now, module.ringLife());
    if (CIRCLES.empty()) {
      return;
    }
    try {
      draw(
          renderer.mainRenderTarget(),
          renderer.gameRenderState().levelRenderState.cameraRenderState,
          module,
          now);
    } catch (RuntimeException e) {
      broken = true;
      CIRCLES.clear();
      BATCH.release();
      CxVisualsClient.LOG.error("Jump circles failed to draw, they are off for this session", e);
    }
  }

  private static void draw(
      RenderTarget main, CameraRenderState camera, ShadersModule module, long now) {
    GpuTextureView color = main.getColorTextureView();
    GpuTextureView depth = main.getDepthTextureView();
    if (color == null || depth == null || camera.isPanoramicMode) {
      return;
    }
    GpuDevice device = RenderSystem.getDevice();
    if (!compiled) {
      compiled = true;
      if (!CircleBatch.compiles(device)) {
        broken = true;
        CxVisualsClient.LOG.warn("Jump circle shader failed to compile, circles stay off");
        return;
      }
    }
    look(camera);
    if (BATCH.build(CIRCLES, VIEW, module, now) == 0) {
      return;
    }
    LENS.set(EntityShaders.lens()).mul(camera.viewRotationMatrix);
    CommandEncoder encoder = device.createCommandEncoder();
    BATCH.upload(encoder);
    BATCH.look(encoder, LENS, module, (now % CLOCK_WRAP) / 1.0e9F, CircleBatch.WHOLE);
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals jump circles",
            color,
            Optional.empty(),
            depth,
            OptionalDouble.empty())) {
      BATCH.draw(pass, module.ringThroughWalls.get());
    }
    passes++;
  }

  private static void look(CameraRenderState camera) {
    VIEW.x = camera.pos.x;
    VIEW.y = camera.pos.y;
    VIEW.z = camera.pos.z;
    camera.orientation.transform(AXIS.set(0.0F, 0.0F, -1.0F));
    VIEW.fx = AXIS.x;
    VIEW.fy = AXIS.y;
    VIEW.fz = AXIS.z;
    camera.orientation.transform(AXIS.set(1.0F, 0.0F, 0.0F));
    VIEW.rx = AXIS.x;
    VIEW.ry = AXIS.y;
    VIEW.rz = AXIS.z;
    camera.orientation.transform(AXIS.set(0.0F, 1.0F, 0.0F));
    VIEW.ux = AXIS.x;
    VIEW.uy = AXIS.y;
    VIEW.uz = AXIS.z;
  }
}
