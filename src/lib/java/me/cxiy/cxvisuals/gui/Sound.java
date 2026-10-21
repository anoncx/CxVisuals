package me.cxiy.cxvisuals.gui;

public final class Sound {
  public enum Cue {
    ON,
    OFF,
    TAB,
    STEP,
    OPEN,
    CLOSE
  }

  public interface Sink {
    void play(Cue cue);
  }

  private static volatile Sink sink = cue -> {};

  private Sound() {}

  public static void sink(Sink next) {
    sink = next == null ? cue -> {} : next;
  }

  public static void play(Cue cue) {
    sink.play(cue);
  }

  public static void toggled(boolean on) {
    play(on ? Cue.ON : Cue.OFF);
  }
}
