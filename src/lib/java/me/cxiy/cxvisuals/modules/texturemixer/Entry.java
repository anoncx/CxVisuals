package me.cxiy.cxvisuals.modules.texturemixer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Entry {
  public enum Kind {
    BLOCK,
    ITEM,
    ENTITY
  }

  public record State(String label, Shape shape) {}

  private final String id;
  private final String title;
  private final String lower;
  private final Kind kind;
  private final List<State> states;
  private final List<String> textures;
  private final float zoom;

  public Entry(String id, Kind kind, Shape shape) {
    this(id, kind, List.of(new State("", shape)), 1.0F);
  }

  public Entry(String id, Kind kind, List<State> states, float zoom) {
    this.id = id;
    this.kind = kind;
    this.states = List.copyOf(states);
    this.zoom = zoom;
    Set<String> union = new LinkedHashSet<>();
    for (State state : states) {
      union.addAll(state.shape().textures());
    }
    this.textures = new ArrayList<>(union);
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
    return states.get(0).shape();
  }

  public List<State> states() {
    return states;
  }

  public List<String> textures() {
    return textures;
  }

  public float zoom() {
    return zoom;
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
