package me.cxiy.cxvisuals.modules.donutsmp;

public interface TrendsHost {
  int handle(String id);

  String title(String id);

  boolean watched(String id);

  void watch(String id);

  int watchRevision();

  long updated();

  long staleAfter();

  float flip(String id);

  String flipText(String id);

  void search(String id);
}
