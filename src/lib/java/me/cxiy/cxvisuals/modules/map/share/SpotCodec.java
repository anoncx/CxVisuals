package me.cxiy.cxvisuals.modules.map.share;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class SpotCodec {
  public record Spot(String dimension, double x, double y, double z, float yaw, long at) {}

  private static final int DIMENSION_LIMIT = 48;
  private static final double EDGE = 30_000_000.0;
  private static final double HEIGHT = 1_000_000.0;
  private static final int FIXED = 1 + 3 * Double.BYTES + Float.BYTES + Long.BYTES;

  private SpotCodec() {}

  public static byte[] encode(Spot spot) {
    String name =
        spot.dimension().length() > DIMENSION_LIMIT
            ? spot.dimension().substring(0, DIMENSION_LIMIT)
            : spot.dimension();
    byte[] raw = name.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(FIXED + raw.length)
        .put((byte) raw.length)
        .put(raw)
        .putDouble(spot.x())
        .putDouble(spot.y())
        .putDouble(spot.z())
        .putFloat(spot.yaw())
        .putLong(spot.at())
        .array();
  }

  public static Spot decode(byte[] data) {
    if (data == null || data.length < FIXED) {
      return null;
    }
    try {
      ByteBuffer in = ByteBuffer.wrap(data);
      int length = in.get() & 0xFF;
      if (length > DIMENSION_LIMIT * 4 || data.length != FIXED + length) {
        return null;
      }
      byte[] raw = new byte[length];
      in.get(raw);
      Spot spot =
          new Spot(
              new String(raw, StandardCharsets.UTF_8),
              in.getDouble(),
              in.getDouble(),
              in.getDouble(),
              in.getFloat(),
              in.getLong());
      return valid(spot) ? spot : null;
    } catch (BufferUnderflowException e) {
      return null;
    }
  }

  private static boolean valid(Spot spot) {
    return !spot.dimension().isEmpty()
        && Math.abs(spot.x()) <= EDGE
        && Math.abs(spot.z()) <= EDGE
        && Math.abs(spot.y()) <= HEIGHT
        && Float.isFinite(spot.yaw());
  }
}
