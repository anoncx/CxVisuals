package me.cxiy.cxvisuals.mc.compat;

import net.minecraft.client.Minecraft;

public final class TextInput {
  private static final Object OWNER = new Object();
  private static boolean on;

  private TextInput() {}

  public static void sync(Minecraft mc, boolean wanted) {
    if (wanted == on) {
      return;
    }
    on = wanted;
    if (wanted) {
      mc.textInputManager().startTextInput(OWNER);
    } else {
      mc.textInputManager().stopTextInput(OWNER);
    }
  }
}
