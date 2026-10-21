package me.cxiy.cxvisuals.modules.avatar.format;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class VoxReader {
  private static final int[] RAMP = {0xEE, 0xDD, 0xBB, 0xAA, 0x88, 0x77, 0x55, 0x44, 0x22, 0x11};
  private static final int[] CUBE = {0xFF, 0xCC, 0x99, 0x66, 0x33, 0x00};
  private static final int LIMIT = 256;

  private record Shape(int sx, int sy, int sz, byte[] voxels) {}

  private record Transform(int child, int[] rotation, int[] translation) {}

  private static final int[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

  private final List<Shape> shapes = new ArrayList<>();
  private final Map<Integer, Transform> transforms = new HashMap<>();
  private final Map<Integer, int[]> groups = new HashMap<>();
  private final Map<Integer, int[]> shapeNodes = new HashMap<>();
  private final int[] palette = new int[256];
  private final Floats positions = new Floats(4096);
  private final Ints colors = new Ints(2048);
  private final Ints indices = new Ints(4096);

  private VoxReader() {}

  public static RawModel read(byte[] data) throws ImportError {
    VoxReader reader = new VoxReader();
    try {
      reader.parse(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
    } catch (BufferUnderflowException | IndexOutOfBoundsException | IllegalArgumentException e) {
      throw new ImportError(Lang.tr("This VOX file is cut short or damaged."));
    }
    return reader.build();
  }

  private void parse(ByteBuffer buffer) throws ImportError {
    defaultPalette();
    buffer.position(8);
    String main = id(buffer);
    if (!main.equals("MAIN")) {
      throw new ImportError(Lang.tr("This VOX file has no MAIN chunk."));
    }
    int content = buffer.getInt();
    buffer.getInt();
    buffer.position(buffer.position() + content);
    int[] size = null;
    while (buffer.remaining() >= 12) {
      String id = id(buffer);
      int length = buffer.getInt();
      int children = buffer.getInt();
      int start = buffer.position();
      ByteBuffer body = buffer.slice(start, length).order(ByteOrder.LITTLE_ENDIAN);
      switch (id) {
        case "SIZE" -> size = new int[] {body.getInt(), body.getInt(), body.getInt()};
        case "XYZI" -> {
          if (size == null) {
            throw new ImportError(Lang.tr("This VOX file has voxels before their size."));
          }
          shapes.add(shape(size, body));
        }
        case "RGBA" -> {
          for (int i = 1; i < 256; i++) {
            int r = body.get() & 0xFF;
            int g = body.get() & 0xFF;
            int b = body.get() & 0xFF;
            int a = body.get() & 0xFF;
            palette[i] = a << 24 | r << 16 | g << 8 | b;
          }
        }
        case "nTRN" -> transform(body);
        case "nGRP" -> {
          int node = body.getInt();
          dict(body);
          int count = body.getInt();
          int[] list = new int[count];
          for (int i = 0; i < count; i++) {
            list[i] = body.getInt();
          }
          groups.put(node, list);
        }
        case "nSHP" -> {
          int node = body.getInt();
          dict(body);
          int count = body.getInt();
          int[] models = new int[count];
          for (int i = 0; i < count; i++) {
            models[i] = body.getInt();
            dict(body);
          }
          shapeNodes.put(node, models);
        }
        default -> {}
      }
      buffer.position(start + length + children);
    }
  }

  private static Shape shape(int[] size, ByteBuffer body) throws ImportError {
    int sx = size[0];
    int sy = size[1];
    int sz = size[2];
    if (sx <= 0 || sy <= 0 || sz <= 0 || sx > LIMIT || sy > LIMIT || sz > LIMIT) {
      throw new ImportError(Lang.tr("This VOX model has an unusual size."));
    }
    byte[] voxels = new byte[sx * sy * sz];
    int count = body.getInt();
    for (int i = 0; i < count; i++) {
      int x = body.get() & 0xFF;
      int y = body.get() & 0xFF;
      int z = body.get() & 0xFF;
      byte color = body.get();
      if (x < sx && y < sy && z < sz) {
        voxels[(z * sy + y) * sx + x] = color;
      }
    }
    return new Shape(sx, sy, sz, voxels);
  }

  private void transform(ByteBuffer body) {
    int node = body.getInt();
    dict(body);
    int child = body.getInt();
    body.getInt();
    body.getInt();
    int frames = body.getInt();
    int[] rotation = IDENTITY;
    int[] translation = {0, 0, 0};
    for (int f = 0; f < frames; f++) {
      Map<String, String> frame = dict(body);
      if (f > 0) {
        continue;
      }
      String t = frame.get("_t");
      if (t != null) {
        String[] parts = t.trim().split("\\s+");
        for (int i = 0; i < Math.min(3, parts.length); i++) {
          translation[i] = Integer.parseInt(parts[i]);
        }
      }
      String r = frame.get("_r");
      if (r != null) {
        rotation = rotation(Integer.parseInt(r.trim()));
      }
    }
    transforms.put(node, new Transform(child, rotation, translation));
  }

  private static int[] rotation(int bits) {
    int first = bits & 3;
    int second = (bits >> 2) & 3;
    int third = 3 - first - second;
    int[] m = new int[9];
    if (first > 2 || second > 2 || third < 0 || third > 2 || first == second) {
      return IDENTITY;
    }
    m[first] = (bits & 16) != 0 ? -1 : 1;
    m[3 + second] = (bits & 32) != 0 ? -1 : 1;
    m[6 + third] = (bits & 64) != 0 ? -1 : 1;
    return m;
  }

  private static Map<String, String> dict(ByteBuffer body) {
    int count = body.getInt();
    Map<String, String> out = new HashMap<>();
    for (int i = 0; i < count; i++) {
      String key = text(body);
      out.put(key, text(body));
    }
    return out;
  }

  private static String text(ByteBuffer body) {
    int length = body.getInt();
    byte[] bytes = new byte[length];
    body.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static String id(ByteBuffer buffer) {
    byte[] bytes = new byte[4];
    buffer.get(bytes);
    return new String(bytes, StandardCharsets.US_ASCII);
  }

  private RawModel build() throws ImportError {
    if (transforms.isEmpty()) {
      for (Shape shape : shapes) {
        mesh(shape, IDENTITY, new int[3], false);
      }
    } else {
      visit(0, IDENTITY, new int[3], 0);
    }
    if (indices.size() == 0) {
      throw new ImportError(Lang.tr("This VOX file has no voxels."));
    }
    RawModel.Primitive primitive = new RawModel.Primitive();
    primitive.positions = positions.toArray();
    primitive.colors = colors.toArray();
    primitive.indices = indices.toArray();
    return RawModel.single(primitive, RawModel.Axis.Z);
  }

  private void visit(int node, int[] rotation, int[] translation, int depth) {
    if (depth > 64) {
      return;
    }
    Transform transform = transforms.get(node);
    if (transform != null) {
      int[] combined = multiply(rotation, transform.rotation);
      int[] moved = apply(rotation, transform.translation);
      visit(
          transform.child,
          combined,
          new int[] {
            moved[0] + translation[0], moved[1] + translation[1], moved[2] + translation[2]
          },
          depth + 1);
      return;
    }
    int[] children = groups.get(node);
    if (children != null) {
      for (int child : children) {
        visit(child, rotation, translation, depth + 1);
      }
      return;
    }
    int[] models = shapeNodes.get(node);
    if (models != null) {
      for (int model : models) {
        if (model >= 0 && model < shapes.size()) {
          mesh(shapes.get(model), rotation, translation, true);
        }
      }
    }
  }

  private void mesh(Shape shape, int[] rotation, int[] translation, boolean centred) {
    int[] dims = {shape.sx, shape.sy, shape.sz};
    float[] centre =
        centred ? new float[] {shape.sx / 2, shape.sy / 2, shape.sz / 2} : new float[3];
    boolean flip = determinant(rotation) < 0;
    int[] x = new int[3];
    int[] q = new int[3];
    for (int d = 0; d < 3; d++) {
      int u = (d + 1) % 3;
      int v = (d + 2) % 3;
      int[] mask = new int[dims[u] * dims[v]];
      for (int sign = -1; sign <= 1; sign += 2) {
        q[0] = 0;
        q[1] = 0;
        q[2] = 0;
        q[d] = sign;
        for (x[d] = 0; x[d] < dims[d]; x[d]++) {
          int n = 0;
          for (x[v] = 0; x[v] < dims[v]; x[v]++) {
            for (x[u] = 0; x[u] < dims[u]; x[u]++) {
              int here = voxel(shape, x[0], x[1], x[2]);
              int there = voxel(shape, x[0] + q[0], x[1] + q[1], x[2] + q[2]);
              mask[n++] = here != 0 && there == 0 ? here : 0;
            }
          }
          n = 0;
          for (int j = 0; j < dims[v]; j++) {
            for (int i = 0; i < dims[u]; ) {
              int color = mask[n];
              if (color == 0) {
                i++;
                n++;
                continue;
              }
              int w = 1;
              while (i + w < dims[u] && mask[n + w] == color) {
                w++;
              }
              int h = 1;
              grow:
              while (j + h < dims[v]) {
                for (int k = 0; k < w; k++) {
                  if (mask[n + k + h * dims[u]] != color) {
                    break grow;
                  }
                }
                h++;
              }
              float[] base = new float[3];
              base[d] = x[d] + (sign > 0 ? 1 : 0);
              base[u] = i;
              base[v] = j;
              float[] du = new float[3];
              du[u] = w;
              float[] dv = new float[3];
              dv[v] = h;
              quad(base, du, dv, sign > 0 != flip, palette[color], rotation, translation, centre);
              for (int l = 0; l < h; l++) {
                for (int k = 0; k < w; k++) {
                  mask[n + k + l * dims[u]] = 0;
                }
              }
              i += w;
              n += w;
            }
          }
        }
      }
    }
  }

  private void quad(
      float[] base,
      float[] du,
      float[] dv,
      boolean forward,
      int color,
      int[] rotation,
      int[] translation,
      float[] centre) {
    int start = positions.size() / 3;
    float[][] corners = {
      {base[0], base[1], base[2]},
      {base[0] + du[0], base[1] + du[1], base[2] + du[2]},
      {base[0] + du[0] + dv[0], base[1] + du[1] + dv[1], base[2] + du[2] + dv[2]},
      {base[0] + dv[0], base[1] + dv[1], base[2] + dv[2]}
    };
    for (float[] corner : corners) {
      float cx = corner[0] - centre[0];
      float cy = corner[1] - centre[1];
      float cz = corner[2] - centre[2];
      positions.add(
          rotation[0] * cx + rotation[1] * cy + rotation[2] * cz + translation[0],
          rotation[3] * cx + rotation[4] * cy + rotation[5] * cz + translation[1],
          rotation[6] * cx + rotation[7] * cy + rotation[8] * cz + translation[2]);
      colors.add(color);
    }
    if (forward) {
      indices.add(start, start + 1, start + 2);
      indices.add(start, start + 2, start + 3);
    } else {
      indices.add(start, start + 2, start + 1);
      indices.add(start, start + 3, start + 2);
    }
  }

  private static int voxel(Shape shape, int x, int y, int z) {
    if (x < 0 || y < 0 || z < 0 || x >= shape.sx || y >= shape.sy || z >= shape.sz) {
      return 0;
    }
    return shape.voxels[(z * shape.sy + y) * shape.sx + x] & 0xFF;
  }

  private static int[] multiply(int[] a, int[] b) {
    int[] out = new int[9];
    for (int r = 0; r < 3; r++) {
      for (int c = 0; c < 3; c++) {
        out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
      }
    }
    return out;
  }

  private static int[] apply(int[] m, int[] v) {
    return new int[] {
      m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
      m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
      m[6] * v[0] + m[7] * v[1] + m[8] * v[2]
    };
  }

  private static int determinant(int[] m) {
    return m[0] * (m[4] * m[8] - m[5] * m[7])
        - m[1] * (m[3] * m[8] - m[5] * m[6])
        + m[2] * (m[3] * m[7] - m[4] * m[6]);
  }

  private void defaultPalette() {
    int index = 1;
    for (int r : CUBE) {
      for (int g : CUBE) {
        for (int b : CUBE) {
          if (index < 216) {
            palette[index++] = 0xFF000000 | r << 16 | g << 8 | b;
          }
        }
      }
    }
    for (int channel = 0; channel < 4; channel++) {
      for (int value : RAMP) {
        palette[index++] =
            switch (channel) {
              case 0 -> 0xFF000000 | value;
              case 1 -> 0xFF000000 | value << 8;
              case 2 -> 0xFF000000 | value << 16;
              default -> 0xFF000000 | value << 16 | value << 8 | value;
            };
      }
    }
  }
}
