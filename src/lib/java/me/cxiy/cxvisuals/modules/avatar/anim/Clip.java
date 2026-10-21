package me.cxiy.cxvisuals.modules.avatar.anim;

import me.cxiy.cxvisuals.modules.avatar.format.RawModel;

public final class Clip {
  private final String name;
  private final int[] nodes;
  private final RawModel.Path[] paths;
  private final RawModel.Interp[] interps;
  private final float[][] times;
  private final float[][] values;
  private final float duration;

  public Clip(
      String name,
      int[] nodes,
      RawModel.Path[] paths,
      RawModel.Interp[] interps,
      float[][] times,
      float[][] values) {
    this.name = name;
    this.nodes = nodes;
    this.paths = paths;
    this.interps = interps;
    this.times = times;
    this.values = values;
    float longest = 0.0F;
    for (float[] channel : times) {
      if (channel.length > 0) {
        longest = Math.max(longest, channel[channel.length - 1]);
      }
    }
    this.duration = longest;
  }

  public String name() {
    return name;
  }

  public float duration() {
    return duration;
  }

  public int channels() {
    return nodes.length;
  }

  public int node(int channel) {
    return nodes[channel];
  }

  public RawModel.Path path(int channel) {
    return paths[channel];
  }

  public RawModel.Interp interp(int channel) {
    return interps[channel];
  }

  public float[] times(int channel) {
    return times[channel];
  }

  public float[] values(int channel) {
    return values[channel];
  }

  public void sample(float time, float[] local) {
    for (int c = 0; c < nodes.length; c++) {
      RawModel.Path path = paths[c];
      int width = path == RawModel.Path.R ? 4 : 3;
      int target =
          nodes[c] * Skeleton.STRIDE
              + (path == RawModel.Path.T ? 0 : path == RawModel.Path.R ? 3 : 7);
      float[] keys = times[c];
      float[] data = values[c];
      boolean cubic = interps[c] == RawModel.Interp.CUBIC;
      int keyWidth = cubic ? width * 3 : width;
      int valueOffset = cubic ? width : 0;
      int last = keys.length - 1;
      if (time <= keys[0] || last == 0) {
        copy(data, valueOffset, width, local, target);
        continue;
      }
      if (time >= keys[last]) {
        copy(data, last * keyWidth + valueOffset, width, local, target);
        continue;
      }
      int k = search(keys, time);
      float span = keys[k + 1] - keys[k];
      float t = span <= 0.0F ? 0.0F : (time - keys[k]) / span;
      switch (interps[c]) {
        case STEP -> copy(data, k * keyWidth, width, local, target);
        case LINEAR -> {
          if (width == 4) {
            Affine.slerp(data, k * 4, data, (k + 1) * 4, t, local, target);
          } else {
            for (int i = 0; i < 3; i++) {
              float a = data[k * 3 + i];
              local[target + i] = a + (data[(k + 1) * 3 + i] - a) * t;
            }
          }
        }
        case CUBIC -> {
          float t2 = t * t;
          float t3 = t2 * t;
          float h00 = 2.0F * t3 - 3.0F * t2 + 1.0F;
          float h10 = t3 - 2.0F * t2 + t;
          float h01 = -2.0F * t3 + 3.0F * t2;
          float h11 = t3 - t2;
          int a = k * keyWidth;
          int b = (k + 1) * keyWidth;
          for (int i = 0; i < width; i++) {
            float p0 = data[a + width + i];
            float m0 = data[a + width * 2 + i] * span;
            float p1 = data[b + width + i];
            float m1 = data[b + i] * span;
            local[target + i] = h00 * p0 + h10 * m0 + h01 * p1 + h11 * m1;
          }
          if (width == 4) {
            Affine.normalize4(local, target);
          }
        }
      }
    }
  }

  private static int search(float[] keys, float time) {
    int low = 0;
    int high = keys.length - 2;
    while (low < high) {
      int mid = (low + high + 1) >>> 1;
      if (keys[mid] <= time) {
        low = mid;
      } else {
        high = mid - 1;
      }
    }
    return low;
  }

  private static void copy(float[] from, int offset, int width, float[] to, int target) {
    System.arraycopy(from, offset, to, target, width);
  }
}
