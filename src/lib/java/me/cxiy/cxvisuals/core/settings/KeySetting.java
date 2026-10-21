package me.cxiy.cxvisuals.core.settings;

public final class KeySetting extends TransientSetting {
  private final KeyHandle handle;

  KeySetting(String id, String title, KeyHandle handle) {
    super(id, title);
    this.handle = handle;
  }

  public KeyHandle handle() {
    return handle;
  }
}
