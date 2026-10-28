package me.cxiy.cxvisuals.mc.menu.bridge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.mc.compat.Keys;
import me.cxiy.cxvisuals.mc.mixin.ConnectScreenAccessor;
import me.cxiy.cxvisuals.mc.mixin.KeyEntryAccessor;
import me.cxiy.cxvisuals.mc.mixin.LevelLoadingScreenAccessor;
import me.cxiy.cxvisuals.mc.mixin.OptionsSubScreenAccessor;
import me.cxiy.cxvisuals.mc.mixin.ProgressScreenAccessor;
import me.cxiy.cxvisuals.mc.mixin.ScreenAccessor;
import me.cxiy.cxvisuals.mc.modules.accounts.Accounts;
import me.cxiy.cxvisuals.menu.model.Control;
import me.cxiy.cxvisuals.menu.model.Form;
import me.cxiy.cxvisuals.menu.model.FormSource;
import me.cxiy.cxvisuals.menu.model.Glyph;
import me.cxiy.cxvisuals.menu.model.ItemList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.LoadingDotsWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.PlayerFaceWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;

public final class FormBridge implements FormSource {
  private static final int FOOTER = 60;
  private static final float PERCENT = 100.0F;
  private static final String GENERAL = Lang.key("General");
  private static final int[] OWN_KEYS = {256, 257, 335, 32, 258, 262, 263, 264, 265};
  private static final Comparator<AbstractWidget> READING =
      Comparator.<AbstractWidget>comparingInt(AbstractWidget::getY)
          .thenComparingInt(AbstractWidget::getX);

  private static String chosen = "";

  private record Nav(String label, Runnable action, boolean root, boolean current)
      implements Control {
    @Override
    public Kind kind() {
      return Kind.BUTTON;
    }

    @Override
    public boolean on() {
      return current;
    }

    @Override
    public void press() {
      chosen = root ? "" : label;
      action.run();
    }
  }

  private record Tab(TabNavigationBar bar, int index, TabButton button) implements Control {
    @Override
    public Kind kind() {
      return Kind.BUTTON;
    }

    @Override
    public String label() {
      return Texts.plain(button.getMessage());
    }

    @Override
    public boolean on() {
      return button.isSelected();
    }

    @Override
    public boolean enabled() {
      return button.active;
    }

    @Override
    public void press() {
      bar.selectTab(index, true);
    }
  }

  private final Screen screen;
  private Form form;
  private long signature = Long.MIN_VALUE;

  public FormBridge(Screen screen) {
    this.screen = screen;
  }

  public static boolean adaptable(Screen screen) {
    int picking = 0;
    for (Object part : parts(screen)) {
      if (part instanceof TabNavigationBar || part instanceof ContainerObjectSelectionList<?>) {
        continue;
      }
      if (part instanceof ObjectSelectionList<?>) {
        picking++;
        continue;
      }
      if (!(part instanceof AbstractWidget widget) || !known(widget)) {
        return false;
      }
    }
    return picking <= 1;
  }

  private static boolean known(AbstractWidget widget) {
    return WidgetControl.supports(widget)
        || widget instanceof AbstractStringWidget
        || widget instanceof FittingMultiLineTextWidget
        || widget instanceof LoadingDotsWidget
        || widget instanceof ImageWidget
        || widget instanceof PlayerFaceWidget;
  }

  private static Set<Object> parts(Screen screen) {
    Set<Object> parts = new LinkedHashSet<>(screen.children());
    parts.addAll(((ScreenAccessor) screen).cxvisuals$renderables());
    return parts;
  }

  private static OptionsScreen root(Screen screen) {
    Screen at = screen;
    for (int depth = 0; depth < 4 && at != null; depth++) {
      if (at instanceof OptionsScreen options) {
        return options;
      }
      at =
          at instanceof OptionsSubScreen
              ? ((OptionsSubScreenAccessor) at).cxvisuals$lastScreen()
              : null;
    }
    return null;
  }

  private long stamp() {
    long hash = stamp(17L, screen.children());
    hash = stamp(hash, ((ScreenAccessor) screen).cxvisuals$renderables());
    if (screen instanceof ConnectScreen) {
      hash =
          hash * 31L + System.identityHashCode(((ConnectScreenAccessor) screen).cxvisuals$status());
    } else if (screen instanceof ProgressScreen) {
      ProgressScreenAccessor progress = (ProgressScreenAccessor) screen;
      hash = hash * 31L + System.identityHashCode(progress.cxvisuals$header());
      hash = hash * 31L + System.identityHashCode(progress.cxvisuals$stage());
    }
    return hash;
  }

