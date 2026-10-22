package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.friends.Identity;
import me.cxiy.cxvisuals.modules.friends.LinkCode;

public final class IdentityPanel extends Widget {
  private static final int SAVE = 0;
  private static final int OFFER = 1;
  private static final int COPY = 2;
  private static final int LINK = 3;
  private static final int UNLINK = 4;
  private static final int ADOPT = 5;
  private static final int NAME = 6;
  private static final int ENTRY = 7;

  private static final String LOCKED =
      Lang.key(
          "Your chats are locked on this device. Enter a link code from a device that can read them, or start over here.");
  private static final String PICK = Lang.key("Pick a name friends can find you by.");
  private static final String FOUND = Lang.key("Friends find you as {0}.");
  private static final String HOW =
      Lang.key("Enter this on your other account or device. It works once.");
  private static final String IDLE =
      Lang.key("Share your friends and chats with another account or device.");
  private static final float LEFT = 8.0F;
  private static final float RIGHT = 6.0F;
  private static final float FIELD_ROW = 52.0F;
  private static final float CAPTION = 24.0F;
  private static final float NOTE = 22.0F;
  private static final float MEMBER = 40.0F;
  private static final float HEADER = 30.0F;
  private static final float WELL = 40.0F;
  private static final float ACTION = 48.0F;
  private static final float PAD = 10.0F;
  private static final float FACE = 26.0F;
  private static final float FACE_RADIUS = 8.0F;
  private static final long MINUTE = 60_000L;

  private final Identity identity;
  private final Hits hits = new Hits(32);
  private final Field name = new Field(Lang.key("Choose a username"), Identity.HANDLE_MAX);
  private final Field entry = new Field(Lang.key("Enter a link code"), 16);
  private final List<String> rows = new ArrayList<>(4);
  private Canvas canvas;
  private String wrapped = "";
  private float wrappedRoom = -1.0F;
  private float addWidth = -1.0F;

  public IdentityPanel(Identity identity) {
    this.identity = identity;
    this.h = FIELD_ROW;
  }

  public float hitX(int kind) {
    return hits.centerX(kind, null);
  }

  public float hitY(int kind) {
    return hits.centerY(kind, null);
  }

  public boolean typing() {
    return name.focused() || entry.focused();
  }

  @Override
  public float measure(Canvas c) {
    canvas = c;
    h = layout(c, null, 0.0F, 0.0F, false, 0.0F);
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    canvas = c;
    hits.clear();
    h = layout(c, t, mouseX, mouseY, true, dt);
  }

