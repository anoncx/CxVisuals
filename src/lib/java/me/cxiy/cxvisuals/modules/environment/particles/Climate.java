package me.cxiy.cxvisuals.modules.environment.particles;

public final class Climate {
  public static final float LEVELS = 15.0F;

  private static final float DARKEST = 0.4F;
  private static final float NETHER_LIGHT = 0.62F;
  private static final float END_LIGHT = 0.5F;

  public enum Realm {
    OVERWORLD,
    NETHER,
    END
  }

  public Realm realm = Realm.OVERWORLD;
  public String biome = "";
  public boolean cold;
  public float rain;
  public float night;
  public float open = 1.0F;
  public float light = 1.0F;
  public float fogStart = 512.0F;
  public float fogEnd = 1024.0F;
  public float far = 256.0F;

  public float clearNight() {
    return realm == Realm.OVERWORLD ? night * (1.0F - rain) * open : 0.0F;
  }

  public float lit(float sky, float lamps) {
    return switch (realm) {
      case NETHER -> NETHER_LIGHT;
      case END -> END_LIGHT;
      case OVERWORLD ->
          Math.max(
              DARKEST, Math.max(sky * (1.0F - 0.6F * night) * (1.0F - 0.3F * rain), lamps * 0.85F));
    };
  }
}
