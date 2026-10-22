package me.cxiy.cxvisuals.modules.friends;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Identity {
  public enum State {
    OFF,
    READY,
    LOCKED
  }

  public record Member(String uuid, String name, boolean home) {}

  public interface Actions {
    void handle(String handle);

    void offer();

    void claim(String code);

    void unlink(String uuid);

    void adopt();
  }

  public static final int HANDLE_MIN = 3;
  public static final int HANDLE_MAX = 16;
  private static final Actions IDLE =
      new Actions() {
        @Override
        public void handle(String handle) {}

        @Override
        public void offer() {}

        @Override
        public void claim(String code) {}

        @Override
        public void unlink(String uuid) {}

        @Override
        public void adopt() {}
      };

  private final List<Member> members = new ArrayList<>();
  private final List<Member> membersView = Collections.unmodifiableList(members);
  private Actions actions = IDLE;
  private State state = State.OFF;
  private String self = "";
  private String handle = "";
  private String code = "";
  private long codeUntil;
  private String notice = "";
  private boolean busy;
  private int revision;

  public void actions(Actions next) {
    actions = next == null ? IDLE : next;
  }

  public Actions actions() {
    return actions;
  }

  public State state() {
    return state;
  }

  public String self() {
    return self;
  }

  public String handle() {
    return handle;
  }

  public List<Member> members() {
    return membersView;
  }

  public String code() {
    return codeUntil > System.currentTimeMillis() ? code : "";
  }

  public long codeUntil() {
    return codeUntil;
  }

  public String notice() {
    return notice;
  }

  public boolean busy() {
    return busy;
  }

  public int revision() {
    return revision;
  }

  public void state(State next) {
    state = next;
    revision++;
  }

  public void handle(String next) {
    handle = next == null ? "" : next;
    revision++;
  }

  public void roster(String own, List<Member> next) {
    self = own == null ? "" : own;
    members.clear();
    members.addAll(next);
    revision++;
  }

  public void code(String next, long until) {
    code = next == null ? "" : next;
    codeUntil = until;
    revision++;
  }

  public void notice(String text) {
    notice = text == null ? "" : text;
    revision++;
  }

  public void busy(boolean on) {
    busy = on;
    revision++;
  }

  public void clear() {
    state = State.OFF;
    self = "";
    handle = "";
    code = "";
    codeUntil = 0L;
    notice = "";
    busy = false;
    members.clear();
    revision++;
  }

  public static boolean validHandle(String text) {
    if (text.length() < HANDLE_MIN || text.length() > HANDLE_MAX) {
      return false;
    }
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (!(c < 128 && Character.isLetterOrDigit(c)) && c != '_') {
        return false;
      }
    }
    return true;
  }
}
