package me.cxiy.cxvisuals.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Page {
  private final Settings owner;
  private final String id;
  private final String title;
  private final List<Group> groups = new ArrayList<>();
  private final List<Group> view = Collections.unmodifiableList(groups);

  Page(Settings owner, String id, String title) {
    this.owner = owner;
    this.id = id;
    this.title = title;
  }

  public String id() {
    return id;
  }

  public String title() {
    return Lang.tr(title);
  }

  public List<Group> groups() {
    return view;
  }

  public Group leading(String groupTitle) {
    Group group = new Group(owner, id, groupTitle);
    groups.add(0, group);
    return group;
  }

  public Group group(String groupTitle) {
    Group group = new Group(owner, id, groupTitle);
    groups.add(group);
    return group;
  }
}
