package me.cxiy.cxvisuals.modules.hud.media;

import java.util.List;
import java.util.regex.Pattern;

public final class LyricsMatch {
  public record Hit(double seconds, String synced) {}

  public static final long TOLERANCE_MS = 2000L;

  private static final Pattern FEATURING =
      Pattern.compile("(?i)\\s*[(\\[][^)\\]]*\\b(feat\\.?|ft\\.?|with)\\b[^)\\]]*[)\\]]");
  private static final Pattern EDITION =
      Pattern.compile(
          "(?i)\\s*-\\s*(remaster(ed)?|radio edit|single version|album version|bonus track|extended)\\b.*$");
  private static final Pattern ARTISTS =
      Pattern.compile("(?i)\\s*(,|&|;|/|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bwith\\b)\\s*");

  private LyricsMatch() {}

  public static String pick(List<Hit> hits, long durationMs) {
    String best = null;
    double nearest = Double.MAX_VALUE;
    for (int i = 0; i < hits.size(); i++) {
      Hit hit = hits.get(i);
      if (hit.synced() == null || hit.synced().isBlank()) {
        continue;
      }
      if (durationMs <= 0L) {
        return hit.synced();
      }
      double gap = Math.abs(hit.seconds() * 1000.0 - durationMs);
      if (gap <= TOLERANCE_MS && gap < nearest && within(hit.synced(), durationMs)) {
        nearest = gap;
        best = hit.synced();
      }
    }
    return best;
  }

  private static boolean within(String synced, long durationMs) {
    Lyrics parsed = LrcParser.parse(synced);
    return parsed.size() == 0 || parsed.time(parsed.size() - 1) <= durationMs + TOLERANCE_MS;
  }

  public static String cleanTitle(String title) {
    String cleaned =
        EDITION.matcher(FEATURING.matcher(title).replaceAll("")).replaceAll("").strip();
    return cleaned.isEmpty() ? title : cleaned;
  }

  public static String primaryArtist(String artist) {
    String[] names = ARTISTS.split(artist);
    String lead = names.length == 0 ? "" : names[0].strip();
    return lead.isEmpty() ? artist : lead;
  }
}
