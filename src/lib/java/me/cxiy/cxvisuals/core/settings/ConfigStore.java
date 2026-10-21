package me.cxiy.cxvisuals.core.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class ConfigStore {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Path file;
  private final Map<String, Setting<?>> settings;
  private final Consumer<String> warn;

  public ConfigStore(Path file, Map<String, Setting<?>> settings, Consumer<String> warn) {
    this.file = file;
    this.settings = settings;
    this.warn = warn;
  }

  public void load() {
    if (!Files.exists(file)) {
      return;
    }
    JsonObject root;
    try {
      JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
      if (!parsed.isJsonObject()) {
        throw new IllegalStateException("root is not an object");
      }
      root = parsed.getAsJsonObject();
    } catch (IOException | RuntimeException e) {
      quarantine(e);
      return;
    }
    for (Map.Entry<String, Setting<?>> entry : settings.entrySet()) {
      JsonElement value = root.get(entry.getKey());
      String[] former = entry.getValue().formerKeys();
      for (int i = 0; value == null && i < former.length; i++) {
        value = root.get(former[i]);
      }
      if (value != null) {
        apply(entry.getKey(), entry.getValue(), value, file);
      }
    }
  }

  public boolean importLegacy(Path legacy, UnaryOperator<String> keyMap) {
    if (Files.exists(file) || !Files.exists(legacy)) {
      return false;
    }
    JsonObject root;
    try {
      JsonElement parsed = JsonParser.parseString(Files.readString(legacy, StandardCharsets.UTF_8));
      if (!parsed.isJsonObject()) {
        throw new IllegalStateException("root is not an object");
      }
      root = parsed.getAsJsonObject();
    } catch (IOException | RuntimeException e) {
      warn.accept("Could not import " + legacy.getFileName() + ": " + e.getMessage());
      return false;
    }
    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
      String key = keyMap.apply(entry.getKey());
      Setting<?> setting = key == null ? null : settings.get(key);
      if (setting != null) {
        apply(key, setting, entry.getValue(), legacy);
      }
    }
    return true;
  }

  private void apply(String key, Setting<?> setting, JsonElement value, Path source) {
    try {
      setting.fromJson(value);
    } catch (RuntimeException e) {
      setting.reset();
      warn.accept("Ignoring " + key + " in " + source.getFileName() + ": " + e.getMessage());
    }
  }

  public void save() {
    JsonObject root = new JsonObject();
    for (Map.Entry<String, Setting<?>> entry : settings.entrySet()) {
      root.add(entry.getKey(), entry.getValue().toJson());
    }
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Path temp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      warn.accept("Could not save " + file.getFileName() + ": " + e.getMessage());
    }
  }

  private void quarantine(Exception cause) {
    Path backup = file.resolveSibling(file.getFileName() + ".bak");
    try {
      Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
      warn.accept(
          "Could not read "
              + file.getFileName()
              + ", moved it to "
              + backup.getFileName()
              + ": "
              + cause.getMessage());
    } catch (IOException e) {
      warn.accept("Could not read " + file.getFileName() + " or back it up: " + e.getMessage());
    }
  }
}
