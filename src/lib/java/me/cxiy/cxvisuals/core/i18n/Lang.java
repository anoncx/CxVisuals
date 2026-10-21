package me.cxiy.cxvisuals.core.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Lang {
  private static final String PATH = "/assets/cxvisuals/i18n/";
  private static final char FORM_BREAK = '|';
  private static final Language[] LANGUAGES = Language.values();
  private static final Tables[] LOADED = new Tables[LANGUAGES.length];
  private static final List<Runnable> LISTENERS = new ArrayList<>(4);

  private record Tables(Map<String, String> text, Map<String, String[]> forms) {}

  private static volatile Tables tables;
  private static volatile Language language = Language.ENGLISH;
  private static volatile int generation;

  private Lang() {}

  public static Language language() {
    return language;
  }

  public static Locale locale() {
    return language.locale;
  }

  public static int generation() {
    return generation;
  }

  public static void onChange(Runnable listener) {
    LISTENERS.add(listener);
  }

  public static synchronized void use(Language next) {
    if (next == language) {
      return;
    }
    tables = next == Language.ENGLISH ? null : load(next);
    language = next;
    generation++;
    for (int i = 0; i < LISTENERS.size(); i++) {
      LISTENERS.get(i).run();
    }
  }

  public static String key(String text) {
    return text;
  }

  public static String[] keys(String... texts) {
    return texts;
  }

  public static String tr(String text) {
    Tables current = tables;
    if (current == null) {
      return text;
    }
    String found = current.text.get(text);
    return found == null ? text : found;
  }

  public static String tr(String context, String text) {
    Tables current = tables;
    if (current == null) {
      return text;
    }
    String found = current.text.get(context);
    return found == null ? tr(text) : found;
  }

  public static String count(int count, String one, String other) {
    Tables current = tables;
    if (current == null) {
      return count == 1 || count == -1 ? one : other;
    }
    String[] forms = current.forms.get(other);
    if (forms == null) {
      forms = split(current.text.get(other), one, other);
      current.forms.put(other, forms);
    }
    return forms[Math.min(forms.length - 1, language.form(count))];
  }

  public static String format(String pattern, Object... values) {
    String text = tr(pattern);
    int brace = text.indexOf('{');
    if (brace < 0) {
      return text;
    }
    StringBuilder out = new StringBuilder(text.length() + 16 * values.length);
    int from = 0;
    while (brace >= 0) {
      int close = text.indexOf('}', brace);
      int index = close == brace + 2 ? text.charAt(brace + 1) - '0' : -1;
      if (index < 0 || index >= values.length) {
        out.append(text, from, brace + 1);
        from = brace + 1;
      } else {
        out.append(text, from, brace).append(values[index]);
        from = close + 1;
      }
      brace = text.indexOf('{', from);
    }
    return out.append(text, from, text.length()).toString();
  }

  private static String[] split(String entry, String one, String other) {
    if (entry == null) {
      return new String[] {one, other};
    }
    int parts = 1;
    for (int i = 0; i < entry.length(); i++) {
      if (entry.charAt(i) == FORM_BREAK) {
        parts++;
      }
    }
    String[] forms = new String[parts];
    int start = 0;
    for (int i = 0; i < parts; i++) {
      int end = entry.indexOf(FORM_BREAK, start);
      forms[i] = end < 0 ? entry.substring(start) : entry.substring(start, end);
      start = end + 1;
    }
    return forms;
  }

  private static Tables load(Language target) {
    Tables cached = LOADED[target.ordinal()];
    if (cached != null) {
      return cached;
    }
    Map<String, String> text = new HashMap<>(2048);
    try (InputStream stream = Lang.class.getResourceAsStream(PATH + target.code + ".json")) {
      if (stream != null) {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
          JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
          for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            text.put(entry.getKey().intern(), entry.getValue().getAsString());
          }
        }
      }
    } catch (IOException | RuntimeException e) {
      text.clear();
    }
    Tables loaded = new Tables(text, new ConcurrentHashMap<>(32));
    LOADED[target.ordinal()] = loaded;
    return loaded;
  }
}
