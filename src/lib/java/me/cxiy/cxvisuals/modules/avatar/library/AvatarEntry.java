package me.cxiy.cxvisuals.modules.avatar.library;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipMap;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.bake.Tune;

public final class AvatarEntry {
  public enum State {
    IMPORTING,
    LOADING,
    READY,
    BROKEN
  }

  private static final AtomicInteger HANDLES = new AtomicInteger(1);

  private final String id;
  private final String name;
  private final String format;
  private final String hash;
  private final String source;
  private final long created;
  private final int handle = HANDLES.getAndIncrement();
  private State state;
  private Tune tune;
  private ClipMap clipMap = ClipMap.AUTO;
  private int clipRevision;
  private BakedAvatar world;
  private BakedAvatar preview;
  private int budget;
  private int revision;
  private int triangles = -1;
  private int clips;
  private boolean loading;

  AvatarEntry(
      String id,
      String name,
      String format,
      String hash,
      String source,
      long created,
      State state,
      Tune tune) {
    this.id = id;
    this.name = name;
    this.format = format;
    this.hash = hash;
    this.source = source;
    this.created = created;
    this.state = state;
    this.tune = tune;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String format() {
    return format;
  }

  public String hash() {
    return hash;
  }

  public String source() {
    return source;
  }

  public long created() {
    return created;
  }

  public int handle() {
    return handle;
  }

  public State state() {
    return state;
  }

  public Tune tune() {
    return tune;
  }

  public ClipMap clipMap() {
    return clipMap;
  }

  public int clipRevision() {
    return clipRevision;
  }

  public BakedAvatar world() {
    return world;
  }

  public BakedAvatar preview() {
    return preview;
  }

  public int budget() {
    return budget;
  }

  public int revision() {
    return revision;
  }

  public boolean animated() {
    return clips > 0 && tune.clips();
  }

  public String meta() {
    return meta(format, triangles, clips);
  }

  public static String meta(String format, int triangles, int clips) {
    StringBuilder text = new StringBuilder(Lang.tr(format));
    if (triangles >= 0) {
      text.append(" · ");
      if (triangles < 1000) {
        text.append(triangles);
      } else {
        text.append(String.format(Locale.ROOT, "%.1fK", triangles / 1000.0));
      }
      text.append(' ').append(Lang.tr("TRIS"));
    }
    if (clips > 0) {
      text.append(" · ").append(clips).append(' ').append(Lang.count(clips, "CLIP", "CLIPS"));
    }
    return text.toString();
  }

  void state(State next) {
    state = next;
  }

  void tune(Tune next) {
    tune = next;
  }

  void clipMap(ClipMap next) {
    clipMap = next;
    clipRevision++;
  }

  void stats(int triangleCount, int clipCount) {
    triangles = triangleCount;
    clips = clipCount;
  }

  void baked(BakedAvatar nextWorld, BakedAvatar nextPreview, int nextBudget) {
    world = nextWorld;
    preview = nextPreview;
    budget = nextBudget;
    triangles = nextWorld.triangles;
    clips = nextWorld.clips.length;
    state = State.READY;
    revision++;
  }

  boolean loading() {
    return loading;
  }

  void loading(boolean flag) {
    loading = flag;
  }
}
