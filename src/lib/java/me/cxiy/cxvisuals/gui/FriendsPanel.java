package me.cxiy.cxvisuals.gui;

import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.friends.Friends;
import me.cxiy.cxvisuals.modules.friends.FriendsModule;

public final class FriendsPanel extends Widget {
  public static final int ADD = 0;
  public static final int ACCEPT = 1;
  public static final int DECLINE = 2;
  public static final int CANCEL = 3;
  public static final int OPEN = 4;
  public static final int JOIN = 5;
  public static final int STAR = 6;
  public static final int FIELD = 7;

  private static final String EMPTY =
      Lang.key("No friends yet. Add someone by their username or Minecraft name.");
  private static final float LEFT = 8.0F;
  private static final float RIGHT = 6.0F;
  private static final float TOP = 44.0F;
  private static final float NOTICE = 20.0F;
  private static final float ROW = 48.0F;
  private static final float SLIM = 40.0F;
  private static final float FACE = 32.0F;
  private static final float FACE_RADIUS = 9.0F;
  private static final float GAP = 10.0F;
  private static final float DOT = 9.0F;
  private static final float BADGE = 18.0F;
  private static final float STAR_SIZE = 6.0F;
  private static final int NAME_LIMIT = 16;

  private final Friends friends;
  private final PanelHost host;
  private final Hits hits = new Hits(160);
  private final Field field = new Field(Lang.key("Add by username or name"), NAME_LIMIT);
  private float addWidth = -1.0F;

  public FriendsPanel(Friends friends, PanelHost host) {
    this.friends = friends;
    this.host = host;
    this.h = TOP + ROW;
  }

  public float hitX(int kind, String uuid) {
    return hits.centerX(kind, uuid);
  }

  public float hitY(int kind, String uuid) {
    return hits.centerY(kind, uuid);
  }

  public boolean typing() {
    return field.focused();
  }

  private float noticeHeight() {
    return friends.notice().isEmpty() ? 0.0F : NOTICE;
  }

