package me.cxiy.cxvisuals.modules.accounts;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Links {
  public static final int DEFAULT_PORT = 25565;

  private static final String STORE = "links";
  private static final Type MAP = new TypeToken<Map<String, String>>() {}.getType();

  private final Vault vault;
  private final Gson gson = new Gson();
  private final Map<String, String> links = new HashMap<>();
  private int revision;

  public Links(Vault vault) {
    this.vault = vault;
  }

  public static String normalize(String address) {
    if (address == null) {
      return "";
    }
    String text = address.trim().toLowerCase(Locale.ROOT);
    if (text.isEmpty()) {
      return "";
    }
    int scheme = text.indexOf("://");
    if (scheme >= 0) {
      text = text.substring(scheme + 3);
    }
    int slash = text.indexOf('/');
    if (slash >= 0) {
      text = text.substring(0, slash);
    }
    String host = text;
    int port = DEFAULT_PORT;
    int colon = text.lastIndexOf(':');
    if (colon > 0 && text.indexOf(':') == colon) {
      host = text.substring(0, colon);
      try {
        port = Integer.parseInt(text.substring(colon + 1));
      } catch (NumberFormatException e) {
        port = DEFAULT_PORT;
      }
    }
    while (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    return host + ":" + port;
  }

  public synchronized void load() {
    links.clear();
    String json = vault.read(STORE);
    if (json != null) {
      try {
        Map<String, String> loaded = gson.fromJson(json, MAP);
        if (loaded != null) {
          loaded.forEach(
              (address, id) -> {
                if (address != null && id != null) {
                  links.put(normalize(address), id);
                }
              });
        }
      } catch (JsonSyntaxException e) {
        links.clear();
      }
    }
    revision++;
  }

  public synchronized void clear() {
    links.clear();
    revision++;
  }

  public synchronized int revision() {
    return revision;
  }

  public synchronized String get(String address) {
    return links.get(normalize(address));
  }

  public synchronized void set(String address, String entryId) {
    String key = normalize(address);
    if (key.isEmpty() || entryId == null) {
      return;
    }
    links.put(key, entryId);
    save();
  }

  public synchronized void remove(String address) {
    if (links.remove(normalize(address)) != null) {
      save();
    }
  }

  public synchronized void forget(String entryId) {
    if (links.values().removeIf(entryId::equals)) {
      save();
    }
  }

  public synchronized int count(String entryId) {
    int found = 0;
    for (String id : links.values()) {
      if (id.equals(entryId)) {
        found++;
      }
    }
    return found;
  }

  public synchronized String first(String entryId) {
    String best = null;
    for (Map.Entry<String, String> link : links.entrySet()) {
      if (link.getValue().equals(entryId) && (best == null || link.getKey().compareTo(best) < 0)) {
        best = link.getKey();
      }
    }
    return best;
  }

  private void save() {
    revision++;
    vault.write(STORE, gson.toJson(links, MAP));
  }
}
