package me.cxiy.cxvisuals.modules.hud;

public final class ChatLog {
  public static final int CAPACITY = 32;

  private final String[] text = new String[CAPACITY];
  private final int[] ids = new int[CAPACITY];
  private final boolean[] system = new boolean[CAPACITY];
  private final float[] born = new float[CAPACITY];
  private int head;
  private int size;
  private int revision;
  private int next;
  private float clock;

  public int add(String message, boolean fromSystem) {
    int slot = (head + size) % CAPACITY;
    if (size == CAPACITY) {
      head = (head + 1) % CAPACITY;
    } else {
      size++;
    }
    text[slot] = message;
    ids[slot] = next;
    system[slot] = fromSystem;
    born[slot] = clock;
    revision++;
    return next++;
  }

  public void clear() {
    if (size > 0) {
      size = 0;
      head = 0;
      revision++;
    }
  }

  public void advance(float dt) {
    clock += dt;
  }

  public int size() {
    return size;
  }

  public int revision() {
    return revision;
  }

  public float clock() {
    return clock;
  }

  public String text(int index) {
    return text[(head + index) % CAPACITY];
  }

  public int id(int index) {
    return ids[(head + index) % CAPACITY];
  }

  public boolean system(int index) {
    return system[(head + index) % CAPACITY];
  }

  public float born(int index) {
    return born[(head + index) % CAPACITY];
  }
}
