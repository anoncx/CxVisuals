package me.cxiy.cxvisuals.modules.download;

public final class Recording {
  public enum State {
    IDLE,
    RECORDING,
    SAVING,
    SAVED,
    FAILED
  }

  public interface Actions {
    void snapshot();

    void start();

    void finish();

    void discard();
  }

  private static final Actions IDLE_ACTIONS =
      new Actions() {
        @Override
        public void snapshot() {}

        @Override
        public void start() {}

        @Override
        public void finish() {}

        @Override
        public void discard() {}
      };

  private volatile State state = State.IDLE;
  private volatile int chunks;
  private volatile long bytes;
  private volatile String world = "";
  private volatile String notice = "";
  private Actions actions = IDLE_ACTIONS;

  public void actions(Actions next) {
    actions = next == null ? IDLE_ACTIONS : next;
  }

  public Actions actions() {
    return actions;
  }

  public State state() {
    return state;
  }

  public void state(State next) {
    state = next;
  }

  public boolean recording() {
    return state == State.RECORDING;
  }

  public int chunks() {
    return chunks;
  }

  public void chunks(int count) {
    chunks = count;
  }

  public long bytes() {
    return bytes;
  }

  public void bytes(long count) {
    bytes = count;
  }

  public String world() {
    return world;
  }

  public void world(String name) {
    world = name == null ? "" : name;
  }

  public String notice() {
    return notice;
  }

  public void notice(String text) {
    notice = text == null ? "" : text;
  }

  public static String size(long bytes) {
    if (bytes < 1024L * 1024L) {
      return Math.max(1L, bytes / 1024L) + " KB";
    }
    long tenths = bytes * 10L / (1024L * 1024L);
    return tenths / 10L + "." + tenths % 10L + " MB";
  }
}
