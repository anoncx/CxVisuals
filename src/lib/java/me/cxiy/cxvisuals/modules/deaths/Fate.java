package me.cxiy.cxvisuals.modules.deaths;

public enum Fate {
  RITUAL,
  DISSOLVE,
  ASCEND,
  GLITCH,
  MATRIX,
  VOID,
  RANDOM;

  public static final String[] LABELS = {
    "Ritual", "Dissolve", "Ascend", "Glitch", "Dot matrix", "Void", "Random"
  };
  public static final int CHOICES = 6;

  private static final float SINK_FROM = 0.22F;
  private static final float SINK_SPAN = 0.62F;
  private static final float SINK_DEPTH = 1.2F;
  private static final float TREMBLE = 0.035F;
  private static final float RISE = 2.4F;
  private static final float CRUSH_FROM = 0.45F;
  private static final float CRUSH_SPAN = 0.33F;
  private static final float THINNEST = 0.015F;
  private static final float SHRINK_FROM = 0.8F;
  private static final float SHRINK_SPAN = 0.16F;
  private static final float HORIZON_OPEN_FROM = 0.42F;
  private static final float HORIZON_OPEN_TO = 0.72F;
  private static final float HORIZON_SHUT_FROM = 0.86F;
  private static final float HORIZON_SHUT_TO = 0.93F;

  public Fate resolve(int seed) {
    return this == RANDOM ? values()[Math.floorMod(seed * 0x9E3779B1, CHOICES)] : this;
  }

  public void pose(float t, float height, int seed, Posture out) {
    out.reset();
    switch (this) {
      case RITUAL -> {
        float pull = unit((t - SINK_FROM) / SINK_SPAN);
        out.rise = -SINK_DEPTH * height * pull * pull * pull;
        float shake = smooth((t - 0.1F) / 0.12F) * (1.0F - smooth((t - 0.75F) / 0.15F));
        out.jitterX = (float) Math.sin(t * 173.0F + seed) * TREMBLE * shake;
        out.jitterZ = (float) Math.cos(t * 151.0F + seed * 0.7F) * TREMBLE * shake;
      }
      case ASCEND -> {
        float lift = smooth(t);
        out.rise = RISE * lift * lift;
        out.scale = 1.0F - 0.12F * lift;
      }
      case GLITCH -> {
        out.squash = 1.0F - (1.0F - THINNEST) * smooth((t - CRUSH_FROM) / CRUSH_SPAN);
        out.scale = 1.0F - smooth((t - SHRINK_FROM) / SHRINK_SPAN);
        out.pivot = 0.5F;
      }
      default -> {}
    }
  }

  public static float holeRadius(float height, float width) {
    return Math.max(height, width) * 0.2F + 0.1F;
  }

  public static float horizon(float t) {
    return smooth((t - HORIZON_OPEN_FROM) / (HORIZON_OPEN_TO - HORIZON_OPEN_FROM))
        * (1.0F - smooth((t - HORIZON_SHUT_FROM) / (HORIZON_SHUT_TO - HORIZON_SHUT_FROM)));
  }

  private static float unit(float t) {
    return t < 0.0F ? 0.0F : Math.min(t, 1.0F);
  }

  static float smooth(float t) {
    float c = unit(t);
    return c * c * (3.0F - 2.0F * c);
  }
}
