package me.cxiy.cxvisuals.gui;

import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.modules.waypoints.Icons;
import me.cxiy.cxvisuals.modules.waypoints.Waypoints;

public final class WaypointPanel extends Widget {
  public static final int ADD = 0;
  public static final int SHARE = 1;
  public static final int DELETE = 2;
  public static final int FIELD = 3;
  public static final int FRIEND = 4;
  public static final int ICON_BUTTON = 5;
  public static final int ICON_CHOICE = 6;

  private static final String EMPTY =
      Lang.key("No waypoints here yet. Name one and press Add here.");
  private static final String SUGGESTED = Lang.key("Suggested");
  private static final String NOBODY = Lang.key("Connect and add a friend to share waypoints.");
  private static final float LEFT = 8.0F;
  private static final float RIGHT = 6.0F;
  private static final float TOP = 44.0F;
  private static final float NOTICE = 20.0F;
  private static final float ROW = 44.0F;
  private static final float PICKER = 46.0F;
  private static final float DOT = 8.0F;
  private static final float ICON = 14.0F;
  private static final float CHOICE_ART = 16.0F;
  private static final float GAP = 12.0F;
  private static final float CHIP = 28.0F;
  private static final float CHIP_FACE = 18.0F;
  private static final float CHIP_PAD = 6.0F;
  private static final float CHIP_GAP = 6.0F;
  private static final float SEND_SECONDS = 0.55F;
  private static final float HOLD_SECONDS = 1.1F;
  private static final float SCROLL_STEP = 36.0F;
  private static final float OFFSCREEN = -10000.0F;

  private final Waypoints waypoints;
  private final Hits hits = new Hits(200);
  private final Field field = new Field(Lang.key("Name this spot"), Waypoints.NAME_LIMIT);
  private final Anim open = Anim.reveal(0.0F);
  private List<Waypoints.Target> targets = List.of();
  private String sharing = "";
  private String iconing = "";
  private String shown = "";
  private String sentTo = "";
  private boolean delivered;
  private float sending = -1.0F;
  private float strip;
  private float stripOverflow;
  private float pickerTop;
  private float addWidth = -1.0F;

  public WaypointPanel(Waypoints waypoints) {
    this.waypoints = waypoints;
    this.h = TOP + ROW;
  }

  public float hitX(int kind, String id) {
    return hits.centerX(kind, id);
  }

  public float hitY(int kind, String id) {
    return hits.centerY(kind, id);
  }

  public boolean typing() {
    return field.focused();
  }

  public boolean picking() {
    return !sharing.isEmpty();
  }

  private float noticeHeight() {
    return waypoints.notice().isEmpty() ? 0.0F : NOTICE;
  }

  private float extra() {
    return PICKER * Math.max(0.0F, Math.min(1.0F, open.get()));
  }

  @Override
  public float measure(Canvas c) {
    if (addWidth < 0.0F) {
      addWidth =
          (float)
              Math.ceil(c.textWidth(FontStyle.CAPTION, Lang.tr("Add here")) + Hits.PILL_PAD * 2.0F);
    }
    h = TOP + noticeHeight() + Math.max(1, waypoints.list().size()) * ROW + extra();
    return w;
  }

  private void pickIcon(String id) {
    if (id.equals(iconing)) {
      iconing = "";
      return;
    }
    iconing = id;
    sharing = "";
    shown = id;
    strip = 0.0F;
  }

