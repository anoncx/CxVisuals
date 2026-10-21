package me.cxiy.cxvisuals.modules.hud.media;

public final class LyricTracker {
  private static final int INITIAL = 16;

  private int[] starts = new int[INITIAL];
  private int[] ends = new int[INITIAL];
  private Lyrics source;
  private int line = -1;
  private int words;
  private int weight;
  private int word;
  private float wordProgress;
  private long lineStart;
  private long lineEnd;
  private long wordStart;
  private long wordEnd;

  public void reset() {
    source = null;
    line = -1;
    words = 0;
    weight = 0;
    word = 0;
    wordProgress = 0.0F;
  }

  public boolean update(Lyrics lyrics, long positionMs, long durationMs) {
    if (lyrics.state() != Lyrics.State.FOUND) {
      reset();
      return false;
    }
    int index = lyrics.indexAt(positionMs);
    if (index < 0) {
      reset();
      return false;
    }
    if (index != line || lyrics != source) {
      source = lyrics;
      line = index;
      split(lyrics.line(index));
    }
    long start = lyrics.time(index);
    long end = lyrics.endOf(index, durationMs);
    lineStart = start;
    lineEnd = end;
    if (words == 0) {
      word = 0;
      wordProgress = 0.0F;
      wordStart = start;
      wordEnd = end;
      return false;
    }
    float progress =
        end <= start
            ? 1.0F
            : Math.max(0.0F, Math.min(1.0F, (positionMs - start) / (float) (end - start)));
    float target = progress * weight;
    int consumed = 0;
    for (int i = 0; i < words; i++) {
      int own = ends[i] - starts[i] + 1;
      if (target < consumed + own || i == words - 1) {
        word = i;
        wordProgress = Math.max(0.0F, Math.min(1.0F, (target - consumed) / own));
        long span = Math.max(0L, end - start);
        wordStart = start + span * consumed / weight;
        wordEnd = start + span * (consumed + own) / weight;
        break;
      }
      consumed += own;
    }
    return true;
  }

  private void split(String text) {
    words = 0;
    weight = 0;
    int length = text.length();
    int at = 0;
    while (at < length) {
      while (at < length && text.charAt(at) == ' ') {
        at++;
      }
      if (at >= length) {
        break;
      }
      int from = at;
      while (at < length && text.charAt(at) != ' ') {
        at++;
      }
      if (words == starts.length) {
        starts = java.util.Arrays.copyOf(starts, words * 2);
        ends = java.util.Arrays.copyOf(ends, words * 2);
      }
      starts[words] = from;
      ends[words] = at;
      weight += at - from + 1;
      words++;
    }
  }

  public int line() {
    return line;
  }

  public int word() {
    return word;
  }

  public int words() {
    return words;
  }

  public int wordStart(int index) {
    return starts[index];
  }

  public int wordEnd(int index) {
    return ends[index];
  }

  public long lineStartMs() {
    return lineStart;
  }

  public long lineEndMs() {
    return lineEnd;
  }

  public long wordStartMs() {
    return wordStart;
  }

  public long wordEndMs() {
    return wordEnd;
  }

  public float wordProgress() {
    return wordProgress;
  }
}
