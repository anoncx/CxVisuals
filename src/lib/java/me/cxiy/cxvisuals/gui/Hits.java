package me.cxiy.cxvisuals.gui;

public final class Hits {
  public static final int NONE = -1;
  public static final float PILL = 26.0F;
  public static final float PILL_PAD = 13.0F;

  private final float[] left;
  private final float[] top;
  private final float[] width;
  private final float[] height;
  private final int[] kinds;
  private final Object[] targets;
  private int count;
  private int pressedKind = NONE;
  private Object pressedTarget;

  public Hits(int capacity) {
    left = new float[capacity];
    top = new float[capacity];
    width = new float[capacity];
    height = new float[capacity];
    kinds = new int[capacity];
    targets = new Object[capacity];
  }

  public void clear() {
    for (int i = 0; i < count; i++) {
      targets[i] = null;
    }
    count = 0;
  }

  public void add(int kind, Object target, float x, float y, float w, float h) {
    if (count == kinds.length) {
      return;
    }
    left[count] = x;
    top[count] = y;
    width[count] = w;
    height[count] = h;
    kinds[count] = kind;
    targets[count] = target;
    count++;
  }

  public int at(float mx, float my) {
    for (int i = count - 1; i >= 0; i--) {
      if (mx >= left[i] && mx < left[i] + width[i] && my >= top[i] && my < top[i] + height[i]) {
        return i;
      }
    }
    return NONE;
  }

  public int kind(int index) {
    return kinds[index];
  }

  public Object target(int index) {
    return targets[index];
  }

  public float centerX(int kind, Object target) {
    int index = find(kind, target);
    return index == NONE ? Float.NaN : left[index] + width[index] * 0.5F;
  }

  public float centerY(int kind, Object target) {
    int index = find(kind, target);
    return index == NONE ? Float.NaN : top[index] + height[index] * 0.5F;
  }

  private int find(int kind, Object target) {
    for (int i = 0; i < count; i++) {
      if (kinds[i] == kind && (target == null || target.equals(targets[i]))) {
        return i;
      }
    }
    return NONE;
  }

  public boolean press(float mx, float my) {
    int index = at(mx, my);
    pressedKind = index == NONE ? NONE : kinds[index];
    pressedTarget = index == NONE ? null : targets[index];
    return index != NONE;
  }

  public int release(float mx, float my) {
    int kind = pressedKind;
    Object target = pressedTarget;
    pressedKind = NONE;
    pressedTarget = null;
    int index = at(mx, my);
    if (kind == NONE
        || index == NONE
        || kinds[index] != kind
        || (target != null && !target.equals(targets[index]))) {
      return NONE;
    }
    return index;
  }

  public boolean down(int kind, Object target) {
    return pressedKind == kind
        && (target == null ? pressedTarget == null : target.equals(pressedTarget));
  }

  public float pill(
      Canvas c,
      Theme t,
      int kind,
      Object target,
      String label,
      float right,
      float middle,
      float mx,
      float my,
      int ink,
      boolean filled) {
    float w = (float) Math.ceil(c.textWidth(FontStyle.CAPTION, label) + PILL_PAD * 2.0F);
    float x = right - w;
    float y = middle - PILL * 0.5F;
    boolean over = mx >= x && mx < x + w && my >= y && my < y + PILL;
    float tint = down(kind, target) ? 0.16F : over ? 0.08F : 0.0F;
    int fill =
        filled ? Theme.mix(t.ink(), t.paper(), tint) : Theme.mix(t.surface2(), t.ink(), tint);
    Depth.raised(c, t, x, y, w, PILL, PILL * 0.5F, fill);
    c.text(
        FontStyle.CAPTION,
        label,
        x + PILL_PAD,
        y + (PILL - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        filled ? (t.paper() | 0xFF000000) : ink);
    add(kind, target, x, y, w, PILL);
    return x;
  }

  public static void face(
      Canvas c, Theme t, int entityId, String name, float x, float y, float size, float radius) {
    if (entityId >= 0 && c.face(entityId, x, y, size, radius)) {
      return;
    }
    c.roundRect(x, y, size, size, radius, t.surface2());
    String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    c.text(
        FontStyle.MONO,
        initial,
        x + (size - c.textWidth(FontStyle.MONO, initial)) * 0.5F,
        y + (size - c.lineHeight(FontStyle.MONO)) * 0.5F,
        t.ink());
  }
}
