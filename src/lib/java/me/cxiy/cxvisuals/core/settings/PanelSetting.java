package me.cxiy.cxvisuals.core.settings;

public final class PanelSetting extends TransientSetting {
  private boolean pinned;

  PanelSetting(String id) {
    super(id, id);
  }

  public PanelSetting pin() {
    pinned = true;
    return this;
  }

  public boolean pinned() {
    return pinned;
  }
}
