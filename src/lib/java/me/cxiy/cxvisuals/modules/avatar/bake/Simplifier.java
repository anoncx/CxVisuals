package me.cxiy.cxvisuals.modules.avatar.bake;

import java.util.HashMap;
import java.util.HashSet;

public final class Simplifier {
  private static final int MAX_RESOLUTION = 2048;
  private static final int UV_CELLS = 64;

  private Simplifier() {}

  public static BakedAvatar.Part[] simplify(BakedAvatar.Part[] parts, int budget) {
    int total = 0;
    for (BakedAvatar.Part part : parts) {
      total += part.triangles();
    }
    if (total <= budget) {
      return parts;
    }
    float[] bounds = {
      Float.MAX_VALUE,
      Float.MAX_VALUE,
      Float.MAX_VALUE,
      -Float.MAX_VALUE,
      -Float.MAX_VALUE,
      -Float.MAX_VALUE
    };
    for (BakedAvatar.Part part : parts) {
      float[] p = part.positions;
      for (int i = 0; i < p.length; i += 3) {
        for (int k = 0; k < 3; k++) {
          bounds[k] = Math.min(bounds[k], p[i + k]);
          bounds[k + 3] = Math.max(bounds[k + 3], p[i + k]);
        }
      }
    }
    float extent =
        Math.max(bounds[3] - bounds[0], Math.max(bounds[4] - bounds[1], bounds[5] - bounds[2]));
    if (extent <= 0.0F) {
      return parts;
    }
    int low = 1;
    int high = MAX_RESOLUTION;
    BakedAvatar.Part[] best = null;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      BakedAvatar.Part[] attempt = cluster(parts, bounds, extent / mid);
      if (count(attempt) <= budget) {
        best = attempt;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return best != null ? best : cluster(parts, bounds, extent);
  }

  private static int count(BakedAvatar.Part[] parts) {
    int total = 0;
    for (BakedAvatar.Part part : parts) {
      total += part.triangles();
    }
    return total;
  }

  private static BakedAvatar.Part[] cluster(BakedAvatar.Part[] parts, float[] bounds, float cell) {
    BakedAvatar.Part[] out = new BakedAvatar.Part[parts.length];
    float inv = 1.0F / cell;
    for (int p = 0; p < parts.length; p++) {
      out[p] = cluster(parts[p], bounds, inv);
    }
    return out;
  }

  private static BakedAvatar.Part cluster(BakedAvatar.Part part, float[] bounds, float inv) {
    int vertices = part.vertexCount();
    int[] remap = new int[vertices];
    HashMap<Long, Integer> cells = new HashMap<>(vertices);
    int clusters = 0;
    int[] first = new int[vertices];
    float[] sums = new float[vertices * 3];
    float[] normals = new float[vertices * 3];
    int[] counts = new int[vertices];
    for (int v = 0; v < vertices; v++) {
      long x = Math.min(MAX_RESOLUTION, (long) ((part.positions[v * 3] - bounds[0]) * inv));
      long y = Math.min(MAX_RESOLUTION, (long) ((part.positions[v * 3 + 1] - bounds[1]) * inv));
      long z = Math.min(MAX_RESOLUTION, (long) ((part.positions[v * 3 + 2] - bounds[2]) * inv));
      long joint = part.joints == null ? 0 : dominant(part, v) + 1;
      long u = (long) Math.floor(part.uvs[v * 2] * UV_CELLS) & 0x3F;
      long w = (long) Math.floor(part.uvs[v * 2 + 1] * UV_CELLS) & 0x3F;
      long key = x | y << 12 | z << 24 | joint << 36 | u << 45 | w << 51;
      Integer index = cells.get(key);
      if (index == null) {
        index = clusters++;
        cells.put(key, index);
        first[index] = v;
      }
      int c = index;
      remap[v] = c;
      sums[c * 3] += part.positions[v * 3];
      sums[c * 3 + 1] += part.positions[v * 3 + 1];
      sums[c * 3 + 2] += part.positions[v * 3 + 2];
      normals[c * 3] += part.normals[v * 3];
      normals[c * 3 + 1] += part.normals[v * 3 + 1];
      normals[c * 3 + 2] += part.normals[v * 3 + 2];
      counts[c]++;
    }
    int[] indices = new int[part.indices.length];
    int kept = 0;
    HashSet<Long> seen = new HashSet<>();
    for (int t = 0; t < part.indices.length; t += 3) {
      int a = remap[part.indices[t]];
      int b = remap[part.indices[t + 1]];
      int c = remap[part.indices[t + 2]];
      if (a == b || b == c || a == c) {
        continue;
      }
      int lo = Math.min(a, Math.min(b, c));
      int hi = Math.max(a, Math.max(b, c));
      int mid = a + b + c - lo - hi;
      long signature = (long) lo << 42 | (long) mid << 21 | hi;
      if (!seen.add(signature)) {
        continue;
      }
      indices[kept++] = a;
      indices[kept++] = b;
      indices[kept++] = c;
    }
    float[] positions = new float[clusters * 3];
    float[] outNormals = new float[clusters * 3];
    float[] uvs = new float[clusters * 2];
    int[] colors = part.colors == null ? null : new int[clusters];
    byte[] joints = part.joints == null ? null : new byte[clusters * 4];
    float[] weights = part.weights == null ? null : new float[clusters * 4];
    for (int c = 0; c < clusters; c++) {
      float scale = 1.0F / counts[c];
      positions[c * 3] = sums[c * 3] * scale;
      positions[c * 3 + 1] = sums[c * 3 + 1] * scale;
      positions[c * 3 + 2] = sums[c * 3 + 2] * scale;
      float nx = normals[c * 3];
      float ny = normals[c * 3 + 1];
      float nz = normals[c * 3 + 2];
      float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
      int source = first[c];
      if (length < 1e-6F) {
        nx = part.normals[source * 3];
        ny = part.normals[source * 3 + 1];
        nz = part.normals[source * 3 + 2];
        length = 1.0F;
      }
      outNormals[c * 3] = nx / length;
      outNormals[c * 3 + 1] = ny / length;
      outNormals[c * 3 + 2] = nz / length;
      uvs[c * 2] = part.uvs[source * 2];
      uvs[c * 2 + 1] = part.uvs[source * 2 + 1];
      if (colors != null) {
        colors[c] = part.colors[source];
      }
      if (joints != null) {
        System.arraycopy(part.joints, source * 4, joints, c * 4, 4);
      }
      if (weights != null) {
        System.arraycopy(part.weights, source * 4, weights, c * 4, 4);
      }
    }
    int[] trimmed = new int[kept];
    System.arraycopy(indices, 0, trimmed, 0, kept);
    return new BakedAvatar.Part(
        part.texture,
        part.color,
        part.blend,
        part.doubleSided,
        positions,
        outNormals,
        uvs,
        colors,
        joints,
        weights,
        trimmed);
  }

  private static int dominant(BakedAvatar.Part part, int v) {
    int best = 0;
    float weight = -1.0F;
    for (int k = 0; k < 4; k++) {
      float w = part.weights[v * 4 + k];
      if (w > weight) {
        weight = w;
        best = k;
      }
    }
    return weight <= 0.0F ? 255 : part.joints[v * 4 + best] & 0xFF;
  }
}
