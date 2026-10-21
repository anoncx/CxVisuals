package me.cxiy.cxvisuals.modules.avatar.bake;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.avatar.anim.Affine;
import me.cxiy.cxvisuals.modules.avatar.anim.Clip;
import me.cxiy.cxvisuals.modules.avatar.anim.Skeleton;
import me.cxiy.cxvisuals.modules.avatar.format.ImportError;
import me.cxiy.cxvisuals.modules.avatar.format.RawModel;

public final class Baker {
  public static final int MAX_JOINTS = 128;
  public static final float HEIGHT = 1.8F;
  private static final float HALF_PI = (float) (Math.PI / 2.0);

  private static final class Builder {
    final RawModel.Material material;
    float[] positions = new float[64 * 3];
    float[] normals = new float[64 * 3];
    float[] uvs = new float[64 * 2];
    int[] colors = new int[64];
    byte[] joints = new byte[64 * 4];
    float[] weights = new float[64 * 4];
    int[] indices = new int[384];
    int capacity = 64;
    int vertices;
    int indexCount;
    boolean anyColor;

    Builder(RawModel.Material material) {
      this.material = material;
    }

    int vertex(
        float x, float y, float z, float nx, float ny, float nz, float u, float v, int color) {
      if (vertices == capacity) {
        int grown = capacity * 2;
        capacity = grown;
        positions = Arrays.copyOf(positions, grown * 3);
        normals = Arrays.copyOf(normals, grown * 3);
        uvs = Arrays.copyOf(uvs, grown * 2);
        colors = Arrays.copyOf(colors, grown);
        joints = Arrays.copyOf(joints, grown * 4);
        weights = Arrays.copyOf(weights, grown * 4);
      }
      int i = vertices++;
      positions[i * 3] = x;
      positions[i * 3 + 1] = y;
      positions[i * 3 + 2] = z;
      normals[i * 3] = nx;
      normals[i * 3 + 1] = ny;
      normals[i * 3 + 2] = nz;
      uvs[i * 2] = u;
      uvs[i * 2 + 1] = v;
      colors[i] = color;
      return i;
    }

    void triangle(int a, int b, int c) {
      if (indexCount + 3 > indices.length) {
        indices = Arrays.copyOf(indices, indices.length * 2);
      }
      indices[indexCount++] = a;
      indices[indexCount++] = b;
      indices[indexCount++] = c;
    }
  }

  private final RawModel model;
  private final int nodeCount;
  private final float[] locals;
  private final float[] globals;
  private final boolean[] animated;
  private final List<Integer> paletteNodes = new ArrayList<>();
  private final List<float[]> paletteBinds = new ArrayList<>();
  private final Map<String, Integer> paletteKeys = new HashMap<>();
  private final TreeMap<Integer, Builder> builders = new TreeMap<>();
  private final List<String> warnings = new ArrayList<>();
  private final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
  private final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
  private float[] axis;
  private int[] position;

  private Baker(RawModel model) {
    this.model = model;
    this.nodeCount = model.nodes.size();
    this.locals = new float[nodeCount * 16];
    this.globals = new float[nodeCount * 16];
    this.animated = new boolean[nodeCount];
  }

  public static BakedAvatar bake(RawModel model, Tune tune, int budget, int edge)
      throws ImportError {
    return new Baker(model).run(tune, budget, edge);
  }

