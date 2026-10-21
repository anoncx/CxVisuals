package me.cxiy.cxvisuals.modules.environment.particles;

public final class Wind {
  private static final float SPEED = 1.3F;
  private static final float CALM_SHARE = 0.35F;

  public float x;
  public float z;
  public float gust;

  public void update(float time, float strength) {
    float heading = 0.7F + Waves.wave(time * 0.011F) * 2.4F;
    float swell = Math.clamp(0.5F + 0.5F * Waves.wave(time * 0.23F + 7.0F), 0.0F, 1.0F);
    gust = swell * swell * (3.0F - 2.0F * swell);
    float speed = strength * SPEED * (CALM_SHARE + (1.0F - CALM_SHARE) * gust);
    x = Waves.cos(heading) * speed;
    z = Waves.sin(heading) * speed;
  }
}
