package me.cxiy.cxvisuals.gui.accounts;

import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.Choice;

final class Pick implements Choice {
  private final List<String> labels;
  private int index;

  Pick(String... labels) {
    String[] shown = new String[labels.length];
    for (int i = 0; i < labels.length; i++) {
      shown[i] = Lang.tr(labels[i]);
    }
    this.labels = List.of(shown);
  }

  @Override
  public List<String> labels() {
    return labels;
  }

  @Override
  public int index() {
    return index;
  }

  @Override
  public void setIndex(int next) {
    index = Math.max(0, Math.min(labels.size() - 1, next));
  }
}
