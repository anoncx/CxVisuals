package me.cxiy.cxvisuals.modules.avatar.format;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class ObjReader {
  private static final int INDEX_LIMIT = 1 << 21;

  private static final class Builder {
    final Floats positions = new Floats(1024);
    final Floats uvs = new Floats(1024);
    final Floats normals = new Floats(1024);
    final Ints colors = new Ints(1024);
    final Ints indices = new Ints(2048);
    final HashMap<Long, Integer> welded = new HashMap<>();
    boolean anyUv;
    boolean anyNormal;
    boolean anyColor;
  }

  private ObjReader() {}

  public static RawModel read(byte[] data, SideFiles side) throws ImportError {
    RawModel model = new RawModel();
    Floats positions = new Floats(4096);
    Ints vertexColors = new Ints(4096);
    boolean coloured = false;
    Floats texcoords = new Floats(4096);
    Floats normals = new Floats(4096);
    Map<String, Integer> materials = new HashMap<>();
    Map<String, Integer> images = new HashMap<>();
    Map<Integer, Builder> builders = new LinkedHashMap<>();
    int material = -1;
    int[] corner = new int[3];
    int[] fan = new int[64];

    Lines lines = new Lines(data, 0);
    while (lines.next()) {
      switch (lines.token(0)) {
        case "v" -> {
          positions.add(lines.number(1), lines.number(2), lines.number(3));
          if (lines.count() >= 7) {
            coloured = true;
            vertexColors.add(
                0xFF000000
                    | channel(lines.number(4)) << 16
                    | channel(lines.number(5)) << 8
                    | channel(lines.number(6)));
          } else {
            vertexColors.add(0xFFFFFFFF);
          }
        }
        case "vt" ->
            texcoords.add(lines.number(1), lines.count() > 2 ? 1.0F - lines.number(2) : 1.0F);
        case "vn" -> normals.add(lines.number(1), lines.number(2), lines.number(3));
        case "mtllib" -> library(lines.rest(1), side, model, materials, images);
        case "usemtl" -> {
          String name = lines.rest(1);
          Integer index = materials.get(name);
          if (index == null) {
            index = model.materials.size();
            RawModel.Material fallback = new RawModel.Material();
            fallback.name = name;
            model.materials.add(fallback);
            materials.put(name, index);
          }
          material = index;
        }
        case "f" -> {
          int count = Math.min(lines.count() - 1, fan.length);
          if (count < 3) {
            continue;
          }
          Builder builder = builders.computeIfAbsent(material, key -> new Builder());
          for (int k = 0; k < count; k++) {
            parseCorner(
                lines.token(k + 1),
                positions.size() / 3,
                texcoords.size() / 2,
                normals.size() / 3,
                corner);
            fan[k] = vertex(builder, corner, positions, vertexColors, texcoords, normals);
          }
          builder.anyColor |= coloured;
          for (int k = 1; k + 1 < count; k++) {
            builder.indices.add(fan[0], fan[k], fan[k + 1]);
          }
        }
        default -> {}
      }
    }
    if (builders.isEmpty()) {
      throw new ImportError(Lang.tr("This OBJ has no faces."));
    }
    RawModel.Mesh mesh = new RawModel.Mesh();
    for (Map.Entry<Integer, Builder> entry : builders.entrySet()) {
      Builder builder = entry.getValue();
      RawModel.Primitive primitive = new RawModel.Primitive();
      primitive.positions = builder.positions.toArray();
      primitive.uvs = builder.anyUv ? builder.uvs.toArray() : null;
      primitive.normals = builder.anyNormal ? builder.normals.toArray() : null;
      primitive.colors = builder.anyColor ? builder.colors.toArray() : null;
      primitive.indices = builder.indices.toArray();
      primitive.material = entry.getKey();
      mesh.primitives.add(primitive);
    }
    model.meshes.add(mesh);
    RawModel.Node node = new RawModel.Node();
    node.mesh = 0;
    model.nodes.add(node);
    return model;
  }

  private static int vertex(
      Builder builder,
      int[] corner,
      Floats positions,
      Ints colors,
      Floats texcoords,
      Floats normals) {
    long key = (long) corner[0] << 42 | (long) (corner[1] + 1) << 21 | (corner[2] + 1);
    Integer existing = builder.welded.get(key);
    if (existing != null) {
      return existing;
    }
    int index = builder.positions.size() / 3;
    int p = corner[0] * 3;
    builder.positions.add(positions.get(p), positions.get(p + 1), positions.get(p + 2));
    builder.colors.add(colors.get(corner[0]));
    if (corner[1] >= 0) {
      builder.uvs.add(texcoords.get(corner[1] * 2), texcoords.get(corner[1] * 2 + 1));
      builder.anyUv = true;
    } else {
      builder.uvs.add(0.0F, 0.0F);
    }
    if (corner[2] >= 0) {
      int n = corner[2] * 3;
      builder.normals.add(normals.get(n), normals.get(n + 1), normals.get(n + 2));
      builder.anyNormal = true;
    } else {
      builder.normals.add(0.0F, 0.0F, 0.0F);
    }
    builder.welded.put(key, index);
    return index;
  }

  private static void parseCorner(
      String token, int positionCount, int uvCount, int normalCount, int[] out) throws ImportError {
    int first = token.indexOf('/');
    int second = first < 0 ? -1 : token.indexOf('/', first + 1);
    out[0] = resolve(first < 0 ? token : token.substring(0, first), positionCount, false);
    out[1] =
        first < 0
            ? -1
            : resolve(
                second < 0 ? token.substring(first + 1) : token.substring(first + 1, second),
                uvCount,
                true);
    out[2] = second < 0 ? -1 : resolve(token.substring(second + 1), normalCount, true);
  }

  private static int resolve(String text, int count, boolean optional) throws ImportError {
    if (text.isEmpty()) {
      if (optional) {
        return -1;
      }
      throw new ImportError(Lang.tr("This OBJ has a face without a vertex."));
    }
    int value;
    try {
      value = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw new ImportError(Lang.format("This OBJ has a broken face \"{0}\".", text));
    }
    int index = value < 0 ? count + value : value - 1;
    if (index < 0 || index >= count) {
      throw new ImportError(Lang.tr("This OBJ has a face pointing at a missing vertex."));
    }
    if (index >= INDEX_LIMIT) {
      throw new ImportError(Lang.tr("This OBJ is too large."));
    }
    return index;
  }

  private static void library(
      String path,
      SideFiles side,
      RawModel model,
      Map<String, Integer> materials,
      Map<String, Integer> images) {
    byte[] data;
    try {
      data = side.read(path);
    } catch (IOException e) {
      model.warnings.add(Lang.format("Material file {0} is missing; using plain colours.", path));
      return;
    }
    Lines lines = new Lines(data, 0);
    RawModel.Material current = null;
    float alpha = 1.0F;
    int rgb = 0xFFFFFF;
    try {
      while (lines.next()) {
        switch (lines.token(0)) {
          case "newmtl" -> {
            finish(current, rgb, alpha);
            current = new RawModel.Material();
            current.name = lines.rest(1);
            alpha = 1.0F;
            rgb = 0xFFFFFF;
            Integer existing = materials.get(current.name);
            if (existing != null) {
              model.materials.set(existing, current);
            } else {
              materials.put(current.name, model.materials.size());
              model.materials.add(current);
            }
          }
          case "Kd" ->
              rgb =
                  channel(lines.number(1)) << 16
                      | channel(lines.number(2)) << 8
                      | channel(lines.number(3));
          case "d" -> alpha = lines.number(1);
          case "Tr" -> alpha = 1.0F - lines.number(1);
          case "map_Kd" -> {
            if (current != null) {
              current.image = image(lines, side, model, images);
            }
          }
          default -> {}
        }
      }
    } catch (ImportError e) {
      model.warnings.add(Lang.format("Material file {0} is damaged; using plain colours.", path));
    }
    finish(current, rgb, alpha);
  }

  private static void finish(RawModel.Material material, int rgb, float alpha) {
    if (material == null) {
      return;
    }
    material.color = channel(alpha) << 24 | rgb;
    material.alpha = alpha < 0.999F ? RawModel.Alpha.BLEND : RawModel.Alpha.OPAQUE;
  }

  private static int image(
      Lines lines, SideFiles side, RawModel model, Map<String, Integer> images) {
    String whole = lines.rest(1);
    String last = lines.token(lines.count() - 1);
    for (String candidate : new String[] {whole, last}) {
      String path = candidate.replace('\\', '/');
      Integer known = images.get(path);
      if (known != null) {
        return known;
      }
      try {
        byte[] bytes = side.read(path);
        int index = model.images.size();
        model.images.add(bytes);
        images.put(path, index);
        return index;
      } catch (IOException e) {
        continue;
      }
    }
    model.warnings.add(Lang.format("Texture {0} is missing; using plain colours.", last));
    return -1;
  }

  private static int channel(float value) {
    return (int) (Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
  }
}
