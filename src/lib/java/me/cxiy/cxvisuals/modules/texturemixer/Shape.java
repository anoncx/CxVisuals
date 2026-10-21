package me.cxiy.cxvisuals.modules.texturemixer;

import java.util.LinkedHashSet;
import java.util.Set;

public final class Shape {
  public static final int DOWN = 0;
  public static final int UP = 1;
  public static final int NORTH = 2;
  public static final int SOUTH = 3;
  public static final int WEST = 4;
  public static final int EAST = 5;

  public record Face(String texture, float u0, float v0, float u1, float v1, boolean tint) {}

  public record Box(float x0, float y0, float z0, float x1, float y1, float z1, Face[] faces) {}

  private final Box[] boxes;
  private final Set<String> textures;
  private final boolean cube;
  private final boolean extruded;

  public Shape(Box[] boxes) {
    this(boxes, false);
  }

  private Shape(Box[] boxes, boolean extruded) {
    this.extruded = extruded;
    this.boxes = boxes;
    Set<String> found = new LinkedHashSet<>();
    boolean full = boxes.length == 1;
    for (Box box : boxes) {
      for (Face face : box.faces()) {
        if (face != null) {
          found.add(face.texture());
        }
      }
      full &=
          box.x0() == 0 && box.y0() == 0 && box.z0() == 0 && box.x1() == 16 && box.y1() == 16
              && box.z1() == 16;
    }
    this.textures = found;
    this.cube = full;
  }

  public static Shape sprite(String texture) {
    Face edge = new Face(texture, 0, 0, 16, 16, false);
    Face back = new Face(texture, 16, 0, 0, 16, false);
    Face[] faces = {edge, edge, back, edge, edge, edge};
    return new Shape(new Box[] {new Box(0, 0, 7.5F, 16, 16, 8.5F, faces)}, true);
  }

  public Box[] boxes() {
    return boxes;
  }

  public Set<String> textures() {
    return textures;
  }

  public boolean extruded() {
    return extruded;
  }

  public boolean cube() {
    return cube;
  }

  public boolean empty() {
    return boxes.length == 0 || textures.isEmpty();
  }
}
