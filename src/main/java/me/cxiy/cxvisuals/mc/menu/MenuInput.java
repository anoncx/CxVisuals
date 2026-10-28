package me.cxiy.cxvisuals.mc.menu;

import me.cxiy.cxvisuals.mc.compat.Keys;
import me.cxiy.cxvisuals.menu.views.MenuView;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import me.cxiy.cxvisuals.mc.compat.Mouse;

public final class MenuInput {
  private static final int PRIMARY = 0;

  private MenuInput() {}

  public static MenuView claim(Screen screen) {
    if (screen instanceof KeyBindsScreen binds && binds.selectedKey != null) {
      return null;
    }
    return MenuStage.view(screen);
  }

  public static boolean pressed(MenuView view, MouseButtonEvent event) {
    if (Mouse.button(event) == PRIMARY) {
      float scale = MenuStage.pointerScale();
      view.mouseDown((float) event.x() * scale, (float) event.y() * scale);
    }
    return true;
  }

  public static boolean released(MenuView view, MouseButtonEvent event) {
    if (Mouse.button(event) == PRIMARY) {
      float scale = MenuStage.pointerScale();
      view.mouseUp((float) event.x() * scale, (float) event.y() * scale);
    }
    return true;
  }

  public static boolean dragged(MenuView view, MouseButtonEvent event) {
    if (Mouse.button(event) == PRIMARY) {
      float scale = MenuStage.pointerScale();
      view.mouseDrag((float) event.x() * scale, (float) event.y() * scale);
    }
    return true;
  }

  public static boolean scrolled(MenuView view, double x, double y, double amount) {
    float scale = MenuStage.pointerScale();
    view.scroll((float) x * scale, (float) y * scale, (float) amount);
    return true;
  }

  public static boolean key(MenuView view, KeyEvent event) {
    view.keyDown(Keys.code(event), Keys.scan(event), Keys.mods(event));
    return true;
  }

  public static boolean typed(MenuView view, CharacterEvent event) {
    return view.charTyped(event.codepoint());
  }

  public static boolean framePressed(Screen screen, MouseButtonEvent event) {
    return Mouse.button(event) == PRIMARY
        && Skins.framed(screen)
        && MenuStage.frameDown((float) event.x(), (float) event.y());
  }

  public static void frameReleased(Screen screen, MouseButtonEvent event) {
    if (Mouse.button(event) == PRIMARY && Skins.framed(screen)) {
      MenuStage.frameUp((float) event.x(), (float) event.y());
    }
  }
}
