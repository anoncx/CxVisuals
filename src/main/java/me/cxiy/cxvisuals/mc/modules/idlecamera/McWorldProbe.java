package me.cxiy.cxvisuals.mc.modules.idlecamera;

import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.modules.idlecamera.camera.WorldProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class McWorldProbe implements WorldProbe {
  private static final double SPREAD = 0.1;
  private static final int RAYS = 8;

  private Level level;
  private Entity entity;

  void aim(Level level, Entity entity) {
    this.level = level;
    this.entity = entity;
  }

  void release() {
    level = null;
    entity = null;
  }

  @Override
  public double clearDistance(Vec3d from, Vec3d to) {
    double full = from.distance(to);
    if (level == null || entity == null || full < 1e-6) {
      return full;
    }
    double clear = full;
    for (int i = 0; i < RAYS; i++) {
      double ox = ((i & 1) * 2 - 1) * SPREAD;
      double oy = ((i >> 1 & 1) * 2 - 1) * SPREAD;
      double oz = ((i >> 2 & 1) * 2 - 1) * SPREAD;
      Vec3 start = new Vec3(from.x() + ox, from.y() + oy, from.z() + oz);
      Vec3 end = new Vec3(to.x() + ox, to.y() + oy, to.z() + oz);
      HitResult hit =
          level.clip(
              new ClipContext(
                  start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
      if (hit.getType() != HitResult.Type.MISS) {
        clear = Math.min(clear, hit.getLocation().distanceTo(start));
      }
    }
    return clear;
  }
}
