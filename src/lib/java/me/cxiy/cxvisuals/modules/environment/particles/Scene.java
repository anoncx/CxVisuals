package me.cxiy.cxvisuals.modules.environment.particles;

public record Scene(Kind kind, Tint tint) {
  private static final Scene[] PLAIN = new Scene[Kind.values().length];

  static {
    for (Kind kind : Kind.values()) {
      PLAIN[kind.ordinal()] = new Scene(kind, kind.tint);
    }
  }

  public static Scene of(Kind kind) {
    return PLAIN[kind.ordinal()];
  }
}
