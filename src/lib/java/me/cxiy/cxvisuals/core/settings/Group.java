package me.cxiy.cxvisuals.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Group {
  private final Settings owner;
  private final String pageId;
  private final String title;
  private final List<Setting<?>> settings = new ArrayList<>();
  private final List<Setting<?>> view = Collections.unmodifiableList(settings);
  private boolean saved = true;
  private Supplier<Boolean> shown = () -> true;

  Group(Settings owner, String pageId, String title) {
    this.owner = owner;
    this.pageId = pageId;
    this.title = title;
  }

  public String title() {
    return Lang.tr(title);
  }

  public List<Setting<?>> settings() {
    return view;
  }

  public BoolSetting bool(String id, String title, boolean defaultValue) {
    return add(new BoolSetting(id, title, defaultValue));
  }

  public TextSetting text(String id, String title, String defaultValue) {
    return add(new TextSetting(id, title, defaultValue));
  }

  public TextSetting secret(String id, String title, String defaultValue) {
    return add(new TextSetting(id, title, defaultValue).concealed());
  }

  public NumberSetting number(
      String id,
      String title,
      double defaultValue,
      double min,
      double max,
      double step,
      String unit) {
    return add(new NumberSetting(id, title, defaultValue, min, max, step, unit));
  }

  public <E extends Enum<E>> EnumSetting<E> enumOf(
      String id, String title, E defaultValue, String... labels) {
    return add(new EnumSetting<>(id, title, defaultValue, labels));
  }

  public PaletteSetting palette(String id, String title, int defaultPreset) {
    return add(new PaletteSetting(id, title, defaultPreset));
  }

  public PickSetting picks(String id, String title) {
    return add(new PickSetting(id, title));
  }

  public KeySetting key(String id, String title, KeyHandle handle) {
    return add(new KeySetting(id, title, handle));
  }

  public ActionSetting action(
      String id, String title, String buttonLabel, ActionSetting.Kind kind, Runnable action) {
    return add(new ActionSetting(id, title, buttonLabel, kind, action));
  }

  public InfoSetting info(String id, String title, String text) {
    return add(new InfoSetting(id, title, text));
  }

  public PanelSetting panel(String id) {
    return add(new PanelSetting(id));
  }

  public Group unsaved() {
    saved = false;
    return this;
  }

  public Group showWhen(Supplier<Boolean> shown) {
    this.shown = shown;
    return this;
  }

  public boolean shown() {
    try {
      return shown.get();
    } catch (RuntimeException e) {
      return true;
    }
  }

  public <S extends Setting<?>> S add(S setting) {
    settings.add(setting);
    if (saved) {
      owner.register(pageId, setting);
    }
    return setting;
  }
}
