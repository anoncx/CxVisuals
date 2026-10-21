package me.cxiy.cxvisuals.mc.modules.idlecamera;

import me.cxiy.cxvisuals.core.math.Angles;
import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Avoidance;
import me.cxiy.cxvisuals.modules.idlecamera.camera.CameraPose;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Director;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotType;
import me.cxiy.cxvisuals.modules.idlecamera.idle.CinematicController;
import me.cxiy.cxvisuals.modules.idlecamera.idle.CinematicController.Conditions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CameraHook {
  private static final double DETACH_DISTANCE = 0.3;
  private static final double MAX_FRAME_SECONDS = 0.25;
  private static final int MAX_FAILURES = 3;
  private static final Conditions[] CONDITIONS = new Conditions[32];
  private static final McWorldProbe PROBE = new McWorldProbe();

  public static double x;
  public static double y;
  public static double z;
  public static float yaw;
  public static float pitch;
  public static float roll;
  public static boolean detach;

  private static long lastNanos;
  private static float targetFov;
  private static boolean fovActive;
  private static int failures;

  static {
    for (int bits = 0; bits < CONDITIONS.length; bits++) {
      CONDITIONS[bits] =
          new Conditions(
              (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0);
    }
  }

  private CameraHook() {}

  public static boolean frame(
      Entity entity, float partialTicks, Vec3 position, float vanillaYaw, float vanillaPitch) {
    long now = System.nanoTime();
    double dt = lastNanos == 0L ? 0.0 : Math.min((now - lastNanos) / 1.0e9, MAX_FRAME_SECONDS);
    lastNanos = now;
    fovActive = false;
    IdleCamera.weight = 0.0F;

    CinematicController controller = IdleCamera.controller();
    if (controller == null || entity == null || failures >= MAX_FAILURES) {
      return false;
    }
    try {
      Minecraft mc = Minecraft.getInstance();
      boolean alive = !(entity instanceof LivingEntity living) || living.isAlive();
      int bits =
          (mc.level != null ? 1 : 0)
              | (alive ? 2 : 0)
              | (mc.gui.screen() != null ? 4 : 0)
              | (mc.isPaused() ? 8 : 0)
              | (mc.gui.screen() instanceof PauseScreen ? 16 : 0);
      controller.frame(dt, CONDITIONS[bits]);
      if (!controller.active()) {
        return false;
      }
      PROBE.aim(mc.level, entity);
      Director.Output out =
          controller.director().update(dt, McSubject.of(entity, partialTicks), PROBE);
      CameraPose pose = out.pose();
      double w = out.weight();
      if (pose == null || w <= 0.0 || !pose.isFinite()) {
        return false;
      }
      double travel = w;
      if (w < 1.0 && IdleCamera.module().avoidBlocks.get()) {
        travel =
            Math.min(
                w,
                Avoidance.reach(new Vec3d(position.x, position.y, position.z), pose.pos(), PROBE));
      }
      x = position.x + (pose.pos().x() - position.x) * travel;
      y = position.y + (pose.pos().y() - position.y) * travel;
      z = position.z + (pose.pos().z() - position.z) * travel;
      yaw = (float) (vanillaYaw + Angles.delta(vanillaYaw, pose.yaw()) * w);
      pitch =
          (float) Math.max(-90.0, Math.min(90.0, vanillaPitch + (pose.pitch() - vanillaPitch) * w));
      roll = (float) (pose.roll() * w);
      double dx = x - position.x;
      double dy = y - position.y;
      double dz = z - position.z;
      detach = dx * dx + dy * dy + dz * dz > DETACH_DISTANCE * DETACH_DISTANCE;
      targetFov = (float) pose.fov();
      fovActive = true;
      IdleCamera.weight = (float) w;
      return true;
    } catch (RuntimeException e) {
      fail(controller, e);
      return false;
    } finally {
      PROBE.release();
    }
  }

  public static float fov(float vanillaFov) {
    return fovActive ? vanillaFov + (targetFov - vanillaFov) * IdleCamera.weight : vanillaFov;
  }

  private static void fail(CinematicController controller, RuntimeException e) {
    failures++;
    Director director = controller.director();
    ShotType type = director.currentType();
    if (type != null) {
      director.disableForSession(type);
    }
    director.abort();
    CxVisualsClient.LOG.error(
        "Cinematic stopped after an error{}", type == null ? "" : " in shot " + type.id(), e);
  }
}