  private void pick(String id) {
    if (id.equals(sharing)) {
      sharing = "";
      return;
    }
    sharing = id;
    iconing = "";
    shown = id;
    targets = waypoints.actions().targets();
    strip = 0.0F;
    sending = -1.0F;
    sentTo = "";
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    if (sending >= 0.0F) {
      sending += dt;
      if (sending > SEND_SECONDS + HOLD_SECONDS) {
        sending = -1.0F;
        sharing = "";
      }
    }
    open.set(sharing.isEmpty() && iconing.isEmpty() ? 0.0F : 1.0F);
    open.update(dt);
    measure(c);
    hits.clear();
    float right = x + w - RIGHT;
    float fieldTop = y + (TOP - Field.HEIGHT) * 0.5F;
    float fieldWidth = w - LEFT - RIGHT - addWidth - 8.0F;
    field.draw(c, t, x + LEFT, fieldTop, fieldWidth, dt);
    hits.add(FIELD, null, x + LEFT, fieldTop, fieldWidth, Field.HEIGHT);
    hits.pill(
        c, t, ADD, null, Lang.tr("Add here"), right, y + TOP * 0.5F, mouseX, mouseY, t.ink(), true);
    float cursor = y + TOP;
    if (!waypoints.notice().isEmpty()) {
      c.text(
          FontStyle.SMALL,
          waypoints.notice(),
          x + LEFT + 4.0F,
          cursor + (NOTICE - c.lineHeight(FontStyle.SMALL)) * 0.5F - 3.0F,
          t.grey());
      cursor += NOTICE;
    }
    List<Waypoints.Waypoint> list = waypoints.list();
    float rowLine = c.lineHeight(FontStyle.ROW);
    float monoLine = c.lineHeight(FontStyle.MONO);
    if (list.isEmpty()) {
      c.roundRect(x + LEFT, cursor, w - LEFT, 1.0F, 0.0F, t.hair());
      c.text(
          FontStyle.SMALL,
          Lang.tr(EMPTY),
          x + LEFT + 4.0F,
          cursor + (ROW - c.lineHeight(FontStyle.SMALL)) * 0.5F,
          t.grey());
      return;
    }
    String here = waypoints.dimension();
    long now = System.currentTimeMillis();
    float extra = extra();
    for (int i = 0; i < list.size(); i++) {
      Waypoints.Waypoint waypoint = list.get(i);
      c.roundRect(x + LEFT, cursor, w - LEFT, 1.0F, 0.0F, t.hair());
      boolean elsewhere = !waypoint.dimension().equals(here);
      boolean chosen = waypoint.id().equals(sharing);
      int tint = Theme.withAlpha(waypoint.color(), elsewhere ? 0.4F : 1.0F);
      float iconX = x + LEFT + 4.0F + ICON * 0.5F;
      float iconY = cursor + ROW * 0.5F;
      if (WaypointArt.has(waypoint.icon())) {
        WaypointArt.draw(c, iconX, iconY, ICON, waypoint.icon(), tint);
      } else {
        c.roundRect(iconX - DOT * 0.5F, iconY - DOT * 0.5F, DOT, DOT, 2.0F, tint);
      }
      if (!waypoint.death()) {
        hits.add(ICON_BUTTON, waypoint.id(), iconX - ICON, iconY - ICON, ICON * 2.0F, ICON * 2.0F);
      }
      float textX = x + LEFT + 4.0F + ICON + GAP;
      float textTop = cursor + (ROW - rowLine - monoLine - 1.0F) * 0.5F;
      float edge =
          hits.pill(
                  c,
                  t,
                  DELETE,
                  waypoint.id(),
                  Lang.tr("Delete"),
                  right,
                  cursor + ROW * 0.5F,
                  mouseX,
                  mouseY,
                  t.alert(),
                  false)
              - 6.0F;
      edge =
          hits.pill(
                  c,
                  t,
                  SHARE,
                  waypoint.id(),
                  chosen ? Lang.tr("Close") : Lang.tr("Share"),
                  edge,
                  cursor + ROW * 0.5F,
                  mouseX,
                  mouseY,
                  t.ink(),
                  chosen)
              - 8.0F;
      c.pushClip(textX, cursor, edge - textX, ROW);
      c.text(FontStyle.ROW, waypoint.name(), textX, textTop, elsewhere ? t.ink2() : t.ink());
      float placeWidth = c.textWidth(FontStyle.MONO, waypoint.place());
      c.text(FontStyle.MONO, waypoint.place(), textX, textTop + rowLine + 1.0F, t.grey());
      String note =
          waypoint.death()
              ? waypoints.ago(waypoint, now)
              : elsewhere ? Lang.tr("other dimension") : "";
      if (!note.isEmpty()) {
        c.text(FontStyle.MONO, "·", textX + placeWidth + 6.0F, textTop + rowLine + 1.0F, t.grey());
        c.text(
            FontStyle.MONO,
            note,
            textX + placeWidth + 18.0F,
            textTop + rowLine + 1.0F,
            waypoint.death() ? t.ink2() : t.grey());
      }
      c.popClip();
      cursor += ROW;
      if (extra > 0.5F && waypoint.id().equals(shown)) {
        if (!iconing.isEmpty()) {
          iconPicker(c, t, waypoint, cursor, extra, mouseX, mouseY);
        } else {
          picker(c, t, cursor, extra, mouseX, mouseY);
        }
        cursor += extra;
      }
    }
  }

