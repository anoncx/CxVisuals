package me.cxiy.cxvisuals.modules.shaders.trails;

public interface TrailSink {
  void vertex(
      float x,
      float y,
      float z,
      float u,
      float v,
      int kind,
      float progress,
      float alpha,
      float aux,
      float tone,
      int seed,
      float extra);
}
