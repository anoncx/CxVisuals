package me.cxiy.cxvisuals.mc.menu;

import java.util.function.Function;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.core.settings.TextSetting;
import me.cxiy.cxvisuals.mc.CxVisuals;
import me.cxiy.cxvisuals.menu.model.TextStore;

final class McText implements TextStore {
  private final Function<ClientSettings, TextSetting> pick;

  McText(Function<ClientSettings, TextSetting> pick) {
    this.pick = pick;
  }

  @Override
  public String read() {
    return pick.apply(CxVisuals.registry().client()).get();
  }

  @Override
  public void write(String value) {
    pick.apply(CxVisuals.registry().client()).set(value);
  }
}
