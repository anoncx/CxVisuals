package me.cxiy.cxvisuals.modules.texturemixer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Shapes {
  private static final String NAMESPACE = "minecraft:";
  private static final int DEPTH = 12;
  private static final int STATES = 10;
  private static final Set<String> SKIPPED =
      Set.of(
          "facing",
          "axis",
          "rotation",
          "waterlogged",
          "distance",
          "persistent",
          "face",
          "half",
          "hinge",
          "open");

  private final Assets assets;
  private final Map<String, JsonObject> models = new HashMap<>(256);

  public Shapes(Assets assets) {
    this.assets = assets;
  }

  public List<Entry.State> states(String name) {
    List<Entry.State> out = new ArrayList<>(2);
    try {
      JsonObject state = json("blockstates/" + name + ".json");
      if (state.has("variants")) {
        Set<String> seen = new HashSet<>();
        JsonObject variants = state.getAsJsonObject("variants");
        for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
          if (out.size() >= STATES) {
            break;
          }
          Shape shape = resolve(modelOf(entry.getValue()));
          if (shape != null && !shape.empty() && seen.add(String.join("|", shape.textures()))) {
            out.add(new Entry.State(label(entry.getKey()), shape));
          }
        }
      }
      if (out.isEmpty()) {
        String model = stateModel(state);
        Shape shape = model == null ? null : resolve(model);
        if (shape != null) {
          out.add(new Entry.State("", shape));
        }
      }
    } catch (IOException | RuntimeException ignored) {
      out.clear();
    }
    return out;
  }

  private static String label(String variant) {
    StringBuilder out = new StringBuilder();
    for (String part : variant.split(",")) {
      int split = part.indexOf('=');
      if (split <= 0) {
        continue;
      }
      String key = part.substring(0, split);
      String value = part.substring(split + 1);
      if (SKIPPED.contains(key)) {
        continue;
      }
      if (!out.isEmpty()) {
        out.append(", ");
      }
      if (value.equals("true")) {
        out.append(Entry.pretty(key));
      } else if (value.equals("false")) {
        out.append("Not ").append(Entry.pretty(key).toLowerCase(java.util.Locale.ROOT));
      } else {
        out.append(Entry.pretty(key)).append(' ').append(Entry.pretty(value));
      }
    }
    return out.toString();
  }

  private static String stateModel(JsonObject state) {
    if (state.has("variants")) {
      JsonObject variants = state.getAsJsonObject("variants");
      for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
        return modelOf(entry.getValue());
      }
    }
    if (state.has("multipart")) {
      for (JsonElement part : state.getAsJsonArray("multipart")) {
        JsonObject object = part.getAsJsonObject();
        if (!object.has("when")) {
          return modelOf(object.get("apply"));
        }
      }
      JsonArray parts = state.getAsJsonArray("multipart");
      if (!parts.isEmpty()) {
        return modelOf(parts.get(0).getAsJsonObject().get("apply"));
      }
    }
    return null;
  }

  private static String modelOf(JsonElement element) {
    JsonElement pick = element.isJsonArray() ? element.getAsJsonArray().get(0) : element;
    return pick.getAsJsonObject().get("model").getAsString();
  }

  public Shape resolve(String model) throws IOException {
    Map<String, String> variables = new HashMap<>();
    JsonArray elements = null;
    String current = model;
    for (int depth = 0; depth < DEPTH && current != null; depth++) {
      JsonObject object = json("models/" + strip(current) + ".json");
      if (object.has("textures")) {
        for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("textures").entrySet()) {
          variables.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
        }
      }
      if (elements == null && object.has("elements")) {
        elements = object.getAsJsonArray("elements");
      }
      current = object.has("parent") ? object.get("parent").getAsString() : null;
    }
    if (elements == null) {
      return null;
    }
    List<Shape.Box> boxes = new ArrayList<>(elements.size());
    for (JsonElement element : elements) {
      Shape.Box box = box(element.getAsJsonObject(), variables);
      if (box != null) {
        boxes.add(box);
      }
    }
    return new Shape(boxes.toArray(new Shape.Box[0]));
  }

  private static Shape.Box box(JsonObject element, Map<String, String> variables) {
    float[] from = floats(element.getAsJsonArray("from"));
    float[] to = floats(element.getAsJsonArray("to"));
    if (element.has("rotation")) {
      JsonObject rotation = element.getAsJsonObject("rotation");
      float angle = rotation.has("angle") ? rotation.get("angle").getAsFloat() : 0.0F;
      if (angle != 0.0F && Math.abs(angle) != 90.0F && Math.abs(angle) != 180.0F) {
        return null;
      }
    }
    JsonObject faces = element.getAsJsonObject("faces");
    Shape.Face[] result = new Shape.Face[6];
    String[] names = {"down", "up", "north", "south", "west", "east"};
    boolean any = false;
    for (int i = 0; i < names.length; i++) {
      if (!faces.has(names[i])) {
        continue;
      }
      JsonObject face = faces.getAsJsonObject(names[i]);
      String texture = lookup(face.get("texture").getAsString(), variables);
      if (texture == null) {
        continue;
      }
      float[] uv = face.has("uv") ? floats(face.getAsJsonArray("uv")) : defaults(i, from, to);
      result[i] = new Shape.Face(texture, uv[0], uv[1], uv[2], uv[3], face.has("tintindex"));
      any = true;
    }
    return any
        ? new Shape.Box(from[0], from[1], from[2], to[0], to[1], to[2], result)
        : null;
  }

  private static float[] defaults(int face, float[] a, float[] b) {
    return switch (face) {
      case Shape.DOWN -> new float[] {a[0], 16 - b[2], b[0], 16 - a[2]};
      case Shape.UP -> new float[] {a[0], a[2], b[0], b[2]};
      case Shape.NORTH -> new float[] {16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1]};
      case Shape.SOUTH -> new float[] {a[0], 16 - b[1], b[0], 16 - a[1]};
      case Shape.WEST -> new float[] {a[2], 16 - b[1], b[2], 16 - a[1]};
      default -> new float[] {16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1]};
    };
  }

  private static String lookup(String reference, Map<String, String> variables) {
    String value = reference;
    for (int i = 0; i < DEPTH && value.startsWith("#"); i++) {
      value = variables.get(value.substring(1));
      if (value == null) {
        return null;
      }
    }
    return value.startsWith("#") ? null : strip(value);
  }

  private static String strip(String id) {
    return id.startsWith(NAMESPACE) ? id.substring(NAMESPACE.length()) : id;
  }

  private static float[] floats(JsonArray array) {
    float[] values = new float[array.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = array.get(i).getAsFloat();
    }
    return values;
  }

  private JsonObject json(String path) throws IOException {
    JsonObject cached = models.get(path);
    if (cached != null) {
      return cached;
    }
    JsonObject parsed =
        JsonParser.parseString(new String(assets.read(path), StandardCharsets.UTF_8))
            .getAsJsonObject();
    models.put(path, parsed);
    return parsed;
  }
}
