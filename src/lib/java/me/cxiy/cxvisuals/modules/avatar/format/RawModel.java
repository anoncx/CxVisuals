package me.cxiy.cxvisuals.modules.avatar.format;

import java.util.ArrayList;
import java.util.List;

public final class RawModel {
  public enum Axis {
    Y,
    Z
  }

  public enum Alpha {
    OPAQUE,
    MASK,
    BLEND
  }

  public enum Path {
    T,
    R,
    S
  }

  public enum Interp {
    STEP,
    LINEAR,
    CUBIC
  }

  public static final class Node {
    public String name = "";
    public int parent = -1;
    public float[] t = {0.0F, 0.0F, 0.0F};
    public float[] r = {0.0F, 0.0F, 0.0F, 1.0F};
    public float[] s = {1.0F, 1.0F, 1.0F};
    public float[] matrix;
    public int mesh = -1;
    public int skin = -1;
  }

  public static final class Primitive {
    public float[] positions;
    public float[] normals;
    public float[] uvs;
    public int[] colors;
    public int[] joints;
    public float[] weights;
    public int[] indices;
    public int material = -1;

    public int vertexCount() {
      return positions.length / 3;
    }
  }

  public static final class Mesh {
    public final List<Primitive> primitives = new ArrayList<>(1);
  }

  public static final class Material {
    public String name = "";
    public int color = 0xFFFFFFFF;
    public int image = -1;
    public Alpha alpha = Alpha.OPAQUE;
    public boolean doubleSided;
  }

  public static final class Skin {
    public int[] joints;
    public float[] inverseBind;
  }

  public static final class Channel {
    public int node;
    public Path path;
    public Interp interp = Interp.LINEAR;
    public float[] times;
    public float[] values;
  }

  public static final class Animation {
    public String name = "";
    public final List<Channel> channels = new ArrayList<>();
  }

  public final List<Node> nodes = new ArrayList<>();
  public final List<Mesh> meshes = new ArrayList<>();
  public final List<Material> materials = new ArrayList<>();
  public final List<byte[]> images = new ArrayList<>();
  public final List<Skin> skins = new ArrayList<>();
  public final List<Animation> animations = new ArrayList<>();
  public final List<String> warnings = new ArrayList<>(1);
  public Axis up = Axis.Y;
  public float unitScale = 1.0F;
  public String format = "";

  public static RawModel single(Primitive primitive, Axis up) {
    RawModel model = new RawModel();
    Mesh mesh = new Mesh();
    mesh.primitives.add(primitive);
    model.meshes.add(mesh);
    Node node = new Node();
    node.mesh = 0;
    model.nodes.add(node);
    model.up = up;
    return model;
  }

  public int triangleCount() {
    int total = 0;
    for (Mesh mesh : meshes) {
      for (Primitive primitive : mesh.primitives) {
        total += primitive.indices.length / 3;
      }
    }
    return total;
  }
}
