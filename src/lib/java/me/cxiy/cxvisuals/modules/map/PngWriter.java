package me.cxiy.cxvisuals.modules.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class PngWriter {
  private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

  private PngWriter() {}

  public static byte[] encode(int[] argb, int width, int height) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream(argb.length + 128);
      out.write(SIGNATURE);
      byte[] header = new byte[13];
      put(header, 0, width);
      put(header, 4, height);
      header[8] = 8;
      header[9] = 6;
      chunk(out, "IHDR", header);
      ByteArrayOutputStream raw = new ByteArrayOutputStream(argb.length * 4 + height);
      try (DeflaterOutputStream zip =
          new DeflaterOutputStream(raw, new Deflater(Deflater.BEST_COMPRESSION))) {
        byte[] row = new byte[1 + width * 4];
        for (int y = 0; y < height; y++) {
          for (int x = 0; x < width; x++) {
            int pixel = argb[y * width + x];
            int at = 1 + x * 4;
            row[at] = (byte) (pixel >> 16);
            row[at + 1] = (byte) (pixel >> 8);
            row[at + 2] = (byte) pixel;
            row[at + 3] = (byte) (pixel >>> 24);
          }
          zip.write(row);
        }
      }
      chunk(out, "IDAT", raw.toByteArray());
      chunk(out, "IEND", new byte[0]);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void chunk(ByteArrayOutputStream out, String type, byte[] data)
      throws IOException {
    byte[] length = new byte[4];
    put(length, 0, data.length);
    out.write(length);
    byte[] name = type.getBytes(StandardCharsets.US_ASCII);
    out.write(name);
    out.write(data);
    CRC32 crc = new CRC32();
    crc.update(name);
    crc.update(data);
    byte[] sum = new byte[4];
    put(sum, 0, (int) crc.getValue());
    out.write(sum);
  }

  private static void put(byte[] to, int at, int value) {
    to[at] = (byte) (value >>> 24);
    to[at + 1] = (byte) (value >>> 16);
    to[at + 2] = (byte) (value >>> 8);
    to[at + 3] = (byte) value;
  }
}
