package me.cxiy.cxvisuals.modules.avatar.format;

import java.util.Arrays;

final class Floats {
  private float[] data;
  private int size;

  Floats(int capacity) {
    data = new float[Math.max(4, capacity)];
  }

  void add(float value) {
    if (size == data.length) {
      data = Arrays.copyOf(data, data.length * 2);
    }
    data[size++] = value;
  }

  void add(float a, float b) {
    add(a);
    add(b);
  }

  void add(float a, float b, float c) {
    add(a);
    add(b);
    add(c);
  }

  float get(int index) {
    return data[index];
  }

  int size() {
    return size;
  }

  float[] toArray() {
    return Arrays.copyOf(data, size);
  }
}
