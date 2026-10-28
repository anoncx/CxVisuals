package me.cxiy.cxvisuals.mc.compat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;

public final class Swing {
  private Swing() {}

  public static boolean mainHand(LivingEntity entity) {
    LivingEntity.SwingDescription swing = entity.getCurrentSwing();
    return swing != null && swing.hand() == InteractionHand.MAIN_HAND;
  }

  public static void start(LivingEntity entity) {
    entity.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
  }

  public static float progress(LivingEntity entity, float partial) {
    return entity.getSwingAnimation(partial);
  }
}
