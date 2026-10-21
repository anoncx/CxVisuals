package me.cxiy.cxvisuals.modules.accounts;

import me.cxiy.cxvisuals.core.i18n.Lang;

public enum Kind {
  MICROSOFT(Lang.key("Microsoft")),
  OFFLINE(Lang.key("Offline")),
  TOKEN(Lang.key("Token"));

  public static final String[] LABELS = {"Microsoft", "Offline", "Token"};

  private final String label;

  Kind(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public static Kind parse(String name) {
    if (name == null) {
      return null;
    }
    for (Kind kind : values()) {
      if (kind.name().equalsIgnoreCase(name)) {
        return kind;
      }
    }
    return null;
  }
}
