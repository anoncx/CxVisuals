package me.cxiy.cxvisuals.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.KeyHandle;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.Settings;

public abstract class Module {
  private final String id;
  private final String name;
  private final boolean switchable;
  private final KeyHandle toggleKey;
  private final List<ActionSetting> actions = new ArrayList<>(2);
  private final List<ActionSetting> actionsView = Collections.unmodifiableList(actions);
  private final List<Module> folded = new ArrayList<>(2);
  private List<Page> merged;
  private Module host;
  private String hostId;
  private int slot;
  private Supplier<Boolean> listing = () -> true;
  private boolean sealed;

  public final Settings settings;
  public final BoolSetting enabled;

  protected Module(String id, String name, boolean enabledByDefault, KeyBinder keys) {
    this(id, name, true, enabledByDefault, keys);
  }

  Module(String id, String name, boolean switchable, boolean enabledByDefault, KeyBinder keys) {
    this.id = id;
    this.name = name;
    this.switchable = switchable;
    this.settings = new Settings(id);
    this.enabled = switchable ? settings.toggle("enabled", "Enabled", enabledByDefault) : null;
    this.toggleKey =
        switchable ? keys.bind(id + ".toggle", "Toggle " + name, KeyBinder.UNBOUND) : null;
  }

  public final String id() {
    return id;
  }

  public final String name() {
    return Lang.tr(name);
  }

  public final boolean listed() {
    return host == null && listing.get();
  }

  protected final void hostedBy(String id, int position) {
    hostId = id;
    slot = position;
  }

  public final String hostId() {
    return hostId;
  }

  public final Module host() {
    return host;
  }

  public final List<Module> folded() {
    return Collections.unmodifiableList(folded);
  }

  final void fold(Module child) {
    if (child.host != null || child == this) {
      throw new IllegalStateException("cannot fold " + child.id);
    }
    child.host = this;
    folded.add(child);
    folded.sort(Comparator.comparingInt(other -> other.slot));
    merged = null;
  }

  protected final void listedWhen(Supplier<Boolean> shown) {
    listing = shown;
  }

  public boolean pinned() {
    return false;
  }

  public String group() {
    return "";
  }

  public boolean live() {
    return switchable && enabled.get();
  }

  public final boolean switchable() {
    return switchable;
  }

  public final void toggle() {
    if (switchable) {
      enabled.set(!enabled.get());
    }
  }

  public final List<Page> sections() {
    if (!sealed) {
      sealed = true;
      if (switchable) {
        Page first =
            settings.pages().isEmpty()
                ? settings.page("general", "General")
                : settings.pages().get(0);
        Group module = hostId == null ? first.group("Module") : first.leading(name);
        if (hostId != null) {
          module.unsaved().add(enabled);
        }
        module.key("toggle_key", "Toggle key", toggleKey);
      }
    }
    if (folded.isEmpty()) {
      return settings.pages();
    }
    if (merged == null) {
      List<Page> all = new ArrayList<>(settings.pages());
      for (Module child : folded) {
        all.addAll(child.sections());
      }
      merged = Collections.unmodifiableList(all);
    }
    return merged;
  }

  public final List<ActionSetting> actions() {
    if (folded.isEmpty()) {
      return actionsView;
    }
    List<ActionSetting> all = new ArrayList<>(actions);
    for (Module child : folded) {
      all.addAll(child.actions());
    }
    return all;
  }

  protected final Page section(String sectionId, String title) {
    return settings.page(sectionId, title);
  }

  protected final ActionSetting action(
      String actionId, String label, ActionSetting.Kind kind, Runnable run) {
    ActionSetting action = settings.command(actionId, label, kind, run);
    actions.add(action);
    return action;
  }
}
