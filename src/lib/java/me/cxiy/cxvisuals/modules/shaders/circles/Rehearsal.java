package me.cxiy.cxvisuals.modules.shaders.circles;

import me.cxiy.cxvisuals.modules.shaders.ShadersModule;

public final class Rehearsal {
  public static final float AIR = 0.6F;
  public static final float HEIGHT = 1.25F;

  private static final float REST = 0.8F;
  private static final float SHORTEST = 2.2F;
  private static final int JUMP_SEED = 0x2A1;
  private static final int LAND_SEED = 0x5C3;

  private Rehearsal() {}

  public static float loop(ShadersModule module) {
    return Math.max(SHORTEST, AIR + module.ringLife() + REST);
  }

  public static float height(float clock) {
    if (clock <= 0.0F || clock >= AIR) {
      return 0.0F;
    }
    float t = clock / AIR;
    return HEIGHT * 4.0F * t * (1.0F - t);
  }

  public static boolean jumps(ShadersModule module) {
    return module.onJump.get() || !module.onLand.get();
  }

  public static void stage(Circles into, ShadersModule module, float clock) {
    into.clear();
    if (jumps(module) && clock >= 0.0F) {
      into.spawn(0.0, 0.0, 0.0, 0.0F, 1.0F, 0L, JUMP_SEED);
    }
    if (module.onLand.get() && clock >= AIR) {
      into.spawn(0.0, 0.0, 0.0, 0.0F, Hops.landing(HEIGHT), nanos(AIR), LAND_SEED);
    }
  }

  public static long nanos(float clock) {
    return (long) (clock * 1.0e9);
  }
}
