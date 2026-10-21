package me.cxiy.cxvisuals.modules.social;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.bake.Texture;

public final class SharedAvatar {
  public static final int SHARE_EDGE = 512;
  public static final int MINI_EDGE = 96;
  public static final int MINI_BYTES = 768 << 10;
  public static final int MAX_BYTES = 8 << 20;
  public static final int MAX_PARTS = 64;
  public static final int MAX_TEXTURES = 8;
  public static final int MAX_EDGE = 1024;
  public static final int MAX_CLIPS = 64;
  public static final int MAX_NODES = 256;
  public static final int MAX_TRIANGLES = 60_000;

  private SharedAvatar() {}

  public static BakedAvatar downscale(BakedAvatar avatar) {
    Texture[] textures = new Texture[avatar.textures.length];
    for (int i = 0; i < textures.length; i++) {
      textures[i] = shrink(avatar.textures[i]);
    }
    return new BakedAvatar(
        avatar.parts,
        textures,
        avatar.skeleton,
        avatar.clips,
        avatar.fit,
        avatar.width,
        avatar.height,
        avatar.depth,
        avatar.sourceTriangles,
        List.of());
  }

  public static BakedAvatar mini(BakedAvatar preview) {
    Texture[] textures = new Texture[preview.textures.length];
    for (int i = 0; i < textures.length; i++) {
      textures[i] = shrink(preview.textures[i], MINI_EDGE);
    }
    return new BakedAvatar(
        preview.parts,
        textures,
        preview.skeleton,
        preview.clips,
        preview.fit,
        preview.width,
        preview.height,
        preview.depth,
        preview.sourceTriangles,
        List.of());
  }

  public static byte[] pack(byte[] blob) throws IOException {
    ByteArrayOutputStream raw = new ByteArrayOutputStream(Math.max(1024, blob.length / 3));
    try (GZIPOutputStream out = new GZIPOutputStream(raw, 1 << 14)) {
      out.write(blob);
    }
    return raw.toByteArray();
  }

  public static byte[] unpack(byte[] blob, int limit) {
    if (blob == null || blob.length < 2 || (blob[0] & 0xFF) != 0x1F || (blob[1] & 0xFF) != 0x8B) {
      return blob != null && blob.length <= limit ? blob : null;
    }
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(blob), 1 << 14)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, blob.length * 4));
      byte[] chunk = new byte[1 << 14];
      int read;
      while ((read = in.read(chunk)) > 0) {
        if (out.size() + read > limit) {
          return null;
        }
        out.write(chunk, 0, read);
      }
      return out.toByteArray();
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  public static Texture shrink(Texture texture) {
    return shrink(texture, SHARE_EDGE);
  }

  public static Texture shrink(Texture texture, int edge) {
    int width = texture.width();
    int height = texture.height();
    int[] argb = texture.argb();
    while (Math.max(width, height) > edge && width >= 2 && height >= 2) {
      int nextWidth = width / 2;
      int nextHeight = height / 2;
      int[] next = new int[nextWidth * nextHeight];
      for (int y = 0; y < nextHeight; y++) {
        for (int x = 0; x < nextWidth; x++) {
          int a = argb[(y * 2) * width + x * 2];
          int b = argb[(y * 2) * width + x * 2 + 1];
          int c = argb[(y * 2 + 1) * width + x * 2];
          int d = argb[(y * 2 + 1) * width + x * 2 + 1];
          next[y * nextWidth + x] = mix(mix(a, b), mix(c, d));
        }
      }
      width = nextWidth;
      height = nextHeight;
      argb = next;
    }
    return new Texture(width, height, argb);
  }

  public static byte[] toBytes(BakedAvatar avatar) throws IOException {
    ByteArrayOutputStream raw = new ByteArrayOutputStream(1 << 20);
    try (DataOutputStream out = new DataOutputStream(raw)) {
      avatar.write(out);
    }
    return raw.toByteArray();
  }

  public static BakedAvatar fromBytes(byte[] blob) {
    if (blob == null || blob.length < 16 || blob.length > MAX_BYTES) {
      return null;
    }
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
      BakedAvatar avatar = BakedAvatar.read(in);
      return valid(avatar) ? avatar : null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  public static boolean valid(BakedAvatar avatar) {
    return problem(avatar).isEmpty();
  }

  public static String problem(BakedAvatar avatar) {
    if (avatar == null
        || avatar.parts == null
        || avatar.textures == null
        || avatar.skeleton == null
        || avatar.clips == null
        || avatar.fit == null
        || avatar.fit.length != 16) {
      return "the model data is incomplete";
    }
    if (avatar.parts.length > MAX_PARTS) {
      return avatar.parts.length + " mesh parts, the limit is " + MAX_PARTS;
    }
    if (avatar.textures.length > MAX_TEXTURES) {
      return avatar.textures.length + " textures, the limit is " + MAX_TEXTURES;
    }
    if (avatar.clips.length > MAX_CLIPS) {
      return avatar.clips.length + " animation clips, the limit is " + MAX_CLIPS;
    }
    if (avatar.skeleton.count() > MAX_NODES) {
      return avatar.skeleton.count() + " bones, the limit is " + MAX_NODES;
    }
    if (avatar.triangles > MAX_TRIANGLES || avatar.triangles <= 0) {
      return avatar.triangles + " triangles, the limit is " + MAX_TRIANGLES;
    }
    if (!finite(avatar.width)
        || !finite(avatar.height)
        || !finite(avatar.depth)
        || !finite(avatar.fit)) {
      return "the model has broken measurements";
    }
    for (Texture texture : avatar.textures) {
      if (texture == null
          || texture.width() <= 0
          || texture.height() <= 0
          || texture.width() > MAX_EDGE
          || texture.height() > MAX_EDGE
          || texture.argb() == null
          || texture.argb().length != texture.width() * texture.height()) {
        return "a texture is missing or larger than " + MAX_EDGE + " pixels";
      }
    }
    for (BakedAvatar.Part part : avatar.parts) {
      if (part == null
          || part.positions == null
          || part.normals == null
          || part.uvs == null
          || part.indices == null) {
        return "a mesh part is incomplete";
      }
      if (!finite(part.positions)
          || !finite(part.normals)
          || !finite(part.uvs)
          || !finite(part.weights)) {
        return "a mesh part has broken coordinates";
      }
    }
    return "";
  }

  private static int mix(int first, int second) {
    int a = (((first >>> 24) & 0xFF) + ((second >>> 24) & 0xFF) + 1) >> 1;
    int r = ((((first >> 16) & 0xFF) + ((second >> 16) & 0xFF) + 1) >> 1) & 0xFF;
    int g = ((((first >> 8) & 0xFF) + ((second >> 8) & 0xFF) + 1) >> 1) & 0xFF;
    int b = (((first & 0xFF) + (second & 0xFF) + 1) >> 1) & 0xFF;
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  private static boolean finite(float value) {
    return !Float.isNaN(value) && !Float.isInfinite(value);
  }

  private static boolean finite(float[] values) {
    if (values == null) {
      return true;
    }
    for (float value : values) {
      if (!finite(value)) {
        return false;
      }
    }
    return true;
  }
}
