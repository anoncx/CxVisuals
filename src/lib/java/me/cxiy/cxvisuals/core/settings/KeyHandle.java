package me.cxiy.cxvisuals.core.settings;

public interface KeyHandle {
  String label();

  void rebind(int keyCode, int scanCode);
}
