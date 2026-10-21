package me.cxiy.cxvisuals.menu.views;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.menu.model.Control;
import me.cxiy.cxvisuals.menu.model.Glyph;
import me.cxiy.cxvisuals.menu.model.Item;
import me.cxiy.cxvisuals.menu.model.QuickPlay;
import me.cxiy.cxvisuals.menu.model.TextStore;
import me.cxiy.cxvisuals.menu.model.TitleSource;

final class Channels {
  static final int COLUMNS = 4;
  static final int ROWS = 3;
  static final int PER_PAGE = COLUMNS * ROWS;
  static final int MAX_PAGES = 8;
  static final String PINNED = "nav:" + Glyph.SINGLE;

  private static final float INTERVAL = 0.4F;
  private static final int MOST_WORLDS = 6;
  private static final int MOST_SERVERS = 6;
  private static final String WIDGET = "widget:";
  private static final String SHOTS = Lang.key("Screenshots");

  private final TitleSource source;
  private final QuickPlay quick;
  private final TextStore store;
  private final List<Channel> pool = new ArrayList<>(PER_PAGE * 2);
  private final Map<String, Channel> loose = new LinkedHashMap<>();
  private List<Channel> slots = new ArrayList<>(PER_PAGE);
  private long signature = Long.MIN_VALUE;
  private float cooldown;

  Channels(TitleSource source, QuickPlay quick, TextStore store) {
    this.source = source;
    this.quick = quick;
    this.store = store;
    rebuild();
  }

  int size() {
    return slots.size();
  }

  Channel get(int index) {
    return slots.get(index);
  }

  int pages() {
    return slots.size() / PER_PAGE;
  }

  boolean pinned(int index) {
    return index == 0 && PINNED.equals(slots.get(0).id);
  }

  boolean movable(int index) {
    return !slots.get(index).empty() && !pinned(index);
  }

  boolean poll(float dt) {
    cooldown -= dt;
    if (cooldown > 0.0F) {
      return false;
    }
    cooldown = INTERVAL;
    if (!source.changed() && signature == stamp()) {
      return false;
    }
    rebuild();
    return true;
  }

  boolean move(int from, int to) {
    if (from == to
        || from < 0
        || to < 0
        || from >= slots.size()
        || to >= slots.size()
        || !movable(from)
        || pinned(to)) {
      return false;
    }
    Channel moved = slots.get(from);
    slots.set(from, slots.get(to));
    slots.set(to, moved);
    settle();
    return true;
  }

  boolean place(int slot, String widgetId) {
    Widget widget = Widgets.byId(widgetId);
    if (widget == null
        || slot < 0
        || slot >= slots.size()
        || !slots.get(slot).empty()
        || placed(widgetId)) {
      return false;
    }
    slots.set(slot, Channel.of(widget));
    settle();
    return true;
  }

  boolean remove(int slot) {
    if (slot < 0 || slot >= slots.size() || slots.get(slot).widget == null) {
      return false;
    }
    slots.set(slot, Channel.EMPTY);
    settle();
    return true;
  }

  List<Widget> available() {
    List<Widget> open = new ArrayList<>(Widgets.all().size());
    for (Widget widget : Widgets.all()) {
      if (!placed(widget.id())) {
        open.add(widget);
      }
    }
    return open;
  }

  private boolean placed(String widgetId) {
    for (Channel channel : slots) {
      if (channel.widget != null && channel.widget.id().equals(widgetId)) {
        return true;
      }
    }
    return false;
  }

  private long stamp() {
    long hash = 17L;
    if (quick == null) {
      return hash;
    }
    try {
      hash = hash * 31L + shot().hashCode();
      for (int group = 0; group < QuickPlay.GROUPS; group++) {
        int count =
            Math.min(group == QuickPlay.WORLDS ? MOST_WORLDS : MOST_SERVERS, quick.count(group));
        hash = hash * 31L + count;
        for (int i = 0; i < count; i++) {
          Item item = quick.item(group, i);
          hash =
              (hash * 31L + item.title.hashCode()) * 31L
                  + item.trailing.hashCode()
                  + (item.flagged ? 1L : 0L);
        }
      }
    } catch (RuntimeException e) {
      return hash;
    }
    return hash;
  }

