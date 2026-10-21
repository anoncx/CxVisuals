package me.cxiy.cxvisuals.menu.model;

public interface ItemList {
  int NONE = -1;

  int size();

  Item get(int index);

  int selected();

  void select(int index);

  void open(int index);

  default String empty() {
    return "";
  }
}
