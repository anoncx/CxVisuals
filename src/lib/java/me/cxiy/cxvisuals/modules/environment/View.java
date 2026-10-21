package me.cxiy.cxvisuals.modules.environment;

public final class View {
  public double x;
  public double y;
  public double z;
  public float fx;
  public float fy;
  public float fz = 1.0F;
  public float rx = -1.0F;
  public float ry;
  public float rz;
  public float ux;
  public float uy = 1.0F;
  public float uz;
  public float yawRate;

  public View look(float yawDegrees, float pitchDegrees) {
    double yaw = Math.toRadians(yawDegrees);
    double pitch = Math.toRadians(pitchDegrees);
    double level = Math.cos(pitch);
    fx = (float) (-Math.sin(yaw) * level);
    fy = (float) -Math.sin(pitch);
    fz = (float) (Math.cos(yaw) * level);
    rx = (float) -Math.cos(yaw);
    ry = 0.0F;
    rz = (float) -Math.sin(yaw);
    float cx = ry * fz - rz * fy;
    float cy = rz * fx - rx * fz;
    float cz = rx * fy - ry * fx;
    float length = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
    ux = cx / length;
    uy = cy / length;
    uz = cz / length;
    return this;
  }
}
