package me.cxiy.cxvisuals.modules.environment.particles;

public interface Sink {
  void quad(
      float x,
      float y,
      float z,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      int argb,
      Shape shape,
      float detail,
      int seed);
}
