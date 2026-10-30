package me.cxiy.cxvisuals.mc.modules.map.forecast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.map.MapModule;
import me.cxiy.cxvisuals.modules.map.MapWorld;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

public final class Forecasts {
  private static final String FILE = "seeds.json";
  private static final long MAX_FILE = 1L << 20;
  private static final Pattern REPLY = Pattern.compile("Seed: \\[(-?[0-9]{1,20})\\]");
  private static final Executor LOADER =
      Executors.newSingleThreadExecutor(
          run -> {
            Thread thread = new Thread(run, "CxVisuals Forecast");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY + 1);
            return thread;
          });

  private static final Map<String, String> SEEDS = new HashMap<>();
  private static MapModule module;
  private static MapWorld world;
  private static Path file;
  private static boolean applying;
  private static String seedScope = "";
  private static String active = "";
  private static volatile String built = "";
  private static volatile ForecastWorld ready;
  private static volatile boolean failed;
  private static boolean shown;

  private Forecasts() {}

  public static void init(MapModule mapModule, MapWorld mapWorld, Path directory) {
    module = mapModule;
    world = mapWorld;
    file = directory.resolve(FILE);
    try {
      if (Files.isRegularFile(file) && Files.size(file) < MAX_FILE) {
        JsonObject root =
            JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        root.entrySet().forEach(entry -> SEEDS.put(entry.getKey(), entry.getValue().getAsString()));
      }
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("Couldn't read seeds: {}", e.getMessage());
    }
    module.seed.onChange(Forecasts::edited);
    ClientReceiveMessageEvents.GAME.register(
        (message, overlay) -> {
          Matcher found = REPLY.matcher(message.getString());
          if (!overlay && found.find()) {
            module.seed.set(found.group(1));
          }
        });
  }

  public static boolean showing() {
    return shown;
  }

  public static void tick(String scope, String dimension) {
    if (module == null) {
      return;
    }
    if (!scope.equals(seedScope)) {
      seedScope = scope;
      applying = true;
      module.seed.set(SEEDS.getOrDefault(scope, ""));
      applying = false;
    }
    ForecastWorld.Kind kind = ForecastWorld.kind(dimension);
    String text = module.seed.get().strip();
    String want =
        module.forecast.get() && !text.isEmpty() && kind != null && !scope.isEmpty()
            ? scope + "|" + dimension + "|" + text
            : "";
    if (!want.equals(active)) {
      active = want;
      failed = false;
      ready = null;
      shown = false;
      world.forecast(null);
      if (!want.isEmpty()) {
        build(want, ForecastWorld.seed(text), kind);
      }
    }
    ForecastWorld next = ready;
    if (next != null && built.equals(active)) {
      ready = null;
      world.forecast(next);
      shown = true;
    }
  }

  public static void reset() {
    active = "";
    built = "";
    ready = null;
    shown = false;
    seedScope = "";
    if (world != null) {
      world.forecast(null);
    }
  }

  private static void build(String key, long seed, ForecastWorld.Kind kind) {
    WorldgenData.access(LOADER)
        .thenApplyAsync(provider -> ForecastWorld.create(provider, seed, kind), LOADER)
        .whenComplete(
            (engine, error) -> {
              if (error != null) {
                failed = true;
                CxVisualsClient.LOG.warn("Couldn't build the forecast: {}", error.toString());
                return;
              }
              built = key;
              ready = engine;
            });
  }

  private static void edited() {
    if (applying || seedScope.isEmpty()) {
      return;
    }
    String text = module.seed.get().strip();
    if (text.isEmpty()) {
      SEEDS.remove(seedScope);
    } else {
      SEEDS.put(seedScope, text);
    }
    JsonObject root = new JsonObject();
    SEEDS.forEach(root::addProperty);
    String json = root.toString();
    CompletableFuture.runAsync(() -> write(json));
  }

  private static synchronized void write(String json) {
    try {
      Files.createDirectories(file.getParent());
      Path temp = file.resolveSibling(FILE + ".tmp");
      Files.writeString(temp, json, StandardCharsets.UTF_8);
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("Couldn't save seeds: {}", e.getMessage());
    }
  }
}
