package me.cxiy.cxvisuals.modules.environment.particles;

public interface Ground {
  int OPEN = 0xF0;
  float AIR = -1.0F;

  float floor(double x, double top, double z);

  default float top(int x, int y, int z) {
    return AIR;
  }

  default int light(int x, int y, int z) {
    return OPEN;
  }
}