  private static long stamp(long seed, List<?> parts) {
    long hash = seed;
    for (int i = 0; i < parts.size(); i++) {
      Object part = parts.get(i);
      hash = hash * 31L + System.identityHashCode(part);
      if (part instanceof TabNavigationBar bar) {
        for (GuiEventListener tab : bar.children()) {
          hash = hash * 31L + (tab instanceof TabButton button && button.isSelected() ? 1L : 0L);
        }
      } else if (part instanceof ContainerObjectSelectionList<?> list) {
        List<?> entries = list.children();
        hash = hash * 31L + entries.size();
        if (!entries.isEmpty()) {
          hash = hash * 31L + System.identityHashCode(entries.get(0));
          hash = hash * 31L + System.identityHashCode(entries.get(entries.size() - 1));
        }
      } else if (part instanceof AbstractWidget widget) {
        hash = hash * 31L + (widget.visible ? 1L : 0L);
        if (widget instanceof AbstractStringWidget) {
          hash = hash * 31L + System.identityHashCode(widget.getMessage());
        }
      }
    }
    return hash;
  }

  @Override
  public boolean changed() {
    if (form != null) {
      if (screen instanceof LevelLoadingScreen) {
        form.progress = ((LevelLoadingScreenAccessor) screen).cxvisuals$progress();
      } else if (screen instanceof ProgressScreen) {
        form.progress =
            Math.max(0, ((ProgressScreenAccessor) screen).cxvisuals$progress()) / PERCENT;
      }
    }
    return stamp() != signature;
  }

  @Override
  public void back() {
    if (screen.shouldCloseOnEsc()) {
      screen.onClose();
    }
  }

  @Override
  public boolean key(int keyCode, int scanCode, int modifiers) {
    for (int owned : OWN_KEYS) {
      if (owned == keyCode) {
        return false;
      }
    }
    return screen.keyPressed(Keys.event(keyCode, scanCode, modifiers));
  }

  @Override
  public Form read() {
    signature = stamp();
    Form next = new Form();
    String title = Texts.plain(screen.getTitle());
    next.title = title;
    List<AbstractWidget> loose = new ArrayList<>();
    List<AbstractWidget> texts = new ArrayList<>();
    ContainerObjectSelectionList<?> grid = null;
    for (Object part : parts(screen)) {
      if (part instanceof TabNavigationBar bar) {
        List<? extends GuiEventListener> buttons = bar.children();
        for (int i = 0; i < buttons.size(); i++) {
          if (buttons.get(i) instanceof TabButton button) {
            next.tabs.add(new Tab(bar, i, button));
          }
        }
      } else if (part instanceof ContainerObjectSelectionList<?> list) {
        grid = list;
      } else if (part instanceof ObjectSelectionList<?> list) {
        next.items = items(list);
      } else if (part instanceof AbstractWidget widget && widget.visible) {
        if (widget instanceof LoadingDotsWidget) {
          next.busy = true;
        } else if (widget instanceof AbstractStringWidget
            || widget instanceof FittingMultiLineTextWidget) {
          texts.add(widget);
        } else if (WidgetControl.supports(widget) && !(widget instanceof PlainTextButton)) {
          loose.add(widget);
        }
      }
    }
    loose.sort(READING);
    texts.sort(READING);
    OptionsScreen options = root(screen);
    if (options != null) {
      sidebar(next, options, loose);
    }
    boolean simple =
        grid == null
            && next.items == null
            && next.tabs.isEmpty()
            && options == null
            && plain(loose);
    if (simple) {
      for (AbstractWidget text : texts) {
        String line = Texts.plain(text.getMessage());
        if (!line.isBlank() && !line.equals(title)) {
          next.lines.add(line);
        }
      }
    }
    int footer = screen.height - FOOTER;
    List<AbstractWidget> ordered = new ArrayList<>(loose);
    if (!simple) {
      ordered.addAll(texts);
      ordered.sort(READING);
    }
    for (AbstractWidget widget : ordered) {
      if (widget instanceof AbstractStringWidget || widget instanceof FittingMultiLineTextWidget) {
        String line = Texts.plain(widget.getMessage());
        if (!line.isBlank() && !line.equals(title)) {
          next.rows.add(Form.Row.caption(line));
        }
        continue;
      }
      boolean button =
          widget instanceof AbstractButton
              && WidgetControl.of(widget, false).kind() == Control.Kind.BUTTON;
      if (button && (simple || widget.getY() >= footer)) {
        next.actions.add(WidgetControl.of(widget, next.actions.isEmpty()));
      } else {
        next.rows.add(Form.Row.of(WidgetControl.of(widget, false)));
      }
    }
    if (grid != null) {
      rows(next, grid);
    }
    extras(next);
    form = next;
    return next;
  }

