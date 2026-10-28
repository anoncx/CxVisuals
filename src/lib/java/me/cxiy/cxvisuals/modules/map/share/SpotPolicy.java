package me.cxiy.cxvisuals.modules.map.share;

public final class SpotPolicy {
  public static final long MIN_GAP_MILLIS = 250L;
  public static final long HEARTBEAT_MILLIS = 1_000L;
  public static final long LIFETIME_MILLIS = 10_000L;
  public static final long FADE_MILLIS = 3_000L;
  private static final double MOVED_SQUARED = 0.25;
  private static final float TURNED_DEGREES = 5.0F;

  public boolean shouldSend(SpotCodec.Spot last, SpotCodec.Spot now, long sinceMillis) {
    if (last == null) {
      return true;
    }
    if (sinceMillis < MIN_GAP_MILLIS) {
      return false;
    }
    if (!last.dimension().equals(now.dimension())) {
      return true;
    }
    double dx = now.x() - last.x();
    double dy = now.y() - last.y();
    double dz = now.z() - last.z();
    if (dx * dx + dy * dy + dz * dz > MOVED_SQUARED) {
      return true;
    }
    float turn = Math.abs(((now.yaw() - last.yaw()) % 360.0F + 540.0F) % 360.0F - 180.0F);
    return turn > TURNED_DEGREES || sinceMillis >= HEARTBEAT_MILLIS;
  }

  public static float visibility(long sentAt, long nowMillis) {
    long age = nowMillis - sentAt;
    if (age <= LIFETIME_MILLIS - FADE_MILLIS) {
      return 1.0F;
    }
    if (age >= LIFETIME_MILLIS) {
      return 0.0F;
    }
    return (LIFETIME_MILLIS - age) / (float) FADE_MILLIS;
  }
}
