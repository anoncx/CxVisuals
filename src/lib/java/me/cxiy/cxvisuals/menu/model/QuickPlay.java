package me.cxiy.cxvisuals.menu.model;

public interface QuickPlay {
  int WORLDS = 0;
  int SERVERS = 1;
  int GROUPS = 2;

  int count(int group);

  Item item(int group, int index);

  void open(int group, int index);

  byte[] shot();

  String shotLabel();

  void openShots();
}
