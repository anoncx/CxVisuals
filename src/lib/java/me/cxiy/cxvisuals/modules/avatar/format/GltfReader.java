package me.cxiy.cxvisuals.modules.avatar.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class GltfReader {
  private static final int MAGIC = 0x46546C67;
  private static final int JSON_CHUNK = 0x4E4F534A;
  private static final int BIN_CHUNK = 0x004E4942;

  private record View(ByteBuffer data, int stride) {}

  private final JsonObject root;
  private final SideFiles side;
  private final RawModel model = new RawModel();
  private final List<ByteBuffer> buffers = new ArrayList<>();
  private final Map<Integer, Integer> imageSlots = new HashMap<>();

  private GltfReader(JsonObject root, SideFiles side) {
    this.root = root;
    this.side = side;
  }

  public static RawModel read(byte[] data, SideFiles side) throws ImportError {
    ByteBuffer glbBin = null;
    String json;
    ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    if (data.length >= 12 && buffer.getInt(0) == MAGIC) {
      if (buffer.getInt(4) != 2) {
        throw new ImportError(Lang.tr("Only glTF 2.0 files are supported."));
      }
      int offset = 12;
      json = null;
      while (offset + 8 <= data.length) {
        int length = buffer.getInt(offset);
        int type = buffer.getInt(offset + 4);
        int start = offset + 8;
        if (length < 0 || start + length > data.length) {
          throw new ImportError(Lang.tr("This GLB file is cut short or damaged."));
        }
        if (type == JSON_CHUNK && json == null) {
          json = new String(data, start, length, StandardCharsets.UTF_8);
        } else if (type == BIN_CHUNK && glbBin == null) {
          glbBin = ByteBuffer.wrap(data, start, length).slice().order(ByteOrder.LITTLE_ENDIAN);
        }
        offset = start + ((length + 3) & ~3);
      }
      if (json == null) {
        throw new ImportError(Lang.tr("This GLB file has no scene data."));
      }
    } else {
      json = new String(data, StandardCharsets.UTF_8);
    }
    JsonObject root;
    try {
      root = JsonParser.parseString(json).getAsJsonObject();
    } catch (JsonParseException | IllegalStateException e) {
      throw new ImportError(Lang.tr("This glTF file's scene data is damaged."));
    }
    GltfReader reader = new GltfReader(root, side);
    try {
      return reader.parse(glbBin);
    } catch (ClassCastException
        | IllegalStateException
        | NullPointerException
        | IndexOutOfBoundsException
        | UnsupportedOperationException
        | NumberFormatException e) {
      throw new ImportError(Lang.tr("This glTF file is damaged or uses something unusual."));
    }
  }

  private RawModel parse(ByteBuffer glbBin) throws ImportError {
    for (JsonElement extension : array(root, "extensionsRequired")) {
      String name = extension.getAsString();
      if (name.contains("draco") || name.contains("meshopt")) {
        throw new ImportError(
            Lang.tr("This glTF uses mesh compression. Export it without compression."));
      }
    }
    JsonArray bufferList = array(root, "buffers");
    for (int i = 0; i < bufferList.size(); i++) {
      JsonObject buffer = bufferList.get(i).getAsJsonObject();
      if (!buffer.has("uri")) {
        if (glbBin == null) {
          throw new ImportError(Lang.tr("This glTF points at binary data that isn't there."));
        }
        buffers.add(glbBin);
      } else {
        buffers.add(
            ByteBuffer.wrap(resource(buffer.get("uri").getAsString(), true))
                .order(ByteOrder.LITTLE_ENDIAN));
      }
    }
    materials();
    meshes();
    nodes();
    skins();
    animations();
    if (model.triangleCount() == 0) {
      throw new ImportError(Lang.tr("This glTF has no triangles."));
    }
    return model;
  }

  private byte[] resource(String uri, boolean required) throws ImportError {
    if (uri.startsWith("data:")) {
      int comma = uri.indexOf(',');
      if (comma < 0 || !uri.substring(0, comma).endsWith(";base64")) {
        throw new ImportError(Lang.tr("This glTF has an embedded file that can't be read."));
      }
      try {
        return Base64.getDecoder().decode(uri.substring(comma + 1));
      } catch (IllegalArgumentException e) {
        throw new ImportError(Lang.tr("This glTF has an embedded file that can't be read."));
      }
    }
    String path = URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
    try {
      return side.read(path);
    } catch (IOException e) {
      if (required) {
        throw new ImportError(
            Lang.format(
                "The file {0} that belongs to this glTF is missing. Keep it next to the .gltf file.",
                path));
      }
      return null;
    }
  }

  private void materials() {
    JsonArray textures = array(root, "textures");
    JsonArray images = array(root, "images");
    for (JsonElement element : array(root, "materials")) {
      JsonObject source = element.getAsJsonObject();
      RawModel.Material material = new RawModel.Material();
      material.name = string(source, "name");
      float[] factor = {1.0F, 1.0F, 1.0F, 1.0F};
      JsonObject texture = null;
      JsonObject pbr = object(source, "pbrMetallicRoughness");
      if (pbr != null) {
        floats(pbr, "baseColorFactor", factor);
        texture = object(pbr, "baseColorTexture");
      }
      JsonObject extensions = object(source, "extensions");
      JsonObject gloss =
          extensions == null ? null : object(extensions, "KHR_materials_pbrSpecularGlossiness");
      if (gloss != null) {
        floats(gloss, "diffuseFactor", factor);
        if (texture == null) {
          texture = object(gloss, "diffuseTexture");
        }
      }
      material.color =
          channel(factor[3]) << 24
              | channel(factor[0]) << 16
              | channel(factor[1]) << 8
              | channel(factor[2]);
      material.alpha =
          switch (string(source, "alphaMode")) {
            case "MASK" -> RawModel.Alpha.MASK;
            case "BLEND" -> RawModel.Alpha.BLEND;
            default -> RawModel.Alpha.OPAQUE;
          };
      material.doubleSided = source.has("doubleSided") && source.get("doubleSided").getAsBoolean();
      if (texture != null && texture.has("index")) {
        int textureIndex = texture.get("index").getAsInt();
        if (textureIndex >= 0 && textureIndex < textures.size()) {
          JsonObject textureJson = textures.get(textureIndex).getAsJsonObject();
          int imageIndex = textureJson.has("source") ? textureJson.get("source").getAsInt() : -1;
          if (imageIndex < 0) {
            JsonObject textureExtensions = object(textureJson, "extensions");
            JsonObject webp =
                textureExtensions == null ? null : object(textureExtensions, "EXT_texture_webp");
            if (webp != null && webp.has("source")) {
              model.warnings.add(Lang.tr("WebP textures aren't supported; using plain colours."));
            }
          }
          if (imageIndex >= 0 && imageIndex < images.size()) {
            material.image = image(imageIndex, images.get(imageIndex).getAsJsonObject());
          }
        }
      }
      model.materials.add(material);
    }
  }

  private int image(int index, JsonObject image) {
    Integer known = imageSlots.get(index);
    if (known != null) {
      return known;
    }
    byte[] bytes = null;
    String label = Lang.tr("an embedded image");
    try {
      if (image.has("bufferView")) {
        View view = view(image.get("bufferView").getAsInt());
        bytes = new byte[view.data.remaining()];
        view.data.duplicate().get(bytes);
      } else if (image.has("uri")) {
        String uri = image.get("uri").getAsString();
        label =
            uri.startsWith("data:")
                ? label
                : URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
        bytes = resource(uri, false);
      }
    } catch (ImportError e) {
      bytes = null;
    }
    if (bytes == null) {
      model.warnings.add(Lang.format("Texture {0} is missing; using plain colours.", label));
      imageSlots.put(index, -1);
      return -1;
    }
    int slot = model.images.size();
    model.images.add(bytes);
    imageSlots.put(index, slot);
    return slot;
  }

  private void meshes() throws ImportError {
    for (JsonElement element : array(root, "meshes")) {
      RawModel.Mesh mesh = new RawModel.Mesh();
      for (JsonElement primitiveElement : array(element.getAsJsonObject(), "primitives")) {
        RawModel.Primitive primitive = primitive(primitiveElement.getAsJsonObject());
        if (primitive != null) {
          mesh.primitives.add(primitive);
        }
      }
      model.meshes.add(mesh);
    }
  }

  private RawModel.Primitive primitive(JsonObject source) throws ImportError {
    int mode = source.has("mode") ? source.get("mode").getAsInt() : 4;
    if (mode < 4 || mode > 6) {
      return null;
    }
    JsonObject attributes = source.getAsJsonObject("attributes");
    if (!attributes.has("POSITION")) {
      return null;
    }
    RawModel.Primitive primitive = new RawModel.Primitive();
    primitive.positions = accessor(attributes.get("POSITION").getAsInt());
    int vertices = primitive.vertexCount();
    primitive.normals =
        attributes.has("NORMAL") ? accessor(attributes.get("NORMAL").getAsInt()) : null;
    primitive.uvs =
        attributes.has("TEXCOORD_0") ? accessor(attributes.get("TEXCOORD_0").getAsInt()) : null;
    if (attributes.has("COLOR_0")) {
      int index = attributes.get("COLOR_0").getAsInt();
      float[] colors = accessor(index);
      int width =
          components(
              array(root, "accessors").get(index).getAsJsonObject().get("type").getAsString());
      primitive.colors = new int[vertices];
      for (int i = 0; i < vertices; i++) {
        int base = i * width;
        float alpha = width == 4 ? colors[base + 3] : 1.0F;
        primitive.colors[i] =
            channel(alpha) << 24
                | channel(colors[base]) << 16
                | channel(colors[base + 1]) << 8
                | channel(colors[base + 2]);
      }
    }
    if (attributes.has("JOINTS_0") && attributes.has("WEIGHTS_0")) {
      float[] joints = accessor(attributes.get("JOINTS_0").getAsInt());
      primitive.joints = new int[joints.length];
      for (int i = 0; i < joints.length; i++) {
        primitive.joints[i] = (int) joints[i];
      }
      primitive.weights = accessor(attributes.get("WEIGHTS_0").getAsInt());
    }
    int[] order;
    if (source.has("indices")) {
      float[] raw = accessor(source.get("indices").getAsInt());
      order = new int[raw.length];
      for (int i = 0; i < raw.length; i++) {
        order[i] = (int) raw[i];
      }
    } else {
      order = new int[vertices];
      for (int i = 0; i < vertices; i++) {
        order[i] = i;
      }
    }
    for (int index : order) {
      if (index < 0 || index >= vertices) {
        throw new ImportError(Lang.tr("This glTF has a triangle pointing at a missing vertex."));
      }
    }
    primitive.indices =
        switch (mode) {
          case 5 -> strip(order);
          case 6 -> fan(order);
          default ->
              order.length % 3 == 0 ? order : Arrays.copyOf(order, order.length - order.length % 3);
        };
    primitive.material = source.has("material") ? source.get("material").getAsInt() : -1;
    if (primitive.material >= model.materials.size()) {
      primitive.material = -1;
    }
    return primitive;
  }

  private static int[] strip(int[] order) {
    int count = Math.max(0, order.length - 2);
    int[] out = new int[count * 3];
    for (int i = 0; i < count; i++) {
      boolean even = (i & 1) == 0;
      out[i * 3] = even ? order[i] : order[i + 1];
      out[i * 3 + 1] = even ? order[i + 1] : order[i];
      out[i * 3 + 2] = order[i + 2];
    }
    return out;
  }

  private static int[] fan(int[] order) {
    int count = Math.max(0, order.length - 2);
    int[] out = new int[count * 3];
    for (int i = 0; i < count; i++) {
      out[i * 3] = order[0];
      out[i * 3 + 1] = order[i + 1];
      out[i * 3 + 2] = order[i + 2];
    }
    return out;
  }

  private void nodes() {
    JsonArray nodes = array(root, "nodes");
    for (int i = 0; i < nodes.size(); i++) {
      JsonObject source = nodes.get(i).getAsJsonObject();
      RawModel.Node node = new RawModel.Node();
      node.name = string(source, "name");
      if (source.has("matrix")) {
        node.matrix = new float[16];
        floats(source, "matrix", node.matrix);
      }
      floats(source, "translation", node.t);
      floats(source, "rotation", node.r);
      floats(source, "scale", node.s);
      node.mesh = source.has("mesh") ? source.get("mesh").getAsInt() : -1;
      if (node.mesh >= model.meshes.size()) {
        node.mesh = -1;
      }
      node.skin = source.has("skin") ? source.get("skin").getAsInt() : -1;
      model.nodes.add(node);
    }
    for (int i = 0; i < nodes.size(); i++) {
      for (JsonElement child : array(nodes.get(i).getAsJsonObject(), "children")) {
        int index = child.getAsInt();
        if (index >= 0
            && index < model.nodes.size()
            && index != i
            && model.nodes.get(index).parent < 0) {
          model.nodes.get(index).parent = i;
        }
      }
    }
    boolean[] reachable = new boolean[nodes.size()];
    JsonArray scenes = array(root, "scenes");
    if (scenes.isEmpty()) {
      Arrays.fill(reachable, true);
    } else {
      int scene = root.has("scene") ? root.get("scene").getAsInt() : 0;
      JsonObject chosen =
          scenes.get(Math.min(Math.max(scene, 0), scenes.size() - 1)).getAsJsonObject();
      for (JsonElement entry : array(chosen, "nodes")) {
        int index = entry.getAsInt();
        if (index >= 0 && index < reachable.length) {
          reachable[index] = true;
        }
      }
      boolean changed = true;
      while (changed) {
        changed = false;
        for (int i = 0; i < reachable.length; i++) {
          int parent = model.nodes.get(i).parent;
          if (!reachable[i] && parent >= 0 && reachable[parent]) {
            reachable[i] = true;
            changed = true;
          }
        }
      }
    }
    for (int i = 0; i < reachable.length; i++) {
      if (!reachable[i]) {
        model.nodes.get(i).mesh = -1;
      }
    }
  }

  private void skins() throws ImportError {
    for (JsonElement element : array(root, "skins")) {
      JsonObject source = element.getAsJsonObject();
      RawModel.Skin skin = new RawModel.Skin();
      JsonArray joints = array(source, "joints");
      skin.joints = new int[joints.size()];
      for (int i = 0; i < joints.size(); i++) {
        skin.joints[i] = joints.get(i).getAsInt();
      }
      if (source.has("inverseBindMatrices")) {
        skin.inverseBind = accessor(source.get("inverseBindMatrices").getAsInt());
      } else {
        skin.inverseBind = new float[skin.joints.length * 16];
        for (int i = 0; i < skin.joints.length; i++) {
          skin.inverseBind[i * 16] = 1.0F;
          skin.inverseBind[i * 16 + 5] = 1.0F;
          skin.inverseBind[i * 16 + 10] = 1.0F;
          skin.inverseBind[i * 16 + 15] = 1.0F;
        }
      }
      model.skins.add(skin);
    }
  }

  private void animations() throws ImportError {
    JsonArray list = array(root, "animations");
    for (int a = 0; a < list.size(); a++) {
      JsonObject source = list.get(a).getAsJsonObject();
      RawModel.Animation animation = new RawModel.Animation();
      animation.name =
          source.has("name") ? source.get("name").getAsString() : "Animation " + (a + 1);
      JsonArray samplers = array(source, "samplers");
      for (JsonElement element : array(source, "channels")) {
        JsonObject channelJson = element.getAsJsonObject();
        JsonObject target = channelJson.getAsJsonObject("target");
        if (target == null || !target.has("node")) {
          continue;
        }
        RawModel.Path path =
            switch (string(target, "path")) {
              case "translation" -> RawModel.Path.T;
              case "rotation" -> RawModel.Path.R;
              case "scale" -> RawModel.Path.S;
              default -> null;
            };
        if (path == null) {
          continue;
        }
        JsonObject sampler = samplers.get(channelJson.get("sampler").getAsInt()).getAsJsonObject();
        RawModel.Channel channel = new RawModel.Channel();
        channel.node = target.get("node").getAsInt();
        channel.path = path;
        channel.interp =
            switch (string(sampler, "interpolation")) {
              case "STEP" -> RawModel.Interp.STEP;
              case "CUBICSPLINE" -> RawModel.Interp.CUBIC;
              default -> RawModel.Interp.LINEAR;
            };
        channel.times = accessor(sampler.get("input").getAsInt());
        channel.values = accessor(sampler.get("output").getAsInt());
        int width = path == RawModel.Path.R ? 4 : 3;
        int expected =
            channel.times.length * width * (channel.interp == RawModel.Interp.CUBIC ? 3 : 1);
        if (channel.times.length == 0
            || channel.values.length < expected
            || channel.node >= model.nodes.size()) {
          continue;
        }
        animation.channels.add(channel);
      }
      if (!animation.channels.isEmpty()) {
        model.animations.add(animation);
      }
    }
  }

  private View view(int index) throws ImportError {
    JsonObject view = array(root, "bufferViews").get(index).getAsJsonObject();
    int bufferIndex = view.get("buffer").getAsInt();
    if (bufferIndex < 0 || bufferIndex >= buffers.size()) {
      throw new ImportError(Lang.tr("This glTF points at binary data that isn't there."));
    }
    ByteBuffer buffer = buffers.get(bufferIndex);
    int offset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
    int length = view.get("byteLength").getAsInt();
    if (offset < 0 || length < 0 || offset + length > buffer.limit()) {
      throw new ImportError(Lang.tr("This glTF's binary data is cut short."));
    }
    ByteBuffer slice =
        buffer
            .duplicate()
            .position(offset)
            .limit(offset + length)
            .slice()
            .order(ByteOrder.LITTLE_ENDIAN);
    return new View(slice, view.has("byteStride") ? view.get("byteStride").getAsInt() : 0);
  }

  private float[] accessor(int index) throws ImportError {
    JsonObject accessor = array(root, "accessors").get(index).getAsJsonObject();
    int count = accessor.get("count").getAsInt();
    int componentType = accessor.get("componentType").getAsInt();
    int width = components(accessor.get("type").getAsString());
    boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();
    float[] out = new float[count * width];
    if (accessor.has("bufferView")) {
      View view = view(accessor.get("bufferView").getAsInt());
      int offset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
      int size = size(componentType);
      int stride = view.stride > 0 ? view.stride : size * width;
      if (count > 0
          && offset + (long) (count - 1) * stride + (long) size * width > view.data.limit()) {
        throw new ImportError(Lang.tr("This glTF's binary data is cut short."));
      }
      for (int i = 0; i < count; i++) {
        int base = offset + i * stride;
        for (int k = 0; k < width; k++) {
          out[i * width + k] = component(view.data, base + k * size, componentType, normalized);
        }
      }
    }
    JsonObject sparse = object(accessor, "sparse");
    if (sparse != null) {
      int sparseCount = sparse.get("count").getAsInt();
      JsonObject indices = sparse.getAsJsonObject("indices");
      JsonObject values = sparse.getAsJsonObject("values");
      View indexView = view(indices.get("bufferView").getAsInt());
      View valueView = view(values.get("bufferView").getAsInt());
      int indexOffset = indices.has("byteOffset") ? indices.get("byteOffset").getAsInt() : 0;
      int valueOffset = values.has("byteOffset") ? values.get("byteOffset").getAsInt() : 0;
      int indexType = indices.get("componentType").getAsInt();
      int size = size(componentType);
      for (int i = 0; i < sparseCount; i++) {
        int target =
            (int) component(indexView.data, indexOffset + i * size(indexType), indexType, false);
        if (target < 0 || target >= count) {
          throw new ImportError(Lang.tr("This glTF has damaged sparse data."));
        }
        for (int k = 0; k < width; k++) {
          out[target * width + k] =
              component(
                  valueView.data, valueOffset + (i * width + k) * size, componentType, normalized);
        }
      }
    }
    return out;
  }

  private static float component(ByteBuffer data, int at, int type, boolean normalized)
      throws ImportError {
    return switch (type) {
      case 5120 -> normalized ? Math.max(data.get(at) / 127.0F, -1.0F) : data.get(at);
      case 5121 -> normalized ? (data.get(at) & 0xFF) / 255.0F : data.get(at) & 0xFF;
      case 5122 -> normalized ? Math.max(data.getShort(at) / 32767.0F, -1.0F) : data.getShort(at);
      case 5123 ->
          normalized ? (data.getShort(at) & 0xFFFF) / 65535.0F : data.getShort(at) & 0xFFFF;
      case 5125 -> data.getInt(at) & 0xFFFFFFFFL;
      case 5126 -> data.getFloat(at);
      default -> throw new ImportError(Lang.tr("This glTF uses an unknown number type."));
    };
  }

  private static int size(int type) throws ImportError {
    return switch (type) {
      case 5120, 5121 -> 1;
      case 5122, 5123 -> 2;
      case 5125, 5126 -> 4;
      default -> throw new ImportError(Lang.tr("This glTF uses an unknown number type."));
    };
  }

  private static int components(String type) throws ImportError {
    return switch (type) {
      case "SCALAR" -> 1;
      case "VEC2" -> 2;
      case "VEC3" -> 3;
      case "VEC4", "MAT2" -> 4;
      case "MAT3" -> 9;
      case "MAT4" -> 16;
      default -> throw new ImportError(Lang.tr("This glTF uses an unknown data shape."));
    };
  }

  private static int channel(float value) {
    return (int) (Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
  }

  private static JsonArray array(JsonObject object, String name) {
    JsonElement element = object.get(name);
    return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
  }

  private static JsonObject object(JsonObject object, String name) {
    JsonElement element = object.get(name);
    return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
  }

  private static String string(JsonObject object, String name) {
    JsonElement element = object.get(name);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
  }

  private static void floats(JsonObject object, String name, float[] into) {
    JsonArray values = array(object, name);
    for (int i = 0; i < Math.min(values.size(), into.length); i++) {
      into[i] = values.get(i).getAsFloat();
    }
  }
}
