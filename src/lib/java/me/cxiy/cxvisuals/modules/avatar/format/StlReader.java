package me.cxiy.cxvisuals.modules.avatar.format;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class StlReader {
  private StlReader() {}

  public static RawModel read(byte[] data) throws ImportError {
    Floats positions;
    if (binary(data)) {
      positions = binaryPositions(data);
    } else {
      positions = asciiPositions(data);
    }
    int vertices = positions.size() / 3;
    if (vertices < 3) {
      throw new ImportError(Lang.tr("This STL has no triangles."));
    }
    RawModel.Primitive primitive = new RawModel.Primitive();
    primitive.positions = positions.toArray();
    primitive.indices = new int[vertices - vertices % 3];
    for (int i = 0; i < primitive.indices.length; i++) {
      primitive.indices[i] = i;
    }
    return RawModel.single(primitive, RawModel.Axis.Z);
  }

  private static boolean binary(byte[] data) throws ImportError {
    if (data.length < 84) {
      return false;
    }
    long count = ByteBuffer.wrap(data, 80, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    if (84 + count * 50 == data.length) {
      return true;
    }
    if (startsWithSolid(data)) {
      return false;
    }
    throw new ImportError(Lang.tr("This STL file is cut short or damaged."));
  }

  private static boolean startsWithSolid(byte[] data) {
    String head =
        new String(data, 0, Math.min(data.length, 256), StandardCharsets.ISO_8859_1).stripLeading();
    return head.startsWith("solid") && head.contains("facet")
        || head.startsWith("solid") && head.contains("endsolid");
  }

  private static Floats binaryPositions(byte[] data) {
    ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    int count = buffer.getInt(80);
    Floats positions = new Floats(count * 9);
    int offset = 84;
    for (int i = 0; i < count; i++) {
      int corner = offset + 12;
      for (int k = 0; k < 9; k++) {
        positions.add(buffer.getFloat(corner + k * 4));
      }
      offset += 50;
    }
    return positions;
  }

  private static Floats asciiPositions(byte[] data) throws ImportError {
    Lines lines = new Lines(data, 0);
    Floats positions = new Floats(1024);
    while (lines.next()) {
      if (lines.token(0).equals("vertex")) {
        positions.add(lines.number(1), lines.number(2), lines.number(3));
      }
    }
    return positions;
  }
}
