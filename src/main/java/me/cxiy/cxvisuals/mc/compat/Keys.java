package me.cxiy.cxvisuals.mc.compat;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.input.KeyEvent;

public final class Keys {
  private static final int SHIFT = 1;
  private static final int CONTROL = 2;
  private static final int ALT = 4;
  private static final int SUPER = 8;
  private static final int NUMPAD_MINUS = 86;
  private static final int FIRST_LETTER = 65;
  private static final int FIRST_DIGIT = 48;
  private static final int FIRST_FUNCTION = 290;
  private static final Int2IntOpenHashMap TO_LIB = new Int2IntOpenHashMap();
  private static final Int2IntOpenHashMap TO_VANILLA = new Int2IntOpenHashMap();

  static {
    TO_LIB.defaultReturnValue(-1);
    TO_VANILLA.defaultReturnValue(-1);
    for (int i = 0; i < 26; i++) {
      pair(InputConstants.KEY_A + i, FIRST_LETTER + i);
    }
    for (int i = 1; i < 10; i++) {
      pair(InputConstants.KEY_1 + i - 1, FIRST_DIGIT + i);
    }
    for (int i = 0; i < 12; i++) {
      pair(InputConstants.KEY_F1 + i, FIRST_FUNCTION + i);
    }
    pair(InputConstants.KEY_0, FIRST_DIGIT);
    pair(InputConstants.KEY_SPACE, 32);
    pair(InputConstants.KEY_MINUS, 45);
    pair(InputConstants.KEY_EQUALS, 61);
    pair(InputConstants.KEY_ESCAPE, 256);
    pair(InputConstants.KEY_RETURN, 257);
    pair(InputConstants.KEY_TAB, 258);
    pair(InputConstants.KEY_BACKSPACE, 259);
    pair(InputConstants.KEY_INSERT, 260);
    pair(InputConstants.KEY_DELETE, 261);
    pair(InputConstants.KEY_RIGHT, 262);
    pair(InputConstants.KEY_LEFT, 263);
    pair(InputConstants.KEY_DOWN, 264);
    pair(InputConstants.KEY_UP, 265);
    pair(InputConstants.KEY_PAGEUP, 266);
    pair(InputConstants.KEY_PAGEDOWN, 267);
    pair(InputConstants.KEY_HOME, 268);
    pair(InputConstants.KEY_END, 269);
    pair(InputConstants.KEY_LSHIFT, 340);
    pair(InputConstants.KEY_LCONTROL, 341);
    pair(InputConstants.KEY_LALT, 342);
    pair(InputConstants.KEY_RSHIFT, 344);
    pair(InputConstants.KEY_RCONTROL, 345);
    pair(InputConstants.KEY_RALT, 346);
    pair(NUMPAD_MINUS, 333);
    pair(InputConstants.KEY_ADD, 334);
    pair(InputConstants.KEY_NUMPADENTER, 335);
  }

  private Keys() {}

  private static void pair(int vanilla, int lib) {
    TO_LIB.put(vanilla, lib);
    TO_VANILLA.put(lib, vanilla);
  }

  public static int lib(int vanilla) {
    if (vanilla == InputConstants.UNKNOWN.getValue()) {
      return -1;
    }
    int lib = TO_LIB.get(vanilla);
    return lib < 0 ? vanilla : lib;
  }

  public static int vanilla(int lib) {
    if (lib < 0) {
      return InputConstants.UNKNOWN.getValue();
    }
    int vanilla = TO_VANILLA.get(lib);
    return vanilla < 0 ? lib : vanilla;
  }

  public static int code(KeyEvent event) {
    return lib(event.key());
  }

  public static int scan(KeyEvent event) {
    return event.keycode();
  }

  public static int mods(KeyEvent event) {
    return (event.hasShiftDown() ? SHIFT : 0)
        | (event.hasControlDown() ? CONTROL : 0)
        | (event.hasAltDown() ? ALT : 0)
        | ((event.modifiers() & InputConstants.MOD_SUPER) != 0 ? SUPER : 0);
  }

  public static KeyEvent event(int libKey, int scan, int libMods) {
    int mods = 0;
    if ((libMods & SHIFT) != 0) {
      mods |= InputConstants.MOD_SHIFT;
    }
    if ((libMods & CONTROL) != 0) {
      mods |= InputConstants.MOD_CONTROL;
    }
    if ((libMods & ALT) != 0) {
      mods |= InputConstants.MOD_ALT;
    }
    if ((libMods & SUPER) != 0) {
      mods |= InputConstants.MOD_SUPER;
    }
    return new KeyEvent(vanilla(libKey), scan, mods);
  }

  public static boolean down(int vanilla) {
    return InputConstants.isKeyDown(vanilla);
  }
}
