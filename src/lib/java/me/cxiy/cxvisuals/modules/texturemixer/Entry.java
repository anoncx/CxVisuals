package me.cxiy.cxvisuals.modules.texturemixer;

import java.util.List;
import java.util.Locale;

public final class Entry {
  public enum Kind {
    BLOCK,
    ITEM
  }

  private final String id;
  private final String title;
  private final String lower;
  private final Kind kind;
  private final Shape shape;
  private final List<String> textures;

  public Entry(String id, Kind kind, Shape shape) {
    this.id = id;
    this.kind = kind;
    this.shape = shape;
    this.textures = List.copyOf(shape.textures());
    this.title = pretty(id);
    this.lower = (title + " " + id).toLowerCase(Locale.ROOT);
  }

  public String id() {
    return id;
  }

  public String title() {
    return title;
  }

  public String lower() {
    return lower;
  }

  public Kind kind() {
    return kind;
  }

  public Shape shape() {
    return shape;
  }

  public List<String> textures() {
    return textures;
  }

  public static String pretty(String id) {
    StringBuilder out = new StringBuilder(id.length());
    boolean start = true;
    for (int i = 0; i < id.length(); i++) {
      char c = id.charAt(i);
      if (c == '_') {
        out.append(' ');
        start = true;
      } else {
        out.append(start ? Character.toUpperCase(c) : c);
        start = false;
      }
    }
    return out.toString();
  }
}
