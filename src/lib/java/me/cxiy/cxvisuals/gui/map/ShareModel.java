package me.cxiy.cxvisuals.gui.map;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class ShareModel {
  public enum Step {
    PICK,
    REVIEW,
    SENT
  }

  public record Friend(String uuid, String name, int entityId, boolean online, boolean member) {}

  public record Warning(String title, List<String> lines, String action) {}

  private static final int NAMED = 3;

  private final String server;
  private final List<Friend> friends;
  private final Set<String> selected = new LinkedHashSet<>();
  private boolean backfill;
  private Step step = Step.PICK;

  public ShareModel(String server, List<Friend> friends) {
    this.server = server;
    this.friends = List.copyOf(friends);
  }

  public String server() {
    return server;
  }

  public Step step() {
    return step;
  }

  public boolean backfill() {
    return backfill;
  }

  public void backfill(boolean on) {
    backfill = on;
    step = Step.PICK;
  }

  public List<Friend> candidates() {
    return friends.stream().filter(friend -> !friend.member()).toList();
  }

  public List<Friend> members() {
    return friends.stream().filter(Friend::member).toList();
  }

  public List<String> selected() {
    return List.copyOf(selected);
  }

  public boolean isSelected(String uuid) {
    return selected.contains(uuid);
  }

  public void toggle(String uuid) {
    boolean offered = candidates().stream().anyMatch(friend -> friend.uuid().equals(uuid));
    if (!offered || step == Step.SENT) {
      return;
    }
    if (!selected.remove(uuid)) {
      selected.add(uuid);
    }
    step = Step.PICK;
  }

  public boolean canContinue() {
    return !selected.isEmpty() && step == Step.PICK;
  }

  public void review() {
    if (canContinue()) {
      step = Step.REVIEW;
    }
  }

  public void back() {
    if (step == Step.REVIEW) {
      step = Step.PICK;
    }
  }

  public boolean send() {
    if (step != Step.REVIEW) {
      return false;
    }
    step = Step.SENT;
    return true;
  }

  public Warning warning() {
    List<String> names = new ArrayList<>();
    for (Friend friend : candidates()) {
      if (selected.contains(friend.uuid())) {
        names.add(friend.name());
      }
    }
    String who = who(names);
    List<String> lines = new ArrayList<>(4);
    lines.add(Lang.format("They will see everything you explore on {0} from now on.", server));
    if (backfill) {
      lines.add(Lang.tr("They will also get every area you have already explored there."));
      if (!members().isEmpty()) {
        lines.add(Lang.tr("Everyone already in this map gets those areas too."));
      }
    }
    lines.add(Lang.tr("Your location stays private unless you switch it on for them."));
    lines.add(Lang.tr("Waypoints are never shared this way."));
    return new Warning(
        Lang.format("Share this map with {0}?", who),
        lines,
        names.size() == 1 ? Lang.format("Share map with {0}", who) : Lang.tr("Share map"));
  }

  private static String who(List<String> names) {
    if (names.size() <= NAMED) {
      return String.join(", ", names);
    }
    return String.join(", ", names.subList(0, NAMED - 1))
        + " "
        + Lang.format("and {0} others", names.size() - (NAMED - 1));
  }
}