  private BakedAvatar run(Tune tune, int budget, int edge) throws ImportError {
    warnings.addAll(model.warnings);
    axis = axis(tune, model.unitScale);
    transforms();
    for (RawModel.Animation animation : model.animations) {
      for (RawModel.Channel channel : animation.channels) {
        if (channel.node >= 0 && channel.node < nodeCount) {
          animated[channel.node] = true;
        }
      }
    }
    for (int n = 0; n < nodeCount; n++) {
      RawModel.Node node = model.nodes.get(n);
      if (node.mesh >= 0 && node.mesh < model.meshes.size()) {
        for (RawModel.Primitive primitive : model.meshes.get(node.mesh).primitives) {
          gather(n, node, primitive);
        }
      }
    }
    int source = 0;
    for (Builder builder : builders.values()) {
      source += builder.indexCount / 3;
    }
    if (source == 0) {
      throw new ImportError(Lang.tr("This model has no triangles."));
    }
    if (paletteNodes.size() > MAX_JOINTS) {
      throw new ImportError(
          Lang.format("Too many bones ({0}). The limit is {1}.", paletteNodes.size(), MAX_JOINTS));
    }
    Map<Integer, Integer> slots = new HashMap<>();
    List<Texture> textures = new ArrayList<>();
    boolean skinned = !paletteNodes.isEmpty();
    BakedAvatar.Part[] parts = new BakedAvatar.Part[builders.size()];
    int p = 0;
    for (Builder builder : builders.values()) {
      RawModel.Material material = builder.material;
      int texture =
          material.image >= 0 && material.image < model.images.size()
              ? texture(material.image, edge, slots, textures)
              : -1;
      int v = builder.vertices;
      parts[p++] =
          new BakedAvatar.Part(
              texture,
              material.color,
              material.alpha == RawModel.Alpha.BLEND,
              material.doubleSided || !windingKnown(model.format),
              Arrays.copyOf(builder.positions, v * 3),
              Arrays.copyOf(builder.normals, v * 3),
              Arrays.copyOf(builder.uvs, v * 2),
              builder.anyColor ? Arrays.copyOf(builder.colors, v) : null,
              skinned ? Arrays.copyOf(builder.joints, v * 4) : null,
              skinned ? Arrays.copyOf(builder.weights, v * 4) : null,
              Arrays.copyOf(builder.indices, builder.indexCount));
    }
    parts = Simplifier.simplify(parts, budget);
    Skeleton skeleton = skeleton();
    float[] fit = fit();
    float scale = fitScale();
    return new BakedAvatar(
        parts,
        textures.toArray(new Texture[0]),
        skeleton,
        clipsFor(skeleton),
        fit,
        (max[0] - min[0]) * scale,
        HEIGHT,
        (max[2] - min[2]) * scale,
        source,
        warnings);
  }

  private static boolean windingKnown(String format) {
    return switch (format) {
      case "OBJ", "STL", "PLY" -> false;
      default -> true;
    };
  }

  private static float[] axis(Tune tune, float unitScale) {
    float[] scale = Affine.identity(new float[16]);
    scale[0] = unitScale;
    scale[5] = unitScale;
    scale[10] = unitScale;
    float[] up =
        tune.up() == RawModel.Axis.Z
            ? Affine.rotationX(-HALF_PI, new float[16])
            : Affine.identity(new float[16]);
    float[] turn = Affine.rotationY(tune.turn() * HALF_PI, new float[16]);
    float[] out = Affine.multiply(up, 0, scale, 0, new float[16], 0);
    return Affine.multiply(turn, 0, out, 0, new float[16], 0);
  }

  private void transforms() throws ImportError {
    for (int n = 0; n < nodeCount; n++) {
      RawModel.Node node = model.nodes.get(n);
      if (node.matrix != null && node.matrix.length == 16) {
        System.arraycopy(node.matrix, 0, locals, n * 16, 16);
      } else {
        Affine.compose(
            node.t[0], node.t[1], node.t[2], node.r[0], node.r[1], node.r[2], node.r[3], node.s[0],
            node.s[1], node.s[2], locals, n * 16);
      }
    }
    boolean[] done = new boolean[nodeCount];
    for (int n = 0; n < nodeCount; n++) {
      global(n, done, 0);
    }
  }

  private void global(int n, boolean[] done, int depth) throws ImportError {
    if (done[n]) {
      return;
    }
    if (depth > nodeCount) {
      throw new ImportError(Lang.tr("This model's node tree loops back on itself."));
    }
    int parent = model.nodes.get(n).parent;
    if (parent >= 0 && parent < nodeCount) {
      global(parent, done, depth + 1);
      Affine.multiply(globals, parent * 16, locals, n * 16, globals, n * 16);
    } else {
      System.arraycopy(locals, n * 16, globals, n * 16, 16);
    }
    done[n] = true;
  }

  private int animatedAncestor(int n) {
    int guard = 0;
    while (n >= 0 && n < nodeCount && guard++ <= nodeCount) {
      if (animated[n]) {
        return n;
      }
      n = model.nodes.get(n).parent;
    }
    return -1;
  }

  private int entry(int node, float[] bind, int offset) {
    float[] matrix = Arrays.copyOfRange(bind, offset, offset + 16);
    String key = node + Arrays.toString(matrix);
    Integer known = paletteKeys.get(key);
    if (known != null) {
      return known;
    }
    int index = paletteNodes.size();
    paletteNodes.add(node);
    paletteBinds.add(matrix);
    paletteKeys.put(key, index);
    return index;
  }

