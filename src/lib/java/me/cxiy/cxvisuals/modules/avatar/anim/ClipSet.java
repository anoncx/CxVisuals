package me.cxiy.cxvisuals.modules.avatar.anim;

import java.util.Arrays;
import java.util.Locale;

public final class ClipSet {
  public enum State {
    IDLE(null, "idle", "stand", "rest", "breath"),
    WALK(IDLE, "walk", "move"),
    RUN(WALK, "run", "sprint", "gallop"),
    JUMP(IDLE, "jump", "hop"),
    FALL(JUMP, "fall"),
    SNEAK(IDLE, "sneak", "crouch"),
    SWIM(IDLE, "swim"),
    FLY(IDLE, "fly", "glide", "flap"),
    ATTACK(null, "attack", "swing", "punch", "slash", "strike"),
    HURT(null, "hurt", "damage", "hit"),
    DEATH(null, "death", "die", "dead");

    final State fallback;
    final String[] keywords;

    State(State fallback, String... keywords) {
      this.fallback = fallback;
      this.keywords = keywords;
    }
  }

  private static final State[] STATES = State.values();
  private static final int[] NONE = new int[0];

  public static final int EVENTS = STATES.length;

  private final int[][] clips = new int[EVENTS][];
  private final boolean[] random = new boolean[EVENTS];
  private final boolean[] own = new boolean[EVENTS];

  private ClipSet() {}

  public static State state(int ordinal) {
    return STATES[ordinal];
  }

  public static ClipSet of(Clip[] clips) {
    return of(clips, ClipMap.AUTO);
  }

  public static ClipSet of(Clip[] clips, ClipMap map) {
    ClipSet set = new ClipSet();
    int[] matched = match(clips);
    int[][] direct = new int[EVENTS][];
    for (int event = 0; event < EVENTS; event++) {
      String[] names = map.names(event);
      if (names != null && names.length == 0) {
        direct[event] = NONE;
        continue;
      }
      int[] found = names == null ? NONE : resolve(clips, names);
      if (found.length > 0) {
        direct[event] = found;
        set.random[event] = map.random(event);
      } else if (matched[event] >= 0) {
        direct[event] = new int[] {matched[event]};
      }
    }
    for (int event = 0; event < EVENTS; event++) {
      int[] mine = direct[event];
      if (mine != null) {
        set.clips[event] = mine;
        set.own[event] = mine.length > 0;
        continue;
      }
      set.clips[event] = NONE;
      State cursor = STATES[event].fallback;
      while (cursor != null) {
        int[] from = direct[cursor.ordinal()];
        if (from != null && from.length > 0) {
          set.clips[event] = from;
          set.random[event] = set.random[cursor.ordinal()];
          break;
        }
        cursor = cursor.fallback;
      }
    }
    return set;
  }

  public static String key(Clip[] clips, int index) {
    String name = clips[index].name();
    int earlier = 0;
    for (int i = 0; i < index; i++) {
      if (clips[i].name().equals(name)) {
        earlier++;
      }
    }
    return earlier == 0 ? name : name + "#" + (earlier + 1);
  }

  public static int find(Clip[] clips, String key) {
    for (int i = 0; i < clips.length; i++) {
      if (key(clips, i).equals(key)) {
        return i;
      }
    }
    return -1;
  }

  public static int[] resolve(Clip[] clips, String[] keys) {
    int[] found = new int[keys.length];
    int count = 0;
    outer:
    for (String key : keys) {
      int index = find(clips, key);
      if (index < 0) {
        continue;
      }
      for (int i = 0; i < count; i++) {
        if (found[i] == index) {
          continue outer;
        }
      }
      found[count++] = index;
    }
    return count == found.length ? found : Arrays.copyOf(found, count);
  }

  private static int[] match(Clip[] clips) {
    int[] matched = new int[EVENTS];
    Arrays.fill(matched, -1);
    boolean any = false;
    for (State state : STATES) {
      outer:
      for (int c = 0; c < clips.length; c++) {
        String name = clips[c].name().toLowerCase(Locale.ROOT);
        for (String keyword : state.keywords) {
          if (name.contains(keyword) && !claimed(matched, c, state)) {
            matched[state.ordinal()] = c;
            any = true;
            break outer;
          }
        }
      }
    }
    if (!any && clips.length > 0) {
      matched[State.IDLE.ordinal()] = 0;
    }
    return matched;
  }

  private static boolean claimed(int[] matched, int clip, State state) {
    for (int i = 0; i < state.ordinal(); i++) {
      if (matched[i] == clip) {
        return true;
      }
    }
    return false;
  }

  public int[] clips(int event) {
    return clips[event];
  }

  public boolean random(int event) {
    return random[event];
  }

  public boolean own(int event) {
    return own[event];
  }

  public boolean own(State state) {
    return own[state.ordinal()];
  }

  public int clip(State state) {
    int[] options = clips[state.ordinal()];
    return options.length == 0 ? -1 : options[0];
  }
}
