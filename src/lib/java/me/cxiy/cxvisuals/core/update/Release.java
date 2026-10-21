package me.cxiy.cxvisuals.core.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

public record Release(String sha1, String version, String note) {
  public static final Release NONE = new Release("", "", "");

  public static Release parse(JsonObject manifest) {
    String sha = text(manifest, "sha1").toLowerCase(Locale.ROOT);
    if (sha.length() != 40) {
      return NONE;
    }
    String note = "";
    JsonElement notes = manifest.get("notes");
    if (notes != null && notes.isJsonArray()) {
      JsonArray list = notes.getAsJsonArray();
      if (list.size() > 0 && list.get(0).isJsonPrimitive()) {
        note = list.get(0).getAsString().strip();
      }
    }
    return new Release(sha, text(manifest, "version"), note);
  }

  public boolean known() {
    return !sha1.isEmpty();
  }

  public boolean newerThan(String installedSha1) {
    return known() && !installedSha1.isEmpty() && !sha1.equalsIgnoreCase(installedSha1);
  }

  private static String text(JsonObject json, String key) {
    JsonElement value = json.get(key);
    return value != null && value.isJsonPrimitive() ? value.getAsString().strip() : "";
  }
}
