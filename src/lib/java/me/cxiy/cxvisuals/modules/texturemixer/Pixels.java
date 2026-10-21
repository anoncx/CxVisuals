package me.cxiy.cxvisuals.modules.texturemixer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class Pixels {
  private final int width;
  private final int height;
  private final int[] argb;
  private final Pixels[] sheet;
  private final int[] sequence;
  private final int[] ends;

  private Pixels(int width, int height, int[] argb) {
    this(width, height, argb, null, null, null);
  }

  private Pixels(
      int width, int height, int[] argb, Pixels[] sheet, int[] sequence, int[] ends) {
    this.width = width;
    this.height = height;
    this.argb = argb;
    this.sheet = sheet;
    this.sequence = sequence;
    this.ends = ends;
  }

  public static Pixels decode(byte[] png) throws IOException {
    return decode(png, null, false);
  }

  public static Pixels decode(byte[] png, byte[] meta, boolean strip) throws IOException {
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    if (image == null) {
      throw new IOException("not an image");
    }
    int w = image.getWidth();
    int total = image.getHeight();
    int[] all = new int[w * total];
    image.getRGB(0, 0, w, total, all, 0, w);
    if (!strip || total <= w || total % w != 0) {
      return new Pixels(w, total, all);
    }
    int count = total / w;
    Pixels[] frames = new Pixels[count];
    for (int i = 0; i < count; i++) {
      int[] part = new int[w * w];
      System.arraycopy(all, i * w * w, part, 0, w * w);
      frames[i] = new Pixels(w, w, part);
    }
    List<int[]> timeline = timeline(meta, count);
    int[] order = new int[timeline.size()];
    int[] ends = new int[timeline.size()];
    int clock = 0;
    for (int i = 0; i < order.length; i++) {
      order[i] = timeline.get(i)[0];
      clock += Math.max(1, timeline.get(i)[1]);
      ends[i] = clock;
    }
    return new Pixels(w, w, frames[0].argb, frames, order, ends);
  }

  private static List<int[]> timeline(byte[] meta, int count) {
    int base = 1;
    List<int[]> out = new ArrayList<>(count);
    try {
      if (meta != null) {
        JsonObject root = JsonParser.parseString(new String(meta, StandardCharsets.UTF_8))
            .getAsJsonObject();
        JsonObject animation = root.getAsJsonObject("animation");
        if (animation != null) {
          if (animation.has("frametime")) {
            base = Math.max(1, animation.get("frametime").getAsInt());
          }
          if (animation.has("frames")) {
            JsonArray frames = animation.getAsJsonArray("frames");
            for (JsonElement element : frames) {
              if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                int time =
                    object.has("time") ? Math.max(1, object.get("time").getAsInt()) : base;
                out.add(new int[] {clamp(object.get("index").getAsInt(), count), time});
              } else {
                out.add(new int[] {clamp(element.getAsInt(), count), base});
              }
            }
          }
        }
      }
    } catch (RuntimeException ignored) {
      out.clear();
    }
    if (out.isEmpty()) {
      for (int i = 0; i < count; i++) {
        out.add(new int[] {i, base});
      }
    }
    return out;
  }

  private static int clamp(int index, int count) {
    return Math.max(0, Math.min(count - 1, index));
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public int[] argb() {
    return argb;
  }

  public boolean animated() {
    return sheet != null && sequence.length > 1;
  }

  public Pixels at(int tick) {
    if (!animated()) {
      return this;
    }
    int cycle = ends[ends.length - 1];
    int local = Math.floorMod(tick, cycle);
    for (int i = 0; i < ends.length; i++) {
      if (local < ends[i]) {
        return sheet[sequence[i]];
      }
    }
    return sheet[sequence[0]];
  }

  public int sample(float u, float v) {
    int x = Math.min(width - 1, Math.max(0, (int) (u * width)));
    int y = Math.min(height - 1, Math.max(0, (int) (v * height)));
    return argb[y * width + x];
  }
}
