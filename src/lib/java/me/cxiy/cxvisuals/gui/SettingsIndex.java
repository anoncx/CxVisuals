package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.InfoSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;
import me.cxiy.cxvisuals.core.settings.Setting;

final class SettingsIndex {
  record Hit(
      int entry,
      int page,
      String settingId,
      String module,
      String pageTitle,
      String group,
      String title,
      String description,
      int score) {
    String path() {
      return pageTitle.isEmpty() || pageTitle.equals(module) ? module : module + " › " + pageTitle;
    }
  }

  private static final int TITLE_START = 8;
  private static final int TITLE_HAS = 5;
  private static final int GROUP_HAS = 3;
  private static final int PATH_HAS = 2;
  private static final int TEXT_HAS = 1;

  private final List<Hit> all = new ArrayList<>();

  SettingsIndex(List<ModuleView> entries) {
    for (int e = 0; e < entries.size(); e++) {
      Module module = entries.get(e).module;
      List<Page> pages = module.sections();
      for (int p = 0; p < pages.size(); p++) {
        for (Group group : pages.get(p).groups()) {
          for (Setting<?> setting : group.settings()) {
            if (searchable(setting)) {
              all.add(
                  new Hit(
                      e,
                      p,
                      setting.id(),
                      module.name(),
                      pages.get(p).title(),
                      group.title(),
                      setting.title(),
                      setting.description(),
                      0));
            }
          }
        }
      }
    }
  }

  private static boolean searchable(Setting<?> setting) {
    return !(setting instanceof PanelSetting)
        && !(setting instanceof InfoSetting)
        && !setting.title().isEmpty();
  }

  int size() {
    return all.size();
  }

  List<Hit> search(String query, int limit) {
    String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
    if (words.length == 0 || words[0].isEmpty()) {
      return List.of();
    }
    List<Hit> found = new ArrayList<>();
    for (int i = 0; i < all.size(); i++) {
      Hit hit = all.get(i);
      int score = score(hit, words);
      if (score > 0) {
        found.add(
            new Hit(
                hit.entry(),
                hit.page(),
                hit.settingId(),
                hit.module(),
                hit.pageTitle(),
                hit.group(),
                hit.title(),
                hit.description(),
                score));
      }
    }
    found.sort(
        (a, b) ->
            a.score() != b.score()
                ? Integer.compare(b.score(), a.score())
                : a.entry() != b.entry()
                    ? Integer.compare(a.entry(), b.entry())
                    : Integer.compare(a.page(), b.page()));
    return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
  }

  private static int score(Hit hit, String[] words) {
    String title = hit.title().toLowerCase(Locale.ROOT);
    String group = hit.group().toLowerCase(Locale.ROOT);
    String path = (hit.module() + ' ' + hit.pageTitle()).toLowerCase(Locale.ROOT);
    String text = hit.description().toLowerCase(Locale.ROOT);
    int total = 0;
    for (String word : words) {
      int best = 0;
      if (title.startsWith(word) || title.contains(' ' + word)) {
        best = TITLE_START;
      } else if (title.contains(word)) {
        best = TITLE_HAS;
      } else if (group.contains(word)) {
        best = GROUP_HAS;
      } else if (path.contains(word)) {
        best = PATH_HAS;
      } else if (text.contains(word)) {
        best = TEXT_HAS;
      }
      if (best == 0) {
        return 0;
      }
      total += best;
    }
    return total;
  }
}
