package me.cxiy.cxvisuals.modules.avatar.format;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class PlyReader {
  private enum Encoding {
    ASCII,
    LITTLE,
    BIG
  }

  private enum Type {
    INT8(1, false),
    UINT8(1, false),
    INT16(2, false),
    UINT16(2, false),
    INT32(4, false),
    UINT32(4, false),
    FLOAT32(4, true),
    FLOAT64(8, true);

    final int size;
    final boolean real;

    Type(int size, boolean real) {
      this.size = size;
      this.real = real;
    }

    static Type of(String name) throws ImportError {
      return switch (name.toLowerCase(Locale.ROOT)) {
        case "char", "int8" -> INT8;
        case "uchar", "uint8" -> UINT8;
        case "short", "int16" -> INT16;
        case "ushort", "uint16" -> UINT16;
        case "int", "int32" -> INT32;
        case "uint", "uint32" -> UINT32;
        case "float", "float32" -> FLOAT32;
        case "double", "float64" -> FLOAT64;
        default ->
            throw new ImportError(Lang.format("This PLY uses an unknown type \"{0}\".", name));
      };
    }
  }

  private record Property(String name, Type type, Type countType) {
    boolean list() {
      return countType != null;
    }
  }

  private static final class Element {
    final String name;
    final int count;
    final List<Property> properties = new ArrayList<>();

    Element(String name, int count) {
      this.name = name;
      this.count = count;
    }

    int index(String... names) {
      for (int i = 0; i < properties.size(); i++) {
        for (String candidate : names) {
          if (properties.get(i).name.equals(candidate)) {
            return i;
          }
        }
      }
      return -1;
    }
  }

  private interface Source {
    double scalar(Type type) throws ImportError;

    void endRow() throws ImportError;
  }

  private PlyReader() {}

  public static RawModel read(byte[] data) throws ImportError {
    int headerEnd = headerEnd(data);
    Lines header = new Lines(new String(data, 0, headerEnd, StandardCharsets.ISO_8859_1));
    Encoding encoding = null;
    List<Element> elements = new ArrayList<>();
    while (header.next()) {
      switch (header.token(0)) {
        case "format" ->
            encoding =
                switch (header.count() > 1 ? header.token(1) : "") {
                  case "ascii" -> Encoding.ASCII;
                  case "binary_little_endian" -> Encoding.LITTLE;
                  case "binary_big_endian" -> Encoding.BIG;
                  default -> throw new ImportError(Lang.tr("This PLY has an unknown format line."));
                };
        case "element" -> elements.add(new Element(header.token(1), (int) header.number(2)));
        case "property" -> {
          if (elements.isEmpty()) {
            throw new ImportError(Lang.tr("This PLY lists a property before any element."));
          }
          Element element = elements.get(elements.size() - 1);
          if (header.token(1).equals("list")) {
            element.properties.add(
                new Property(header.token(4), Type.of(header.token(3)), Type.of(header.token(2))));
          } else {
            element.properties.add(new Property(header.token(2), Type.of(header.token(1)), null));
          }
        }
        default -> {}
      }
    }
    if (encoding == null) {
      throw new ImportError(Lang.tr("This PLY has no format line."));
    }
    Source source = source(data, headerEnd, encoding);

    Floats positions = null;
    Floats normals = null;
    Floats uvs = null;
    int[] colors = null;
    Ints indices = new Ints(1024);
    int vertexCount = 0;
    for (Element element : elements) {
      if (element.name.equals("vertex")) {
        vertexCount = element.count;
        int x = element.index("x");
        int y = element.index("y");
        int z = element.index("z");
        if (x < 0 || y < 0 || z < 0) {
          throw new ImportError(Lang.tr("This PLY's vertices have no x, y, z."));
        }
        int nx = element.index("nx");
        int ny = element.index("ny");
        int nz = element.index("nz");
        int u = element.index("s", "u", "texture_u", "texture_s");
        int v = element.index("t", "v", "texture_v", "texture_t");
        int red = element.index("red", "r", "diffuse_red");
        int green = element.index("green", "g", "diffuse_green");
        int blue = element.index("blue", "b", "diffuse_blue");
        int alpha = element.index("alpha", "a");
        positions = new Floats(element.count * 3);
        normals = nx >= 0 && ny >= 0 && nz >= 0 ? new Floats(element.count * 3) : null;
        uvs = u >= 0 && v >= 0 ? new Floats(element.count * 2) : null;
        colors = red >= 0 && green >= 0 && blue >= 0 ? new int[element.count] : null;
        double[] row = new double[element.properties.size()];
        for (int i = 0; i < element.count; i++) {
          readRow(source, element, row, null);
          positions.add((float) row[x], (float) row[y], (float) row[z]);
          if (normals != null) {
            normals.add((float) row[nx], (float) row[ny], (float) row[nz]);
          }
          if (uvs != null) {
            uvs.add((float) row[u], 1.0F - (float) row[v]);
          }
          if (colors != null) {
            boolean real = element.properties.get(red).type.real;
            int a = alpha < 0 ? 255 : channel(row[alpha], real);
            colors[i] =
                a << 24
                    | channel(row[red], real) << 16
                    | channel(row[green], real) << 8
                    | channel(row[blue], real);
          }
        }
      } else if (element.name.equals("face")) {
        int list = element.index("vertex_indices", "vertex_index");
        double[] row = new double[element.properties.size()];
        Ints corners = new Ints(8);
        for (int i = 0; i < element.count; i++) {
          readRow(source, element, row, list < 0 ? null : corners);
          for (int k = 1; k + 1 < corners.size(); k++) {
            indices.add(corners.get(0), corners.get(k), corners.get(k + 1));
          }
        }
      } else {
        double[] row = new double[element.properties.size()];
        for (int i = 0; i < element.count; i++) {
          readRow(source, element, row, null);
        }
      }
    }
    if (positions == null || indices.size() == 0) {
      throw new ImportError(Lang.tr("This PLY has no faces. Point clouds can't be worn."));
    }
    for (int i = 0; i < indices.size(); i++) {
      int index = indices.get(i);
      if (index < 0 || index >= vertexCount) {
        throw new ImportError(Lang.tr("This PLY has a face pointing at a missing vertex."));
      }
    }
    RawModel.Primitive primitive = new RawModel.Primitive();
    primitive.positions = positions.toArray();
    primitive.normals = normals == null ? null : normals.toArray();
    primitive.uvs = uvs == null ? null : uvs.toArray();
    primitive.colors = colors;
    primitive.indices = indices.toArray();
    return RawModel.single(primitive, RawModel.Axis.Z);
  }

  private static int channel(double value, boolean real) {
    double scaled = real ? value * 255.0 : value;
    return (int) Math.max(0.0, Math.min(255.0, scaled));
  }

  private static void readRow(Source source, Element element, double[] row, Ints corners)
      throws ImportError {
    if (corners != null) {
      corners.clear();
    }
    for (int p = 0; p < element.properties.size(); p++) {
      Property property = element.properties.get(p);
      if (property.list()) {
        int count = (int) source.scalar(property.countType);
        boolean keep =
            corners != null
                && (property.name.equals("vertex_indices") || property.name.equals("vertex_index"));
        for (int k = 0; k < count; k++) {
          double value = source.scalar(property.type);
          if (keep) {
            corners.add((int) value);
          }
        }
      } else {
        row[p] = source.scalar(property.type);
      }
    }
    source.endRow();
  }

  private static int headerEnd(byte[] data) throws ImportError {
    byte[] marker = "end_header".getBytes(StandardCharsets.ISO_8859_1);
    outer:
    for (int i = 0; i + marker.length <= data.length; i++) {
      for (int k = 0; k < marker.length; k++) {
        if (data[i + k] != marker[k]) {
          continue outer;
        }
      }
      int end = i + marker.length;
      while (end < data.length && data[end] != '\n') {
        end++;
      }
      return Math.min(data.length, end + 1);
    }
    throw new ImportError(Lang.tr("This PLY has no end_header line."));
  }

  private static Source source(byte[] data, int offset, Encoding encoding) {
    if (encoding == Encoding.ASCII) {
      Lines lines = new Lines(data, offset);
      return new Source() {
        private int token;
        private boolean loaded;

        @Override
        public double scalar(Type type) throws ImportError {
          while (!loaded || token >= lines.count()) {
            if (!lines.next()) {
              throw new ImportError(Lang.tr("This PLY ends early."));
            }
            loaded = true;
            token = 0;
          }
          return lines.number(token++);
        }

        @Override
        public void endRow() {
          loaded = false;
        }
      };
    }
    ByteBuffer buffer =
        ByteBuffer.wrap(data)
            .order(encoding == Encoding.LITTLE ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    buffer.position(offset);
    return new Source() {
      @Override
      public double scalar(Type type) throws ImportError {
        if (buffer.remaining() < type.size) {
          throw new ImportError(Lang.tr("This PLY ends early."));
        }
        return switch (type) {
          case INT8 -> buffer.get();
          case UINT8 -> buffer.get() & 0xFF;
          case INT16 -> buffer.getShort();
          case UINT16 -> buffer.getShort() & 0xFFFF;
          case INT32 -> buffer.getInt();
          case UINT32 -> buffer.getInt() & 0xFFFFFFFFL;
          case FLOAT32 -> buffer.getFloat();
          case FLOAT64 -> buffer.getDouble();
        };
      }

      @Override
      public void endRow() {}
    };
  }
}