  @Override
  public float measure(Canvas c) {
    if (addWidth < 0.0F) {
      addWidth =
          (float) Math.ceil(c.textWidth(FontStyle.CAPTION, Lang.tr("Add")) + Hits.PILL_PAD * 2.0F);
    }
    int people = friends.friends().size();
    h =
        TOP
            + noticeHeight()
            + (friends.incoming().size() + friends.outgoing().size()) * SLIM
            + Math.max(1, people) * ROW;
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    measure(c);
    hits.clear();
    float right = x + w - RIGHT;
    float fieldTop = y + (TOP - Field.HEIGHT) * 0.5F;
    float fieldWidth = w - LEFT - RIGHT - addWidth - 8.0F;
    field.draw(c, t, x + LEFT, fieldTop, fieldWidth, dt);
    hits.add(FIELD, null, x + LEFT, fieldTop, fieldWidth, Field.HEIGHT);
    hits.pill(
        c,
        t,
        ADD,
        null,
        Lang.tr("Add"),
        right,
        y + TOP * 0.5F,
        mouseX,
        mouseY,
        t.ink(),
        !field.text().isBlank());
    float cursor = y + TOP;
    if (!friends.notice().isEmpty()) {
      c.text(
          FontStyle.SMALL,
          friends.notice(),
          x + LEFT + 4.0F,
          cursor + (NOTICE - c.lineHeight(FontStyle.SMALL)) * 0.5F - 3.0F,
          t.grey());
      cursor += NOTICE;
    }
    float rowLine = c.lineHeight(FontStyle.ROW);
    float smallLine = c.lineHeight(FontStyle.SMALL);

    List<Friends.Person> incoming = friends.incoming();
    for (int i = 0; i < incoming.size(); i++) {
      Friends.Person person = incoming.get(i);
      rule(c, t, cursor);
      slim(c, t, person.name(), Lang.tr("Wants to be friends"), cursor, rowLine, smallLine);
      float left =
          hits.pill(
              c,
              t,
              DECLINE,
              person.uuid(),
              Lang.tr("Decline"),
              right,
              cursor + SLIM * 0.5F,
              mouseX,
              mouseY,
              t.ink(),
              false);
      hits.pill(
          c,
          t,
          ACCEPT,
          person.uuid(),
          Lang.tr("Accept"),
          left - 6.0F,
          cursor + SLIM * 0.5F,
          mouseX,
          mouseY,
          t.ink(),
          true);
      cursor += SLIM;
    }
    List<Friends.Person> outgoing = friends.outgoing();
    for (int i = 0; i < outgoing.size(); i++) {
      Friends.Person person = outgoing.get(i);
      rule(c, t, cursor);
      slim(c, t, person.name(), Lang.tr("Request sent"), cursor, rowLine, smallLine);
      hits.pill(
          c,
          t,
          CANCEL,
          person.uuid(),
          Lang.tr("Cancel"),
          right,
          cursor + SLIM * 0.5F,
          mouseX,
          mouseY,
          t.ink(),
          false);
      cursor += SLIM;
    }

    List<Friends.Friend> list = friends.friends();
    if (list.isEmpty()) {
      rule(c, t, cursor);
      c.text(
          FontStyle.SMALL,
          Lang.tr(EMPTY),
          x + LEFT + 4.0F,
          cursor + (ROW - smallLine) * 0.5F,
          t.grey());
      return;
    }
    for (int i = 0; i < list.size(); i++) {
      Friends.Friend friend = list.get(i);
      rule(c, t, cursor);
      boolean over = mouseX >= x && mouseX < x + w && mouseY >= cursor && mouseY < cursor + ROW;
      if (over || hits.down(OPEN, friend.uuid)) {
        c.roundRect(
            x + 2.0F,
            cursor + 2.0F,
            w - 4.0F,
            ROW - 4.0F,
            10.0F,
            Theme.withAlpha(t.ink(), hits.down(OPEN, friend.uuid) ? 0.08F : 0.04F));
      }
      hits.add(OPEN, friend.uuid, x, cursor, w, ROW);
      float faceTop = cursor + (ROW - FACE) * 0.5F;
      c.pushAlpha(friend.online ? 1.0F : 0.45F);
      Hits.face(c, t, friend.entityId, friend.name, x + LEFT, faceTop, FACE, FACE_RADIUS);
      c.popAlpha();
      if (friend.online) {
        float dotX = x + LEFT + FACE - DOT + 2.0F;
        float dotY = faceTop + FACE - DOT + 2.0F;
        c.roundRect(
            dotX - 1.5F, dotY - 1.5F, DOT + 3.0F, DOT + 3.0F, (DOT + 3.0F) * 0.5F, t.surface());
        c.roundRect(dotX, dotY, DOT, DOT, DOT * 0.5F, friend.busy ? t.grey() : t.red());
      }
      float nameX = x + LEFT + FACE + GAP;
      float textTop = cursor + (ROW - rowLine - smallLine - 1.0F) * 0.5F;
      c.text(FontStyle.ROW, friend.name, nameX, textTop, friend.online ? t.ink() : t.ink2());
      float nameEnd = nameX + c.textWidth(FontStyle.ROW, friend.name);
      float starTop = textTop + (rowLine - STAR_SIZE) * 0.5F;
      hits.add(
          STAR, friend.uuid, nameEnd + 2.0F, textTop - 2.0F, STAR_SIZE + 12.0F, rowLine + 4.0F);
      boolean starOver =
          mouseX >= nameEnd + 2.0F
              && mouseX < nameEnd + STAR_SIZE + 14.0F
              && mouseY >= textTop - 2.0F
              && mouseY < textTop + rowLine + 2.0F;
      if (friend.favorite || starOver) {
        c.roundRectBorder(
            nameEnd + 8.0F,
            starTop,
            STAR_SIZE,
            STAR_SIZE,
            STAR_SIZE * 0.5F,
            1.0F,
            friend.favorite ? t.ink() : t.grey());
        if (friend.favorite) {
          c.roundRect(
              nameEnd + 10.0F, starTop + 2.0F, STAR_SIZE - 4.0F, STAR_SIZE - 4.0F, 1.0F, t.ink());
        }
      }
      float edge = right;
      if (friend.unread > 0) {
        String count = friend.unread > 9 ? "9+" : Integer.toString(friend.unread);
        float badge = Math.max(BADGE, c.textWidth(FontStyle.CAPTION, count) + 10.0F);
        c.roundRect(
            edge - badge, cursor + (ROW - BADGE) * 0.5F, badge, BADGE, BADGE * 0.5F, t.red());
        c.text(
            FontStyle.CAPTION,
            count,
            edge - badge + (badge - c.textWidth(FontStyle.CAPTION, count)) * 0.5F,
            cursor + (ROW - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
            0xFFFFFFFF);
        edge -= badge + 8.0F;
      }
      if (friend.online && !friend.server.isEmpty()) {
        edge =
            hits.pill(
                    c,
                    t,
                    JOIN,
                    friend.uuid,
                    Lang.tr("Join"),
                    edge,
                    cursor + ROW * 0.5F,
                    mouseX,
                    mouseY,
                    t.ink(),
                    false)
                - 8.0F;
      }
      c.pushClip(nameX, cursor, edge - nameX, ROW);
      c.text(FontStyle.SMALL, friend.line(), nameX, textTop + rowLine + 1.0F, t.grey());
      c.popClip();
      cursor += ROW;
    }
  }

  private void rule(Canvas c, Theme t, float top) {
    c.roundRect(x + LEFT, top, w - LEFT, 1.0F, 0.0F, t.hair());
  }

  private void slim(
      Canvas c, Theme t, String name, String line, float top, float rowLine, float smallLine) {
    float textTop = top + (SLIM - rowLine - smallLine - 1.0F) * 0.5F;
    c.text(FontStyle.ROW, name, x + LEFT + 4.0F, textTop, t.ink());
    c.text(FontStyle.SMALL, line, x + LEFT + 4.0F, textTop + rowLine + 1.0F, t.grey());
  }

  private void add() {
    String name = field.take();
    if (!name.isEmpty()) {
      friends.actions().add(name);
    }
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      field.focus(false);
      return false;
    }
    int index = hits.at(mx, my);
    boolean typing = index != Hits.NONE && hits.kind(index) == FIELD;
    field.focus(typing);
    if (typing) {
      field.press(mx);
    }
    hits.press(mx, my);
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    field.drag(mx);
  }