  private float layout(
      Canvas c, Theme t, float mx, float my, boolean drawing, float dt) {
    float cursor = y;
    float right = x + w - RIGHT;
    float inner = w - LEFT - RIGHT;
    float line = c.lineHeight(FontStyle.SMALL);
    if (identity.state() == Identity.State.LOCKED) {
      wrap(c, Lang.tr(LOCKED), inner);
      if (drawing) {
        for (int i = 0; i < rows.size(); i++) {
          c.text(FontStyle.SMALL, rows.get(i), x + LEFT, cursor + PAD + i * line, t.alert());
        }
        hits.pill(
            c,
            t,
            ADOPT,
            null,
            Lang.tr("Start over here"),
            right,
            cursor + PAD + rows.size() * line + 6.0F + Hits.PILL * 0.5F,
            mx,
            my,
            t.alert(),
            false);
      }
      cursor += PAD + rows.size() * line + 6.0F + Hits.PILL + PAD;
      if (drawing) {
        rule(c, t, cursor - 1.0F);
      }
    }
    if (addWidth < 0.0F) {
      addWidth =
          (float) Math.ceil(c.textWidth(FontStyle.CAPTION, Lang.tr("Save")) + Hits.PILL_PAD * 2.0F);
    }
    float fieldWidth = inner - addWidth - 8.0F;
    if (drawing) {
      float fieldTop = cursor + (FIELD_ROW - Field.HEIGHT) * 0.5F;
      name.draw(c, t, x + LEFT, fieldTop, fieldWidth, dt);
      hits.add(NAME, null, x + LEFT, fieldTop, fieldWidth, Field.HEIGHT);
      hits.pill(
          c,
          t,
          SAVE,
          null,
          Lang.tr("Save"),
          right,
          cursor + FIELD_ROW * 0.5F,
          mx,
          my,
          t.ink(),
          Identity.validHandle(name.text().strip()));
    }
    cursor += FIELD_ROW;
    if (drawing) {
      String caption =
          identity.handle().isEmpty() ? Lang.tr(PICK) : Lang.format(FOUND, identity.handle());
      c.text(FontStyle.SMALL, caption, x + LEFT + 4.0F, cursor - 6.0F, t.grey());
    }
    cursor += CAPTION - 6.0F;
    List<Identity.Member> members = identity.members();
    if (members.size() > 1 || identity.state() == Identity.State.READY) {
      if (drawing) {
        rule(c, t, cursor);
        c.text(
            FontStyle.CAPTION,
            Lang.tr("Linked accounts").toUpperCase(),
            x + LEFT + 4.0F,
            cursor + (HEADER - c.lineHeight(FontStyle.CAPTION)) * 0.5F + 2.0F,
            t.grey());
      }
      cursor += HEADER;
      for (int i = 0; i < members.size(); i++) {
        Identity.Member member = members.get(i);
        if (drawing) {
          float faceTop = cursor + (MEMBER - FACE) * 0.5F;
          Hits.face(c, t, -1, member.name(), x + LEFT, faceTop, FACE, FACE_RADIUS);
          float textX = x + LEFT + FACE + 10.0F;
          c.text(
              FontStyle.ROW,
              member.name(),
              textX,
              cursor + (MEMBER - c.lineHeight(FontStyle.ROW)) * 0.5F,
              t.ink());
          float edge = right;
          if (!member.home() && identity.state() == Identity.State.READY) {
            edge =
                hits.pill(
                        c,
                        t,
                        UNLINK,
                        member.uuid(),
                        Lang.tr("Unlink"),
                        edge,
                        cursor + MEMBER * 0.5F,
                        mx,
                        my,
                        t.alert(),
                        false)
                    - 8.0F;
          }
          String tag =
              member.uuid().equals(identity.self())
                  ? Lang.tr("This account")
                  : member.home() ? Lang.tr("Main") : "";
          if (!tag.isEmpty()) {
            c.text(
                FontStyle.SMALL,
                tag,
                edge - c.textWidth(FontStyle.SMALL, tag),
                cursor + (MEMBER - line) * 0.5F,
                t.grey());
          }
        }
        cursor += MEMBER;
      }
    }
    if (identity.state() == Identity.State.READY) {
      if (drawing) {
        rule(c, t, cursor);
      }
      String code = identity.code();
      if (code.isEmpty()) {
        if (drawing) {
          c.text(
              FontStyle.SMALL,
              Lang.tr(IDLE),
              x + LEFT + 4.0F,
              cursor + (ACTION - line) * 0.5F,
              t.grey());
        }
        cursor += ACTION;
        if (drawing) {
          hits.pill(
              c,
              t,
              OFFER,
              null,
              Lang.tr("Get link code"),
              right,
              cursor + ACTION * 0.5F - 4.0F,
              mx,
              my,
              t.ink(),
              true);
        }
        cursor += ACTION - 4.0F;
      } else {
        if (drawing) {
          float wellTop = cursor + 10.0F;
          Depth.inset(c, t, x + LEFT, wellTop, inner, WELL, 12.0F, t.well());
          String pretty = LinkCode.pretty(code);
          c.text(
              FontStyle.ROW,
              pretty,
              x + LEFT + 14.0F,
              wellTop + (WELL - c.lineHeight(FontStyle.ROW)) * 0.5F,
              t.ink());
          hits.pill(
              c,
              t,
              COPY,
              null,
              Lang.tr("Copy"),
              x + LEFT + inner - 8.0F,
              wellTop + WELL * 0.5F,
              mx,
              my,
              t.ink(),
              false);
          long left = Math.max(1L, (identity.codeUntil() - System.currentTimeMillis()) / MINUTE + 1L);
          c.text(
              FontStyle.SMALL,
              Lang.tr(HOW) + " " + Lang.format("{0} min left.", left),
              x + LEFT + 4.0F,
              wellTop + WELL + 8.0F,
              t.grey());
        }
        cursor += 10.0F + WELL + 8.0F + line + 8.0F;
      }
    }
    if (drawing) {
      rule(c, t, cursor);
      float fieldTop = cursor + (FIELD_ROW - Field.HEIGHT) * 0.5F;
      float linkWidth =
          (float) Math.ceil(c.textWidth(FontStyle.CAPTION, Lang.tr("Link")) + Hits.PILL_PAD * 2.0F);
      float entryWidth = inner - linkWidth - 8.0F;
      entry.draw(c, t, x + LEFT, fieldTop, entryWidth, dt);
      hits.add(ENTRY, null, x + LEFT, fieldTop, entryWidth, Field.HEIGHT);
      hits.pill(
          c,
          t,
          LINK,
          null,
          Lang.tr("Link"),
          right,
          cursor + FIELD_ROW * 0.5F,
          mx,
          my,
          t.ink(),
          LinkCode.normalize(entry.text()).length() == LinkCode.LENGTH && !identity.busy());
    }
    cursor += FIELD_ROW;
    if (!identity.notice().isEmpty()) {
      if (drawing) {
        c.text(
            FontStyle.SMALL,
            identity.notice(),
            x + LEFT + 4.0F,
            cursor + (NOTE - line) * 0.5F - 4.0F,
            t.grey());
      }
      cursor += NOTE;
    }
    return cursor - y;
  }

