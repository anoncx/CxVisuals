package me.cxiy.cxvisuals.modules.shaders.circles;

public interface CircleSink {
  void vertex(
      float x,
      float y,
      float z,
      float u,
      float v,
      int kind,
      float progress,
      float alpha,
      int seed,
      float radius);
}