  private void gather(int nodeIndex, RawModel.Node node, RawModel.Primitive primitive)
      throws ImportError {
    int vertices = primitive.vertexCount();
    if (vertices == 0 || primitive.indices == null || primitive.indices.length < 3) {
      return;
    }
    RawModel.Skin skin =
        node.skin >= 0 && node.skin < model.skins.size() ? model.skins.get(node.skin) : null;
    boolean skinned =
        skin != null
            && primitive.joints != null
            && primitive.weights != null
            && primitive.joints.length >= vertices * 4
            && primitive.weights.length >= vertices * 4;
    int material =
        primitive.material >= 0 && primitive.material < model.materials.size()
            ? primitive.material
            : -1;
    Builder builder =
        builders.computeIfAbsent(
            material,
            key -> new Builder(key >= 0 ? model.materials.get(key) : new RawModel.Material()));
    float[] normals =
        primitive.normals != null && primitive.normals.length >= vertices * 3
            ? primitive.normals
            : generateNormals(primitive);
    int[] entries = null;
    if (skinned) {
      entries = new int[skin.joints.length];
      for (int k = 0; k < skin.joints.length; k++) {
        int jointNode = skin.joints[k];
        if (jointNode < 0 || jointNode >= nodeCount || skin.inverseBind.length < (k + 1) * 16) {
          throw new ImportError(Lang.tr("This model has a broken skeleton."));
        }
        entries[k] = entry(jointNode, skin.inverseBind, k * 16);
      }
    }
    int rigid = -1;
    float[] matrix = Arrays.copyOfRange(globals, nodeIndex * 16, nodeIndex * 16 + 16);
    float[] normalMatrix = Affine.normalMatrix(matrix, new float[16]);
    boolean flip = !skinned && Affine.determinant3(matrix) < 0.0F;
    if (!skinned) {
      int anchor = animatedAncestor(nodeIndex);
      if (anchor >= 0) {
        rigid =
            entry(
                anchor,
                Affine.invert(
                    Arrays.copyOfRange(globals, anchor * 16, anchor * 16 + 16), new float[16]),
                0);
      }
    }
    float[] point = new float[3];
    float[] direction = new float[3];
    float[] rest = new float[3];
    int base = builder.vertices;
    for (int v = 0; v < vertices; v++) {
      float x = primitive.positions[v * 3];
      float y = primitive.positions[v * 3 + 1];
      float z = primitive.positions[v * 3 + 2];
      float nx = normals[v * 3];
      float ny = normals[v * 3 + 1];
      float nz = normals[v * 3 + 2];
      if (skinned) {
        point[0] = x;
        point[1] = y;
        point[2] = z;
        direction[0] = nx;
        direction[1] = ny;
        direction[2] = nz;
      } else {
        Affine.point(matrix, 0, x, y, z, point);
        Affine.direction(normalMatrix, 0, nx, ny, nz, direction);
        normalize(direction);
      }
      float u =
          primitive.uvs != null && primitive.uvs.length >= vertices * 2
              ? primitive.uvs[v * 2]
              : 0.0F;
      float w =
          primitive.uvs != null && primitive.uvs.length >= vertices * 2
              ? primitive.uvs[v * 2 + 1]
              : 0.0F;
      int color =
          primitive.colors != null && primitive.colors.length >= vertices
              ? primitive.colors[v]
              : 0xFFFFFFFF;
      builder.anyColor |= primitive.colors != null;
      int index =
          builder.vertex(
              point[0], point[1], point[2], direction[0], direction[1], direction[2], u, w, color);
      if (skinned) {
        float total = 0.0F;
        for (int k = 0; k < 4; k++) {
          total += Math.max(0.0F, primitive.weights[v * 4 + k]);
        }
        rest[0] = 0.0F;
        rest[1] = 0.0F;
        rest[2] = 0.0F;
        for (int k = 0; k < 4; k++) {
          float weight = total > 0.0F ? Math.max(0.0F, primitive.weights[v * 4 + k]) / total : 0.0F;
          int joint = primitive.joints[v * 4 + k];
          if (joint < 0 || joint >= entries.length) {
            weight = 0.0F;
            joint = 0;
          }
          int e = entries.length == 0 ? 0 : entries[joint];
          builder.joints[index * 4 + k] = (byte) e;
          builder.weights[index * 4 + k] = weight;
          if (weight > 0.0F) {
            float[] bind = paletteBinds.get(e);
            float[] skinMatrix =
                Affine.multiply(globals, paletteNodes.get(e) * 16, bind, 0, new float[16], 0);
            Affine.point(skinMatrix, 0, x, y, z, direction);
            rest[0] += direction[0] * weight;
            rest[1] += direction[1] * weight;
            rest[2] += direction[2] * weight;
          }
        }
        if (total <= 0.0F) {
          rest[0] = x;
          rest[1] = y;
          rest[2] = z;
        }
        bound(rest);
      } else {
        if (rigid >= 0) {
          builder.joints[index * 4] = (byte) rigid;
          builder.weights[index * 4] = 1.0F;
        }
        bound(point);
      }
    }
    int[] order = primitive.indices;
    for (int t = 0; t + 2 < order.length; t += 3) {
      int a = order[t];
      int b = order[t + 1];
      int c = order[t + 2];
      if (a < 0 || b < 0 || c < 0 || a >= vertices || b >= vertices || c >= vertices) {
        throw new ImportError(Lang.tr("This model has a triangle pointing at a missing vertex."));
      }
      if (flip) {
        builder.triangle(base + a, base + c, base + b);
      } else {
        builder.triangle(base + a, base + b, base + c);
      }
    }
  }

