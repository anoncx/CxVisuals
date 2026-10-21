package me.cxiy.cxvisuals.modules.idlecamera.camera;

import java.util.Random;
import java.util.function.BiFunction;

public record ShotType(
    String id, String name, double minClear, BiFunction<Random, Double, Shot> factory) {
  public Shot create(Random rng, double duration) {
    return factory.apply(rng, duration);
  }
}
