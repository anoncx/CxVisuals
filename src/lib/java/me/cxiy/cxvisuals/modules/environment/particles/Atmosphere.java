package me.cxiy.cxvisuals.modules.environment.particles;

import me.cxiy.cxvisuals.modules.environment.EnvironmentModule;
import me.cxiy.cxvisuals.modules.environment.View;

public final class Atmosphere {
  private static final float CLOCK_WRAP = 4096.0F;
  private static final float PERCENT = 0.01F;
  private static final float NEAREST_SKY = 64.0F;
  private static final float FARTHEST_SKY = 220.0F;
  private static final float SKY_SHARE = 0.8F;
  private static final float CALM_SNOW = 0.5F;
  private static final float STORM_SNOW = 0.9F;

  private final EnvironmentModule module;
  private final Wind wind = new Wind();
  private final Field field;
  private final Lanterns lanterns;
  private final Meteors meteors;
  private final Fireworks fireworks;
  private final Ripples ripples;
  private final Downpour downpour;
  private float time;

  public Atmosphere(EnvironmentModule module, long seed) {
    this.module = module;
    field = new Field(seed);
    lanterns = new Lanterns(seed * 31L + 7L);
    meteors = new Meteors(seed * 17L + 3L);
    fireworks = new Fireworks(seed * 13L + 5L);
    ripples = new Ripples(seed * 29L + 11L);
    downpour = new Downpour();
  }

  public Field field() {
    return field;
  }

  public Lanterns lanterns() {
    return lanterns;
  }

  public Meteors meteors() {
    return meteors;
  }

  public Fireworks fireworks() {
    return fireworks;
  }

  public Ripples ripples() {
    return ripples;
  }

  public Downpour downpour() {
    return downpour;
  }

  public boolean empty() {
    return field.live() == 0
        && lanterns.alive() == 0
        && meteors.alive() == 0
        && fireworks.alive() == 0
        && ripples.alive() == 0
        && downpour.count() == 0;
  }

  public void clear() {
    field.clear();
    lanterns.clear();
    meteors.clear();
    fireworks.clear();
    ripples.clear();
    downpour.clear();
  }

  public void update(View view, Climate climate, Ground ground, float dt) {
    time = (time + dt) % CLOCK_WRAP;
    wind.update(time, module.wind.get().floatValue() * PERCENT);
    Scene wanted = module.particles.get() ? wanted(climate) : null;
    boolean outside = climate.realm == Climate.Realm.OVERWORLD;
    float amount = module.density.get().floatValue() * PERCENT;
    float exposure = 1.0F;
    if (wanted != null && outside && wanted.kind().falls) {
      exposure = climate.open;
      if (wanted.kind() == Kind.SNOW && module.air.get() == EnvironmentModule.Air.AUTO) {
        amount *= CALM_SNOW + climate.rain * STORM_SNOW;
      }
    }
    field.update(view, wanted, amount, exposure, dt, time, wind, ground);
    float night = climate.clearNight();
    lanterns.update(
        view,
        module.lanterns.get() ? module.lanternCount.get().intValue() : 0,
        night,
        climate.fogEnd,
        dt,
        time,
        wind,
        ground);
    meteors.update(
        dt, module.meteors.get() ? night : 0.0F, module.meteorRate.get().floatValue() * PERCENT);
    downpour.update(
        module.rain.get() && outside && !climate.cold ? climate.rain : 0.0F,
        module.rainDensity.get().floatValue() * PERCENT,
        wind,
        ground,
        dt);
    ripples.update(
        view, wetness(climate), module.rippleDensity.get().floatValue() * PERCENT, ground, dt);
    fireworks.update(
        view,
        module.fireworks.get() ? night : 0.0F,
        module.fireworkRate.get().floatValue() * PERCENT,
        climate.fogEnd,
        dt,
        ground);
  }

  private float wetness(Climate climate) {
    if (climate.realm != Climate.Realm.OVERWORLD) {
      return 0.0F;
    }
    return switch (module.ripples.get()) {
      case OFF -> 0.0F;
      case RAIN -> climate.cold ? 0.0F : climate.rain;
      case ALWAYS -> 1.0F;
    };
  }

  private Scene wanted(Climate climate) {
    Kind kind = module.air.get().kind;
    return kind == null ? Moods.pick(climate) : Scene.of(kind);
  }

  public void emit(View view, Climate climate, Sink sink) {
    float glow = module.glow.get().floatValue() * PERCENT;
    meteors.emit(view, sink, Math.clamp(climate.far * SKY_SHARE, NEAREST_SKY, FARTHEST_SKY), glow);
    fireworks.emit(view, sink, climate.fogStart, climate.fogEnd, glow, time);
    lanterns.emit(view, sink, climate.fogStart, climate.fogEnd, glow, time);
    downpour.emit(view, sink, climate, module.rainStreak.get().floatValue() * PERCENT);
    ripples.emit(
        view,
        sink,
        climate,
        module.rippleSize.get().floatValue() * PERCENT,
        module.rippleSheen.get().floatValue() * PERCENT);
    field.emit(view, sink, module.particleSize.get().floatValue() * PERCENT, glow, climate, time);
  }
}
