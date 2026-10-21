package me.cxiy.cxvisuals.modules.environment.particles;

import java.util.Objects;
import java.util.SplittableRandom;
import me.cxiy.cxvisuals.modules.environment.View;

public final class Field {
  public static final int CAPACITY = 2000;

  private static final float SWAP = 1.4F;
  private static final float SETTLE = 1.5F;
  private static final int SLACK = 6;
  private static final float GONE = 0.004F;

  private final Mote[] motes = new Mote[CAPACITY];
  private final SplittableRandom random;
  private Scene scene;
  private int live;
  private float shown;
  private float presence;
  private boolean placed;
  private double lastX;
  private double lastY;
  private double lastZ;

  public Field(long seed) {
    random = new SplittableRandom(seed);
    for (int i = 0; i < CAPACITY; i++) {
      motes[i] = new Mote();
    }
  }

  public Scene scene() {
    return scene;
  }

  public int live() {
    return live;
  }

  public float shown() {
    return shown * presence;
  }

  public void clear() {
    scene = null;
    live = 0;
    shown = 0.0F;
    presence = 0.0F;
    placed = false;
  }

  public void update(
      View view,
      Scene wanted,
      float amount,
      float exposure,
      float dt,
      float time,
      Wind wind,
      Ground ground) {
    if (!Objects.equals(wanted, scene)) {
      shown = Math.max(0.0F, shown - dt / SWAP);
      if (shown <= 0.0F || live == 0) {
        scene = wanted;
        live = 0;
      }
    } else if (scene != null) {
      shown = Math.min(1.0F, shown + dt / SWAP);
    }
    if (scene == null) {
      live = 0;
      placed = false;
      return;
    }
    Kind kind = scene.kind();
    presence = live == 0 ? exposure : Waves.approach(presence, exposure, SETTLE, dt);
    double moved = Math.abs(view.x - lastX) + Math.abs(view.y - lastY) + Math.abs(view.z - lastZ);
    if (!placed || moved > kind.reach) {
      live = 0;
    }
    placed = true;
    lastX = view.x;
    lastY = view.y;
    lastZ = view.z;
    int target = Math.clamp(Math.round(kind.count * amount), 0, CAPACITY);
    if (live > target + SLACK || target == 0) {
      live = target;
    }
    while (live < target) {
      Motion.spawn(kind, motes[live], view, random, ground, true);
      live++;
    }
    for (int i = 0; i < live; i++) {
      Mote m = motes[i];
      if (!Motion.step(kind, m, dt, time, wind, view, ground)) {
        Motion.spawn(kind, m, view, random, ground, false);
      } else if (wrap(m, view, kind.reach)) {
        if (m.landed) {
          Motion.spawn(kind, m, view, random, ground, false);
        } else {
          Motion.wrapped(kind, m, view, ground);
        }
      }
    }
  }

  private static boolean wrap(Mote m, View view, float reach) {
    double span = reach * 2.0;
    double ox = m.x - view.x;
    double oz = m.z - view.z;
    boolean moved = false;
    if (ox < -reach || ox > reach) {
      m.x = view.x + ox - Math.floor((ox + reach) / span) * span;
      moved = true;
    }
    if (oz < -reach || oz > reach) {
      m.z = view.z + oz - Math.floor((oz + reach) / span) * span;
      moved = true;
    }
    return moved;
  }

  public void emit(View view, Sink sink, float scale, float glow, Climate climate, float time) {
    float fade = shown * presence;
    if (scene == null || fade <= GONE) {
      return;
    }
    for (int i = 0; i < live; i++) {
      Looks.emit(scene, motes[i], view, sink, fade, scale, glow, climate, time);
    }
  }
}