  private void rule(Canvas c, Theme t, float top) {
    c.roundRect(x + LEFT, top, w - LEFT, 1.0F, 0.0F, t.hair());
  }

  private void wrap(Canvas c, String text, float room) {
    if (text.equals(wrapped) && room == wrappedRoom) {
      return;
    }
    wrapped = text;
    wrappedRoom = room;
    rows.clear();
    int start = 0;
    int fits = -1;
    int length = text.length();
    for (int i = 0; i <= length; i++) {
      if (i < length && text.charAt(i) != ' ') {
        continue;
      }
      if (fits < 0 || c.textWidth(FontStyle.SMALL, text.substring(start, i)) <= room) {
        fits = i;
      } else {
        rows.add(text.substring(start, fits));
        start = fits + 1;
        fits = i;
      }
    }
    rows.add(text.substring(start));
  }

  private void save() {
    String text = name.text().strip();
    if (text.equals(identity.handle())) {
      return;
    }
    if (text.isEmpty() || Identity.validHandle(text)) {
      identity.actions().handle(text);
      name.take();
    } else {
      identity.notice(Lang.tr("Use 3 to 16 letters, numbers or underscores."));
    }
  }

  private void claim() {
    String code = LinkCode.normalize(entry.text());
    if (code.length() != LinkCode.LENGTH) {
      identity.notice(Lang.tr("That code doesn't look right."));
      return;
    }
    entry.take();
    identity.actions().claim(code);
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      name.focus(false);
      entry.focus(false);
      return false;
    }
    int index = hits.at(mx, my);
    int kind = index == Hits.NONE ? Hits.NONE : hits.kind(index);
    name.focus(kind == NAME);
    entry.focus(kind == ENTRY);
    if (kind == NAME) {
      name.press(mx);
    } else if (kind == ENTRY) {
      entry.press(mx);
    }
    hits.press(mx, my);
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    name.drag(mx);
    entry.drag(mx);
  }

  @Override
  public void mouseUp(float mx, float my) {
    name.release();
    entry.release();
    int index = hits.release(mx, my);
    if (index == Hits.NONE) {
      return;
    }
    switch (hits.kind(index)) {
      case SAVE -> save();
      case OFFER -> identity.actions().offer();
      case COPY -> {
        if (canvas != null && !identity.code().isEmpty()) {
          canvas.clipboardText(LinkCode.pretty(identity.code()));
        }
      }
      case LINK -> claim();
      case UNLINK -> identity.actions().unlink((String) hits.target(index));
      case ADOPT -> identity.actions().adopt();
      default -> {}
    }
  }

  @Override
  public void finishEditing() {
    name.focus(false);
    entry.focus(false);
  }

  @Override
  public boolean keyDown(int keyCode, boolean control, boolean shift) {
    Field active = name.focused() ? name : entry.focused() ? entry : null;
    if (active == null) {
      return false;
    }
    if (active.submits(keyCode)) {
      if (active == name) {
        save();
      } else {
        claim();
      }
      return true;
    }
    return active.key(keyCode, control, shift);
  }

  @Override
  public boolean charTyped(int codepoint) {
    if (name.focused()) {
      return name.type(codepoint);
    }
    return entry.focused() && entry.type(codepoint);
  }
}
