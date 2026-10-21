package me.cxiy.cxvisuals.modules.deaths;

public interface FxSink {
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
      float size);
}
