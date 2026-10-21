package me.cxiy.cxvisuals.mc.modules.donutsmp;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.modules.social.Social;
import me.cxiy.cxvisuals.modules.donutsmp.PriceHistory;
import me.cxiy.cxvisuals.modules.donutsmp.auction.CrowdFeed;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Market;

final class Crowd {
  private static final String PATH = "/v2/market";
  private static final long NANOS = 1_000_000_000L;
  private static final long FIRST_DELAY = 3L * NANOS;
  private static final long DEBOUNCE = 1_500_000_000L;
  private static final long UPLOAD_GAP = 3L * NANOS;
  private static final long DOWNLOAD_EVERY = 6L * NANOS;
  private static final long FRESH_SECONDS = 6L * 3600L;

  private final Market market;
  private final PriceHistory history;
  private final AtomicBoolean busy = new AtomicBoolean();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          task -> {
            Thread thread = new Thread(task, "CxVisuals-DonutSMP-Crowd");
            thread.setDaemon(true);
            return thread;
          });
  private long uploadedAt;
  private long downloadAt;
  private long sightedStamp;
  private long sightedAt;
  private boolean pending;
  private long uploadedSeconds;
  private long since;
  private volatile String lastError = "";
  private volatile long sentAt;
  private volatile int sent;

  Crowd(Market sightings, PriceHistory history) {
    this.market = sightings;
    this.history = history;
    long now = System.nanoTime();
    uploadedAt = now;
    downloadAt = now + FIRST_DELAY;
  }

  void tick(boolean wanted, boolean recording, boolean onDonut) {
    if (!wanted || !Social.connected() || busy.get()) {
      return;
    }
    long now = System.nanoTime();
    boolean download = now - downloadAt >= 0L;
    long stamp = Prices.sighted();
    if (stamp != sightedStamp) {
      sightedStamp = stamp;
      sightedAt = now;
      pending = true;
    }
    boolean upload =
        onDonut && pending && now - sightedAt >= DEBOUNCE && now - uploadedAt >= UPLOAD_GAP;
    if (!download && !upload || !busy.compareAndSet(false, true)) {
      return;
    }
    if (download) {
      downloadAt = now + DOWNLOAD_EVERY;
    }
    if (upload) {
      uploadedAt = now;
      pending = false;
    }
    worker.execute(
        () -> {
          try {
            if (upload) {
              send();
            }
            if (download) {
              fetch(recording ? history : null);
            }
          } finally {
            busy.set(false);
          }
        });
  }

  private void send() {
    long now = PriceHistory.now();
    List<CrowdFeed.Report> reports = new ArrayList<>();
    for (String key : market.seenSince(uploadedSeconds, CrowdFeed.MOST_REPORTS)) {
      if (key.indexOf('|') >= 0) {
        continue;
      }
      int seen = market.count(key, now);
      if (seen <= 0 || now - market.newest(key, now) > FRESH_SECONDS) {
        continue;
      }
      int tilde = key.indexOf('~');
      String id = tilde > 0 ? key.substring(0, tilde) : key;
      CrowdFeed.Report report =
          new CrowdFeed.Report(id, key, market.variant(key), market.median(key, now), seen);
      if (tilde > 0 && report.variant().plain()) {
        continue;
      }
      reports.add(report);
    }
    if (reports.isEmpty()) {
      return;
    }
    try {
      Social.write(PATH, CrowdFeed.upload(reports));
      uploadedSeconds = now;
      sent = reports.size();
      sentAt = System.nanoTime();
      lastError = "";
    } catch (IOException e) {
      report(e);
    }
  }

  private void fetch(PriceHistory target) {
    try {
      JsonObject reply = Social.read(PATH + "?since=" + since);
      CrowdFeed.Answer answer = CrowdFeed.download(reply);
      Prices.community(answer, target);
      since = Math.max(since, answer.now());
    } catch (IOException e) {
      report(e);
    }
  }

  String status(boolean wanted, boolean onDonut) {
    if (!wanted) {
      return "Off";
    }
    if (!Social.connected()) {
      return "Not connected, open Account to share and get prices";
    }
    String error = lastError;
    if (!error.isEmpty()) {
      return "Cloud problem: " + error;
    }
    String shared;
    if (sentAt == 0L) {
      shared =
          onDonut
              ? "Nothing shared yet, browse the auction house to share prices"
              : "Shares while you are on DonutSMP";
    } else {
      shared =
          "Shared " + sent + " items " + (System.nanoTime() - sentAt) / (60L * NANOS) + "m ago";
    }
    return shared + ", " + Prices.community() + " community prices";
  }

  private void report(IOException error) {
    String text = error.toString();
    if (!text.equals(lastError)) {
      lastError = text;
      CxVisualsClient.LOG.warn("DonutSMP community prices unavailable: {}", text);
    }
  }
}
