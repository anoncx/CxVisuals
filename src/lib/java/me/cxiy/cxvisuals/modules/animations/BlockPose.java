package me.cxiy.cxvisuals.modules.animations;

public final class BlockPose {
  public float pivotX;
  public float pivotY;
  public float pivotZ;
  public float moveX;
  public float moveY;
  public float moveZ;
  public float yaw;
  public float pitch;
  public float roll;
  public float scaleX;
  public float scaleY;
  public float scaleZ;

  void rest(float pivotX, float pivotY, float pivotZ) {
    this.pivotX = pivotX;
    this.pivotY = pivotY;
    this.pivotZ = pivotZ;
    moveX = 0.0F;
    moveY = 0.0F;
    moveZ = 0.0F;
    yaw = 0.0F;
    pitch = 0.0F;
    roll = 0.0F;
    scaleX = 1.0F;
    scaleY = 1.0F;
    scaleZ = 1.0F;
  }

  void scale(float all) {
    scaleX *= all;
    scaleY *= all;
    scaleZ *= all;
  }

  void stretch(float height) {
    float width = (float) (1.0 / Math.sqrt(Math.max(height, 0.05F)));
    scaleX *= width;
    scaleY *= height;
    scaleZ *= width;
  }

  void floor(float min) {
    scaleX = Math.max(scaleX, min);
    scaleY = Math.max(scaleY, min);
    scaleZ = Math.max(scaleZ, min);
  }
}
