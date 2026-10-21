package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.settings.PanelSetting;

public interface PanelFactory {
  PanelFactory NONE = (setting, host) -> null;

  Widget create(PanelSetting setting, PanelHost host);
}
