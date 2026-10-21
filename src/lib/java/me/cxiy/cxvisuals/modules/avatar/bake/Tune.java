package me.cxiy.cxvisuals.modules.avatar.bake;

import me.cxiy.cxvisuals.modules.avatar.format.RawModel;

public record Tune(RawModel.Axis up, int turn, double size, double lift, boolean clips) {
  public static final double MIN_SIZE = 25.0;
  public static final double MAX_SIZE = 400.0;
  public static final double MAX_LIFT = 1.0;

  public Tune {
    turn = Math.floorMod(turn, 4);
    size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
    lift = Math.max(-MAX_LIFT, Math.min(MAX_LIFT, lift));
  }

  public static Tune defaults(RawModel.Axis up) {
    return new Tune(up, 0, 100.0, 0.0, true);
  }

  public boolean sameShape(Tune other) {
    return other != null && other.up == up && other.turn == turn;
  }
}
