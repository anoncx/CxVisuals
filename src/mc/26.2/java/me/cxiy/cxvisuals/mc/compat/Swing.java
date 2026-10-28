package me.cxiy.cxvisuals.mc.compat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

public final class Swing {
  private Swing() {}

  public static boolean mainHand(LivingEntity entity) {
    return entity.swinging && entity.swingingArm == InteractionHand.MAIN_HAND;
  }

  public static void start(LivingEntity entity) {
    entity.swing(InteractionHand.MAIN_HAND);
  }

  public static float progress(LivingEntity entity, float partial) {
    return entity.getAttackAnim(partial);
  }
}
