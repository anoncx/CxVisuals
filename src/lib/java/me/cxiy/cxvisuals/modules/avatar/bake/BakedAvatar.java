package me.cxiy.cxvisuals.modules.avatar.bake;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import me.cxiy.cxvisuals.modules.avatar.anim.Clip;
import me.cxiy.cxvisuals.modules.avatar.anim.Skeleton;
import me.cxiy.cxvisuals.modules.avatar.format.RawModel;

public final class BakedAvatar {
  public static final int VERSION = 1;
  private static final int MAGIC = 0x43584156;
  private static final int LIMIT = 1 << 26;

  public static final class Part {
    public final int texture;
    public final int color;
    public final boolean blend;
    public final boolean doubleSided;
    public final float[] positions;
    public final float[] normals;
    public final float[] uvs;
    public final int[] colors;
    public final byte[] joints;
    public final float[] weights;
    public final int[] indices;

    public Part(
        int texture,
        int color,
        boolean blend,
        boolean doubleSided,
        float[] positions,
        float[] normals,
        float[] uvs,
        int[] colors,
        byte[] joints,
        float[] weights,
        int[] indices) {
      this.texture = texture;
      this.color = color;
      this.blend = blend;
      this.doubleSided = doubleSided;
      this.positions = positions;
      this.normals = normals;
      this.uvs = uvs;
      this.colors = colors;
      this.joints = joints;
      this.weights = weights;
      this.indices = indices;
    }

    public int vertexCount() {
      return positions.length / 3;
    }

    public int triangles() {
      return indices.length / 3;
    }
  }

  public final Part[] parts;
  public final Texture[] textures;
  public final Skeleton skeleton;
  public final Clip[] clips;
  public final float[] fit;
  public final float width;
  public final float height;
  public final float depth;
  public final int triangles;
  public final int sourceTriangles;
  public final List<String> warnings;

  public BakedAvatar(
      Part[] parts,
      Texture[] textures,
      Skeleton skeleton,
      Clip[] clips,
      float[] fit,
      float width,
      float height,
      float depth,
      int sourceTriangles,
      List<String> warnings) {
    this.parts = parts;
    this.textures = textures;
    this.skeleton = skeleton;
    this.clips = clips;
    this.fit = fit;
    this.width = width;
    this.height = height;
    this.depth = depth;
    int total = 0;
    for (Part part : parts) {
      total += part.triangles();
    }
    this.triangles = total;
    this.sourceTriangles = sourceTriangles;
    this.warnings = List.copyOf(warnings);
  }

  public boolean skinned() {
    return skeleton.paletteSize() > 0;
  }

  public void write(DataOutputStream out) throws IOException {
    out.writeInt(MAGIC);
    out.writeInt(VERSION);
    out.writeFloat(width);
    out.writeFloat(height);
    out.writeFloat(depth);
    out.writeInt(sourceTriangles);
    floats(out, fit);
    out.writeInt(textures.length);
    for (Texture texture : textures) {
      out.writeInt(texture.width());
      out.writeInt(texture.height());
      ints(out, texture.argb());
    }
    out.writeInt(parts.length);
    for (Part part : parts) {
      out.writeInt(part.texture);
      out.writeInt(part.color);
      out.writeBoolean(part.blend);
      out.writeBoolean(part.doubleSided);
      floats(out, part.positions);
      floats(out, part.normals);
      floats(out, part.uvs);
      ints(out, part.colors);
      bytes(out, part.joints);
      floats(out, part.weights);
      ints(out, part.indices);
    }
    int nodes = skeleton.count();
    out.writeInt(nodes);
    for (int i = 0; i < nodes; i++) {
      out.writeUTF(skeleton.name(i));
      out.writeInt(skeleton.parent(i));
    }
    floats(out, skeleton.restT());
    floats(out, skeleton.restR());
    floats(out, skeleton.restS());
    int[] palette = new int[skeleton.paletteSize()];
    for (int i = 0; i < palette.length; i++) {
      palette[i] = skeleton.paletteNode(i);
    }
    ints(out, palette);
    floats(out, skeleton.inverseBind());
    out.writeInt(clips.length);
    for (Clip clip : clips) {
      out.writeUTF(clip.name());
      out.writeInt(clip.channels());
      for (int c = 0; c < clip.channels(); c++) {
        out.writeInt(clip.node(c));
        out.writeByte(clip.path(c).ordinal());
        out.writeByte(clip.interp(c).ordinal());
        floats(out, clip.times(c));
        floats(out, clip.values(c));
      }
    }
    out.writeInt(warnings.size());
    for (String warning : warnings) {
      out.writeUTF(warning);
    }
  }

