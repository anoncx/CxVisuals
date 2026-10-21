package me.cxiy.cxvisuals.modules.environment.particles;

final class Waves {
  static final float TURN = (float) (Math.PI * 2.0);

  private Waves() {}

  static float sin(float x) {
    return (float) Math.sin(x);
  }

  static float cos(float x) {
    return (float) Math.cos(x);
  }

  static float wave(float t) {
    return sin(t) * 0.6F + sin(t * 2.31F + 1.3F) * 0.3F + sin(t * 4.71F + 4.1F) * 0.1F;
  }

  static float smooth(float from, float to, float x) {
    float t = Math.clamp((x - from) / (to - from), 0.0F, 1.0F);
    return t * t * (3.0F - 2.0F * t);
  }

  static float approach(float current, float target, float rate, float dt) {
    return current + (target - current) * Math.min(1.0F, rate * dt);
  }

  static int argb(int rgb, float alpha) {
    return Math.clamp(Math.round(alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
  }

  static int shade(int rgb, float light) {
    int r = Math.min(255, Math.round((rgb >> 16 & 0xFF) * light));
    int g = Math.min(255, Math.round((rgb >> 8 & 0xFF) * light));
    int b = Math.min(255, Math.round((rgb & 0xFF) * light));
    return r << 16 | g << 8 | b;
  }

  static int mix(int from, int to, float t) {
    int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
    int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
    int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
    return r << 16 | g << 8 | b;
  }
}
