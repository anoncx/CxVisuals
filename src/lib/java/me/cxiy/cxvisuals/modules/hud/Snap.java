package me.cxiy.cxvisuals.modules.hud;

import java.util.List;

public final class Snap {
  public static final float MARGIN = 8.0F;
  public static final float REACH = 6.0F;

  public float x;
  public float y;
  public float guideX = Float.NaN;
  public float guideY = Float.NaN;

  private float best;

  public void clear() {
    guideX = Float.NaN;
    guideY = Float.NaN;
  }

  public void solve(
      boolean[] moving,
      float left,
      float top,
      float width,
      float height,
      float screenW,
      float screenH,
      List<HudElement> elements,
      float global,
      float gap) {
    x = left;
    y = top;
    clear();

    best = REACH;
    tryX(left, MARGIN, MARGIN);
    tryX(left, (screenW - width) * 0.5F, screenW * 0.5F);
    tryX(left, screenW - MARGIN - width, screenW - MARGIN);
    for (int i = 0; i < elements.size(); i++) {
      HudElement other = elements.get(i);
      if (moving[i] || !Spacing.solid(other)) {
        continue;
      }
      float span = other.w * other.factor(global);
      tryX(left, other.left, other.left);
      tryX(left, other.left + span - width, other.left + span);
      tryX(left, other.left + (span - width) * 0.5F, other.left + span * 0.5F);
      tryX(left, other.left + span + gap, other.left + span);
      tryX(left, other.left - gap - width, other.left);
    }

    best = REACH;
    tryY(top, MARGIN, MARGIN);
    tryY(top, (screenH - height) * 0.5F, screenH * 0.5F);
    tryY(top, screenH - MARGIN - height, screenH - MARGIN);
    for (int i = 0; i < elements.size(); i++) {
      HudElement other = elements.get(i);
      if (moving[i] || !Spacing.solid(other)) {
        continue;
      }
      float span = other.h * other.factor(global);
      tryY(top, other.top, other.top);
      tryY(top, other.top + span - height, other.top + span);
      tryY(top, other.top + (span - height) * 0.5F, other.top + span * 0.5F);
      tryY(top, other.top + span + gap, other.top + span);
      tryY(top, other.top - gap - height, other.top);
    }
  }

  private void tryX(float left, float candidate, float guide) {
    float distance = Math.abs(candidate - left);
    if (distance < best) {
      best = distance;
      x = candidate;
      guideX = guide;
    }
  }

  private void tryY(float top, float candidate, float guide) {
    float distance = Math.abs(candidate - top);
    if (distance < best) {
      best = distance;
      y = candidate;
      guideY = guide;
    }
  }
}
