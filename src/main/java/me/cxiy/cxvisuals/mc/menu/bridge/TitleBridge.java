package me.cxiy.cxvisuals.mc.menu.bridge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.modules.accounts.Accounts;
import me.cxiy.cxvisuals.menu.model.Control;
import me.cxiy.cxvisuals.menu.model.Glyph;
import me.cxiy.cxvisuals.menu.model.TitleSource;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TitleBridge implements TitleSource {
  private static final String LABEL = "CxVisuals";
  private static final Comparator<AbstractWidget> READING =
      Comparator.<AbstractWidget>comparingInt(AbstractWidget::getY)
          .thenComparingInt(AbstractWidget::getX);

  private final Screen screen;
  private final String version;
  private Control options;
  private Control quit;
  private long signature = Long.MIN_VALUE;

  public TitleBridge(Screen screen, String modVersion) {
    this.screen = screen;
    this.version =
        ("Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  CxVisuals " + modVersion)
            .toUpperCase();
  }

  private long stamp() {
    long hash = 17L;
    for (GuiEventListener child : screen.children()) {
      hash = hash * 31L + System.identityHashCode(child);
    }
    return hash;
  }

  private static boolean vanillaIcon(String label) {
    return label.equals(Component.translatable("narrator.button.language").getString())
        || label.equals(Component.translatable("narrator.button.accessibility").getString());
  }

  private static boolean foreign(AbstractWidget widget) {
    String name = widget.getClass().getName();
    return !name.startsWith("net.minecraft.") && !name.startsWith("me.cxiy.cxvisuals.");
  }

  private static boolean modMenu(AbstractWidget widget, String label) {
    return widget.getClass().getName().contains("modmenu")
        || label.equals("Mods")
        || label.startsWith("Mods (");
  }

  private static int glyph(AbstractWidget widget, String label) {
    if (modMenu(widget, label) || foreign(widget)) {
      return Glyph.MODS;
    }
    return glyph(label);
  }

  private static int glyph(String label) {
    if (label.equals(Component.translatable("menu.singleplayer").getString())) {
      return Glyph.SINGLE;
    }
    if (label.equals(Component.translatable("menu.multiplayer").getString())) {
      return Glyph.MULTI;
    }
    if (label.equals(Component.translatable("menu.online").getString())) {
      return Glyph.REALMS;
    }
    return Glyph.NONE;
  }

  @Override
  public boolean changed() {
    return stamp() != signature;
  }

  @Override
  public List<Control> nav() {
    signature = stamp();
    String optionsLabel = Component.translatable("menu.options").getString();
    String quitLabel = Component.translatable("menu.quit").getString();
    List<AbstractWidget> buttons = new ArrayList<>();
    for (GuiEventListener child : screen.children()) {
      if (child instanceof AbstractButton button
          && button.visible
          && !(button instanceof PlainTextButton)
          && !button.getMessage().getString().isBlank()
          && (!(button instanceof SpriteIconButton)
              || (foreign(button) && !vanillaIcon(button.getMessage().getString())))) {
        buttons.add(button);
      }
    }
    buttons.sort(READING);
    options = null;
    quit = null;
    List<Control> nav = new ArrayList<>(buttons.size() + 1);
    for (AbstractWidget button : buttons) {
      String label = button.getMessage().getString();
      if (label.equals(optionsLabel)) {
        options = WidgetControl.of(button, false).icon(Glyph.OPTIONS);
      } else if (label.equals(quitLabel)) {
        quit = WidgetControl.of(button, false).icon(Glyph.QUIT);
      } else {
        nav.add(WidgetControl.of(button, false).icon(glyph(button, label)));
      }
    }
    if (Accounts.live()) {
      nav.add(
          new Control() {
            @Override
            public Kind kind() {
              return Kind.BUTTON;
            }

            @Override
            public String label() {
              return Lang.tr("Accounts");
            }

            @Override
            public int icon() {
              return Glyph.ACCOUNTS;
            }

            @Override
            public void press() {
              Accounts.open();
            }
          });
    }
    nav.add(
        new Control() {
          @Override
          public Kind kind() {
            return Kind.BUTTON;
          }

          @Override
          public String label() {
            return LABEL;
          }

          @Override
          public int icon() {
            return Glyph.CX;
          }

          @Override
          public void press() {
            CxVisualsClient.openSettings(screen);
          }
        });
    return nav;
  }

  @Override
  public Control options() {
    return options;
  }

  @Override
  public Control quit() {
    return quit;
  }

  @Override
  public String version() {
    return version;
  }

  @Override
  public String player() {
    return Minecraft.getInstance().getUser().getName();
  }
}