  private void iconPicker(
      Canvas c,
      Theme t,
      Waypoints.Waypoint waypoint,
      float top,
      float tall,
      float mouseX,
      float mouseY) {
    pickerTop = top;
    boolean inside = mouseY >= top && mouseY < top + tall;
    float mx = inside ? mouseX : OFFSCREEN;
    float left = x + LEFT + 4.0F;
    float room = w - LEFT - RIGHT - 4.0F;
    float middle = top + PICKER * 0.5F - 3.0F;
    String suggestion = Icons.suggest(waypoint.name());
    c.pushClip(left, top, room, tall);
    c.pushAlpha(Math.min(1.0F, tall / PICKER));
    float start = left;
    if (!suggestion.isEmpty() && !suggestion.equals(waypoint.icon())) {
      String label = Lang.tr(SUGGESTED);
      c.text(FontStyle.MONO, label, left, middle - c.lineHeight(FontStyle.MONO) * 0.5F, t.grey());
      start = left + c.textWidth(FontStyle.MONO, label) + 10.0F;
    }
    float cursor = start - strip;
    c.pushClip(start, top, left + room - start, tall);
    boolean suggest = !suggestion.isEmpty() && !suggestion.equals(waypoint.icon());
    int count = Icons.ALL.length + 1;
    if (suggest) {
      choice(c, t, suggestion, waypoint, cursor, middle, mx, mouseY, start);
      cursor += CHIP + CHIP_GAP;
    }
    for (int i = 0; i < count; i++) {
      String icon = i == 0 ? Icons.NONE : Icons.ALL[i - 1];
      if (suggest && icon.equals(suggestion)) {
        continue;
      }
      choice(c, t, icon, waypoint, cursor, middle, mx, mouseY, start);
      cursor += CHIP + CHIP_GAP;
    }
    c.popClip();
    stripOverflow = Math.max(0.0F, count * (CHIP + CHIP_GAP) - CHIP_GAP - (left + room - start));
    strip = Math.max(0.0F, Math.min(stripOverflow, strip));
    c.popAlpha();
    c.popClip();
  }

  private void choice(
      Canvas c,
      Theme t,
      String icon,
      Waypoints.Waypoint waypoint,
      float cursor,
      float middle,
      float mx,
      float mouseY,
      float start) {
    float chipTop = middle - CHIP * 0.5F;
    boolean chosen = icon.equals(waypoint.icon());
    boolean over =
        mx >= Math.max(start, cursor)
            && mx < cursor + CHIP
            && mouseY >= chipTop
            && mouseY < chipTop + CHIP;
    boolean down = hits.down(ICON_CHOICE, icon);
    Depth.raised(
        c,
        t,
        cursor,
        chipTop,
        CHIP,
        CHIP,
        CHIP * 0.5F,
        chosen ? t.ink() : Theme.mix(t.surface2(), t.ink(), down ? 0.16F : over ? 0.08F : 0.0F));
    int ink = chosen ? t.paper() : t.ink();
    if (icon.isEmpty()) {
      c.roundRect(cursor + (CHIP - DOT) * 0.5F, middle - DOT * 0.5F, DOT, DOT, 2.0F, ink);
    } else {
      WaypointArt.draw(c, cursor + CHIP * 0.5F, middle, CHOICE_ART, icon, ink);
    }
    hits.add(ICON_CHOICE, icon, Math.max(start, cursor), chipTop, CHIP, CHIP);
  }

