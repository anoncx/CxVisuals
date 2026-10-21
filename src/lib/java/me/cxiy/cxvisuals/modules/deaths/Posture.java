package me.cxiy.cxvisuals.modules.deaths;

public final class Posture {
  public float rise;
  public float scale;
  public float squash;
  public float pivot;
  public float jitterX;
  public float jitterZ;

  public Posture() {
    reset();
  }

  public void reset() {
    rise = 0.0F;
    scale = 1.0F;
    squash = 1.0F;
    pivot = 0.0F;
    jitterX = 0.0F;
    jitterZ = 0.0F;
  }

  public boolean still() {
    return rise == 0.0F && scale == 1.0F && squash == 1.0F && jitterX == 0.0F && jitterZ == 0.0F;
  }
}
