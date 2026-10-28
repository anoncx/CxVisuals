package me.cxiy.cxvisuals.mc.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

public final class Mouse {
  public static final int LEFT = 0;
  public static final int RIGHT = 1;
  public static final int MIDDLE = 2;

  private Mouse() {}

  public static int button(MouseButtonEvent event) {
    return lib(event.button());
  }

  public static int button(MouseButtonInfo info) {
    return lib(info.button());
  }

  private static int lib(int vanilla) {
    return switch (vanilla) {
      case InputConstants.MOUSE_BUTTON_LEFT -> LEFT;
      case InputConstants.MOUSE_BUTTON_RIGHT -> RIGHT;
      case InputConstants.MOUSE_BUTTON_MIDDLE -> MIDDLE;
      default -> vanilla;
    };
  }
}
