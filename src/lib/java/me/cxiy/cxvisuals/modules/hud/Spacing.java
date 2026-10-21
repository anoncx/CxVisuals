package me.cxiy.cxvisuals.modules.hud;

import java.util.List;

public final class Spacing {
  private static final float TOUCH = 0.01F;
  private static final int SEARCH_STEPS = 10;

  private Spacing() {}

  public static boolean solid(HudElement element) {
    if (!element.shown.get() || element.w <= 0.0F) {
      return false;
    }
    return !(element instanceof HostedElement hosted) || hosted.solid();
  }

  public static boolean free(
      List<HudElement> elements, boolean[] moving, float dx, float dy, float gap, float global) {
    for (int i = 0; i < elements.size(); i++) {
      if (!moving[i]) {
        continue;
      }
      HudElement mover = elements.get(i);
      float factor = mover.factor(global);
      float width = mover.w * factor;
      float height = mover.h * factor;
      for (int k = 0; k < elements.size(); k++) {
        HudElement other = elements.get(k);
        if (moving[k] || !solid(other)) {
          continue;
        }
        float otherFactor = other.factor(global);
        float otherWidth = other.w * otherFactor;
        float otherHeight = other.h * otherFactor;
        if (!near(
            mover.left + dx,
            mover.top + dy,
            width,
            height,
            other.left,
            other.top,
            otherWidth,
            otherHeight,
            gap)) {
          continue;
        }
        if (!near(
            mover.left,
            mover.top,
            width,
            height,
            other.left,
            other.top,
            otherWidth,
            otherHeight,
            gap)) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean crowded(
      List<HudElement> elements, HudElement element, float gap, float global) {
    float factor = element.factor(global);
    float width = element.w * factor;
    float height = element.h * factor;
    for (int k = 0; k < elements.size(); k++) {
      HudElement other = elements.get(k);
      if (other == element || !solid(other)) {
        continue;
      }
      float otherFactor = other.factor(global);
      if (near(
          element.left,
          element.top,
          width,
          height,
          other.left,
          other.top,
          other.w * otherFactor,
          other.h * otherFactor,
          gap)) {
        return true;
      }
    }
    return false;
  }

  public static float reach(
      List<HudElement> elements,
      boolean[] moving,
      float dx,
      float dy,
      boolean alongX,
      float fixed,
      float gap,
      float global) {
    float wanted = alongX ? dx : dy;
    if (wanted == 0.0F
        || free(elements, moving, alongX ? wanted : fixed, alongX ? fixed : wanted, gap, global)) {
      return wanted;
    }
    float low = 0.0F;
    float high = 1.0F;
    for (int i = 0; i < SEARCH_STEPS; i++) {
      float middle = (low + high) * 0.5F;
      float step = wanted * middle;
      if (free(elements, moving, alongX ? step : fixed, alongX ? fixed : step, gap, global)) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return wanted * low;
  }

  private static boolean near(
      float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh, float gap) {
    float reach = gap - TOUCH;
    return ax < bx + bw + reach
        && ax + aw + reach > bx
        && ay < by + bh + reach
        && ay + ah + reach > by;
  }
}
