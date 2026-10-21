package me.cxiy.cxvisuals.modules.avatar.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class BbmodelReader {
  private static final float UNIT = 1.0F / 16.0F;
  private static final float RADIANS = (float) (Math.PI / 180.0);
  private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

  private static final class Geometry {
    final Floats positions = new Floats(256);
    final Floats uvs = new Floats(256);
    final Ints indices = new Ints(384);
    final Map<Integer, Ints> byMaterial = new HashMap<>();
  }

  private record Group(int node, float[] pivot, float[] rotation) {}

  private final JsonObject root;
  private final RawModel model = new RawModel();
  private final Map<String, JsonObject> elements = new HashMap<>();
  private final Map<String, Group> groups = new HashMap<>();
  private final Map<Integer, Geometry> geometry = new HashMap<>();
  private final Map<String, Integer> textureIds = new HashMap<>();
  private final List<float[]> textureSizes = new ArrayList<>();
  private float resolutionW = 16.0F;
  private float resolutionH = 16.0F;
  private boolean boxDefault;

  private BbmodelReader(JsonObject root) {
    this.root = root;
  }

  public static RawModel read(byte[] data) throws ImportError {
    JsonObject root;
    try {
      root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    } catch (JsonParseException | IllegalStateException e) {
      throw new ImportError(Lang.tr("This Blockbench file is damaged."));
    }
    try {
      return new BbmodelReader(root).parse();
    } catch (ClassCastException
        | IllegalStateException
        | NullPointerException
        | IndexOutOfBoundsException
        | UnsupportedOperationException
        | NumberFormatException e) {
      throw new ImportError(Lang.tr("This Blockbench file is damaged or uses something unusual."));
    }
  }

  private RawModel parse() throws ImportError {
    JsonObject meta = object(root, "meta");
    boxDefault = meta != null && meta.has("box_uv") && meta.get("box_uv").getAsBoolean();
    JsonObject resolution = object(root, "resolution");
    if (resolution != null) {
      resolutionW = Math.max(1.0F, number(resolution.get("width"), 16.0F));
      resolutionH = Math.max(1.0F, number(resolution.get("height"), 16.0F));
    }
    textures();
    for (JsonElement element : array(root, "elements")) {
      JsonObject object = element.getAsJsonObject();
      if (object.has("uuid")
          && (!object.has("visibility") || object.get("visibility").getAsBoolean())) {
        elements.put(object.get("uuid").getAsString(), object);
      }
    }
    RawModel.Node top = new RawModel.Node();
    top.name = "root";
    model.nodes.add(top);
    Group rootGroup = new Group(0, new float[3], new float[3]);
    JsonArray outliner = array(root, "outliner");
    if (outliner.isEmpty()) {
      for (JsonObject element : elements.values()) {
        place(element, rootGroup);
      }
    } else {
      for (JsonElement entry : outliner) {
        walk(entry, rootGroup);
      }
    }
    for (Map.Entry<Integer, Geometry> entry : geometry.entrySet()) {
      Geometry shape = entry.getValue();
      if (shape.byMaterial.isEmpty()) {
        continue;
      }
      float[] positions = shape.positions.toArray();
      float[] uvs = shape.uvs.toArray();
      RawModel.Mesh mesh = new RawModel.Mesh();
      for (Map.Entry<Integer, Ints> part : shape.byMaterial.entrySet()) {
        RawModel.Primitive primitive = new RawModel.Primitive();
        primitive.positions = positions;
        primitive.uvs = uvs;
        primitive.indices = part.getValue().toArray();
        primitive.material = part.getKey();
        mesh.primitives.add(primitive);
      }
      model.nodes.get(entry.getKey()).mesh = model.meshes.size();
      model.meshes.add(mesh);
    }
    if (model.triangleCount() == 0) {
      throw new ImportError(Lang.tr("This Blockbench model has no visible faces."));
    }
    animations();
    model.unitScale = UNIT;
    model.up = RawModel.Axis.Y;
    return model;
  }

  private void textures() {
    JsonArray list = array(root, "textures");
    for (int i = 0; i < list.size(); i++) {
      JsonObject texture = list.get(i).getAsJsonObject();
      RawModel.Material material = new RawModel.Material();
      material.name = texture.has("name") ? texture.get("name").getAsString() : "texture " + i;
      material.alpha = RawModel.Alpha.MASK;
      String source = texture.has("source") ? texture.get("source").getAsString() : "";
      int comma = source.indexOf(',');
      if (source.startsWith("data:") && comma > 0) {
        try {
          material.image = model.images.size();
          model.images.add(Base64.getDecoder().decode(source.substring(comma + 1)));
        } catch (IllegalArgumentException e) {
          material.image = -1;
          model.warnings.add(
              Lang.format("Texture {0} couldn't be read; using plain colours.", material.name));
        }
      } else {
        model.warnings.add(
            Lang.format(
                "Texture {0} isn't saved inside the model; using plain colours.", material.name));
      }
      float uvW =
          texture.has("uv_width") ? number(texture.get("uv_width"), resolutionW) : resolutionW;
      float uvH =
          texture.has("uv_height") ? number(texture.get("uv_height"), resolutionH) : resolutionH;
      textureSizes.add(new float[] {Math.max(1.0F, uvW), Math.max(1.0F, uvH)});
      if (texture.has("uuid")) {
        textureIds.put(texture.get("uuid").getAsString(), i);
      }
      model.materials.add(material);
    }
  }

  private void walk(JsonElement entry, Group parent) {
    if (entry.isJsonPrimitive()) {
      JsonObject element = elements.get(entry.getAsString());
      if (element != null) {
        place(element, parent);
      }
      return;
    }
    JsonObject object = entry.getAsJsonObject();
    if (object.has("visibility") && !object.get("visibility").getAsBoolean()) {
      return;
    }
    float[] pivot = vector(object, "origin", 0.0F);
    float[] rotation = vector(object, "rotation", 0.0F);
    RawModel.Node node = new RawModel.Node();
    node.name = object.has("name") ? object.get("name").getAsString() : "";
    node.parent = parent.node;
    node.t =
        new float[] {
          pivot[0] - parent.pivot[0], pivot[1] - parent.pivot[1], pivot[2] - parent.pivot[2]
        };
    node.r = euler(rotation[0], rotation[1], rotation[2]);
    int index = model.nodes.size();
    model.nodes.add(node);
    Group group = new Group(index, pivot, rotation);
    if (object.has("uuid")) {
      groups.put(object.get("uuid").getAsString(), group);
    }
    for (JsonElement child : array(object, "children")) {
      walk(child, group);
    }
  }

  private void place(JsonObject element, Group group) {
    String type = element.has("type") ? element.get("type").getAsString() : "cube";
    if (type.equals("cube")) {
      cube(element, group);
    } else if (type.equals("mesh")) {
      mesh(element, group);
    }
  }

  private void cube(JsonObject element, Group group) {
    float inflate = element.has("inflate") ? number(element.get("inflate"), 0.0F) : 0.0F;
    float[] from = vector(element, "from", 0.0F);
    float[] to = vector(element, "to", 0.0F);
    float x0 = Math.min(from[0], to[0]) - inflate;
    float y0 = Math.min(from[1], to[1]) - inflate;
    float z0 = Math.min(from[2], to[2]) - inflate;
    float x1 = Math.max(from[0], to[0]) + inflate;
    float y1 = Math.max(from[1], to[1]) + inflate;
    float z1 = Math.max(from[2], to[2]) + inflate;
    float[] origin = vector(element, "origin", 0.0F);
    float[] q = euler3(vector(element, "rotation", 0.0F));
    boolean box = element.has("box_uv") ? element.get("box_uv").getAsBoolean() : boxDefault;
    float[] offset = vector2(element, "uv_offset");
    float dx = (float) Math.floor(Math.max(from[0], to[0]) - Math.min(from[0], to[0]) + 0.0001F);
    float dy = (float) Math.floor(Math.max(from[1], to[1]) - Math.min(from[1], to[1]) + 0.0001F);
    float dz = (float) Math.floor(Math.max(from[2], to[2]) - Math.min(from[2], to[2]) + 0.0001F);
    JsonObject faces = object(element, "faces");
    if (faces == null) {
      return;
    }
    Geometry shape = geometry.computeIfAbsent(group.node, key -> new Geometry());
    for (String name : FACES) {
      JsonObject face = object(faces, name);
      if (face == null) {
        continue;
      }
      int texture = texture(face.get("texture"));
      if (texture == -2) {
        continue;
      }
      float[] corners =
          switch (name) {
            case "north" -> new float[] {x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0};
            case "south" -> new float[] {x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1};
            case "east" -> new float[] {x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1};
            case "west" -> new float[] {x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0};
            case "up" -> new float[] {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1};
            default -> new float[] {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0};
          };
      float[] uv = box ? boxUv(name, offset[0], offset[1], dx, dy, dz) : faceUv(face);
      int turn =
          face.has("rotation")
              ? Math.floorMod(Math.round(number(face.get("rotation"), 0.0F) / 90.0F), 4)
              : 0;
      float[] size =
          texture >= 0 && texture < textureSizes.size()
              ? textureSizes.get(texture)
              : new float[] {resolutionW, resolutionH};
      float[] cornerUv = {uv[0], uv[1], uv[2], uv[1], uv[2], uv[3], uv[0], uv[3]};
      int base = shape.positions.size() / 3;
      float[] point = new float[3];
      for (int c = 0; c < 4; c++) {
        point[0] = corners[c * 3] - origin[0];
        point[1] = corners[c * 3 + 1] - origin[1];
        point[2] = corners[c * 3 + 2] - origin[2];
        rotate(q, point);
        shape.positions.add(
            point[0] + origin[0] - group.pivot[0],
            point[1] + origin[1] - group.pivot[1],
            point[2] + origin[2] - group.pivot[2]);
        int from4 = (c + 4 - turn) % 4;
        shape.uvs.add(cornerUv[from4 * 2] / size[0], cornerUv[from4 * 2 + 1] / size[1]);
      }
      Ints indices = shape.byMaterial.computeIfAbsent(texture, key -> new Ints(64));
      indices.add(base, base + 3, base + 2);
      indices.add(base, base + 2, base + 1);
    }
  }

  private void mesh(JsonObject element, Group group) {
    JsonObject vertices = object(element, "vertices");
    JsonObject faces = object(element, "faces");
    if (vertices == null || faces == null) {
      return;
    }
    float[] origin = vector(element, "origin", 0.0F);
    float[] q = euler3(vector(element, "rotation", 0.0F));
    Geometry shape = geometry.computeIfAbsent(group.node, key -> new Geometry());
    float[] point = new float[3];
    for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
      JsonObject face = entry.getValue().getAsJsonObject();
      int texture = texture(face.get("texture"));
      if (texture == -2) {
        continue;
      }
      JsonArray keys = array(face, "vertices");
      if (keys.size() < 3 || keys.size() > 4) {
        continue;
      }
      JsonObject uvMap = object(face, "uv");
      float[] size =
          texture >= 0 && texture < textureSizes.size()
              ? textureSizes.get(texture)
              : new float[] {resolutionW, resolutionH};
      int base = shape.positions.size() / 3;
      for (JsonElement key : keys) {
        JsonArray position = vertices.getAsJsonArray(key.getAsString());
        point[0] = position.get(0).getAsFloat();
        point[1] = position.get(1).getAsFloat();
        point[2] = position.get(2).getAsFloat();
        rotate(q, point);
        shape.positions.add(
            point[0] + origin[0] - group.pivot[0],
            point[1] + origin[1] - group.pivot[1],
            point[2] + origin[2] - group.pivot[2]);
        JsonArray uv = uvMap == null ? null : uvMap.getAsJsonArray(key.getAsString());
        shape.uvs.add(
            uv == null ? 0.0F : uv.get(0).getAsFloat() / size[0],
            uv == null ? 0.0F : uv.get(1).getAsFloat() / size[1]);
      }
      Ints indices = shape.byMaterial.computeIfAbsent(texture, key -> new Ints(64));
      if (keys.size() == 4 && sameSide(shape.positions, base)) {
        indices.add(base, base + 1, base + 3);
        indices.add(base, base + 3, base + 2);
      } else {
        indices.add(base, base + 1, base + 2);
        if (keys.size() == 4) {
          indices.add(base, base + 2, base + 3);
        }
      }
    }
  }

  private static boolean sameSide(Floats p, int base) {
    float[] a = point(p, base);
    float[] b = point(p, base + 1);
    float[] c = point(p, base + 2);
    float[] d = point(p, base + 3);
    float[] n1 = normal(a, c, b);
    float[] n2 = normal(a, c, d);
    return n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2] > 0.0F;
  }

  private static float[] point(Floats p, int index) {
    return new float[] {p.get(index * 3), p.get(index * 3 + 1), p.get(index * 3 + 2)};
  }

  private static float[] normal(float[] a, float[] b, float[] c) {
    float ux = b[0] - a[0];
    float uy = b[1] - a[1];
    float uz = b[2] - a[2];
    float vx = c[0] - a[0];
    float vy = c[1] - a[1];
    float vz = c[2] - a[2];
    return new float[] {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
  }

  private int texture(JsonElement value) {
    if (value == null || value.isJsonNull()) {
      return -2;
    }
    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
      return value.getAsBoolean() ? -1 : -2;
    }
    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
      int index = value.getAsInt();
      return index >= 0 && index < model.materials.size() ? index : -1;
    }
    Integer index = textureIds.get(value.getAsString());
    return index == null ? -1 : index;
  }

  private static float[] faceUv(JsonObject face) {
    JsonArray uv = face.has("uv") ? face.getAsJsonArray("uv") : null;
    if (uv == null || uv.size() < 4) {
      return new float[4];
    }
    return new float[] {
      uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat()
    };
  }

  private static float[] boxUv(String face, float u, float v, float dx, float dy, float dz) {
    return switch (face) {
      case "east" -> new float[] {u, v + dz, u + dz, v + dz + dy};
      case "north" -> new float[] {u + dz, v + dz, u + dz + dx, v + dz + dy};
      case "west" -> new float[] {u + dz + dx, v + dz, u + dz + dx + dz, v + dz + dy};
      case "south" -> new float[] {u + dz + dx + dz, v + dz, u + dz + dx + dz + dx, v + dz + dy};
      case "up" -> new float[] {u + dz + dx, v + dz, u + dz, v};
      default -> new float[] {u + dz + dx + dx, v, u + dz + dx, v + dz};
    };
  }

  private void animations() {
    for (JsonElement element : array(root, "animations")) {
      JsonObject source = element.getAsJsonObject();
      RawModel.Animation animation = new RawModel.Animation();
      animation.name = source.has("name") ? source.get("name").getAsString() : "animation";
      JsonObject animators = object(source, "animators");
      if (animators == null) {
        continue;
      }
      for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
        Group group = groups.get(entry.getKey());
        if (group == null) {
          continue;
        }
        RawModel.Node node = model.nodes.get(group.node);
        List<JsonObject> keyframes = new ArrayList<>();
        for (JsonElement keyframe : array(entry.getValue().getAsJsonObject(), "keyframes")) {
          keyframes.add(keyframe.getAsJsonObject());
        }
        keyframes.sort(
            (a, b) -> Float.compare(number(a.get("time"), 0.0F), number(b.get("time"), 0.0F)));
        for (RawModel.Path path : RawModel.Path.values()) {
          String name =
              switch (path) {
                case T -> "position";
                case R -> "rotation";
                case S -> "scale";
              };
          Floats times = new Floats(8);
          Floats values = new Floats(32);
          boolean step = false;
          for (JsonObject keyframe : keyframes) {
            if (!name.equals(
                keyframe.has("channel") ? keyframe.get("channel").getAsString() : "")) {
              continue;
            }
            JsonArray points = array(keyframe, "data_points");
            if (points.isEmpty()) {
              continue;
            }
            JsonObject point = points.get(0).getAsJsonObject();
            float x = number(point.get("x"), 0.0F);
            float y = number(point.get("y"), 0.0F);
            float z = number(point.get("z"), 0.0F);
            times.add(number(keyframe.get("time"), 0.0F));
            step |=
                keyframe.has("interpolation")
                    && keyframe.get("interpolation").getAsString().equals("step");
            switch (path) {
              case T -> values.add(node.t[0] - x, node.t[1] + y, node.t[2] + z);
              case R -> {
                float[] q =
                    euler(group.rotation[0] - x, group.rotation[1] - y, group.rotation[2] + z);
                values.add(q[0], q[1], q[2]);
                values.add(q[3]);
              }
              case S ->
                  values.add(x == 0.0F ? 1.0F : x, y == 0.0F ? 1.0F : y, z == 0.0F ? 1.0F : z);
            }
          }
          if (times.size() == 0) {
            continue;
          }
          RawModel.Channel channel = new RawModel.Channel();
          channel.node = group.node;
          channel.path = path;
          channel.interp = step ? RawModel.Interp.STEP : RawModel.Interp.LINEAR;
          channel.times = times.toArray();
          channel.values = values.toArray();
          animation.channels.add(channel);
        }
      }
      if (!animation.channels.isEmpty()) {
        model.animations.add(animation);
      }
    }
  }

  private static float[] euler3(float[] degrees) {
    return euler(degrees[0], degrees[1], degrees[2]);
  }

  static float[] euler(float xDegrees, float yDegrees, float zDegrees) {
    float hx = xDegrees * RADIANS * 0.5F;
    float hy = yDegrees * RADIANS * 0.5F;
    float hz = zDegrees * RADIANS * 0.5F;
    float sx = (float) Math.sin(hx);
    float cx = (float) Math.cos(hx);
    float sy = (float) Math.sin(hy);
    float cy = (float) Math.cos(hy);
    float sz = (float) Math.sin(hz);
    float cz = (float) Math.cos(hz);
    return new float[] {
      sx * cy * cz - cx * sy * sz,
      cx * sy * cz + sx * cy * sz,
      cx * cy * sz - sx * sy * cz,
      cx * cy * cz + sx * sy * sz
    };
  }

  private static void rotate(float[] q, float[] v) {
    float x = q[0];
    float y = q[1];
    float z = q[2];
    float w = q[3];
    float tx = 2.0F * (y * v[2] - z * v[1]);
    float ty = 2.0F * (z * v[0] - x * v[2]);
    float tz = 2.0F * (x * v[1] - y * v[0]);
    float rx = v[0] + w * tx + (y * tz - z * ty);
    float ry = v[1] + w * ty + (z * tx - x * tz);
    float rz = v[2] + w * tz + (x * ty - y * tx);
    v[0] = rx;
    v[1] = ry;
    v[2] = rz;
  }

  private static float number(JsonElement element, float fallback) {
    if (element == null || !element.isJsonPrimitive()) {
      return fallback;
    }
    try {
      return Float.parseFloat(element.getAsString().strip());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static float[] vector(JsonObject object, String name, float fallback) {
    float[] out = {fallback, fallback, fallback};
    JsonArray array = array(object, name);
    for (int i = 0; i < Math.min(3, array.size()); i++) {
      out[i] = number(array.get(i), fallback);
    }
    return out;
  }

  private static float[] vector2(JsonObject object, String name) {
    float[] out = new float[2];
    JsonArray array = array(object, name);
    for (int i = 0; i < Math.min(2, array.size()); i++) {
      out[i] = number(array.get(i), 0.0F);
    }
    return out;
  }

  private static JsonArray array(JsonObject object, String name) {
    JsonElement element = object.get(name);
    return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
  }

  private static JsonObject object(JsonObject object, String name) {
    JsonElement element = object.get(name);
    return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
  }
}
