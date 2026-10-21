package me.cxiy.cxvisuals.gui;

public interface PanelHost {
  void openTab(String pageId);

  default float room() {
    return 0.0F;
  }
}
