package me.cxiy.cxvisuals.gui.map;

import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class PublishModel {
  public static final int TITLE_LIMIT = 48;

  private final String server;
  private final boolean hasSelection;
  private String title;
  private boolean whole = true;
  private boolean showName = true;
  private boolean busy;

  public PublishModel(String server, String suggested, boolean hasSelection) {
    this.server = server;
    this.title = clip(suggested);
    this.hasSelection = hasSelection;
  }

  public String server() {
    return server;
  }

  public String title() {
    return title;
  }

  public void title(String next) {
    title = clip(next);
  }

  public boolean whole() {
    return whole;
  }

  public boolean hasSelection() {
    return hasSelection;
  }

  public void whole(boolean next) {
    whole = next || !hasSelection;
  }

  public boolean showName() {
    return showName;
  }

  public void showName(boolean next) {
    showName = next;
  }

  public boolean busy() {
    return busy;
  }

  public boolean canPublish() {
    return !busy && !title.isBlank();
  }

  public boolean begin() {
    if (!canPublish()) {
      return false;
    }
    busy = true;
    return true;
  }

  public void finish(boolean published) {
    busy = published;
  }

  public List<String> warning() {
    List<String> lines = new ArrayList<>(4);
    lines.add(
        Lang.format("This map becomes public on {0}: anyone with CxVisuals can see it.", server));
    lines.add(
        showName
            ? Lang.tr("Your Minecraft name is shown as the author.")
            : Lang.tr("It is published without your name."));
    if (whole) {
      lines.add(Lang.tr("Maps larger than 128 by 128 chunks are trimmed around you."));
    }
    lines.add(Lang.tr("Only terrain is shared. Waypoints and chat never are."));
    lines.add(Lang.tr("You can delete it any time."));
    return lines;
  }

  private static String clip(String value) {
    String plain = value == null ? "" : value.strip();
    return plain.length() > TITLE_LIMIT ? plain.substring(0, TITLE_LIMIT) : plain;
  }
}
