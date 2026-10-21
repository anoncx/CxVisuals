package me.cxiy.cxvisuals.menu.model;

import java.util.ArrayList;
import java.util.List;

public final class Form {
  public static final float NO_PROGRESS = -1.0F;

  public static final class Row {
    public final String caption;
    public final String label;
    public final List<Control> controls;

    private Row(String caption, String label, List<Control> controls) {
      this.caption = caption;
      this.label = label;
      this.controls = controls;
    }

    public static Row caption(String text) {
      return new Row(text, null, List.of());
    }

    public static Row of(Control control) {
      return new Row(null, null, List.of(control));
    }

    public static Row of(String label, List<Control> controls) {
      return new Row(null, label, controls);
    }
  }

  public String title = "";
  public final List<String> lines = new ArrayList<>();
  public final List<Control> sidebar = new ArrayList<>();
  public final List<Control> tabs = new ArrayList<>();
  public final List<Row> rows = new ArrayList<>();
  public final List<Control> actions = new ArrayList<>();
  public ItemList items;
  public float progress = NO_PROGRESS;
  public boolean busy;
}
