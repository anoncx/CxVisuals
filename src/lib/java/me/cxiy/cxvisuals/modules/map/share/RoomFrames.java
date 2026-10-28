package me.cxiy.cxvisuals.modules.map.share;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class RoomFrames {
  private static final int HEAD = Long.BYTES + Integer.BYTES;

  private RoomFrames() {}

  public static List<RoomSync.Frame> parse(byte[] body) throws IOException {
    ByteBuffer in = ByteBuffer.wrap(body);
    List<RoomSync.Frame> frames = new ArrayList<>();
    while (in.hasRemaining()) {
      if (in.remaining() < HEAD) {
        throw new IOException("unreadable page");
      }
      long seq = in.getLong();
      int length = in.getInt();
      if (length < 0 || length > in.remaining()) {
        throw new IOException("unreadable page");
      }
      byte[] blob = new byte[length];
      in.get(blob);
      frames.add(new RoomSync.Frame(seq, blob));
    }
    return frames;
  }
}
