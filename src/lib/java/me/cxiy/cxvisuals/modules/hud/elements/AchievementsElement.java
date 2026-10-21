package me.cxiy.cxvisuals.modules.hud.elements;

import java.util.ArrayDeque;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.AchievementStack;
import me.cxiy.cxvisuals.modules.hud.Achievement;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class AchievementsElement extends HudElement {
  public static final double HOME_X = -10.0;
  public static final double HOME_Y = -130.0;

  private static final int WAITING_LIMIT = 12;

  public final AchievementStack stack = new AchievementStack();
  public final BoolSetting recipes;
  public final BoolSetting sound;

  private final ArrayDeque<Achievement> waiting = new ArrayDeque<>();

  public AchievementsElement(Settings settings) {
    super(
        settings,
        "achievements",
        "Achievements",
        Category.INFO,
        true,
        Anchor.RIGHT,
        HOME_X,
        HOME_Y);
    recipes =
        group()
            .bool("recipes", "Recipe unlocks", true)
            .describe("Shows recipe unlocks as cards too, merged when several arrive together.");
    sound =
        group()
            .bool("sound", "Play sound", true)
            .describe("Plays the usual advancement sound when a card appears.");
  }

  @Override
  public float radius() {
    return 22.0F;
  }

  @Override
  public boolean instant() {
    return true;
  }

  @Override
  public boolean present(HudData data) {
    return stack.size() > 0 || !waiting.isEmpty();
  }

  public void offer(Achievement card) {
    if (card.mergeable()) {
      Achievement newest = stack.newest();
      if (newest != null
          && newest.mergeable()
          && stack.newestAge() < AchievementStack.MERGE_WINDOW) {
        newest.merge(card.title());
        stack.refresh();
        return;
      }
      Achievement tail = waiting.peekLast();
      if (tail != null && tail.mergeable()) {
        tail.merge(card.title());
        return;
      }
    }
    if (waiting.size() >= WAITING_LIMIT) {
      waiting.poll();
    }
    waiting.add(card);
  }

  public void clear() {
    waiting.clear();
    stack.clear();
  }

  public float align() {
    return anchor.get().fx;
  }

  public boolean rising() {
    return anchor.get().fy > 0.5F;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    w = AchievementStack.WIDTH;
    h =
        AchievementStack.extent(
            Math.max(1, data.editing ? AchievementStack.samples() : stack.size()));
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    if (data.editing) {
      stack.sample(c, t, x, y, h, align(), rising());
      return;
    }
    while (!stack.full() && !waiting.isEmpty()) {
      stack.push(waiting.poll());
    }
    stack.draw(c, t, x, y, h, align(), rising(), dt);
  }
}
