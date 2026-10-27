package me.cxiy.cxvisuals.modules.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class RegionCodec {
  public static final int MAGIC = 0x43584D31;
  public static final int VERSION = 3;
  private static final int CHUNK_SLOTS = Region.CHUNKS * Region.CHUNKS;

  private RegionCodec() {}

  public static byte[] encode(Region region, int[] thumbnail, BlockPalette palette)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 16);
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeInt(MAGIC);
    out.writeShort(VERSION);
    out.writeInt(region.x);
    out.writeInt(region.z);
    byte[] thumb = deflate(ints(thumbnail));
    out.writeInt(thumb.length);
    out.write(thumb);
    ByteArrayOutputStream raw = new ByteArrayOutputStream(1 << 18);
    DataOutputStream body = new DataOutputStream(raw);
    for (long stamp : region.stamps) {
      body.writeLong(stamp);
    }
    Locals locals = new Locals();
    for (int slot = 0; slot < CHUNK_SLOTS; slot++) {
      if (region.stamps[slot] != 0L) {
        int baseX = (slot & 31) << 4;
        int baseZ = (slot >> 5) << 4;
        for (int row = 0; row < 16; row++) {
          int from = Region.index(baseX, baseZ + row);
          for (int i = 0; i < 16; i++) {
            locals.add(region.block[from + i]);
            locals.add(region.ground[from + i]);
          }
        }
      }
    }
    locals.write(body, palette);
    Region.Column column = new Region.Column();
    for (int slot = 0; slot < CHUNK_SLOTS; slot++) {
      if (region.stamps[slot] != 0L) {
        region.read(slot & 31, slot >> 5, column);
        writeColumn(body, column, locals);
      }
    }
    body.flush();
    byte[] packed = deflate(raw.toByteArray());
    out.writeInt(packed.length);
    out.write(packed);
    out.flush();
    return bytes.toByteArray();
  }

  public static int[] thumbnail(byte[] file) throws IOException {
    DataInputStream in = header(file);
    in.readInt();
    in.readInt();
    return thumbnail(in);
  }

  public static Region decode(byte[] file, BlockPalette palette) throws IOException {
    DataInputStream in = header(file);
    int x = in.readInt();
    int z = in.readInt();
    int thumb = in.readInt();
    in.skipNBytes(thumb);
    int length = in.readInt();
    byte[] packed = in.readNBytes(length);
    DataInputStream body =
        new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(packed)));
    Region region = new Region(x, z);
    long[] stamps = new long[CHUNK_SLOTS];
    for (int i = 0; i < CHUNK_SLOTS; i++) {
      stamps[i] = body.readLong();
    }
    char[] globals = Locals.read(body, palette);
    Region.Column column = new Region.Column();
    for (int slot = 0; slot < CHUNK_SLOTS; slot++) {
      if (stamps[slot] != 0L) {
        readColumn(body, column, globals);
        region.write(slot & 31, slot >> 5, column, stamps[slot]);
      }
    }
    region.saved(region.revision());
    return region;
  }

  static final class Locals {
    private final java.util.Map<Character, Character> local = new java.util.HashMap<>();
    private final java.util.List<Character> order = new java.util.ArrayList<>();

    void add(char global) {
      if (global != BlockPalette.NONE && !local.containsKey(global)) {
        order.add(global);
        local.put(global, (char) order.size());
      }
    }

    char of(char global) {
      Character found = local.get(global);
      return found == null ? (char) BlockPalette.NONE : found;
    }

    void write(DataOutputStream out, BlockPalette palette) throws IOException {
      out.writeInt(order.size());
      for (char global : order) {
        out.writeUTF(palette.key(global));
      }
    }

    static char[] read(DataInputStream in, BlockPalette palette) throws IOException {
      int count = in.readInt();
      if (count < 0 || count >= BlockPalette.LIMIT) {
        throw new IOException("bad palette");
      }
      char[] globals = new char[count + 1];
      for (int i = 1; i <= count; i++) {
        globals[i] = (char) palette.id(in.readUTF());
      }
      return globals;
    }
  }

  static void writeColumn(DataOutputStream out, Region.Column column, Locals locals)
      throws IOException {
    writeColumn(out, column);
    for (int i = 0; i < Region.CHUNK_COLUMNS; i++) {
      out.writeChar(locals.of(column.block[i]));
    }
    out.write(column.depth);
    for (int i = 0; i < Region.CHUNK_COLUMNS; i++) {
      out.writeShort(column.floor[i]);
      out.writeChar(locals.of(column.ground[i]));
    }
  }

  static void readColumn(DataInputStream in, Region.Column column, char[] globals)
      throws IOException {
    readColumn(in, column);
    for (int i = 0; i < Region.CHUNK_COLUMNS; i++) {
      column.block[i] = global(globals, in.readChar());
    }
    in.readFully(column.depth);
    for (int i = 0; i < Region.CHUNK_COLUMNS; i++) {
      column.floor[i] = in.readShort();
      column.ground[i] = global(globals, in.readChar());
    }
  }

  private static char global(char[] globals, int local) {
    return local < globals.length ? globals[local] : (char) BlockPalette.NONE;
  }

  static void writeColumn(DataOutputStream out, Region.Column column) throws IOException {
    for (int i = 0; i < Region.CHUNK_COLUMNS; i++) {
      int color = column.color[i];
      out.writeByte(color >> 16);
      out.writeByte(color >> 8);
      out.writeByte(color);
    }
    for (int i = 0; i < Region.CHUNK_COLUMNS; i++) {
      out.writeShort(column.height[i]);
    }
    out.write(column.water);
  }

  static void readColumn(DataInputStream in, Region.Column column) throws IOException {
    for (int i = 0; i < Region.CHUNK_COLUMNS; i++) {
      int r = in.readUnsignedByte();
      int g = in.readUnsignedByte();
      column.color[i] = (r << 16) | (g << 8) | in.readUnsignedByte();
    }
    for (int i = 0; i < Region.CHUNK_COLUMNS; i++) {
      column.height[i] = in.readShort();
    }
    in.readFully(column.water);
  }

  private static DataInputStream header(byte[] file) throws IOException {
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(file));
    if (in.readInt() != MAGIC || in.readUnsignedShort() != VERSION) {
      throw new IOException("not a map region");
    }
    return in;
  }

  private static int[] thumbnail(DataInputStream in) throws IOException {
    int length = in.readInt();
    byte[] packed = in.readNBytes(length);
    DataInputStream body =
        new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(packed)));
    int[] out = new int[TileBaker.PIXELS];
    for (int i = 0; i < out.length; i++) {
      out[i] = body.readInt();
    }
    return out;
  }

  private static byte[] ints(int[] values) {
    byte[] out = new byte[values.length * 4];
    for (int i = 0, at = 0; i < values.length; i++, at += 4) {
      int v = values[i];
      out[at] = (byte) (v >> 24);
      out[at + 1] = (byte) (v >> 16);
      out[at + 2] = (byte) (v >> 8);
      out[at + 3] = (byte) v;
    }
    return out;
  }

  static byte[] deflate(byte[] raw) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
    Deflater deflater = new Deflater(Deflater.BEST_SPEED);
    try (DeflaterOutputStream stream = new DeflaterOutputStream(out, deflater, 1 << 14)) {
      stream.write(raw);
    } finally {
      deflater.end();
    }
    return out.toByteArray();
  }
}