  private void bound(float[] p) {
    float[] out = Affine.point(axis, 0, p[0], p[1], p[2], new float[3]);
    for (int k = 0; k < 3; k++) {
      min[k] = Math.min(min[k], out[k]);
      max[k] = Math.max(max[k], out[k]);
    }
  }

  private float fitScale() {
    float height = max[1] - min[1];
    if (height < 1e-6F) {
      height = Math.max(max[0] - min[0], max[2] - min[2]);
    }
    return height < 1e-6F ? 1.0F : HEIGHT / height;
  }

  private float[] fit() {
    float scale = fitScale();
    float[] matrix = Affine.identity(new float[16]);
    matrix[0] = scale;
    matrix[5] = scale;
    matrix[10] = scale;
    matrix[12] = -(min[0] + max[0]) * 0.5F * scale;
    matrix[13] = -min[1] * scale;
    matrix[14] = -(min[2] + max[2]) * 0.5F * scale;
    return Affine.multiply(matrix, 0, axis, 0, new float[16], 0);
  }

  private static float[] generateNormals(RawModel.Primitive primitive) {
    float[] p = primitive.positions;
    float[] normals = new float[p.length];
    int[] idx = primitive.indices;
    for (int t = 0; t + 2 < idx.length; t += 3) {
      int a = idx[t] * 3;
      int b = idx[t + 1] * 3;
      int c = idx[t + 2] * 3;
      if (a < 0 || b < 0 || c < 0 || a >= p.length || b >= p.length || c >= p.length) {
        continue;
      }
      float ux = p[b] - p[a];
      float uy = p[b + 1] - p[a + 1];
      float uz = p[b + 2] - p[a + 2];
      float vx = p[c] - p[a];
      float vy = p[c + 1] - p[a + 1];
      float vz = p[c + 2] - p[a + 2];
      float nx = uy * vz - uz * vy;
      float ny = uz * vx - ux * vz;
      float nz = ux * vy - uy * vx;
      for (int corner : new int[] {a, b, c}) {
        normals[corner] += nx;
        normals[corner + 1] += ny;
        normals[corner + 2] += nz;
      }
    }
    float[] direction = new float[3];
    for (int i = 0; i < normals.length; i += 3) {
      direction[0] = normals[i];
      direction[1] = normals[i + 1];
      direction[2] = normals[i + 2];
      normalize(direction);
      normals[i] = direction[0];
      normals[i + 1] = direction[1];
      normals[i + 2] = direction[2];
    }
    return normals;
  }

  private static void normalize(float[] v) {
    float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    if (length < 1e-12F) {
      v[0] = 0.0F;
      v[1] = 1.0F;
      v[2] = 0.0F;
      return;
    }
    v[0] /= length;
    v[1] /= length;
    v[2] /= length;
  }

