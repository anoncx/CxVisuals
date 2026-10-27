package me.cxiy.cxvisuals.gui.map;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import me.cxiy.cxvisuals.core.math.Easing;

public final class Appear {
  private static final float SECONDS = 0.32F;
  private static final int LIMIT = 1024;

  private static final class Age {
    float seconds;
    long frame;
  }

  private final Map<Long, Age> ages = new HashMap<>(128);
  private long frame;
  private float dt;

  public void frame(float delta) {
    if (ages.size() > LIMIT) {
      ages.clear();
    }
    Iterator<Age> entries = ages.values().iterator();
    while (entries.hasNext()) {
      if (entries.next().frame < frame) {
        entries.remove();
      }
    }
    frame++;
    dt = delta;
  }

  public float scale(long key) {
    Age age = ages.get(key);
    if (age == null) {
      age = new Age();
      ages.put(key, age);
    } else if (age.frame < frame) {
      age.seconds = Math.min(SECONDS, age.seconds + dt);
    }
    age.frame = frame;
    return (float) Easing.overshoot(age.seconds / SECONDS);
  }

  public void clear() {
    ages.clear();
  }
}
