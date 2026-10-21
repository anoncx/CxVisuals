package me.cxiy.cxvisuals.mc.modules.idlecamera;

import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Subject;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class McSubject {
  private static final double MOVING_SPEED = 0.5;
  private static final double TICKS_PER_SECOND = 20.0;
  private static final double STANDING_HEIGHT = 1.8;
  private static final double SWIM_BODY_START = -1.0;
  private static final double SWIM_BODY_LIFT = 0.3;

  private McSubject() {}

  static Subject of(Entity cameraEntity, float partialTicks) {
    Entity root = cameraEntity.getRootVehicle();
    Vec3 position = root.getPosition(partialTicks);
    Vec3 motion = root.getDeltaMovement();
    double vx = motion.x * TICKS_PER_SECOND;
    double vy = motion.y * TICKS_PER_SECOND;
    double vz = motion.z * TICKS_PER_SECOND;
    Vec3d velocity = new Vec3d(vx, vy, vz);
    boolean moving = Math.hypot(vx, vz) > MOVING_SPEED;

    if (root == cameraEntity && cameraEntity instanceof LivingEntity living) {
      double bodyYaw = Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot);
      double facing = moving ? Math.toDegrees(Math.atan2(-vx, vz)) : bodyYaw;
      boolean gliding = living.isFallFlying();
      if (gliding || living.isVisuallySwimming()) {
        EntityDimensions standing = living.getDimensions(Pose.STANDING);
        double length = standing.height();
        double scale = length / STANDING_HEIGHT;
        Vec3d origin = new Vec3d(position.x, position.y, position.z);
        if (gliding) {
          return Subject.lying(
              origin,
              velocity,
              facing,
              bodyYaw,
              living.getViewXRot(partialTicks),
              length,
              standing.width(),
              0.0,
              0.0);
        }
        double pitch = living.isInWater() ? living.getViewXRot(partialTicks) : 0.0;
        return Subject.lying(
            origin,
            velocity,
            facing,
            bodyYaw,
            pitch,
            length,
            standing.width(),
            SWIM_BODY_START * scale,
            SWIM_BODY_LIFT * scale);
      }
    }

    AABB box = root.getBoundingBox();
    if (root != cameraEntity) {
      box = box.minmax(cameraEntity.getBoundingBox());
    }
    double width = Math.max(box.getXsize(), box.getZsize());
    double yaw;
    if (moving) {
      yaw = Math.toDegrees(Math.atan2(-vx, vz));
    } else if (root instanceof LivingEntity living) {
      yaw = living.yBodyRot;
    } else {
      yaw = root.getYRot();
    }
    Vec3d base = new Vec3d(position.x, position.y, position.z);
    if (cameraEntity instanceof LivingEntity) {
      Vec3 eye = cameraEntity.getEyePosition(partialTicks);
      return Subject.upright(base, new Vec3d(eye.x, eye.y, eye.z), velocity, yaw, width);
    }
    double height = box.getYsize();
    return new Subject(base.add(0, height * 0.5, 0), velocity, yaw, width, height);
  }
}
