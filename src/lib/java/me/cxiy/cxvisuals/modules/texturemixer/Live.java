package me.cxiy.cxvisuals.modules.texturemixer;

public interface Live {
  enum State {
    OFF,
    WAITING,
    APPLYING,
    APPLIED,
    FAILED
  }

  State state();

  int applied();

  boolean enabled();

  void enabled(boolean on);
}
