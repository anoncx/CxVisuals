package me.cxiy.cxvisuals.modules.idlecamera.camera;

public interface DirectorConfig {
  MotionParams motion();

  double shotLength();

  Transition transition();

  double blendTime();

  boolean shotEnabled(ShotType type);

  double shotWeight(ShotType type);

  default boolean avoidBlocks() {
    return true;
  }
}
