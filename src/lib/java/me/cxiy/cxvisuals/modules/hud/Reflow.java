package me.cxiy.cxvisuals.modules.hud;

import java.util.List;

public final class Reflow {
  private static final int PASSES = 8;
  private static final float TOUCH = 0.01F;
  private static final float BLOCKED = 100000.0F;

  private final float[] x;
  private final float[] y;
  private final float[] w;
  private final float[] h;
  private final boolean[] placed;
  private float bestCost;
  private float bestX;
  private float bestY;

  public Reflow(int capacity) {
    x = new float[capacity];
    y = new float[capacity];
    w = new float[capacity];
    h = new float[capacity];
    placed = new boolean[capacity];
  }

  public void solve(
      List<HudElement> elements,
      boolean[] active,
      float screenW,
      float screenH,
      float gap,
      float global) {
    int count = elements.size();
    for (int i = 0; i < count; i++) {
      HudElement element = elements.get(i);
      placed[i] = false;
      element.pushX = 0.0F;
      element.pushY = 0.0F;
      if (element instanceof HostedElement hosted && hosted.shown.get() && hosted.solid()) {
        take(i, element, global);
        placed[i] = true;
      }
    }
    settle(elements, active, true, screenW, screenH, gap, global);
    settle(elements, active, false, screenW, screenH, gap, global);
  }

  private void take(int index, HudElement element, float global) {
    float factor = element.factor(global);
    x[index] = element.left;
    y[index] = element.top;
    w[index] = element.w * factor;
    h[index] = element.h * factor;
  }

  private void settle(
      List<HudElement> elements,
      boolean[] active,
      boolean elastic,
      float screenW,
      float screenH,
      float gap,
      float global) {
    for (int i = 0; i < elements.size(); i++) {
      HudElement element = elements.get(i);
      if (!active[i] || placed[i] || element.elastic() != elastic) {
        continue;
      }
      take(i, element, global);
      for (int pass = 0; pass < PASSES; pass++) {
        int blocker = blocker(i, elements.size(), gap);
        if (blocker < 0) {
          break;
        }
        escape(i, blocker, elements.size(), element.left, element.top, screenW, screenH, gap);
      }
      placed[i] = true;
      element.pushX = x[i] - element.left;
      element.pushY = y[i] - element.top;
    }
  }

  private int blocker(int index, int count, float gap) {
    return blocker(index, x[index], y[index], count, gap);
  }

  private int blocker(int index, float left, float top, int count, float gap) {
    float reach = gap - TOUCH;
    for (int k = 0; k < count; k++) {
      if (k == index || !placed[k]) {
        continue;
      }
      if (left < x[k] + w[k] + reach
          && left + w[index] + reach > x[k]
          && top < y[k] + h[k] + reach
          && top + h[index] + reach > y[k]) {
        return k;
      }
    }
    return -1;
  }

  private void escape(
      int index,
      int blocker,
      int count,
      float homeX,
      float homeY,
      float screenW,
      float screenH,
      float gap) {
    bestCost = Float.MAX_VALUE;
    bestX = x[index];
    bestY = y[index];
    consider(
        index, count, x[blocker] - gap - w[index], y[index], homeX, homeY, screenW, screenH, gap);
    consider(
        index, count, x[blocker] + w[blocker] + gap, y[index], homeX, homeY, screenW, screenH, gap);
    consider(
        index, count, x[index], y[blocker] - gap - h[index], homeX, homeY, screenW, screenH, gap);
    consider(
        index, count, x[index], y[blocker] + h[blocker] + gap, homeX, homeY, screenW, screenH, gap);
    x[index] = bestX;
    y[index] = bestY;
  }

  private void consider(
      int index,
      int count,
      float left,
      float top,
      float homeX,
      float homeY,
      float screenW,
      float screenH,
      float gap) {
    if (left < 0.0F || top < 0.0F || left + w[index] > screenW || top + h[index] > screenH) {
      return;
    }
    float cost = Math.abs(left - homeX) + Math.abs(top - homeY);
    if (blocker(index, left, top, count, gap) >= 0) {
      cost += BLOCKED;
    }
    if (cost < bestCost) {
      bestCost = cost;
      bestX = left;
      bestY = top;
    }
  }
}