  public static BakedAvatar read(DataInputStream in) throws IOException {
    if (in.readInt() != MAGIC) {
      throw new IOException("not a baked avatar");
    }
    int version = in.readInt();
    if (version != VERSION) {
      throw new IOException("baked avatar version " + version);
    }
    float width = in.readFloat();
    float height = in.readFloat();
    float depth = in.readFloat();
    int sourceTriangles = in.readInt();
    float[] fit = floats(in);
    if (fit == null || fit.length != 16) {
      throw new IOException("bad fit");
    }
    Texture[] textures = new Texture[count(in)];
    for (int i = 0; i < textures.length; i++) {
      int w = in.readInt();
      int h = in.readInt();
      int[] argb = ints(in);
      if (argb == null || argb.length != w * h) {
        throw new IOException("bad texture");
      }
      textures[i] = new Texture(w, h, argb);
    }
    Part[] parts = new Part[count(in)];
    for (int i = 0; i < parts.length; i++) {
      parts[i] =
          new Part(
              in.readInt(),
              in.readInt(),
              in.readBoolean(),
              in.readBoolean(),
              floats(in),
              floats(in),
              floats(in),
              ints(in),
              bytes(in),
              floats(in),
              ints(in));
      if (parts[i].positions == null
          || parts[i].normals == null
          || parts[i].uvs == null
          || parts[i].indices == null) {
        throw new IOException("bad part");
      }
    }
    int nodes = count(in);
    String[] names = new String[nodes];
    int[] parents = new int[nodes];
    for (int i = 0; i < nodes; i++) {
      names[i] = in.readUTF();
      parents[i] = in.readInt();
    }
    Skeleton skeleton =
        new Skeleton(names, parents, floats(in), floats(in), floats(in), ints(in), floats(in));
    Clip[] clips = new Clip[count(in)];
    RawModel.Path[] pathValues = RawModel.Path.values();
    RawModel.Interp[] interpValues = RawModel.Interp.values();
    for (int i = 0; i < clips.length; i++) {
      String name = in.readUTF();
      int channels = count(in);
      int[] targets = new int[channels];
      RawModel.Path[] paths = new RawModel.Path[channels];
      RawModel.Interp[] interps = new RawModel.Interp[channels];
      float[][] times = new float[channels][];
      float[][] values = new float[channels][];
      for (int c = 0; c < channels; c++) {
        targets[c] = in.readInt();
        paths[c] = pathValues[in.readUnsignedByte() % pathValues.length];
        interps[c] = interpValues[in.readUnsignedByte() % interpValues.length];
        times[c] = floats(in);
        values[c] = floats(in);
      }
      clips[i] = new Clip(name, targets, paths, interps, times, values);
    }
    String[] warnings = new String[count(in)];
    for (int i = 0; i < warnings.length; i++) {
      warnings[i] = in.readUTF();
    }
    return new BakedAvatar(
        parts,
        textures,
        skeleton,
        clips,
        fit,
        width,
        height,
        depth,
        sourceTriangles,
        List.of(warnings));
  }

  private static int count(DataInputStream in) throws IOException {
    int count = in.readInt();
    if (count < 0 || count > LIMIT) {
      throw new IOException("bad length");
    }
    return count;
  }

  private static void floats(DataOutputStream out, float[] values) throws IOException {
    if (values == null) {
      out.writeInt(-1);
      return;
    }
    out.writeInt(values.length);
    for (float value : values) {
      out.writeFloat(value);
    }
  }

  private static float[] floats(DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length == -1) {
      return null;
    }
    if (length < 0 || length > LIMIT) {
      throw new IOException("bad length");
    }
    float[] values = new float[length];
    for (int i = 0; i < length; i++) {
      values[i] = in.readFloat();
    }
    return values;
  }

  private static void ints(DataOutputStream out, int[] values) throws IOException {
    if (values == null) {
      out.writeInt(-1);
      return;
    }
    out.writeInt(values.length);
    for (int value : values) {
      out.writeInt(value);
    }
  }

  private static int[] ints(DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length == -1) {
      return null;
    }
    if (length < 0 || length > LIMIT) {
      throw new IOException("bad length");
    }
    int[] values = new int[length];
    for (int i = 0; i < length; i++) {
      values[i] = in.readInt();
    }
    return values;
  }

  private static void bytes(DataOutputStream out, byte[] values) throws IOException {
    if (values == null) {
      out.writeInt(-1);
      return;
    }
    out.writeInt(values.length);
    out.write(values);
  }

  private static byte[] bytes(DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length == -1) {
      return null;
    }
    if (length < 0 || length > LIMIT) {
      throw new IOException("bad length");
    }
    byte[] values = new byte[length];
    in.readFully(values);
    return values;
  }
}