  private int texture(int image, int edge, Map<Integer, Integer> slots, List<Texture> textures) {
    Integer known = slots.get(image);
    if (known != null) {
      return known;
    }
    int slot = -1;
    try {
      BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(model.images.get(image)));
      if (decoded != null) {
        BufferedImage scaled = shrink(decoded, edge);
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        int[] argb = scaled.getRGB(0, 0, w, h, null, 0, w);
        slot = textures.size();
        textures.add(new Texture(w, h, argb));
      }
    } catch (IOException | RuntimeException e) {
      slot = -1;
    }
    if (slot < 0) {
      warnings.add(Lang.tr("A texture couldn't be read; using plain colours."));
    }
    slots.put(image, slot);
    return slot;
  }

  private static BufferedImage shrink(BufferedImage source, int edge) {
    BufferedImage current = source;
    if (current.getType() != BufferedImage.TYPE_INT_ARGB) {
      BufferedImage converted =
          new BufferedImage(current.getWidth(), current.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = converted.createGraphics();
      g.drawImage(current, 0, 0, null);
      g.dispose();
      current = converted;
    }
    int longest = Math.max(current.getWidth(), current.getHeight());
    if (longest <= edge) {
      return current;
    }
    float ratio = (float) edge / longest;
    int targetW = Math.max(1, Math.round(current.getWidth() * ratio));
    int targetH = Math.max(1, Math.round(current.getHeight() * ratio));
    while (current.getWidth() > targetW || current.getHeight() > targetH) {
      int w = Math.max(targetW, current.getWidth() / 2);
      int h = Math.max(targetH, current.getHeight() / 2);
      BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = next.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(current, 0, 0, w, h, null);
      g.dispose();
      current = next;
    }
    return current;
  }

  private Skeleton skeleton() {
    if (paletteNodes.isEmpty() && !anyAnimated()) {
      return Skeleton.EMPTY;
    }
    boolean[] keep = new boolean[nodeCount];
    for (int node : paletteNodes) {
      mark(node, keep);
    }
    for (int n = 0; n < nodeCount; n++) {
      if (animated[n]) {
        mark(n, keep);
      }
    }
    int[] order = new int[nodeCount];
    int[] position = new int[nodeCount];
    Arrays.fill(position, -1);
    int count = 0;
    boolean progress = true;
    while (progress) {
      progress = false;
      for (int n = 0; n < nodeCount; n++) {
        if (!keep[n] || position[n] >= 0) {
          continue;
        }
        int parent = model.nodes.get(n).parent;
        if (parent < 0 || parent >= nodeCount || position[parent] >= 0) {
          position[n] = count;
          order[count++] = n;
          progress = true;
        }
      }
    }
    String[] names = new String[count];
    int[] parents = new int[count];
    float[] t = new float[count * 3];
    float[] r = new float[count * 4];
    float[] s = new float[count * 3];
    float[] tt = new float[3];
    float[] rr = new float[4];
    float[] ss = new float[3];
    for (int i = 0; i < count; i++) {
      RawModel.Node node = model.nodes.get(order[i]);
      names[i] = node.name == null ? "" : node.name;
      int parent = node.parent;
      parents[i] = parent >= 0 && parent < nodeCount ? position[parent] : -1;
      if (node.matrix != null && node.matrix.length == 16) {
        Affine.decompose(node.matrix, tt, rr, ss);
      } else {
        System.arraycopy(node.t, 0, tt, 0, 3);
        System.arraycopy(node.r, 0, rr, 0, 4);
        System.arraycopy(node.s, 0, ss, 0, 3);
      }
      System.arraycopy(tt, 0, t, i * 3, 3);
      System.arraycopy(rr, 0, r, i * 4, 4);
      System.arraycopy(ss, 0, s, i * 3, 3);
    }
    int[] palette = new int[paletteNodes.size()];
    float[] binds = new float[palette.length * 16];
    for (int e = 0; e < palette.length; e++) {
      palette[e] = position[paletteNodes.get(e)];
      System.arraycopy(paletteBinds.get(e), 0, binds, e * 16, 16);
    }
    this.position = position;
    return new Skeleton(names, parents, t, r, s, palette, binds);
  }

  private boolean anyAnimated() {
    for (boolean flag : animated) {
      if (flag) {
        return true;
      }
    }
    return false;
  }

  private void mark(int node, boolean[] keep) {
    int guard = 0;
    while (node >= 0 && node < nodeCount && !keep[node] && guard++ <= nodeCount) {
      keep[node] = true;
      node = model.nodes.get(node).parent;
    }
  }

  private Clip[] clipsFor(Skeleton skeleton) {
    if (skeleton.count() == 0) {
      return new Clip[0];
    }
    List<Clip> clips = new ArrayList<>();
    for (RawModel.Animation animation : model.animations) {
      List<RawModel.Channel> kept = new ArrayList<>();
      for (RawModel.Channel channel : animation.channels) {
        if (channel.node >= 0 && channel.node < nodeCount && position[channel.node] >= 0) {
          kept.add(channel);
        }
      }
      if (kept.isEmpty()) {
        continue;
      }
      int size = kept.size();
      int[] nodes = new int[size];
      RawModel.Path[] paths = new RawModel.Path[size];
      RawModel.Interp[] interps = new RawModel.Interp[size];
      float[][] times = new float[size][];
      float[][] values = new float[size][];
      for (int i = 0; i < size; i++) {
        RawModel.Channel channel = kept.get(i);
        nodes[i] = position[channel.node];
        paths[i] = channel.path;
        interps[i] = channel.interp;
        times[i] = channel.times;
        values[i] = channel.values;
      }
      clips.add(new Clip(animation.name, nodes, paths, interps, times, values));
    }
    return clips.toArray(new Clip[0]);
  }
}
