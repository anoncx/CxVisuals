package me.cxiy.cxvisuals.modules.avatar.library;

import java.util.Locale;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Clips;
import me.cxiy.cxvisuals.modules.avatar.anim.Clip;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipMap;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipSet;

public final class LibraryClips implements Clips {
  private static final Clip[] NO_CLIPS = new Clip[0];
  private static final int[] NO_SLOTS = new int[0];
  private static final String[] EVENT_NAMES = new String[ClipSet.EVENTS];

  static {
    for (int event = 0; event < ClipSet.EVENTS; event++) {
      String name = ClipSet.state(event).name();
      EVENT_NAMES[event] = name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
  }

  private final AvatarLibrary library;
  private final Supplier<AvatarEntry> selection;
  private final int[][] assigned = new int[ClipSet.EVENTS][];
  private AvatarEntry entry;
  private Clip[] source = NO_CLIPS;
  private ClipSet set;
  private int meshRevision = -1;
  private int mapRevision = -1;
  private boolean enabled;
  private int revision;

  public LibraryClips(AvatarLibrary library, Supplier<AvatarEntry> selection) {
    this.library = library;
    this.selection = selection;
  }

  private void sync() {
    AvatarEntry now = selection.get();
    if (now == null || now.state() != AvatarEntry.State.READY || now.world() == null) {
      if (entry != null) {
        entry = null;
        source = NO_CLIPS;
        revision++;
      }
      return;
    }
    boolean on = now.tune().clips();
    if (now == entry
        && now.revision() == meshRevision
        && now.clipRevision() == mapRevision
        && on == enabled) {
      return;
    }
    entry = now;
    meshRevision = now.revision();
    mapRevision = now.clipRevision();
    enabled = on;
    revision++;
    source = now.world().clips;
    ClipMap map = now.clipMap();
    set = ClipSet.of(source, map);
    for (int event = 0; event < ClipSet.EVENTS; event++) {
      String[] names = map.names(event);
      assigned[event] =
          names == null || names.length == 0 ? NO_SLOTS : ClipSet.resolve(source, names);
    }
  }

  @Override
  public int state() {
    sync();
    if (entry == null) {
      return NONE;
    }
    if (source.length == 0) {
      return EMPTY;
    }
    return enabled ? READY : DISABLED;
  }

  @Override
  public int revision() {
    return revision;
  }

  @Override
  public int handle() {
    return entry.handle();
  }

  @Override
  public int clipCount() {
    return source.length;
  }

  @Override
  public String clipName(int clip) {
    return source[clip].name();
  }

  @Override
  public float clipSeconds(int clip) {
    return source[clip].duration();
  }

  @Override
  public int eventCount() {
    return ClipSet.EVENTS;
  }

  @Override
  public String eventName(int event) {
    return Lang.tr(EVENT_NAMES[event]);
  }

  @Override
  public int assignedCount(int event) {
    return assigned[event].length;
  }

  @Override
  public int assigned(int event, int slot) {
    return assigned[event][slot];
  }

  @Override
  public boolean off(int event) {
    return entry.clipMap().isOff(event);
  }

  @Override
  public int autoClip(int event) {
    int[] resolved = set.clips(event);
    return resolved.length == 0 ? -1 : resolved[0];
  }

  @Override
  public boolean random(int event) {
    return entry.clipMap().random(event);
  }

  @Override
  public void toggle(int event, int clip) {
    if (entry == null || clip < 0 || clip >= source.length) {
      return;
    }
    int[] now = assigned[event];
    int at = -1;
    for (int i = 0; i < now.length; i++) {
      if (now[i] == clip) {
        at = i;
      }
    }
    String[] names = new String[at >= 0 ? now.length - 1 : now.length + 1];
    int count = 0;
    for (int i = 0; i < now.length; i++) {
      if (i != at) {
        names[count++] = ClipSet.key(source, now[i]);
      }
    }
    if (at < 0) {
      names[count] = ClipSet.key(source, clip);
    }
    ClipMap map = entry.clipMap();
    library.remap(
        entry, names.length == 0 ? map.auto(event) : map.with(event, names, map.random(event)));
  }

  @Override
  public void toggleOff(int event) {
    if (entry == null) {
      return;
    }
    ClipMap map = entry.clipMap();
    library.remap(entry, map.isOff(event) ? map.auto(event) : map.off(event));
  }

  @Override
  public void setRandom(int event, boolean random) {
    if (entry == null) {
      return;
    }
    ClipMap map = entry.clipMap();
    String[] names = map.names(event);
    if (names != null && names.length > 0 && map.random(event) != random) {
      library.remap(entry, map.with(event, names, random));
    }
  }
}
