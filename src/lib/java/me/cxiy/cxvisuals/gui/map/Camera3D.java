package me.cxiy.cxvisuals.gui.map;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class Camera3D {
  public static final float FOV = (float) Math.toRadians(40.0);
  public static final float MIN_PITCH = (float) Math.toRadians(18.0);
  public static final float MAX_PITCH = (float) Math.toRadians(89.0);
  public static final float MIN_DISTANCE = 12.0F;
  public static final float MAX_DISTANCE = 2400.0F;

  public double targetX;
  public double targetY = 64.0;
  public double targetZ;
  public float yaw;
  public float pitch = MAX_PITCH;
  public float distance = 160.0F;

  public final Matrix4f view = new Matrix4f();
  public final Matrix4f projection = new Matrix4f();
  public final Matrix4f viewProjection = new Matrix4f();
  public final Matrix4f culling = new Matrix4f();
  private final Matrix4f inverse = new Matrix4f();
  private final Vector4f point = new Vector4f();
  private final Vector3f eye = new Vector3f();
  private float left;
  private float top;
  private float width = 1.0F;
  private float height = 1.0F;

  public void update(float x, float y, float w, float h, boolean zeroToOne) {
    left = x;
    top = y;
    width = Math.max(1.0F, w);
    height = Math.max(1.0F, h);
    float cos = (float) Math.cos(pitch);
    eye.set(
        (float) Math.sin(yaw) * cos * distance,
        (float) Math.sin(pitch) * distance,
        (float) Math.cos(yaw) * cos * distance);
    view.setLookAt(eye.x, eye.y, eye.z, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
    float far = distance * 6.0F + 4096.0F;
    float near = Math.max(0.5F, distance * 0.02F);
    projection.setPerspective(FOV, width / height, far, near, zeroToOne);
    projection.mul(view, viewProjection);
    culling.setPerspective(FOV, width / height, near, far, false).mul(view);
    viewProjection.invert(inverse);
  }

  public void matrix(boolean zeroToOne, Matrix4f out) {
    float far = distance * 6.0F + 4096.0F;
    float near = Math.max(0.5F, distance * 0.02F);
    out.setPerspective(FOV, width / height, far, near, zeroToOne).mul(view);
  }

  public float eyeX() {
    return eye.x;
  }

  public float eyeY() {
    return eye.y;
  }

  public float eyeZ() {
    return eye.z;
  }

  public static float distanceFor(float pixelsPerBlock, float viewportHeight) {
    return viewportHeight / (2.0F * pixelsPerBlock * (float) Math.tan(FOV * 0.5F));
  }

  public float pixelsPerBlock() {
    return height / (2.0F * distance * (float) Math.tan(FOV * 0.5F));
  }

  public boolean project(double x, double y, double z, float[] out) {
    point.set((float) (x - targetX), (float) (y - targetY), (float) (z - targetZ), 1.0F);
    viewProjection.transform(point);
    if (point.w <= 0.01F) {
      return false;
    }
    float nx = point.x / point.w;
    float ny = point.y / point.w;
    out[0] = left + (nx * 0.5F + 0.5F) * width;
    out[1] = top + (0.5F - ny * 0.5F) * height;
    out[2] = point.w;
    return nx > -1.4F && nx < 1.4F && ny > -1.4F && ny < 1.4F;
  }

  public boolean ground(float sx, float sy, double planeY, double[] out) {
    float nx = (sx - left) / width * 2.0F - 1.0F;
    float ny = 1.0F - (sy - top) / height * 2.0F;
    Vector4f near = new Vector4f(nx, ny, 1.0F, 1.0F);
    Vector4f far = new Vector4f(nx, ny, 0.0F, 1.0F);
    inverse.transform(near);
    inverse.transform(far);
    near.div(near.w);
    far.div(far.w);
    float dy = far.y - near.y;
    if (Math.abs(dy) < 1.0e-5F) {
      return false;
    }
    float t = (float) ((planeY - targetY - near.y) / dy);
    if (t < 0.0F) {
      return false;
    }
    out[0] = targetX + near.x + (far.x - near.x) * t;
    out[1] = targetZ + near.z + (far.z - near.z) * t;
    return true;
  }
}
