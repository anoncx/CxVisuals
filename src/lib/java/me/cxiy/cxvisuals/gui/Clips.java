package me.cxiy.cxvisuals.gui;

public interface Clips {
  int NONE = 0;
  int EMPTY = 1;
  int DISABLED = 2;
  int READY = 3;

  int state();

  int revision();

  int handle();

  int clipCount();

  String clipName(int clip);

  float clipSeconds(int clip);

  int eventCount();

  String eventName(int event);

  int assignedCount(int event);

  int assigned(int event, int slot);

  boolean off(int event);

  int autoClip(int event);

  boolean random(int event);

  void toggle(int event, int clip);

  void toggleOff(int event);

  void setRandom(int event, boolean random);
}
