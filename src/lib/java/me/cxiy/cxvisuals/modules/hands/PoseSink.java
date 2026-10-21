package me.cxiy.cxvisuals.modules.hands;

public interface PoseSink {
  void translate(float x, float y, float z);

  void rotateX(float degrees);

  void rotateY(float degrees);

  void rotateZ(float degrees);

  void scale(float factor);
}
