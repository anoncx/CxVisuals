package me.cxiy.cxvisuals.modules.avatar.format;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class ModelFormats {
  public enum Format {
    GLB("GLB"),
    GLTF("GLTF"),
    OBJ("OBJ"),
    STL("STL"),
    PLY("PLY"),
    BBMODEL("BBMODEL"),
    VOX("VOX");

    public final String label;

    Format(String label) {
      this.label = label;
    }
  }

  public static final List<String> EXTENSIONS =
      List.of("glb", "gltf", "obj", "stl", "ply", "bbmodel", "vox");

  private static final String SUPPORTED =
      Lang.key("Use GLB, glTF, OBJ, STL, PLY, Blockbench or VOX.");
  private static final int SNIFF = 4096;

  private ModelFormats() {}

  public static RawModel read(String fileName, byte[] data, SideFiles side) throws ImportError {
    Format format = detect(fileName, data);
    RawModel model =
        switch (format) {
          case GLB, GLTF -> GltfReader.read(data, side);
          case OBJ -> ObjReader.read(data, side);
          case STL -> StlReader.read(data);
          case PLY -> PlyReader.read(data);
          case BBMODEL -> BbmodelReader.read(data);
          case VOX -> VoxReader.read(data);
        };
    model.format = format.label;
    return model;
  }

  public static String extension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  public static Format detect(String fileName, byte[] data) throws ImportError {
    if (starts(data, "glTF")) {
      return Format.GLB;
    }
    if (starts(data, "VOX ")) {
      return Format.VOX;
    }
    if (starts(data, "ply\n") || starts(data, "ply\r")) {
      return Format.PLY;
    }
    if (starts(data, "Kaydara FBX")) {
      throw new ImportError(Lang.tr("FBX isn't supported. Export as GLB."));
    }
    if (data.length >= 84) {
      long count =
          (data[80] & 0xFFL)
              | (data[81] & 0xFFL) << 8
              | (data[82] & 0xFFL) << 16
              | (data[83] & 0xFFL) << 24;
      if (84 + count * 50 == data.length) {
        return Format.STL;
      }
    }
    int first = firstNonSpace(data);
    if (first >= 0 && data[first] == '{') {
      String head =
          new String(data, first, Math.min(SNIFF, data.length - first), StandardCharsets.UTF_8);
      if (head.contains("\"asset\"")) {
        return Format.GLTF;
      }
      if (head.contains("\"meta\"") || head.contains("\"format_version\"")) {
        return Format.BBMODEL;
      }
    }
    String extension = extension(fileName);
    return switch (extension) {
      case "glb" -> Format.GLB;
      case "gltf" -> Format.GLTF;
      case "obj" -> Format.OBJ;
      case "stl" -> Format.STL;
      case "ply" -> Format.PLY;
      case "bbmodel" -> Format.BBMODEL;
      case "vox" -> Format.VOX;
      case "fbx" -> throw new ImportError(Lang.tr("FBX isn't supported. Export as GLB."));
      default ->
          throw new ImportError(
              Lang.format(
                      "{0} isn't a supported model.",
                      extension.isEmpty()
                          ? Lang.tr("This file")
                          : extension.toUpperCase(Locale.ROOT))
                  + " "
                  + Lang.tr(SUPPORTED));
    };
  }

  private static boolean starts(byte[] data, String magic) {
    if (data.length < magic.length()) {
      return false;
    }
    for (int i = 0; i < magic.length(); i++) {
      if (data[i] != magic.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private static int firstNonSpace(byte[] data) {
    int limit = Math.min(data.length, SNIFF);
    for (int i = 0; i < limit; i++) {
      byte b = data[i];
      if (b != ' '
          && b != '\t'
          && b != '\r'
          && b != '\n'
          && b != (byte) 0xEF
          && b != (byte) 0xBB
          && b != (byte) 0xBF) {
        return i;
      }
    }
    return -1;
  }
}
