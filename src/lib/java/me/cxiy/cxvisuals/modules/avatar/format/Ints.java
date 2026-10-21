package me.cxiy.cxvisuals.modules.avatar.format;

import java.util.Arrays;

final class Ints {
  private int[] data;
  private int size;

  Ints(int capacity) {
    data = new int[Math.max(4, capacity)];
  }

  void add(int value) {
    if (size == data.length) {
      data = Arrays.copyOf(data, data.length * 2);
    }
    data[size++] = value;
  }

  void add(int a, int b, int c) {
    add(a);
    add(b);
    add(c);
  }

  int get(int index) {
    return data[index];
  }

  void clear() {
    size = 0;
  }

  int size() {
    return size;
  }

  int[] toArray() {
    return Arrays.copyOf(data, size);
  }
}
