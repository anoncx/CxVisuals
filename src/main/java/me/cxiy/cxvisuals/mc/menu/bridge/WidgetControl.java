package me.cxiy.cxvisuals.mc.menu.bridge;

import me.cxiy.cxvisuals.mc.mixin.CycleButtonAccessor;
import me.cxiy.cxvisuals.mc.mixin.EditBoxAccessor;
import me.cxiy.cxvisuals.mc.mixin.SliderAccessor;
import me.cxiy.cxvisuals.menu.model.Control;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

final class WidgetControl implements Control {
  static final MouseButtonEvent CLICK = new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(0, 0));

  final AbstractWidget widget;
  private final Kind kind;
  private final boolean leads;
  private Component seen;
  private Object seenValue;
  private int icon;
  private String label = "";
  private String value = "";

  private WidgetControl(AbstractWidget widget, Kind kind, boolean leads) {
    this.widget = widget;
    this.kind = kind;
    this.leads = leads;
  }

  static WidgetControl of(AbstractWidget widget, boolean leads) {
    if (widget instanceof EditBox) {
      return new WidgetControl(widget, Kind.TEXT, false);
    }
    if (widget instanceof Checkbox) {
      return new WidgetControl(widget, Kind.TOGGLE, false);
    }
    if (widget instanceof CycleButton<?> cycle) {
      return new WidgetControl(
          widget, cycle.getValue() instanceof Boolean ? Kind.TOGGLE : Kind.CYCLE, false);
    }
    if (widget instanceof AbstractSliderButton) {
      return new WidgetControl(widget, Kind.SLIDER, false);
    }
    if (widget instanceof AbstractButton) {
      return new WidgetControl(widget, Kind.BUTTON, leads);
    }
    return null;
  }

  static boolean supports(AbstractWidget widget) {
    return widget instanceof EditBox
        || widget instanceof AbstractSliderButton
        || widget instanceof AbstractButton;
  }

  @SuppressWarnings("unchecked")
  private void refresh() {
    Component message = widget.getMessage();
    Object current = widget instanceof CycleButton<?> cycle ? cycle.getValue() : null;
    if (message == seen && current == seenValue) {
      return;
    }
    seen = message;
    seenValue = current;
    String text = Texts.plain(message);
    if (widget instanceof CycleButton<?> cycle) {
      CycleButtonAccessor<Object> access = (CycleButtonAccessor<Object>) cycle;
      String name = Texts.plain(access.cxvisuals$name());
      label = name.isEmpty() ? Texts.before(text) : name;
      value = Texts.plain(access.cxvisuals$stringifier().apply(current));
    } else if (kind == Kind.SLIDER) {
      label = Texts.before(text);
      value = Texts.after(text);
    } else if (kind == Kind.BUTTON) {
      label = Texts.action(message);
      value = "";
    } else {
      label = text;
      value = "";
    }
  }

  @Override
  public Kind kind() {
    return kind;
  }

  @Override
  public String label() {
    refresh();
    return label;
  }

  @Override
  public String value() {
    refresh();
    return value;
  }

  @Override
  public boolean on() {
    if (widget instanceof Checkbox box) {
      return box.selected();
    }
    return widget instanceof CycleButton<?> cycle && Boolean.TRUE.equals(cycle.getValue());
  }

  @Override
  public boolean enabled() {
    return widget.active;
  }

  WidgetControl icon(int glyph) {
    icon = glyph;
    return this;
  }

  @Override
  public int icon() {
    return icon;
  }

  @Override
  public boolean leads() {
    return leads;
  }

  @Override
  public float fraction() {
    return widget instanceof AbstractSliderButton
        ? (float) ((SliderAccessor) widget).cxvisuals$value()
        : 0.0F;
  }

  @Override
  public void fraction(float next) {
    if (widget instanceof AbstractSliderButton) {
      ((SliderAccessor) widget).cxvisuals$setValue(next);
    }
  }

  @Override
  public void release() {
    widget.onRelease(CLICK);
  }

  @Override
  public void press() {
    if (widget.active && widget instanceof AbstractButton button) {
      button.playDownSound(Minecraft.getInstance().getSoundManager());
      button.onPress(CLICK);
    }
  }

  @Override
  public String text() {
    return widget instanceof EditBox box ? box.getValue() : "";
  }

  @Override
  public void text(String next) {
    if (widget instanceof EditBox box && !box.getValue().equals(next)) {
      box.setValue(next);
    }
  }

  @Override
  public int limit() {
    return widget instanceof EditBox ? ((EditBoxAccessor) widget).cxvisuals$maxLength() : 64;
  }
}
