package me.cxiy.cxvisuals.modules.map.share;

import java.util.function.LongSupplier;

public final class TokenBucket {
  private static final double NANOS = 1_000_000_000.0;

  private final LongSupplier nanos;
  private final double perNano;
  private final double burst;
  private double tokens;
  private long last;

  public TokenBucket(LongSupplier nanos, long bytesPerSecond, long burstBytes) {
    this.nanos = nanos;
    this.perNano = bytesPerSecond / NANOS;
    this.burst = burstBytes;
    this.tokens = burstBytes;
    this.last = nanos.getAsLong();
  }

  public synchronized boolean tryTake(long bytes) {
    refill();
    if (bytes > burst || tokens < bytes) {
      return false;
    }
    tokens -= bytes;
    return true;
  }

  public synchronized long waitNanos(long bytes) {
    refill();
    if (bytes > burst) {
      return Long.MAX_VALUE;
    }
    return tokens >= bytes ? 0L : (long) Math.ceil((bytes - tokens) / perNano);
  }

  private void refill() {
    long now = nanos.getAsLong();
    tokens = Math.min(burst, tokens + (now - last) * perNano);
    last = now;
  }
}
