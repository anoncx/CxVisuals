package me.cxiy.cxvisuals.gui;

public interface Gallery {
  int READY = 0;
  int BUSY = 1;
  int BROKEN = 2;

  int count();

  String name(int index);

  String meta(int index);

  int state(int index);

  int handle(int index);

  boolean animated(int index);

  int selected();

  void select(int index);

  void requestImport();

  void delete(int index);

  String notice();

  int noticeId();
}
