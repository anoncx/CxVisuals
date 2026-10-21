package me.cxiy.cxvisuals.mc.gui;

import org.joml.Matrix3x2fc;

record Cut(float left, float top, float right, float bottom) {
  static final Cut NONE =
      new Cut(
          Float.NEGATIVE_INFINITY,
          Float.NEGATIVE_INFINITY,
          Float.POSITIVE_INFINITY,
          Float.POSITIVE_INFINITY);

  static Cut of(Matrix3x2fc pose, float left, float top, float right, float bottom) {
    float scaleX = pose.m00();
    float scaleY = pose.m11();
    if (scaleX <= 0.0F || scaleY <= 0.0F || pose.m01() != 0.0F || pose.m10() != 0.0F) {
      return NONE;
    }
    return new Cut(
        (left - pose.m20()) / scaleX,
        (top - pose.m21()) / scaleY,
        (right - pose.m20()) / scaleX,
        (bottom - pose.m21()) / scaleY);
  }

  boolean misses(float x0, float y0, float x1, float y1) {
    return x1 <= left || x0 >= right || y1 <= top || y0 >= bottom;
  }

  float x(float value) {
    return value < left ? left : Math.min(value, right);
  }

  float y(float value) {
    return value < top ? top : Math.min(value, bottom);
  }

  static float slide(float from, float to, float start, float end, float at) {
    return end == start ? from : from + (to - from) * (at - start) / (end - start);
  }

  static int blend(int from, int to, float amount) {
    if (from == to || amount <= 0.0F) {
      return from;
    }
    if (amount >= 1.0F) {
      return to;
    }
    int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * amount);
    int r =
        Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
    int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
    int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }
}
