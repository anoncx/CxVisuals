package me.cxiy.cxvisuals.modules.hud.media;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.imageio.ImageIO;

public final class Cover {
  private static final int MAX_ENTRIES = 4;
  private static final int SIZE = 128;

  private static final class Entry {
    private volatile byte[] png;
    private volatile boolean done;
    private boolean online;
  }

  private final Function<String, byte[]> loader;
  private final Function<MediaSnapshot, String> lookup;
  private final Executor worker;
  private String shownTrack = "";
  private byte[] shown;
  private final Map<String, Entry> cache =
      new LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
          return size() > MAX_ENTRIES;
        }
      };

  public Cover(
      Function<String, byte[]> loader, Function<MediaSnapshot, String> lookup, Executor worker) {
    this.loader = loader;
    this.lookup = lookup;
    this.worker = worker;
  }

  public byte[] get(MediaSnapshot snapshot, boolean online) {
    if (!snapshot.present()) {
      return null;
    }
    String track = snapshot.trackKey();
    if (snapshot.art().equals(MediaSnapshot.ART_PENDING)) {
      return track.equals(shownTrack) ? shown : null;
    }
    String key = track + '\u0000' + snapshot.art();
    Entry entry;
    synchronized (cache) {
      entry = cache.get(key);
      if (entry != null && !(entry.done && entry.png == null && online && !entry.online)) {
        byte[] png = entry.png;
        if (png != null) {
          shownTrack = track;
          shown = png;
          return png;
        }
        return track.equals(shownTrack) ? shown : null;
      }
      entry = new Entry();
      entry.online = online;
      cache.put(key, entry);
    }
    Entry target = entry;
    worker.execute(
        () -> {
          try {
            target.png = resolve(snapshot, online);
          } catch (IOException | RuntimeException e) {
            target.png = null;
          }
          target.done = true;
        });
    return track.equals(shownTrack) ? shown : null;
  }

  private byte[] resolve(MediaSnapshot snapshot, boolean online) throws IOException {
    String art = snapshot.art();
    boolean remote = art.startsWith("http://") || art.startsWith("https://");
    byte[] raw = art.isEmpty() || (remote && !online) ? null : loader.apply(art);
    BufferedImage image = decode(raw);
    if (image == null && online) {
      String url = lookup.apply(snapshot);
      image = url == null || url.isEmpty() ? null : decode(loader.apply(url));
    }
    if (image == null) {
      return null;
    }
    int side = Math.min(image.getWidth(), image.getHeight());
    int left = (image.getWidth() - side) / 2;
    int top = (image.getHeight() - side) / 2;
    BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = scaled.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.drawImage(image, 0, 0, SIZE, SIZE, left, top, left + side, top + side, null);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream(SIZE * SIZE);
    ImageIO.write(scaled, "png", out);
    return out.toByteArray();
  }

  private static BufferedImage decode(byte[] raw) throws IOException {
    return raw == null || raw.length == 0 ? null : ImageIO.read(new ByteArrayInputStream(raw));
  }
}