  private static boolean plain(List<AbstractWidget> widgets) {
    for (AbstractWidget widget : widgets) {
      Control.Kind kind = WidgetControl.of(widget, false).kind();
      if (kind != Control.Kind.BUTTON && kind != Control.Kind.TEXT) {
        return false;
      }
    }
    return true;
  }

  private void sidebar(Form next, OptionsScreen options, List<AbstractWidget> loose) {
    Minecraft mc = Minecraft.getInstance();
    String title = Texts.plain(screen.getTitle());
    next.sidebar.add(
        new Nav(
            Lang.tr(GENERAL),
            () -> {
              if (mc.gui.screen() != options) {
                mc.gui.setScreen(options);
              }
            },
            true,
            screen == options));
    List<AbstractWidget> entries = new ArrayList<>();
    for (GuiEventListener child : options.children()) {
      if (child instanceof AbstractButton button
          && !(button instanceof PlainTextButton)
          && Texts.opens(button.getMessage())) {
        entries.add(button);
      }
    }
    entries.sort(READING);
    for (AbstractWidget entry : entries) {
      WidgetControl control = WidgetControl.of(entry, false);
      String label = control.label();
      boolean current = screen != options && (label.equals(chosen) || label.equals(title));
      next.sidebar.add(new Nav(label, control::press, false, current));
    }
    if (screen == options) {
      chosen = "";
      loose.removeAll(entries);
    }
  }

  private static void rows(Form next, ContainerObjectSelectionList<?> grid) {
    for (Object entry : grid.children()) {
      if (!(entry instanceof ContainerEventHandler holder)) {
        continue;
      }
      List<Control> controls = new ArrayList<>();
      String caption = null;
      for (GuiEventListener child : holder.children()) {
        if (!(child instanceof AbstractWidget widget) || !widget.visible) {
          continue;
        }
        if (widget instanceof AbstractStringWidget) {
          caption = Texts.plain(widget.getMessage());
        } else {
          WidgetControl control = WidgetControl.of(widget, false);
          if (control != null) {
            controls.add(control);
          }
        }
      }
      if (entry instanceof KeyBindsList.KeyEntry) {
        next.rows.add(
            Form.Row.of(Texts.plain(((KeyEntryAccessor) entry).cxvisuals$name()), controls));
      } else if (controls.isEmpty()) {
        if (caption != null && !caption.isBlank()) {
          next.rows.add(Form.Row.caption(caption));
        }
      } else {
        for (Control control : controls) {
          next.rows.add(Form.Row.of(control));
        }
      }
    }
  }

  private ItemList items(ObjectSelectionList<?> list) {
    if (list instanceof ServerSelectionList servers
        && screen instanceof JoinMultiplayerScreen join) {
      return new ServerItems(join, servers);
    }
    if (list instanceof WorldSelectionList worlds) {
      return new WorldItems(worlds);
    }
    return PlainItems.of((AbstractSelectionList<?>) list);
  }

  private void extras(Form next) {
    if (screen instanceof ConnectScreen) {
      Component status = ((ConnectScreenAccessor) screen).cxvisuals$status();
      next.lines.add(0, Texts.plain(status));
      next.busy = true;
    } else if (screen instanceof LevelLoadingScreen) {
      next.title = Texts.plain(Component.translatable("multiplayer.downloadingTerrain"));
      next.progress = ((LevelLoadingScreenAccessor) screen).cxvisuals$progress();
      next.busy = true;
    } else if (screen instanceof ProgressScreen) {
      ProgressScreenAccessor progress = (ProgressScreenAccessor) screen;
      if (progress.cxvisuals$header() != null) {
        next.title = Texts.plain(progress.cxvisuals$header());
      }
      if (progress.cxvisuals$stage() != null) {
        next.lines.add(Texts.plain(progress.cxvisuals$stage()));
        next.progress = Math.max(0, progress.cxvisuals$progress()) / PERCENT;
      }
      next.busy = true;
    } else if (screen instanceof GenericMessageScreen) {
      next.busy = true;
    } else if (screen instanceof JoinMultiplayerScreen && Accounts.live()) {
      next.actions.add(
          Math.max(0, next.actions.size() - 1),
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
  }
}
