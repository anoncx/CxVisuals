package me.cxiy.cxvisuals.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Configs {
  public static final int MAX_NAME = 24;

  private static final String SUFFIX = ".json";

  private final Path directory;
  private final Map<String, Setting<?>> settings;
  private final Consumer<String> warn;
  private final List<String> names = new ArrayList<>();
  private final List<String> namesView = Collections.unmodifiableList(names);

  public Configs(Path directory, Map<String, Setting<?>> settings, Consumer<String> warn) {
    this(directory, settings, warn, key -> true);
  }

  public Configs(
      Path directory,
      Map<String, Setting<?>> settings,
      Consumer<String> warn,
      Predicate<String> portable) {
    this.directory = directory;
    this.settings = new LinkedHashMap<>();
    for (Map.Entry<String, Setting<?>> entry : settings.entrySet()) {
      if (portable.test(entry.getKey())) {
        this.settings.put(entry.getKey(), entry.getValue());
      }
    }
    this.warn = warn;
    refresh();
  }

  public String export() {
    JsonObject root = new JsonObject();
    for (Map.Entry<String, Setting<?>> entry : settings.entrySet()) {
      root.add(entry.getKey(), entry.getValue().toJson());
    }
    return root.toString();
  }

  public String export(String name) {
    if (!names.contains(name)) {
      return "";
    }
    try {
      JsonObject root =
          JsonParser.parseString(Files.readString(file(name), StandardCharsets.UTF_8))
              .getAsJsonObject();
      JsonObject kept = new JsonObject();
      for (String key : settings.keySet()) {
        JsonElement value = root.get(key);
        if (value != null) {
          kept.add(key, value);
        }
      }
      return kept.size() == 0 ? "" : kept.toString();
    } catch (IOException | RuntimeException e) {
      warn.accept("Could not read config " + name + ": " + e.getMessage());
      return "";
    }
  }

  public String adopt(String raw, String json) {
    String base = clean(raw);
    if (base.isEmpty()) {
      base = Lang.tr("Shared");
    }
    JsonObject kept = new JsonObject();
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      for (String key : settings.keySet()) {
        JsonElement value = root.get(key);
        if (value != null) {
          kept.add(key, value);
        }
      }
    } catch (RuntimeException e) {
      return "";
    }
    if (kept.size() == 0) {
      return "";
    }
    String name = base;
    for (int copy = 2; names.contains(name) && copy < 100; copy++) {
      String suffix = " " + copy;
      name = base.substring(0, Math.min(base.length(), MAX_NAME - suffix.length())).trim() + suffix;
    }
    try {
      Files.createDirectories(directory);
      Files.writeString(file(name), kept.toString(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      warn.accept("Could not store shared config " + name + ": " + e.getMessage());
      return "";
    }
    refresh();
    return names.contains(name) ? name : "";
  }

  public List<String> names() {
    return namesView;
  }

  public void refresh() {
    names.clear();
    if (!Files.isDirectory(directory)) {
      return;
    }
    try (Stream<Path> files = Files.list(directory)) {
      files
          .map(file -> file.getFileName().toString())
          .filter(file -> file.endsWith(SUFFIX))
          .map(file -> file.substring(0, file.length() - SUFFIX.length()))
          .filter(name -> name.equals(clean(name)) && !name.isEmpty())
          .sorted(String.CASE_INSENSITIVE_ORDER)
          .forEach(names::add);
    } catch (IOException e) {
      warn.accept("Could not list configs: " + e.getMessage());
    }
  }

  public static boolean allowed(int codepoint) {
    return Character.isLetterOrDigit(codepoint)
        || codepoint == ' '
        || codepoint == '-'
        || codepoint == '_';
  }

  public static String clean(String raw) {
    StringBuilder text = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length() && text.length() < MAX_NAME; i++) {
      char ch = raw.charAt(i);
      if (allowed(ch)) {
        text.append(ch);
      }
    }
    return text.toString().trim();
  }

  public boolean save(String raw) {
    String name = clean(raw);
    if (name.isEmpty()) {
      return false;
    }
    store(name).save();
    refresh();
    return names.contains(name);
  }

  public boolean load(String name) {
    if (!names.contains(name)) {
      return false;
    }
    for (Setting<?> setting : settings.values()) {
      setting.reset();
    }
    store(name).load();
    return true;
  }

  public void delete(String name) {
    if (!names.contains(name)) {
      return;
    }
    try {
      Files.deleteIfExists(file(name));
    } catch (IOException e) {
      warn.accept("Could not delete config " + name + ": " + e.getMessage());
    }
    refresh();
  }

  private Path file(String name) {
    return directory.resolve(name + SUFFIX);
  }

  private ConfigStore store(String name) {
    return new ConfigStore(file(name), settings, warn);
  }
}
