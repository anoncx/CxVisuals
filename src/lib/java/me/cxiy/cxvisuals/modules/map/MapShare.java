package me.cxiy.cxvisuals.modules.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

public final class MapShare {
  public record Chunk(int x, int z, long stamp, Region.Column column) {}

  public record Area(String scope, String dimension, List<Chunk> chunks) {}

  public static final int MAGIC = 0x43584D53;
  public static final int VERSION = 2;
  public static final int MAX_CHUNKS = 16_384;
  public static final int MAX_BYTES = 8 << 20;
  private static final int TEXT_LIMIT = 128;
  private static final int WORLD_CHUNKS = 30_000_000 >> 4;

  private MapShare() {}

  public static byte[] encode(
      String scope, String dimension, List<Chunk> chunks, BlockPalette palette) throws IOException {
    ByteArrayOutputStream raw = new ByteArrayOutputStream(chunks.size() * 1600 + 64);
    DataOutputStream out = new DataOutputStream(raw);
    out.writeInt(MAGIC);
    out.writeShort(VERSION);
    out.writeUTF(clip(scope));
    out.writeUTF(clip(dimension));
    int count = Math.min(chunks.size(), MAX_CHUNKS);
    RegionCodec.Locals locals = new RegionCodec.Locals();
    for (int i = 0; i < count; i++) {
      for (char block : chunks.get(i).column().block) {
        locals.add(block);
      }
    }
    locals.write(out, palette);
    out.writeInt(count);
    for (int i = 0; i < count; i++) {
      Chunk chunk = chunks.get(i);
      out.writeInt(chunk.x());
      out.writeInt(chunk.z());
      out.writeLong(chunk.stamp());
      RegionCodec.writeColumn(out, chunk.column(), locals);
    }
    out.flush();
    return RegionCodec.deflate(raw.toByteArray());
  }

  public static Area decode(byte[] blob, BlockPalette palette) {
    if (blob == null || blob.length == 0 || blob.length > MAX_BYTES) {
      return null;
    }
    try (DataInputStream in =
        new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(blob)))) {
      if (in.readInt() != MAGIC || in.readUnsignedShort() != VERSION) {
        return null;
      }
      String scope = clip(in.readUTF());
      String dimension = clip(in.readUTF());
      char[] globals = RegionCodec.Locals.read(in, palette);
      int count = in.readInt();
      if (count < 0 || count > MAX_CHUNKS) {
        return null;
      }
      List<Chunk> chunks = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        int x = in.readInt();
        int z = in.readInt();
        long stamp = in.readLong();
        Region.Column column = new Region.Column();
        RegionCodec.readColumn(in, column, globals);
        if (Math.abs(x) <= WORLD_CHUNKS && Math.abs(z) <= WORLD_CHUNKS && stamp > 0L) {
          chunks.add(new Chunk(x, z, stamp, column));
        }
      }
      return dimension.isEmpty() ? null : new Area(scope, dimension, chunks);
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static String clip(String value) {
    if (value == null) {
      return "";
    }
    String plain = value.strip();
    return plain.length() > TEXT_LIMIT ? plain.substring(0, TEXT_LIMIT) : plain;
  }
}