  private void picker(Canvas c, Theme t, float top, float tall, float mouseX, float mouseY) {
    pickerTop = top;
    boolean inside = mouseY >= top && mouseY < top + tall;
    float mx = inside ? mouseX : OFFSCREEN;
    float left = x + LEFT + 4.0F;
    float room = w - LEFT - RIGHT - 4.0F;
    float middle = top + PICKER * 0.5F - 3.0F;
    c.pushClip(left, top, room, tall);
    c.pushAlpha(Math.min(1.0F, tall / PICKER));
    if (targets.isEmpty()) {
      c.text(
          FontStyle.SMALL,
          Lang.tr(NOBODY),
          left,
          middle - c.lineHeight(FontStyle.SMALL) * 0.5F,
          t.grey());
      stripOverflow = 0.0F;
    } else {
      String label = Lang.tr("Send to");
      c.text(FontStyle.MONO, label, left, middle - c.lineHeight(FontStyle.MONO) * 0.5F, t.grey());
      float start = left + c.textWidth(FontStyle.MONO, label) + 10.0F;
      float cursor = start - strip;
      float captionLine = c.lineHeight(FontStyle.CAPTION);
      c.pushClip(start, top, left + room - start, tall);
      for (int i = 0; i < targets.size(); i++) {
        Waypoints.Target target = targets.get(i);
        boolean busy = target.uuid().equals(sentTo) && sending >= 0.0F;
        float progress = busy ? Math.min(1.0F, sending / SEND_SECONDS) : 0.0F;
        boolean done = busy && progress >= 1.0F;
        String text = done ? (delivered ? Lang.tr("Sent") : Lang.tr("Not sent")) : target.name();
        float width =
            (float)
                Math.ceil(
                    CHIP_PAD
                        + CHIP_FACE
                        + 6.0F
                        + c.textWidth(FontStyle.CAPTION, text)
                        + CHIP_PAD
                        + 6.0F);
        float chipTop = middle - CHIP * 0.5F;
        boolean over =
            mx >= Math.max(start, cursor)
                && mx < cursor + width
                && mouseY >= chipTop
                && mouseY < chipTop + CHIP;
        boolean down = hits.down(FRIEND, target.uuid());
        Depth.raised(
            c,
            t,
            cursor,
            chipTop,
            width,
            CHIP,
            CHIP * 0.5F,
            Theme.mix(t.surface2(), t.ink(), down ? 0.16F : over ? 0.08F : 0.0F));
        if (busy) {
          float sweep = (float) Easing.snappy(progress);
          c.pushClip(cursor, chipTop, width * sweep, CHIP);
          c.roundRect(
              cursor, chipTop, width, CHIP, CHIP * 0.5F, done && !delivered ? t.alert() : t.ink());
          c.popClip();
        }
        int ink = busy && progress > 0.5F ? t.paper() : target.online() ? t.ink() : t.ink2();
        Hits.face(
            c,
            t,
            target.entityId(),
            target.name(),
            cursor + CHIP_PAD,
            middle - CHIP_FACE * 0.5F,
            CHIP_FACE,
            CHIP_FACE * 0.3F);
        c.text(
            FontStyle.CAPTION,
            text,
            cursor + CHIP_PAD + CHIP_FACE + 6.0F,
            middle - captionLine * 0.5F,
            ink);
        if (sending < 0.0F) {
          hits.add(FRIEND, target.uuid(), Math.max(start, cursor), chipTop, width, CHIP);
        }
        cursor += width + CHIP_GAP;
      }
      c.popClip();
      stripOverflow = Math.max(0.0F, cursor - CHIP_GAP + strip - (left + room));
      strip = Math.max(0.0F, Math.min(stripOverflow, strip));
    }
    c.popAlpha();
    c.popClip();
  }

  private Waypoints.Waypoint find(String id) {
    List<Waypoints.Waypoint> list = waypoints.list();
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).id().equals(id)) {
        return list.get(i);
      }
    }
    return null;
  }

  private void add() {
    waypoints.actions().addHere(field.take());
  }

  @Override
  public boolean scroll(float mx, float my, float amount) {
    if (!contains(mx, my)
        || (sharing.isEmpty() && iconing.isEmpty())
        || stripOverflow <= 0.0F
        || my < pickerTop
        || my >= pickerTop + PICKER) {
      return false;
    }
    strip = Math.max(0.0F, Math.min(stripOverflow, strip - amount * SCROLL_STEP));
    return true;
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
    String id = (String) hits.target(index);
    switch (hits.kind(index)) {
      case ADD -> add();
      case DELETE -> {
        if (id.equals(sharing)) {
          sharing = "";
        }
        if (id.equals(iconing)) {
          iconing = "";
        }
        waypoints.remove(id);
      }
      case SHARE -> pick(id);
      case ICON_BUTTON -> pickIcon(id);
      case ICON_CHOICE -> {
        waypoints.icon(iconing, id);
        iconing = "";
      }
      case FRIEND -> {
        Waypoints.Waypoint waypoint = find(sharing);
        if (waypoint != null) {
          sentTo = id;
          sending = 0.0F;
          delivered = waypoints.actions().share(waypoint, id);
        }
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
