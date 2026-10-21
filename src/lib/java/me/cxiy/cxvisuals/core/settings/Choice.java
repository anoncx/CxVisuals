package me.cxiy.cxvisuals.core.settings;

import java.util.List;

public interface Choice {
  List<String> labels();

  int index();

  void setIndex(int index);
}
