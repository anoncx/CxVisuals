package me.cxiy.cxvisuals.modules.chat;

import java.util.Locale;
import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class ChatModule extends Module {
  public enum Mode {
    EVERYONE,
    NEARBY,
    KEYWORDS,
    NEARBY_OR_KEYWORDS
  }

  public enum Verdict {
    SHOW,
    MATCH,
    HIDE
  }

  public static final String ID = "chat";
  public static final int OWN = 0;
  public static final int PLAYER = 1;
  public static final int SERVER = 2;

  private static final String[] NONE = new String[0];
  private static final int RECENT = 8;

  public final EnumSetting<Mode> mode;
  public final NumberSetting radius;
  public final TextSetting keywords;
  public final BoolSetting wholeWords;
  public final BoolSetting mentions;
  public final BoolSetting serverMessages;
  public final TextSetting muted;
  public final BoolSetting repeats;
  public final NumberSetting window;
  public final BoolSetting ping;

  private final String[] recentText = new String[RECENT];
  private final long[] recentAt = new long[RECENT];
  private int recentHead;
  private String[] keywordList = NONE;
  private String[] mutedList = NONE;
  private String keywordSource;
  private String mutedSource;

  public ChatModule(KeyBinder keys) {
    super(ID, "Chat", false, keys);
    Page page = section("chat", "Chat");

    mode =
        page.group("Filter")
            .enumOf("mode", "Show", Mode.EVERYONE, "Everyone", "Nearby", "Keywords", "Either");
    radius =
        page.group("Range")
            .showWhen(() -> mode.get() == Mode.NEARBY || mode.get() == Mode.NEARBY_OR_KEYWORDS)
            .number("radius", "Range", 0.0, 0.0, 256.0, 8.0, " blocks")
            .describe("0 means your render distance.");
    Group keyed =
        page.group("Keywords")
            .showWhen(() -> mode.get() == Mode.KEYWORDS || mode.get() == Mode.NEARBY_OR_KEYWORDS);
    keywords =
        keyed
            .text("keywords", "Keywords", "trade, sell, buy")
            .describe("Comma separated. Matches stay visible.");
    wholeWords =
        keyed
            .bool("whole_words", "Whole words only", true)
            .describe("Keeps ban from matching banana.");

    Group always = page.group("Always show");
    mentions =
        always
            .bool("mentions", "Mentions of you", true)
            .describe("Messages with your name always show.");
    serverMessages =
        always
            .bool("server_messages", "Server messages", true)
            .describe("Announcements that name no player.");

    Group mute = page.group("Mute");
    muted =
        mute.text("muted", "Muted words", "").describe("Comma separated. Hidden in every mode.");
    repeats =
        mute.bool("repeats", "Hide repeats", false)
            .describe("Hides a message that repeats a recent one.");
    window =
        page.group("Repeat window")
            .showWhen(repeats::get)
            .number("window", "Repeat window", 30.0, 5.0, 300.0, 5.0, " s")
            .describe("How long a message counts as a repeat.");

    ping =
        page.group("Alert")
            .bool("ping", "Ping on match", true)
            .describe("Soft chime when a mention or keyword shows.");
  }

  public Verdict judge(String text, String self, int origin, double distance, long nowMillis) {
    String lower = text.toLowerCase(Locale.ROOT);
    if (origin != OWN && hit(lower, muted(), false)) {
      return Verdict.HIDE;
    }
    if (origin == OWN) {
      return Verdict.SHOW;
    }
    if (repeated(text, nowMillis)) {
      return Verdict.HIDE;
    }
    if (mentions.get()
        && self != null
        && !self.isEmpty()
        && contains(lower, self.toLowerCase(Locale.ROOT), true)) {
      return Verdict.MATCH;
    }
    Mode current = mode.get();
    boolean keyed = current == Mode.KEYWORDS || current == Mode.NEARBY_OR_KEYWORDS;
    if (keyed && hit(lower, keywords(), wholeWords.get())) {
      return Verdict.MATCH;
    }
    if (origin == SERVER) {
      return serverMessages.get() ? Verdict.SHOW : Verdict.HIDE;
    }
    if (current == Mode.EVERYONE) {
      return Verdict.SHOW;
    }
    if (current != Mode.KEYWORDS && near(distance)) {
      return Verdict.SHOW;
    }
    return Verdict.HIDE;
  }

  private boolean near(double distance) {
    double limit = radius.get();
    if (limit <= 0.0) {
      return distance < Double.POSITIVE_INFINITY;
    }
    return distance <= limit;
  }

  private boolean repeated(String text, long nowMillis) {
    if (!repeats.get()) {
      return false;
    }
    long span = (long) (window.get() * 1000.0);
    boolean seen = false;
    for (int i = 0; i < RECENT; i++) {
      if (recentText[i] != null && nowMillis - recentAt[i] <= span && recentText[i].equals(text)) {
        recentAt[i] = nowMillis;
        seen = true;
        break;
      }
    }
    if (!seen) {
      recentText[recentHead] = text;
      recentAt[recentHead] = nowMillis;
      recentHead = (recentHead + 1) % RECENT;
    }
    return seen;
  }

  private String[] keywords() {
    String source = keywords.get();
    if (!source.equals(keywordSource)) {
      keywordSource = source;
      keywordList = split(source);
    }
    return keywordList;
  }

  private String[] muted() {
    String source = muted.get();
    if (!source.equals(mutedSource)) {
      mutedSource = source;
      mutedList = split(source);
    }
    return mutedList;
  }

  static String[] split(String source) {
    String[] parts = source.toLowerCase(Locale.ROOT).split(",");
    int count = 0;
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        parts[count++] = trimmed;
      }
    }
    if (count == 0) {
      return NONE;
    }
    String[] list = new String[count];
    System.arraycopy(parts, 0, list, 0, count);
    return list;
  }

  private static boolean hit(String lower, String[] needles, boolean whole) {
    for (String needle : needles) {
      if (contains(lower, needle, whole)) {
        return true;
      }
    }
    return false;
  }

  static boolean contains(String lower, String needle, boolean whole) {
    int from = lower.indexOf(needle);
    while (from >= 0) {
      if (!whole) {
        return true;
      }
      int end = from + needle.length();
      boolean before =
          from == 0
              || !Character.isLetterOrDigit(lower.charAt(from - 1))
                  && lower.charAt(from - 1) != '_';
      boolean after =
          end == lower.length()
              || !Character.isLetterOrDigit(lower.charAt(end)) && lower.charAt(end) != '_';
      if (before && after) {
        return true;
      }
      from = lower.indexOf(needle, from + 1);
    }
    return false;
  }

  @Override
  public String group() {
    return "Interface";
  }
}
