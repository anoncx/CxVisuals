package me.cxiy.cxvisuals.mc.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

public final class Keys {
  private Keys() {}

  public static int lib(int vanilla) {
    return vanilla;
  }

  public static int vanilla(int lib) {
    return lib;
  }

  public static int code(KeyEvent event) {
    return event.key();
  }

  public static int scan(KeyEvent event) {
    return event.scancode();
  }

  public static int mods(KeyEvent event) {
    return event.modifiers();
  }

  public static KeyEvent event(int libKey, int scan, int libMods) {
    return new KeyEvent(libKey, scan, libMods);
  }

  public static boolean down(int vanilla) {
    return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), vanilla);
  }
}