  private String shot() {
    String label = quick.shotLabel();
    return label == null ? "" : label;
  }

  private void rebuild() {
    pool.clear();
    for (Control control : source.nav()) {
      String key =
          control.icon() != Glyph.NONE ? Integer.toString(control.icon()) : control.label();
      pool.add(
          Channel.of(
              "nav:" + key,
              control.label(),
              "",
              null,
              control.icon(),
              control.enabled(),
              false,
              control::press));
    }
    if (quick != null) {
      try {
        add(QuickPlay.WORLDS, MOST_WORLDS, Glyph.WORLD, "world:");
        add(QuickPlay.SERVERS, MOST_SERVERS, Glyph.SERVER, "server:");
        String shot = shot();
        if (!shot.isEmpty()) {
          pool.add(
              Channel.of(
                  "shots",
                  Lang.tr(SHOTS),
                  shot,
                  quick.shot(),
                  Glyph.SHOT,
                  true,
                  false,
                  quick::openShots));
        }
      } catch (RuntimeException e) {
        pool.removeIf(
            channel ->
                channel.glyph == Glyph.WORLD
                    || channel.glyph == Glyph.SERVER
                    || channel.glyph == Glyph.SHOT);
      }
    }
    arrange();
    signature = stamp();
  }

  private void add(int group, int most, int glyph, String prefix) {
    int count = Math.min(most, quick.count(group));
    for (int i = 0; i < count; i++) {
      Item item = quick.item(group, i);
      int index = i;
      pool.add(
          Channel.of(
              prefix + item.title,
              item.title,
              item.trailing,
              item.icon,
              glyph,
              true,
              item.flagged,
              () -> quick.open(group, index)));
    }
  }

  private void arrange() {
    loose.clear();
    for (Channel channel : pool) {
      loose.put(channel.id, channel);
    }
    String[] saved = store.read().split("\n", -1);
    List<Channel> next = new ArrayList<>(Math.max(PER_PAGE, saved.length));
    for (String id : saved) {
      next.add(resolve(id));
    }
    for (Channel channel : loose.values()) {
      int free = next.indexOf(Channel.EMPTY);
      if (free < 0) {
        next.add(channel);
      } else {
        next.set(free, channel);
      }
    }
    slots = next;
    pinFirst();
    trim();
    persist();
  }

  private Channel resolve(String id) {
    if (id.isEmpty()) {
      return Channel.EMPTY;
    }
    Channel known = loose.remove(id);
    if (known != null) {
      return known;
    }
    Widget widget = id.startsWith(WIDGET) ? Widgets.byId(id.substring(WIDGET.length())) : null;
    return widget == null ? Channel.EMPTY : Channel.of(widget);
  }

  private void pinFirst() {
    for (int i = 1; i < slots.size(); i++) {
      if (PINNED.equals(slots.get(i).id)) {
        Channel first = slots.get(0);
        slots.set(0, slots.get(i));
        slots.set(i, first);
        return;
      }
    }
  }

  private int used() {
    int used = 0;
    for (int i = 0; i < slots.size(); i++) {
      if (!slots.get(i).empty()) {
        used = i + 1;
      }
    }
    return used;
  }

  private void trim() {
    int used = used();
    int pages = Math.max(1, (used + PER_PAGE - 1) / PER_PAGE);
    if (used >= pages * PER_PAGE && pages < MAX_PAGES) {
      pages++;
    }
    while (slots.size() > pages * PER_PAGE) {
      slots.remove(slots.size() - 1);
    }
    while (slots.size() < pages * PER_PAGE) {
      slots.add(Channel.EMPTY);
    }
  }

  private void settle() {
    trim();
    persist();
  }

  private void persist() {
    int used = used();
    StringBuilder out = new StringBuilder(used * 12);
    for (int i = 0; i < used; i++) {
      if (i > 0) {
        out.append('\n');
      }
      out.append(slots.get(i).id);
    }
    String encoded = out.toString();
    if (!encoded.equals(store.read())) {
      store.write(encoded);
    }
  }
}
