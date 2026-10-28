package me.cxiy.cxvisuals.mc.modules.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.map.ExploreHost;
import me.cxiy.cxvisuals.gui.map.ExploreModel;
import me.cxiy.cxvisuals.gui.map.PublishModel;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.modules.map.Maps;
import me.cxiy.cxvisuals.mc.modules.waypoints.Markers;
import me.cxiy.cxvisuals.modules.map.AreaPreview;
import me.cxiy.cxvisuals.modules.map.MapShare;
import me.cxiy.cxvisuals.modules.social.SocialProtocol;
import net.minecraft.client.Minecraft;

public final class ExploreLink implements ExploreHost {
  private static final String SERVER_SCOPE = "server:";
  private static final int PREVIEW_LIMIT = 256 << 10;
  private static final int CACHE = 96;
  private static final byte[] LOADING = new byte[0];
  private static final byte[] FAILED = new byte[0];
  private static final ExecutorService IO =
      Executors.newFixedThreadPool(
          3,
          task -> {
            Thread thread = new Thread(task, "CxVisuals Explore");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 2);
            return thread;
          });
  private static final Map<String, byte[]> PREVIEWS =
      java.util.Collections.synchronizedMap(
          new LinkedHashMap<>(128, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
              return size() > CACHE;
            }
          });

  private final Supplier<int[]> selection;

  public ExploreLink(Supplier<int[]> selection) {
    this.selection = selection;
  }

  private static String scope() {
    return Markers.scope(Minecraft.getInstance());
  }

  private static String url(String path) {
    return Social.module().baseUrl() + "/v2/explore" + path;
  }

  @Override
  public boolean available() {
    return Social.connected();
  }

  @Override
  public boolean canPublish() {
    return available() && scope().startsWith(SERVER_SCOPE);
  }

  @Override
  public String server() {
    return scope().replaceFirst("^" + SERVER_SCOPE, "");
  }

  @Override
  public boolean hasSelection() {
    return selection.get() != null;
  }

  @Override
  public String suggestedTitle() {
    Minecraft mc = Minecraft.getInstance();
    return mc.player == null
        ? ""
        : Lang.format("Around {0} {1}", mc.player.getBlockX(), mc.player.getBlockZ());
  }

  @Override
  public void list(
      ExploreModel.Request request, Consumer<ExploreModel.Page> done, Runnable failed) {
    String query =
        "?sort="
            + request.sort().name().toLowerCase(java.util.Locale.ROOT)
            + "&q="
            + encode(request.text())
            + "&server="
            + encode(request.server())
            + "&page="
            + request.page();
    IO.execute(
        () -> {
          try {
            JsonObject reply = Social.cloud().get(url(query), Social.token());
            ExploreModel.Page page = page(reply);
            Social.later(() -> done.accept(page));
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.debug("Explore list failed: {}", e.getMessage());
            Social.later(failed);
          }
        });
  }

  @Override
  public void servers(Consumer<List<String>> done) {
    IO.execute(
        () -> {
          try {
            JsonObject reply = Social.cloud().get(url("/servers"), Social.token());
            List<String> names = new ArrayList<>();
            JsonElement rows = reply.get("servers");
            if (rows != null && rows.isJsonArray()) {
              for (JsonElement row : rows.getAsJsonArray()) {
                names.add(SocialProtocol.text(row.getAsJsonObject(), "name", ""));
              }
            }
            Social.later(() -> done.accept(names));
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.debug("Explore servers failed: {}", e.getMessage());
          }
        });
  }

  @Override
  public byte[] preview(String id) {
    byte[] have = PREVIEWS.get(id);
    if (have == LOADING) {
      return null;
    }
    if (have != null) {
      return have;
    }
    PREVIEWS.put(id, LOADING);
    IO.execute(
        () -> {
          byte[] png;
          try {
            png =
                Social.cloud().download(url("/" + id + "/preview"), Social.token(), PREVIEW_LIMIT);
          } catch (IOException | RuntimeException e) {
            png = FAILED;
          }
          PREVIEWS.put(id, png);
        });
    return null;
  }

  @Override
  public void add(String id, Consumer<Boolean> done) {
    IO.execute(
        () -> {
          boolean ok = false;
          MapShare.Area area = null;
          try {
            byte[] blob =
                Social.cloud().download(url("/" + id + "/map"), Social.token(), MapShare.MAX_BYTES);
            area = MapShare.decode(blob, Maps.palette());
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.debug("Explore download failed: {}", e.getMessage());
          }
          MapShare.Area chosen = area;
          Social.later(() -> done.accept(chosen != null && Maps.importArea(chosen)));
        });
  }

  @Override
  public void publish(PublishModel model, Consumer<Boolean> done) {
    String title = model.title();
    boolean anonymous = !model.showName();
    String scope = scope();
    String dimension = Markers.dimension(Minecraft.getInstance());
    Maps.collectForPublish(
        model.whole(),
        selection.get(),
        chunks -> {
          if (chunks.isEmpty()) {
            Maps.say(Lang.tr("Nothing explored there yet."));
            done.accept(false);
            return;
          }
          IO.execute(() -> upload(title, anonymous, scope, dimension, chunks, done));
        });
  }

  private void upload(
      String title,
      boolean anonymous,
      String scope,
      String dimension,
      List<MapShare.Chunk> chunks,
      Consumer<Boolean> done) {
    boolean ok = false;
    try {
      byte[] blob = MapShare.encode(scope, dimension, chunks, Maps.palette());
      byte[] preview = AreaPreview.render(chunks, Maps.world().terrain());
      if (blob.length > MapShare.MAX_BYTES || preview == null) {
        Social.later(
            () -> Maps.say(Lang.tr("That area is too large to share. Pick a smaller one.")));
      } else {
        byte[] body =
            ByteBuffer.allocate(4 + preview.length + blob.length)
                .putInt(preview.length)
                .put(preview)
                .put(blob)
                .array();
        String query =
            "?title=" + encode(title) + "&chunks=" + chunks.size() + (anonymous ? "&anon=1" : "");
        Social.cloud().send(url(query), Social.token(), body);
        ok = true;
      }
    } catch (Cloud.Refused refused) {
      String notice =
          switch (refused.code) {
            case "quota" ->
                Lang.tr("You've reached the limit of published maps. Delete one first.");
            case "slow_down" -> Lang.tr("You're publishing too fast. Try again in a few minutes.");
            default -> Lang.tr("Couldn't publish that map.");
          };
      Social.later(() -> Maps.say(notice));
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.debug("Explore publish failed: {}", e.getMessage());
    }
    boolean published = ok;
    Social.later(() -> done.accept(published));
  }

  @Override
  public void delete(String id, Runnable done) {
    IO.execute(
        () -> {
          try {
            Social.cloud().delete(url("/" + id), Social.token());
            PREVIEWS.remove(id);
            Social.later(done);
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.debug("Explore delete failed: {}", e.getMessage());
          }
        });
  }

  @Override
  public void report(String id) {
    IO.execute(
        () -> {
          try {
            Social.cloud().post(url("/" + id + "/report"), Social.token(), "{}");
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.debug("Explore report failed: {}", e.getMessage());
          }
        });
  }

  private static ExploreModel.Page page(JsonObject reply) {
    List<ExploreModel.Entry> maps = new ArrayList<>();
    JsonElement rows = reply.get("maps");
    if (rows != null && rows.isJsonArray()) {
      for (JsonElement row : rows.getAsJsonArray()) {
        JsonObject json = row.getAsJsonObject();
        maps.add(
            new ExploreModel.Entry(
                SocialProtocol.text(json, "id", ""),
                SocialProtocol.text(json, "title", ""),
                SocialProtocol.text(json, "author", ""),
                SocialProtocol.text(json, "server", ""),
                SocialProtocol.text(json, "dimension", ""),
                (int) SocialProtocol.number(json, "chunks", 0L),
                SocialProtocol.number(json, "size", 0L),
                (int) SocialProtocol.number(json, "downloads", 0L),
                SocialProtocol.number(json, "created", 0L),
                SocialProtocol.flag(json, "mine", false)));
      }
    }
    return new ExploreModel.Page(maps, SocialProtocol.flag(reply, "more", false));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
