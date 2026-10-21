package me.cxiy.cxvisuals.modules.hud;

import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Achievement {
  public static final int NO_ICON = -1;

  public enum Kind {
    TASK(Lang.key("Advancement")),
    GOAL(Lang.key("Goal reached")),
    CHALLENGE(Lang.key("Challenge complete")),
    RECIPE(Lang.key("Recipe unlocked"));

    public final String label;

    Kind(String label) {
      this.label = label;
    }
  }

  private final Kind kind;
  private final String title;
  private final int icon;
  private String detail;
  private String caption = "";
  private int captionIn = -1;
  private int merged = 1;

  public Achievement(Kind kind, String title, String detail, int icon) {
    this.kind = kind;
    this.title = title;
    this.detail = detail;
    this.icon = icon;
  }

  public Kind kind() {
    return kind;
  }

  public String title() {
    return title;
  }

  public String detail() {
    return detail;
  }

  public String caption() {
    if (captionIn != Lang.generation()) {
      captionIn = Lang.generation();
      caption = Lang.tr(kind.label).toUpperCase(Lang.locale());
    }
    return caption;
  }

  public int icon() {
    return icon;
  }

  public int merged() {
    return merged;
  }

  public boolean mergeable() {
    return kind == Kind.RECIPE;
  }

  public void merge(String next) {
    merged++;
    detail = merged + " " + Lang.count(merged, "new recipe", "new recipes") + " · " + next;
  }
}
