package me.cxiy.cxvisuals.modules.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Query {
  public static final Query ALL =
      new Query("", new String[0], new String[0], new String[0], new String[0], new String[0]);

  private final String source;
  private final String[] words;
  private final String[] spaces;
  private final String[] tips;
  private final String[] notWords;
  private final String[] notSpaces;

  private Query(
      String source,
      String[] words,
      String[] spaces,
      String[] tips,
      String[] notWords,
      String[] notSpaces) {
    this.source = source;
    this.words = words;
    this.spaces = spaces;
    this.tips = tips;
    this.notWords = notWords;
    this.notSpaces = notSpaces;
  }

  public static Query parse(String text) {
    String trimmed = text.strip();
    if (trimmed.isEmpty()) {
      return ALL;
    }
    List<String> words = new ArrayList<>(4);
    List<String> spaces = new ArrayList<>(1);
    List<String> tips = new ArrayList<>(1);
    List<String> notWords = new ArrayList<>(1);
    List<String> notSpaces = new ArrayList<>(1);
    for (String token : trimmed.toLowerCase(Locale.ROOT).split("\\s+")) {
      boolean negated = token.length() > 1 && token.charAt(0) == '-';
      String body = negated ? token.substring(1) : token;
      if (body.length() > 1 && body.charAt(0) == '@') {
        (negated ? notSpaces : spaces).add(body.substring(1));
      } else if (body.length() > 1 && body.charAt(0) == '$' && !negated) {
        tips.add(body.substring(1));
      } else if (!body.isEmpty()) {
        (negated ? notWords : words).add(body);
      }
    }
    return new Query(
        trimmed,
        words.toArray(new String[0]),
        spaces.toArray(new String[0]),
        tips.toArray(new String[0]),
        notWords.toArray(new String[0]),
        notSpaces.toArray(new String[0]));
  }

  public String source() {
    return source;
  }

  public boolean empty() {
    return this == ALL;
  }

  public boolean matches(Handles handles, int handle) {
    String name = handles.name(handle);
    for (String word : words) {
      if (!name.contains(word) && !handles.key(handle).contains(word)) {
        return false;
      }
    }
    for (String word : notWords) {
      if (name.contains(word) || handles.key(handle).contains(word)) {
        return false;
      }
    }
    String space = handles.space(handle);
    for (String wanted : spaces) {
      if (!space.contains(wanted)) {
        return false;
      }
    }
    for (String banned : notSpaces) {
      if (space.contains(banned)) {
        return false;
      }
    }
    if (tips.length > 0) {
      String tip = handles.tip(handle);
      for (String word : tips) {
        if (!tip.contains(word)) {
          return false;
        }
      }
    }
    return true;
  }
}
