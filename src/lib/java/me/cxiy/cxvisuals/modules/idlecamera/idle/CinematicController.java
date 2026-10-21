package me.cxiy.cxvisuals.modules.idlecamera.idle;

import me.cxiy.cxvisuals.modules.idlecamera.camera.Director;

public final class CinematicController {
  public record Conditions(
      boolean inWorld, boolean alive, boolean screenOpen, boolean paused, boolean menu) {
    public Conditions(boolean inWorld, boolean alive, boolean screenOpen, boolean paused) {
      this(inWorld, alive, screenOpen, paused, false);
    }
  }

  public interface Config {
    boolean enabled();

    double idleSeconds();

    boolean exitOnDamage();

    boolean playInMenu();
  }

  public static final double GRACE = 0.3;
  public static final double TOGGLE_WINDOW = 0.25;

  private final Config config;
  private final Director director;
  private final IdleTracker idle;

  private boolean startRequested;
  private boolean damaged;
  private double grace;
  private double sinceInputStop = Double.MAX_VALUE / 2;

  public CinematicController(Config config, Director director, IdleTracker idle) {
    this.config = config;
    this.director = director;
    this.idle = idle;
  }

  public Director director() {
    return director;
  }

  public boolean active() {
    return director.state() != Director.State.OFF;
  }

  public void requestStart() {
    startRequested = true;
  }

  public void onDamage() {
    damaged = true;
  }

  public void frame(double dt, Conditions c) {
    idle.advance(dt);
    boolean activity = idle.consumeActivity();
    boolean requested = startRequested;
    boolean hurt = damaged;
    startRequested = false;
    damaged = false;
    if (grace > 0.0) {
      grace -= dt;
      activity = false;
    }

    Director.State state = director.state();
    if (!c.inWorld()) {
      if (state != Director.State.OFF) {
        director.abort();
      }
      return;
    }

    sinceInputStop += dt;
    boolean playing = state == Director.State.ENTERING || state == Director.State.RUNNING;
    if (playing) {
      if (hurt && config.exitOnDamage()) {
        idle.reset();
        director.stop();
      } else if (activity || requested) {
        sinceInputStop = 0.0;
        director.stop();
      } else if (!c.alive() || c.screenOpen() && !behindMenu(c)) {
        director.stop();
      }
      return;
    }

    if (requested) {
      if (clear(c) && sinceInputStop > TOGGLE_WINDOW) {
        begin();
      }
      return;
    }

    if (state == Director.State.OFF
        && config.enabled()
        && clear(c)
        && idle.idleSeconds() >= config.idleSeconds()) {
      begin();
    }
  }

  private boolean behindMenu(Conditions c) {
    return c.menu() && config.playInMenu();
  }

  private boolean clear(Conditions c) {
    return c.inWorld() && c.alive() && (behindMenu(c) || !c.screenOpen() && !c.paused());
  }

  private void begin() {
    director.start();
    if (director.state() == Director.State.ENTERING) {
      grace = GRACE;
    }
  }
}
