package me.cxiy.cxvisuals.modules.motionblur;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

public final class Reprojection {
  public static final float MAX_TURN = 0.5F;

  private static final float STILL_TURN = 1.0e-4F;
  private static final float STILL_TRAVEL = 1.0e-4F;
  private static final float NO_AXIS = 1.0e-9F;

  private final Matrix4f result = new Matrix4f();
  private final Matrix4f inverse = new Matrix4f();
  private final Quaternionf now = new Quaternionf();
  private final Quaternionf turn = new Quaternionf();

  public Matrix4fc matrix() {
    return result;
  }

  public boolean compute(
      Matrix4fc projection,
      Matrix4fc view,
      Matrix4fc previousView,
      float dx,
      float dy,
      float dz,
      float turnScale,
      float moveScale) {
    view.getNormalizedRotation(now);
    previousView.getNormalizedRotation(turn);
    turn.mul(now.conjugate());
    if (turn.w < 0.0F) {
      turn.set(-turn.x, -turn.y, -turn.z, -turn.w);
    }
    float axis = (float) Math.sqrt(turn.x * turn.x + turn.y * turn.y + turn.z * turn.z);
    float angle = Math.min(MAX_TURN, 2.0F * (float) Math.atan2(axis, turn.w) * turnScale);
    boolean turning = angle > STILL_TURN && axis > NO_AXIS;

    float tx = dx * moveScale;
    float ty = dy * moveScale;
    float tz = dz * moveScale;
    boolean travelling = tx * tx + ty * ty + tz * tz > STILL_TRAVEL * STILL_TRAVEL;

    if (!turning && !travelling) {
      result.identity();
      return false;
    }

    projection.mul(view, inverse).invert();
    result.set(projection);
    if (turning) {
      result.rotate(turn.setAngleAxis(angle, turn.x / axis, turn.y / axis, turn.z / axis));
    }
    result.mul(view);
    if (travelling) {
      result.translate(tx, ty, tz);
    }
    result.mul(inverse);
    return true;
  }
}
