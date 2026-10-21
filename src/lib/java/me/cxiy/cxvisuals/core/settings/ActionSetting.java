package me.cxiy.cxvisuals.core.settings;

import me.cxiy.cxvisuals.core.i18n.Lang;

public final class ActionSetting extends TransientSetting {
  public enum Kind {
    QUIET,
    PRIMARY,
    DANGER
  }

  private final String buttonLabel;
  private final Kind kind;
  private final Runnable action;

  ActionSetting(String id, String title, String buttonLabel, Kind kind, Runnable action) {
    super(id, title);
    this.buttonLabel = buttonLabel;
    this.kind = kind;
    this.action = action;
  }

  public String buttonLabel() {
    return Lang.tr(buttonLabel);
  }

  public Kind kind() {
    return kind;
  }

  public void run() {
    action.run();
  }
}