  @Override
  public void mouseUp(float mx, float my) {
    field.release();
    int index = hits.release(mx, my);
    if (index == Hits.NONE) {
      return;
    }
    String uuid = (String) hits.target(index);
    switch (hits.kind(index)) {
      case ADD -> add();
      case ACCEPT -> friends.actions().accept(uuid);
      case DECLINE, CANCEL -> friends.actions().remove(uuid);
      case JOIN -> {
        Friends.Friend friend = friends.friend(uuid);
        if (friend != null && !friend.server.isEmpty()) {
          friends.actions().join(friend.server);
        }
      }
      case STAR -> {
        Friends.Friend friend = friends.friend(uuid);
        if (friend != null) {
          friends.favorite(uuid, !friend.favorite);
        }
      }
      case OPEN -> {
        friends.open(uuid);
        host.openTab(FriendsModule.CHAT);
      }
      default -> {}
    }
  }

  @Override
  public void finishEditing() {
    field.focus(false);
  }

  @Override
  public boolean keyDown(int keyCode, boolean control, boolean shift) {
    if (field.submits(keyCode)) {
      add();
      return true;
    }
    return field.key(keyCode, control, shift);
  }

  @Override
  public boolean charTyped(int codepoint) {
    return field.type(codepoint);
  }
}
