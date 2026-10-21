package me.cxiy.cxvisuals.modules.hands;

import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;

public final class HandView {
  public final NumberSetting x;
  public final NumberSetting y;
  public final NumberSetting z;
  public final NumberSetting scale;
  public final NumberSetting pitch;
  public final NumberSetting yaw;
  public final NumberSetting roll;

  HandView(Page page) {
    Group position = page.group("Position");
    x = position.number("x", "X", 0.0, -1.5, 1.5, 0.01, "");
    y = position.number("y", "Y", 0.0, -1.5, 1.5, 0.01, "");
    z = position.number("z", "Z", 0.0, -1.5, 1.5, 0.01, "");

    Group size = page.group("Size");
    scale = size.number("scale", "Scale", 1.0, 0.2, 2.0, 0.05, "×");

    Group rotation = page.group("Rotation");
    pitch = rotation.number("pitch", "Pitch", 0.0, -180.0, 180.0, 1.0, "°");
    yaw = rotation.number("yaw", "Yaw", 0.0, -180.0, 180.0, 1.0, "°");
    roll = rotation.number("roll", "Roll", 0.0, -180.0, 180.0, 1.0, "°");
  }

  public boolean neutral() {
    return x.get() == 0.0
        && y.get() == 0.0
        && z.get() == 0.0
        && scale.get() == 1.0
        && pitch.get() == 0.0
        && yaw.get() == 0.0
        && roll.get() == 0.0;
  }

  public void position(PoseSink pose, int side) {
    double dx = x.get();
    double dy = y.get();
    double dz = z.get();
    if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
      pose.translate((float) (side * dx), (float) dy, (float) dz);
    }
  }

  public void orient(PoseSink pose, int side) {
    double tilt = pitch.get();
    double turn = yaw.get();
    double lean = roll.get();
    double size = scale.get();
    if (tilt != 0.0) {
      pose.rotateX((float) tilt);
    }
    if (turn != 0.0) {
      pose.rotateY((float) (side * turn));
    }
    if (lean != 0.0) {
      pose.rotateZ((float) (side * lean));
    }
    if (size != 1.0) {
      pose.scale((float) size);
    }
  }
}
