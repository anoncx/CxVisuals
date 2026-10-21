package me.cxiy.cxvisuals.modules.accounts;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AccountBook {
  public enum Order {
    RECENT,
    ADDED,
    NAME;

    public static final String[] LABELS = {"Recent", "Added", "Name"};
  }

  public record Added(Entry entry, boolean fresh) {}

  private static final String STORE = "accounts";
  private static final Type LIST = new TypeToken<List<Entry>>() {}.getType();

  private final Vault vault;
  private final Gson gson = new Gson();
  private final List<Entry> entries = new ArrayList<>();
  private int revision;
  private boolean saved = true;

  public AccountBook(Vault vault) {
    this.vault = vault;
  }

  public synchronized boolean load() {
    entries.clear();
    String json = vault.read(STORE);
    boolean clean = true;
    if (json != null) {
      try {
        List<Entry> loaded = gson.fromJson(json, LIST);
        if (loaded != null) {
          for (Entry entry : loaded) {
            if (entry != null && entry.id != null) {
              entry.uuid = Ids.plain(entry.uuid);
              entries.add(entry);
            }
          }
        }
      } catch (JsonSyntaxException e) {
        entries.clear();
        clean = false;
      }
    }
    revision++;
    return clean;
  }

  public synchronized void clear() {
    entries.clear();
    revision++;
  }

  public synchronized int revision() {
    return revision;
  }

  public synchronized boolean saved() {
    return saved;
  }

  public synchronized int size() {
    return entries.size();
  }

  public synchronized List<Entry> all() {
    return List.copyOf(entries);
  }

  public synchronized Entry find(String id) {
    for (Entry entry : entries) {
      if (entry.id.equals(id)) {
        return entry;
      }
    }
    return null;
  }

  public synchronized Entry byUuid(String uuid) {
    String plain = Ids.plain(uuid);
    if (plain.length() != 32) {
      return null;
    }
    Entry offline = null;
    for (Entry entry : entries) {
      if (entry.uuid.equals(plain)) {
        if (entry.kind() != Kind.OFFLINE) {
          return entry;
        }
        offline = entry;
      }
    }
    return offline;
  }

  public synchronized Added add(Entry incoming) {
    for (Entry entry : entries) {
      if (entry.same(incoming)) {
        if (entry.kind() == Kind.OFFLINE) {
          return new Added(entry, false);
        }
        entry.username = incoming.username.isEmpty() ? entry.username : incoming.username;
        entry.uuid = incoming.uuid.isEmpty() ? entry.uuid : incoming.uuid;
        entry.secret = incoming.secret.isEmpty() ? entry.secret : incoming.secret;
        persist();
        return new Added(entry, false);
      }
    }
    entries.add(incoming);
    persist();
    return new Added(incoming, true);
  }

  public synchronized boolean remove(String id) {
    Entry entry = find(id);
    if (entry == null) {
      return false;
    }
    entries.remove(entry);
    persist();
    return true;
  }

  public synchronized void pin(String id, boolean pinned) {
    Entry entry = find(id);
    if (entry != null && entry.pinned != pinned) {
      entry.pinned = pinned;
      persist();
    }
  }

  public synchronized void used(String id, long now) {
    Entry entry = find(id);
    if (entry != null) {
      entry.used = now;
      persist();
    }
  }

  public synchronized void refreshed(String id, String username, String uuid, String secret) {
    Entry entry = find(id);
    if (entry == null) {
      return;
    }
    entry.username = username == null || username.isEmpty() ? entry.username : username;
    entry.uuid = uuid == null || uuid.isEmpty() ? entry.uuid : Ids.plain(uuid);
    entry.secret = secret == null || secret.isEmpty() ? entry.secret : secret;
    persist();
  }

  public synchronized List<Entry> list(String query, Kind filter, Order order) {
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    String plain = needle.replace("-", "");
    List<Entry> shown = new ArrayList<>(entries.size());
    for (Entry entry : entries) {
      if (filter != null && entry.kind() != filter) {
        continue;
      }
      if (!needle.isEmpty()
          && !entry.lowerName().contains(needle)
          && !(plain.length() >= 4 && entry.uuid.contains(plain))) {
        continue;
      }
      shown.add(entry);
    }
    Comparator<Entry> by =
        switch (order) {
          case RECENT ->
              Comparator.comparingLong((Entry entry) -> Math.max(entry.used, entry.added))
                  .reversed();
          case ADDED -> Comparator.comparingLong((Entry entry) -> entry.added).reversed();
          case NAME -> Comparator.comparing(Entry::lowerName);
        };
    shown.sort(
        Comparator.comparing((Entry entry) -> !entry.pinned)
            .thenComparing(by)
            .thenComparing(Entry::lowerName));
    return shown;
  }

  private void persist() {
    revision++;
    saved = vault.write(STORE, gson.toJson(entries, LIST));
  }
}
