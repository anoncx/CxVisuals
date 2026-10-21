package me.cxiy.cxvisuals.core.settings;

import me.cxiy.cxvisuals.core.i18n.Lang;

public final class InfoSetting extends TransientSetting {
  private String text;

  InfoSetting(String id, String title, String text) {
    super(id, title);
    this.text = text == null ? "" : text;
  }

  public String text() {
    return Lang.tr(text);
  }

  public void show(String next) {
    String value = next == null ? "" : next;
    if (value.equals(text)) {
      return;
    }
    text = value;
    changed();
  }
}
