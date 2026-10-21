package me.cxiy.cxvisuals.modules.accounts;

import java.util.Locale;
import java.util.UUID;

public final class Entry {
  public String id;
  public String kind;
  public String username = "";
  public String uuid = "";
  public String secret = "";
  public long added;
  public long used;
  public boolean pinned;

  public Entry() {}

  public Entry(Kind kind, String username, String uuid, String secret, long now) {
    this.id = UUID.randomUUID().toString();
    this.kind = kind.name();
    this.username = username == null ? "" : username;
    this.uuid = Ids.plain(uuid);
    this.secret = secret == null ? "" : secret;
    this.added = now;
  }

  public Kind kind() {
    Kind parsed = Kind.parse(kind);
    return parsed == null ? Kind.OFFLINE : parsed;
  }

  public boolean verified() {
    return !username.isEmpty() && uuid.length() == 32;
  }

  public boolean same(Entry other) {
    if (other == null || kind() != other.kind()) {
      return false;
    }
    if (kind() == Kind.OFFLINE) {
      return username.equalsIgnoreCase(other.username);
    }
    if (uuid.length() == 32 && uuid.equals(other.uuid)) {
      return true;
    }
    return !secret.isEmpty() && secret.equals(other.secret);
  }

  public String lowerName() {
    return username.toLowerCase(Locale.ROOT);
  }
}
