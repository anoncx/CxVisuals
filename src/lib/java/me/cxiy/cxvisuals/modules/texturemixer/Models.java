package me.cxiy.cxvisuals.modules.texturemixer;

import java.util.ArrayList;
import java.util.List;

public final class Models {
  private final String texture;
  private final float width;
  private final float height;
  private final List<Shape.Box> boxes = new ArrayList<>();
  private float shiftX;
  private float shiftY;
  private float shiftZ;

  public Models(String texture, int width, int height) {
    this.texture = texture;
    this.width = width;
    this.height = height;
  }

  public Models shift(float x, float y, float z) {
    shiftX = x;
    shiftY = y;
    shiftZ = z;
    return this;
  }

  public Models cuboid(
      float x, float y, float z, float w, float h, float d, int u, int v, float grow) {
    return cuboid(x, y, z, w, h, d, u, v, grow, false);
  }

  public Models cuboid(
      float x,
      float y,
      float z,
      float w,
      float h,
      float d,
      int u,
      int v,
      float grow,
      boolean mirror) {
    Shape.Face[] faces = new Shape.Face[6];
    faces[Shape.UP] = face(u + d, v, u + d + w, v + d, mirror);
    faces[Shape.DOWN] = face(u + d + w, v, u + d + 2 * w, v + d, mirror);
    faces[mirror ? Shape.EAST : Shape.WEST] = face(u, v + d, u + d, v + d + h, mirror);
    faces[Shape.SOUTH] = face(u + d, v + d, u + d + w, v + d + h, mirror);
    faces[mirror ? Shape.WEST : Shape.EAST] =
        face(u + d + w, v + d, u + 2 * d + w, v + d + h, mirror);
    faces[Shape.NORTH] = face(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h, mirror);
    boxes.add(
        new Shape.Box(
            x - grow + shiftX,
            -(y + h) - grow + shiftY,
            -(z + d) - grow + shiftZ,
            x + w + grow + shiftX,
            -y + grow + shiftY,
            -z + grow + shiftZ,
            faces));
    return this;
  }

  private Shape.Face face(float u0, float v0, float u1, float v1, boolean mirror) {
    float left = mirror ? u1 : u0;
    float right = mirror ? u0 : u1;
    float scaleU = 16.0F / width;
    float scaleV = 16.0F / height;
    return new Shape.Face(texture, left * scaleU, v0 * scaleV, right * scaleU, v1 * scaleV, false);
  }

  public Shape build() {
    return new Shape(boxes.toArray(new Shape.Box[0]));
  }
}
