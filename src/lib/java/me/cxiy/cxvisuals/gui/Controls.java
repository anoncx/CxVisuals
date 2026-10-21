package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.KeySetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.PaletteSetting;
import me.cxiy.cxvisuals.core.settings.PickSetting;
import me.cxiy.cxvisuals.core.settings.Setting;
import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class Controls {
  private static final int MAX_SEGMENTS = 4;

  private Controls() {}

  public static Widget of(Setting<?> setting) {
    if (setting instanceof BoolSetting bool) {
      return new Toggle(bool);
    }
    if (setting instanceof NumberSetting number) {
      return new Slider(number);
    }
    if (setting instanceof EnumSetting<?> choice) {
      return choice.labels().size() > MAX_SEGMENTS ? new Stepper(choice) : new Segmented(choice);
    }
    if (setting instanceof KeySetting key) {
      return new KeyButton(key);
    }
    if (setting instanceof TextSetting text) {
      return new TextInput(text);
    }
    if (setting instanceof PaletteSetting palette) {
      return new PalettePicker(palette);
    }
    if (setting instanceof PickSetting picks) {
      return new PickList(picks);
    }
    if (setting instanceof ActionSetting action) {
      return new Button(action);
    }
    return null;
  }
}
