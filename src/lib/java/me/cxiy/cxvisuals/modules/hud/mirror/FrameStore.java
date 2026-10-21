package me.cxiy.cxvisuals.modules.hud.mirror;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FrameStore {
  public enum Status {
    OFF,
    WAITING,
    LIVE,
    PAUSED,
    LOST,
    UNSUPPORTED
  }

  public interface Reader {
    void read(ByteBuffer pixels, int width, int height, long revision);
  }

  public static final int DEFAULT_WANTED = 960;

  private final Object lock = new Object();
  private ByteBuffer front = ByteBuffer.allocateDirect(0);
  private ByteBuffer back = ByteBuffer.allocateDirect(0);
  private int frontWidth;
  private int frontHeight;
  private int backWidth;
  private int backHeight;
  private long revision;
  private volatile Status status = Status.OFF;
  private volatile int wanted = DEFAULT_WANTED;
  private volatile long frameNanos;

  public ByteBuffer begin(int width, int height) {
    int bytes = width * height * 4;
    if (back.capacity() < bytes) {
      back = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }
    back.clear().limit(bytes);
    backWidth = width;
    backHeight = height;
    return back;
  }

  public void publish() {
    synchronized (lock) {
      ByteBuffer swap = front;
      front = back;
      back = swap;
      frontWidth = backWidth;
      frontHeight = backHeight;
      revision++;
    }
    frameNanos = System.nanoTime();
    status = Status.LIVE;
  }

  public boolean read(long since, Reader reader) {
    synchronized (lock) {
      if (revision == since || frontWidth == 0) {
        return false;
      }
      front.clear().limit(frontWidth * frontHeight * 4);
      reader.read(front, frontWidth, frontHeight, revision);
      return true;
    }
  }

  public long revision() {
    synchronized (lock) {
      return revision;
    }
  }

  public int width() {
    synchronized (lock) {
      return frontWidth;
    }
  }

  public int height() {
    synchronized (lock) {
      return frontHeight;
    }
  }

  public boolean has() {
    return width() > 0;
  }

  public Status status() {
    return status;
  }

  public void status(Status next) {
    status = next;
  }

  public int wanted() {
    return wanted;
  }

  public void wanted(int width) {
    wanted = Math.max(1, width);
  }

  public long frameNanos() {
    return frameNanos;
  }

  public void clear() {
    synchronized (lock) {
      frontWidth = 0;
      frontHeight = 0;
      revision++;
    }
  }
}
