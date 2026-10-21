package me.cxiy.cxvisuals.modules.donutsmp;

import java.util.Arrays;

public final class Trail {
  private final float[] ring;
  private int head;
  private int size;
  private int revision;

  public Trail(int capacity) {
    ring = new float[capacity];
  }

  public void add(float value) {
    ring[head] = value;
    head = (head + 1) % ring.length;
    size = Math.min(ring.length, size + 1);
    revision++;
  }

  public void clear() {
    Arrays.fill(ring, 0.0F);
    head = 0;
    size = 0;
    revision++;
  }

  public int size() {
    return size;
  }

  public int revision() {
    return revision;
  }

  public void copyTo(float[] out) {
    int start = (head - size + ring.length) % ring.length;
    for (int i = 0; i < size; i++) {
      out[i] = ring[(start + i) % ring.length];
    }
  }

  public int capacity() {
    return ring.length;
  }
}
