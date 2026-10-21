package me.cxiy.cxvisuals.core.module;

import me.cxiy.cxvisuals.core.settings.KeyHandle;

public interface KeyBinder {
  int UNBOUND = -1;

  KeyHandle bind(String id, String title, int defaultKey);
}
