package me.cxiy.cxvisuals.modules.hud.mirror.portal;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;
import me.cxiy.cxvisuals.modules.hud.mirror.Reduce;

public final class PortalProtocol {
  private static final int FRAME = 'F';
  private static final int ERROR = 'E';
  private static final int HEADER = 12;
  private static final int LIMIT = 1 << 28;

  private PortalProtocol() {}

  public static void pump(InputStream source, FrameStore store, Consumer<String> log)
      throws IOException {
    DataInputStream in = new DataInputStream(source);
    ByteBuffer header = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN);
    byte[] payload = new byte[0];
    while (true) {
      int kind = in.read();
      if (kind < 0) {
        return;
      }
      try {
        in.readFully(header.array());
      } catch (EOFException e) {
        return;
      }
      int width = header.getInt(0);
      int height = header.getInt(4);
      int length = header.getInt(8);
      if (length < 0 || length > LIMIT) {
        throw new IOException("frame of " + length + " bytes");
      }
      if (payload.length < length) {
        payload = new byte[length];
      }
      try {
        in.readFully(payload, 0, length);
      } catch (EOFException e) {
        return;
      }
      if (kind == ERROR) {
        log.accept(new String(payload, 0, length, StandardCharsets.UTF_8));
      } else if (kind == FRAME && width > 0 && height > 0 && length >= width * 4 * height) {
        deliver(payload, width, height, length / height, store);
      }
    }
  }

  private static void deliver(byte[] payload, int width, int height, int stride, FrameStore store) {
    int level = Reduce.level(width, height, store.wanted());
    ByteBuffer target = store.begin(Reduce.size(width, level), Reduce.size(height, level));
    Reduce.copy(MemorySegment.ofArray(payload), stride, width, height, level, target);
    store.publish();
  }
}
