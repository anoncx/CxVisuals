package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.modules.friends.Friends;
import me.cxiy.cxvisuals.modules.friends.Note;

public final class ChatPanel extends Widget {
  public static final int SEND = 0;
  public static final int WAVE = 1;
  public static final int SHARE = 2;
  public static final int INVITE = 3;
  public static final int JOIN = 4;
  public static final int REMOVE = 5;
  public static final int TRY = 6;
  public static final int KEEP = 7;
  public static final int ACCEPT = 8;
  public static final int FIELD = 9;
  public static final int CONFIG = 10;
  public static final int SPOT = 11;
  public static final int APPLY = 12;
  public static final int SAVE = 13;
  public static final int ADD = 14;
  public static final int PLUS = 15;
  public static final int BACK = 16;
  public static final int PICK = 17;
  public static final int POINT = 18;
  public static final int DROP = 19;

  public static final float HEIGHT = 268.0F;
  private static final int CLOSED = 0;
  private static final int ACTIONS = 1;
  private static final int AVATARS = 2;
  private static final int CONFIGS = 3;
  private static final int WAYPOINTS = 4;
  private static final String NOBODY =
      Lang.key("Pick a friend on the Friends tab to start talking.");
  private static final String SEALED =
      Lang.key("Your chats are locked on this device. Unlock them on the You tab.");
  private static final String HELLO = Lang.key("No messages yet. Say hi, or send a wave.");
  private static final String[] CELLS =
      Lang.keys("Wave", "Invite", "Location", "Avatar", "Config", "Waypoint");
  private static final int[] CELL_KINDS = {WAVE, INVITE, SPOT, SHARE, CONFIG, POINT};
  private static final float CELL_GAP = 6.0F;
  private static final String[] NOTHING =
      Lang.keys(
          "",
          "",
          "No avatars in your library yet.",
          "No saved configs yet.",
          "No waypoints in this world yet.");
  private static final float HEAD = 42.0F;
  private static final float FOOT = 46.0F;
  private static final float LEFT = 8.0F;
  private static final float RIGHT = 6.0F;
  private static final float FACE = 28.0F;
  private static final float FACE_RADIUS = 8.0F;
  private static final float BUBBLE_PAD_X = 11.0F;
  private static final float BUBBLE_PAD_Y = 7.0F;
  private static final float BUBBLE_RADIUS = 13.0F;
  private static final float BUBBLE_GAP = 4.0F;
  private static final float TURN_GAP = 8.0F;
  private static final float WIDEST = 0.68F;
  private static final float CARD = 46.0F;
  private static final float CARD_ACTIONS = 78.0F;
  private static final float CARD_WIDTH = 200.0F;
  private static final float PLACE_WIDTH = 244.0F;
  private static final float PLACE_MINE = 98.0F;
  private static final float PLACE_THEIRS = 110.0F;
  private static final float PLACE_WELL = 24.0F;
  private static final String[] AXES = {"X", "Y", "Z"};
  private static final float MODEL_WIDTH = 244.0F;
  private static final float MODEL_HEIGHT = 104.0F;
  private static final float MODEL_WELL = 88.0F;
  private static final float NOTE = 24.0F;
  private static final float SCROLL_STEP = 36.0F;
  private static final float ARMED_SECONDS = 4.0F;
  private static final float PLUS_SIZE = 30.0F;
  private static final float TRAY_ROW = 46.0F;
  private static final float TRAY_CARDS = 118.0F;
  private static final float TILE = 78.0F;
  private static final float TILE_HEIGHT = 100.0F;
  private static final float TILE_GAP = 8.0F;
  private static final float TRAY_RESPONSE = 18.0F;
  private static final float POP_SECONDS = 0.22F;
  private static final float POP_RISE = 8.0F;
  private static final float WAITING = 0.55F;
  private static final float BEAD = 4.0F;
  private static final float BEAT = 0.9F;
  private static final float SPIN = 0.7F;
  private static final float OFFSCREEN = -10000.0F;

  private final Friends friends;
  private final PanelHost host;
  private final Hits hits = new Hits(160);
  private final Field field = new Field(Lang.key("Message"), Note.TEXT_LIMIT);
  private final Map<Long, String[]> wraps = new HashMap<>();
  private final List<String> scratch = new ArrayList<>(8);
  private final Map<String, String> places = new HashMap<>();
  private final Map<Long, String[]> coordinates = new HashMap<>();
  private final Map<Long, String> infos = new HashMap<>();
  private final Map<Long, Integer> distances = new HashMap<>();
  private List<Friends.Item> items = List.of();
  private String shown = "";
  private float wrappedFor = -1.0F;
  private float scroll;
  private float overflow;
  private float armed;
  private int seen = -1;
  private int mode = CLOSED;
  private int shownMode = ACTIONS;
  private float tray;
  private float trayTop;
  private float strip;
  private float stripOverflow;
  private long popFloor = Long.MAX_VALUE;
  private float popAge = POP_SECONDS;
  private long sendFloor;
  private float sendAge = POP_SECONDS;
  private float phase;

  public ChatPanel(Friends friends) {
    this(friends, pageId -> {});
  }

  public ChatPanel(Friends friends, PanelHost host) {
    this.friends = friends;
    this.host = host;
    this.h = HEIGHT;
  }

  public float hitX(int kind) {
    return hits.centerX(kind, null);
  }

  public float hitY(int kind) {
    return hits.centerY(kind, null);
  }

  public float hitX(int kind, Object target) {
    return hits.centerX(kind, target);
  }

  public float hitY(int kind, Object target) {
    return hits.centerY(kind, target);
  }

  public boolean typing() {
    return field.focused();
  }

  public boolean trayOpen() {
    return mode != CLOSED;
  }

  @Override
  public float measure(Canvas c) {
    h = Math.max(HEIGHT, host.room());
    return w;
  }

  private float trayTarget() {
    return switch (mode) {
      case CLOSED -> 0.0F;
      case AVATARS -> TRAY_CARDS;
      default -> TRAY_ROW;
    };
  }

  private void open(int next) {
    mode = next;
    strip = 0.0F;
    if (next != CLOSED) {
      shownMode = next;
    }
    items =
        switch (next) {
          case AVATARS -> friends.actions().items(Friends.Share.AVATAR);
          case CONFIGS -> friends.actions().items(Friends.Share.CONFIG);
          case WAYPOINTS -> friends.actions().items(Friends.Share.WAYPOINT);
          default -> List.of();
        };
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    hits.clear();
    armed = Math.max(0.0F, armed - dt);
    popAge = Math.min(POP_SECONDS, popAge + dt);
    sendAge = Math.min(POP_SECONDS, sendAge + dt);
    phase = (phase + dt / BEAT) % 1.0F;
    Friends.Friend friend = friends.current();
    friends.watching(friend != null);
    if (friend == null) {
      c.text(
          FontStyle.SMALL,
          Lang.tr(NOBODY),
          x + (w - c.textWidth(FontStyle.SMALL, Lang.tr(NOBODY))) * 0.5F,
          y + (h - c.lineHeight(FontStyle.SMALL)) * 0.5F,
          t.grey());
      return;
    }
    if (friends.locked()) {
      c.text(
          FontStyle.SMALL,
          Lang.tr(SEALED),
          x + (w - c.textWidth(FontStyle.SMALL, Lang.tr(SEALED))) * 0.5F,
          y + (h - c.lineHeight(FontStyle.SMALL)) * 0.5F,
          t.grey());
      field.focus(false);
      return;
    }
    if (!friend.uuid.equals(shown)) {
      shown = friend.uuid;
      wraps.clear();
      coordinates.clear();
      infos.clear();
      distances.clear();
      scroll = 0.0F;
      armed = 0.0F;
      open(CLOSED);
      tray = 0.0F;
      popFloor = Long.MAX_VALUE;
      field.focus(true);
    }
    float target = trayTarget();
    tray += (target - tray) * (1.0F - (float) Math.exp(-dt * TRAY_RESPONSE));
    if (Math.abs(target - tray) < 0.3F) {
      tray = target;
    }
    head(c, t, friend, mouseX, mouseY);
    float bodyTop = y + HEAD;
    float footTop = y + h - FOOT;
    trayTop = footTop - tray;
    c.roundRect(x + LEFT, bodyTop, w - LEFT, 1.0F, 0.0F, t.hair());
    body(c, t, friend, bodyTop + 1.0F, trayTop - bodyTop - 1.0F, mouseX, mouseY);
    if (tray > 0.5F) {
      tray(c, t, trayTop, mouseX, mouseY);
    }
    c.roundRect(x + LEFT, footTop, w - LEFT, 1.0F, 0.0F, t.hair());
    float middle = footTop + FOOT * 0.5F + 1.0F;
    float plusLeft = x + LEFT;
    float plusTop = middle - PLUS_SIZE * 0.5F;
    boolean overPlus =
        mouseX >= plusLeft
            && mouseX < plusLeft + PLUS_SIZE
            && mouseY >= plusTop
            && mouseY < plusTop + PLUS_SIZE;
    boolean active = mode != CLOSED;
    int plate =
        active
            ? t.ink()
            : Theme.mix(
                t.surface2(), t.ink(), hits.down(PLUS, null) ? 0.16F : overPlus ? 0.08F : 0.0F);
    Depth.raised(c, t, plusLeft, plusTop, PLUS_SIZE, PLUS_SIZE, PLUS_SIZE * 0.5F, plate);
    int mark = active ? (t.paper() | 0xFF000000) : t.ink();
    float centreX = plusLeft + PLUS_SIZE * 0.5F;
    c.roundRect(centreX - 5.0F, middle - 0.75F, 10.0F, 1.5F, 0.75F, mark);
    if (!active) {
      c.roundRect(centreX - 0.75F, middle - 5.0F, 1.5F, 10.0F, 0.75F, mark);
    }
    hits.add(PLUS, null, plusLeft, plusTop, PLUS_SIZE, PLUS_SIZE);
    float right = x + w - RIGHT;
    float sendLeft =
        hits.pill(
            c,
            t,
            SEND,
            null,
            Lang.tr("Send"),
            right,
            middle,
            mouseX,
            mouseY,
            t.ink(),
            !field.text().isBlank());
    float fieldLeft = plusLeft + PLUS_SIZE + 8.0F;
    float fieldTop = middle - Field.HEIGHT * 0.5F;
    float fieldWidth = sendLeft - 8.0F - fieldLeft;
    field.draw(c, t, fieldLeft, fieldTop, fieldWidth, dt);
    hits.add(FIELD, null, fieldLeft, fieldTop, fieldWidth, Field.HEIGHT);
  }

  private void head(Canvas c, Theme t, Friends.Friend friend, float mx, float my) {
    float middle = y + HEAD * 0.5F - 2.0F;
    Hits.face(
        c, t, friend.entityId, friend.name, x + LEFT, middle - FACE * 0.5F, FACE, FACE_RADIUS);
    float nameX = x + LEFT + FACE + 10.0F;
    float rowLine = c.lineHeight(FontStyle.ROW);
    float smallLine = c.lineHeight(FontStyle.SMALL);
    float textTop = middle - (rowLine + smallLine + 1.0F) * 0.5F;
    float edge = x + w - RIGHT;
    edge =
        hits.pill(
                c,
                t,
                REMOVE,
                null,
                armed > 0.0F ? Lang.tr("Confirm remove") : Lang.tr("Remove"),
                edge,
                middle,
                mx,
                my,
                t.alert(),
                false)
            - 6.0F;
    if (friend.online && !friend.server.isEmpty()) {
      edge =
          hits.pill(c, t, JOIN, null, Lang.tr("Join"), edge, middle, mx, my, t.ink(), true) - 6.0F;
    }
    c.pushClip(nameX, y, edge - nameX, HEAD);
    c.text(FontStyle.ROW, friend.name, nameX, textTop, t.ink());
    c.text(
        FontStyle.SMALL,
        friend.online ? friend.line() : Lang.tr("Offline"),
        nameX,
        textTop + rowLine + 1.0F,
        t.grey());
    c.popClip();
  }

  private float tool(
      Canvas c,
      Theme t,
      int kind,
      Object target,
      String label,
      float left,
      float middle,
      float mx,
      float my) {
    float width = (float) Math.ceil(c.textWidth(FontStyle.CAPTION, label) + Hits.PILL_PAD * 2.0F);
    hits.pill(c, t, kind, target, label, left + width, middle, mx, my, t.ink(), false);
    return left + width + 6.0F;
  }

  private void cell(
      Canvas c,
      Theme t,
      int kind,
      String label,
      float left,
      float width,
      float middle,
      float mx,
      float my) {
    float top = middle - Hits.PILL * 0.5F;
    boolean over = mx >= left && mx < left + width && my >= top && my < top + Hits.PILL;
    Depth.raised(
        c,
        t,
        left,
        top,
        width,
        Hits.PILL,
        Hits.PILL * 0.5F,
        Theme.mix(t.surface2(), t.ink(), hits.down(kind, null) ? 0.16F : over ? 0.08F : 0.0F));
    c.text(
        FontStyle.CAPTION,
        label,
        left + (width - c.textWidth(FontStyle.CAPTION, label)) * 0.5F,
        middle - c.lineHeight(FontStyle.CAPTION) * 0.5F,
        t.ink());
    hits.add(kind, null, left, top, width, Hits.PILL);
  }

  private void tray(Canvas c, Theme t, float top, float mouseX, float mouseY) {
    boolean inside = mouseY >= top && mouseY < top + tray;
    float mx = inside ? mouseX : OFFSCREEN;
    c.roundRect(x + LEFT, top, w - LEFT, 1.0F, 0.0F, t.hair());
    c.pushClip(x, top + 1.0F, w, Math.max(0.0F, tray - 1.0F));
    c.pushAlpha(Math.min(1.0F, tray / TRAY_ROW));
    float left = x + LEFT;
    int showing = mode == CLOSED ? shownMode : mode;
    if (showing == ACTIONS) {
      float middle = top + TRAY_ROW * 0.5F + 1.0F;
      float cell = (w - LEFT - RIGHT - (CELLS.length - 1) * CELL_GAP) / CELLS.length;
      for (int i = 0; i < CELLS.length; i++) {
        cell(
            c,
            t,
            CELL_KINDS[i],
            Lang.tr(CELLS[i]),
            left + i * (cell + CELL_GAP),
            cell,
            middle,
            mx,
            mouseY);
      }
      stripOverflow = 0.0F;
    } else {
      boolean cards = showing == AVATARS;
      float middle = top + (cards ? TRAY_CARDS : TRAY_ROW) * 0.5F + 1.0F;
      float start = tool(c, t, BACK, null, Lang.tr("Back"), left, middle, mx, mouseY) + 4.0F;
      float room = x + w - RIGHT - start;
      if (items.isEmpty()) {
        c.text(
            FontStyle.SMALL,
            Lang.tr(NOTHING[showing]),
            start,
            middle - c.lineHeight(FontStyle.SMALL) * 0.5F,
            t.grey());
        stripOverflow = 0.0F;
      } else {
        c.pushClip(start, top, room, tray);
        float sx = mx >= start ? mx : OFFSCREEN;
        float cursor = start - strip;
        for (int i = 0; i < items.size(); i++) {
          Friends.Item item = items.get(i);
          cursor =
              cards
                  ? tile(
                      c,
                      t,
                      item,
                      cursor,
                      top + (TRAY_CARDS - TILE_HEIGHT) * 0.5F + 1.0F,
                      sx,
                      mouseY)
                  : tool(c, t, PICK, item, item.name(), cursor, middle, sx, mouseY);
        }
        c.popClip();
        stripOverflow = Math.max(0.0F, cursor + strip - start - room);
        strip = Math.max(0.0F, Math.min(stripOverflow, strip));
        if (stripOverflow > 0.0F) {
          float bar = room * room / (room + stripOverflow);
          c.roundRect(
              start + (room - bar) * strip / stripOverflow,
              top + tray - 4.0F,
              bar,
              2.0F,
              1.0F,
              Theme.withAlpha(t.ink(), 0.25F));
        }
      }
    }
    c.popAlpha();
    c.popClip();
  }

  private float tile(
      Canvas c, Theme t, Friends.Item item, float left, float top, float mx, float my) {
    boolean over = mx >= left && mx < left + TILE && my >= top && my < top + TILE_HEIGHT;
    boolean down = hits.down(PICK, item);
    Depth.inset(
        c,
        t,
        left,
        top,
        TILE,
        TILE_HEIGHT,
        12.0F,
        Theme.mix(t.surface2(), t.ink(), down ? 0.14F : over ? 0.07F : 0.0F));
    float size = TILE - 12.0F;
    c.model(
        item.handle(),
        left + 6.0F,
        top + 5.0F,
        size,
        size,
        phase * 6.2832F * SPIN,
        over,
        Canvas.AUDITION_NONE);
    float nameWidth = c.textWidth(FontStyle.SMALL, item.name());
    float room = TILE - 10.0F;
    c.pushClip(left + 5.0F, top, room, TILE_HEIGHT);
    c.text(
        FontStyle.SMALL,
        item.name(),
        left + 5.0F + Math.max(0.0F, (room - nameWidth) * 0.5F),
        top + TILE_HEIGHT - c.lineHeight(FontStyle.SMALL) - 6.0F,
        over ? t.ink() : t.ink2());
    c.popClip();
    hits.add(PICK, item, left, top, TILE, TILE_HEIGHT);
    return left + TILE + TILE_GAP;
  }

  private static String body(Note note) {
    return note == Note.UNREADABLE ? Lang.tr(note.text()) : note.text();
  }

  private String[] wrap(Canvas c, long key, String text, float room) {
    String[] cached = wraps.get(key);
    if (cached != null) {
      return cached;
    }
    scratch.clear();
    int start = 0;
    int fits = -1;
    int length = text.length();
    for (int i = 0; i <= length; i++) {
      if (i < length && text.charAt(i) != ' ') {
        if (fits < 0
            && c.textWidth(FontStyle.LABEL, text.substring(start, i + 1)) > room
            && i > start) {
          scratch.add(text.substring(start, i));
          start = i;
        }
        continue;
      }
      if (fits < 0 || c.textWidth(FontStyle.LABEL, text.substring(start, i)) <= room) {
        fits = i;
      } else {
        scratch.add(text.substring(start, fits));
        start = fits + 1;
        fits = i;
      }
      if (i == length) {
        break;
      }
    }
    scratch.add(text.substring(Math.min(start, length)));
    cached = scratch.toArray(new String[0]);
    wraps.put(key, cached);
    return cached;
  }

  private float height(Canvas c, long key, Note note, boolean mine, float room) {
    return switch (note.kind()) {
      case TEXT ->
          wrap(c, key, body(note), room).length * c.lineHeight(FontStyle.LABEL)
              + BUBBLE_PAD_Y * 2.0F;
      case WAVE -> NOTE;
      case WAYPOINT -> mine ? PLACE_MINE : PLACE_THEIRS;
      case MODEL -> MODEL_HEIGHT;
      case INVITE, CONFIG -> mine ? CARD : CARD_ACTIONS;
    };
  }

  private void body(
      Canvas c, Theme t, Friends.Friend friend, float top, float height, float mx, float my) {
    List<Friends.Line> lines = friends.lines(friend.uuid);
    List<Friends.Pending> waiting = friends.pending(friend.uuid);
    float room = (w - LEFT - RIGHT) * WIDEST - BUBBLE_PAD_X * 2.0F;
    if (room != wrappedFor) {
      wrappedFor = room;
      wraps.clear();
    }
    int amount = lines.size() + waiting.size();
    int grown = amount - seen;
    if (grown != 0) {
      if (scroll <= 0.5F || overflow <= 0.5F || seen < 0) {
        scroll = 0.0F;
      }
      seen = amount;
    }
    long newest = lines.isEmpty() ? 0L : lines.get(lines.size() - 1).id();
    if (popFloor == Long.MAX_VALUE) {
      popFloor = newest;
      popAge = POP_SECONDS;
    } else if (newest > popFloor) {
      if (popAge >= POP_SECONDS && grown == 1) {
        popAge = 0.0F;
      } else {
        popFloor = newest;
        popAge = POP_SECONDS;
      }
    }
    long latest = waiting.isEmpty() ? sendFloor : waiting.get(waiting.size() - 1).token;
    if (latest > sendFloor) {
      if (sendAge >= POP_SECONDS && grown == 1) {
        sendFloor = latest;
        sendAge = 0.0F;
      } else {
        sendFloor = latest;
        sendAge = POP_SECONDS;
      }
    }
    if (height <= 0.0F) {
      return;
    }
    if (amount == 0) {
      c.text(
          FontStyle.SMALL,
          Lang.tr(HELLO),
          x + (w - c.textWidth(FontStyle.SMALL, Lang.tr(HELLO))) * 0.5F,
          top + (height - c.lineHeight(FontStyle.SMALL)) * 0.5F,
          t.grey());
      overflow = 0.0F;
      return;
    }
    float total = TURN_GAP;
    for (int i = 0; i < lines.size(); i++) {
      total +=
          height(c, lines.get(i).id(), lines.get(i).note(), lines.get(i).mine(), room)
              + gap(lines, i);
    }
    for (int i = 0; i < waiting.size(); i++) {
      total += height(c, -waiting.get(i).token, waiting.get(i).note, true, room) + TURN_GAP;
    }
    overflow = Math.max(0.0F, total - height);
    scroll = Math.max(0.0F, Math.min(overflow, scroll));
    boolean inside = my >= top && my < top + height;
    float hx = inside ? mx : OFFSCREEN;
    c.pushClip(x, top, w, height);
    float cursor = top + height - TURN_GAP + scroll;
    float lineHeight = c.lineHeight(FontStyle.LABEL);
    float sent = (float) Easing.snappy(sendAge / POP_SECONDS);
    for (int i = waiting.size() - 1; i >= 0 && cursor > top; i--) {
      Friends.Pending item = waiting.get(i);
      float tall = height(c, -item.token, item.note, true, room);
      float lineTop = cursor - tall;
      boolean fresh = item.token == sendFloor && sendAge < POP_SECONDS;
      c.pushAlpha(WAITING * (fresh ? sent : 1.0F));
      float left =
          piece(
              c,
              t,
              friend,
              -item.token,
              item.note,
              true,
              null,
              lineTop + (fresh ? (1.0F - sent) * POP_RISE : 0.0F),
              tall,
              room,
              lineHeight,
              hx,
              my);
      c.popAlpha();
      float middle = lineTop + tall * 0.5F;
      if (item.failed) {
        String label = Lang.tr("Not sent");
        float labelWidth = c.textWidth(FontStyle.MONO, label);
        c.text(
            FontStyle.MONO,
            label,
            left - 10.0F - labelWidth,
            middle - c.lineHeight(FontStyle.MONO) * 0.5F,
            t.alert());
        hits.add(DROP, item, left - 14.0F - labelWidth, lineTop, labelWidth + 14.0F, tall);
      } else {
        for (int bead = 0; bead < 3; bead++) {
          float glow = pulse((phase - bead / 4.0F + 1.0F) % 1.0F);
          c.roundRect(
              left - 12.0F - (2 - bead) * (BEAD + 4.0F),
              middle - BEAD * 0.5F,
              BEAD,
              BEAD,
              BEAD * 0.5F,
              Theme.withAlpha(t.ink(), 0.2F + 0.7F * glow));
        }
      }
      cursor = lineTop - TURN_GAP;
    }
    float popped = (float) Easing.snappy(popAge / POP_SECONDS);
    for (int i = lines.size() - 1; i >= 0 && cursor > top; i--) {
      Friends.Line line = lines.get(i);
      float tall = height(c, line.id(), line.note(), line.mine(), room);
      float lineTop = cursor - tall;
      if (lineTop < top + height) {
        boolean fresh = !line.mine() && line.id() > popFloor && popAge < POP_SECONDS;
        if (fresh) {
          c.pushAlpha(popped);
        }
        piece(
            c,
            t,
            friend,
            line.id(),
            line.note(),
            line.mine(),
            line,
            lineTop + (fresh ? (1.0F - popped) * POP_RISE : 0.0F),
            tall,
            room,
            lineHeight,
            hx,
            my);
        if (fresh) {
          c.popAlpha();
        }
      }
      cursor = lineTop - gap(lines, i);
    }
    if (popAge >= POP_SECONDS) {
      popFloor = Math.max(popFloor, newest);
    }
    c.popClip();
  }

  private static float pulse(float at) {
    float wave = 1.0F - Math.abs(at * 2.0F - 1.0F);
    return wave * wave * (3.0F - 2.0F * wave);
  }

  private static float gap(List<Friends.Line> lines, int index) {
    if (index == 0) {
      return 0.0F;
    }
    Friends.Line line = lines.get(index);
    Friends.Line before = lines.get(index - 1);
    boolean plain = line.note().kind() == Note.Kind.TEXT && before.note().kind() == Note.Kind.TEXT;
    return plain && line.mine() == before.mine() ? BUBBLE_GAP : TURN_GAP;
  }

  private float piece(
      Canvas c,
      Theme t,
      Friends.Friend friend,
      long key,
      Note note,
      boolean mine,
      Friends.Line line,
      float top,
      float tall,
      float room,
      float lineHeight,
      float mx,
      float my) {
    float left = x + LEFT;
    float right = x + w - RIGHT;
    if (note.kind() == Note.Kind.WAVE) {
      String text = mine ? Lang.tr("You waved") : Lang.format("{0} waved", friend.name);
      float width = c.textWidth(FontStyle.MONO, text);
      float textX = x + (w - width) * 0.5F;
      float middle = top + tall * 0.5F;
      c.roundRect(textX - 14.0F, middle - 2.5F, 5.0F, 5.0F, 2.5F, t.red());
      c.text(FontStyle.MONO, text, textX, middle - c.lineHeight(FontStyle.MONO) * 0.5F, t.grey());
      return textX - 14.0F;
    }
    if (note.kind() == Note.Kind.TEXT) {
      String[] rows = wrap(c, key, body(note), room);
      float widest = 0.0F;
      for (String row : rows) {
        widest = Math.max(widest, c.textWidth(FontStyle.LABEL, row));
      }
      float width = widest + BUBBLE_PAD_X * 2.0F;
      float bubbleX = mine ? right - width : left;
      c.roundRect(
          bubbleX,
          top,
          width,
          tall,
          Math.min(BUBBLE_RADIUS, tall * 0.5F),
          mine ? t.ink() : t.surface2());
      int ink = mine ? (t.paper() | 0xFF000000) : t.ink();
      for (int i = 0; i < rows.length; i++) {
        c.text(
            FontStyle.LABEL,
            rows[i],
            bubbleX + BUBBLE_PAD_X,
            top + BUBBLE_PAD_Y + i * lineHeight,
            ink);
      }
      return bubbleX;
    }
    if (note.kind() == Note.Kind.MODEL) {
      return avatar(c, t, key, note, mine, line, mine ? right - MODEL_WIDTH : left, top, mx, my);
    }
    if (note.kind() == Note.Kind.WAYPOINT) {
      return place(
          c, t, key, note, mine, line, mine ? right - PLACE_WIDTH : left, top, tall, mx, my);
    }
    float cardX = mine ? right - CARD_WIDTH : left;
    Depth.raised(c, t, cardX, top, CARD_WIDTH, tall, BUBBLE_RADIUS, t.surface2());
    float textX = cardX + BUBBLE_PAD_X;
    float captionTop = top + 8.0F;
    String caption =
        switch (note.kind()) {
          case MODEL -> Lang.tr("AVATAR");
          case CONFIG -> Lang.tr("CONFIG");
          case WAYPOINT -> Lang.tr("WAYPOINT") + " · " + note.coordinates();
          default -> Lang.tr("INVITE");
        };
    c.text(FontStyle.MONO, caption, textX, captionTop, t.grey());
    c.pushClip(cardX, top, CARD_WIDTH - BUBBLE_PAD_X, tall);
    c.text(
        FontStyle.ROW,
        note.text(),
        textX,
        captionTop + c.lineHeight(FontStyle.MONO) + 2.0F,
        t.ink());
    c.popClip();
    if (mine || line == null) {
      return cardX;
    }
    float pillMiddle = top + tall - Hits.PILL * 0.5F - 8.0F;
    float edge = cardX + CARD_WIDTH - 6.0F;
    if (note.kind() == Note.Kind.INVITE) {
      hits.pill(c, t, ACCEPT, line, Lang.tr("Join"), edge, pillMiddle, mx, my, t.ink(), true);
      return cardX;
    }
    Friends.Parcel state = friends.parcel(line);
    String status =
        switch (state) {
          case WORKING -> Lang.tr("Fetching");
          case WORN -> note.kind() == Note.Kind.CONFIG ? Lang.tr("Applied") : Lang.tr("Wearing");
          case KEPT ->
              note.kind() == Note.Kind.MODEL
                  ? Lang.tr("In your library")
                  : note.kind() == Note.Kind.CONFIG ? Lang.tr("Saved") : Lang.tr("Added");
          case FAILED -> Lang.tr("Unavailable");
          case IDLE -> "";
        };
    if (!status.isEmpty() && note.kind() != Note.Kind.WAYPOINT) {
      c.text(
          FontStyle.MONO,
          status,
          edge - c.textWidth(FontStyle.MONO, status) - 6.0F,
          captionTop,
          state == Friends.Parcel.FAILED ? t.alert() : t.grey());
    }
    if (note.kind() == Note.Kind.WAYPOINT) {
      if (state == Friends.Parcel.KEPT) {
        c.text(
            FontStyle.MONO,
            status,
            edge - c.textWidth(FontStyle.MONO, status) - 6.0F,
            pillMiddle - c.lineHeight(FontStyle.MONO) * 0.5F,
            t.grey());
      } else {
        hits.pill(c, t, ADD, line, Lang.tr("Add"), edge, pillMiddle, mx, my, t.ink(), true);
      }
      return cardX;
    }
    if (state == Friends.Parcel.WORKING || state == Friends.Parcel.FAILED) {
      return cardX;
    }
    if (note.kind() == Note.Kind.CONFIG) {
      if (state == Friends.Parcel.WORN) {
        return cardX;
      }
      edge =
          hits.pill(c, t, APPLY, line, Lang.tr("Apply"), edge, pillMiddle, mx, my, t.ink(), true)
              - 6.0F;
      if (state != Friends.Parcel.KEPT) {
        hits.pill(c, t, SAVE, line, Lang.tr("Save"), edge, pillMiddle, mx, my, t.ink(), false);
      }
      return cardX;
    }
    if (state == Friends.Parcel.KEPT) {
      return cardX;
    }
    edge =
        hits.pill(c, t, KEEP, line, Lang.tr("Keep"), edge, pillMiddle, mx, my, t.ink(), true)
            - 6.0F;
    hits.pill(
        c,
        t,
        TRY,
        line,
        state == Friends.Parcel.WORN ? Lang.tr("Take off") : Lang.tr("Try on"),
        edge,
        pillMiddle,
        mx,
        my,
        t.ink(),
        false);
    return cardX;
  }

  private float avatar(
      Canvas c,
      Theme t,
      long key,
      Note note,
      boolean mine,
      Friends.Line line,
      float cardX,
      float top,
      float mx,
      float my) {
    Depth.raised(c, t, cardX, top, MODEL_WIDTH, MODEL_HEIGHT, BUBBLE_RADIUS, t.surface2());
    float wellX = cardX + 8.0F;
    float wellY = top + 8.0F;
    boolean over = mx >= cardX && mx < cardX + MODEL_WIDTH && my >= top && my < top + MODEL_HEIGHT;
    Depth.inset(c, t, wellX, wellY, MODEL_WELL, MODEL_WELL, 10.0F, t.well());
    int handle = line == null ? Friends.PREVIEW_LOADING : friends.actions().preview(line);
    if (handle >= 0) {
      c.model(
          handle,
          wellX + 3.0F,
          wellY + 3.0F,
          MODEL_WELL - 6.0F,
          MODEL_WELL - 6.0F,
          phase * 6.2832F * SPIN,
          over,
          Canvas.AUDITION_NONE);
    } else if (handle == Friends.PREVIEW_LOADING) {
      for (int bead = 0; bead < 3; bead++) {
        float glow = pulse((phase - bead / 4.0F + 1.0F) % 1.0F);
        c.roundRect(
            wellX + MODEL_WELL * 0.5F - 10.0F + bead * (BEAD + 4.0F),
            wellY + (MODEL_WELL - BEAD) * 0.5F,
            BEAD,
            BEAD,
            BEAD * 0.5F,
            Theme.withAlpha(t.ink(), 0.2F + 0.7F * glow));
      }
    } else {
      String initial = note.text().isEmpty() ? "?" : note.text().substring(0, 1).toUpperCase();
      c.text(
          FontStyle.DOTS,
          initial,
          wellX + (MODEL_WELL - c.textWidth(FontStyle.DOTS, initial)) * 0.5F,
          wellY + (MODEL_WELL - c.lineHeight(FontStyle.DOTS)) * 0.5F,
          t.ink2());
    }
    float textX = wellX + MODEL_WELL + 10.0F;
    float room = cardX + MODEL_WIDTH - 8.0F - textX;
    float monoLine = c.lineHeight(FontStyle.MONO);
    float rowLine = c.lineHeight(FontStyle.ROW);
    float captionTop = top + 9.0F;
    c.text(FontStyle.MONO, Lang.tr("AVATAR"), textX, captionTop, t.grey());
    Friends.Parcel state = mine || line == null ? Friends.Parcel.IDLE : friends.parcel(line);
    String status =
        switch (state) {
          case WORKING -> Lang.tr("Fetching");
          case WORN -> Lang.tr("Wearing");
          case KEPT -> Lang.tr("In library");
          case FAILED -> Lang.tr("Unavailable");
          case IDLE -> "";
        };
    if (!status.isEmpty()) {
      c.text(
          FontStyle.MONO,
          status,
          textX + room - c.textWidth(FontStyle.MONO, status),
          captionTop,
          state == Friends.Parcel.FAILED ? t.alert() : t.ink2());
    }
    c.pushClip(textX, top, room, MODEL_HEIGHT);
    c.text(FontStyle.ROW, note.text(), textX, captionTop + monoLine + 2.0F, t.ink());
    String facts = infos.computeIfAbsent(key, ignored -> note.facts());
    c.text(FontStyle.MONO, facts, textX, captionTop + monoLine + rowLine + 4.0F, t.grey());
    c.popClip();
    if (mine
        || line == null
        || state == Friends.Parcel.WORKING
        || state == Friends.Parcel.FAILED
        || state == Friends.Parcel.KEPT) {
      return cardX;
    }
    float pillMiddle = top + MODEL_HEIGHT - Hits.PILL * 0.5F - 9.0F;
    float edge = cardX + MODEL_WIDTH - 8.0F;
    edge =
        hits.pill(c, t, KEEP, line, Lang.tr("Keep"), edge, pillMiddle, mx, my, t.ink(), true)
            - 6.0F;
    hits.pill(
        c,
        t,
        TRY,
        line,
        state == Friends.Parcel.WORN ? Lang.tr("Take off") : Lang.tr("Try on"),
        edge,
        pillMiddle,
        mx,
        my,
        t.ink(),
        false);
    return cardX;
  }

  private float place(
      Canvas c,
      Theme t,
      long key,
      Note note,
      boolean mine,
      Friends.Line line,
      float cardX,
      float top,
      float tall,
      float mx,
      float my) {
    Depth.raised(c, t, cardX, top, PLACE_WIDTH, tall, BUBBLE_RADIUS, t.surface2());
    float pad = BUBBLE_PAD_X;
    float inner = PLACE_WIDTH - pad * 2.0F;
    float monoLine = c.lineHeight(FontStyle.MONO);
    float rowLine = c.lineHeight(FontStyle.ROW);
    float smallLine = c.lineHeight(FontStyle.SMALL);
    int accent = Note.dimensionColor(note.place());
    float captionTop = top + 8.0F;
    c.roundRect(cardX + pad, captionTop + (monoLine - 6.0F) * 0.5F, 6.0F, 6.0F, 3.0F, accent);
    c.text(
        FontStyle.MONO,
        note.live() ? Lang.tr("LOCATION") : Lang.tr("WAYPOINT"),
        cardX + pad + 12.0F,
        captionTop,
        t.grey());
    String world =
        places.computeIfAbsent(
            note.place(), place -> Note.dimensionName(place).toUpperCase(java.util.Locale.ROOT));
    c.text(
        FontStyle.MONO,
        world,
        cardX + PLACE_WIDTH - pad - c.textWidth(FontStyle.MONO, world),
        captionTop,
        accent);
    float nameTop = captionTop + monoLine + 2.0F;
    c.pushClip(cardX + pad, top, inner, tall);
    c.text(FontStyle.ROW, note.text(), cardX + pad, nameTop, t.ink());
    c.popClip();
    float wellTop = nameTop + rowLine + 6.0F;
    Depth.inset(c, t, cardX + pad, wellTop, inner, PLACE_WELL, 8.0F, t.well());
    String[] values =
        coordinates.computeIfAbsent(
            key,
            ignored ->
                new String[] {
                  Integer.toString(note.x()), Integer.toString(note.y()), Integer.toString(note.z())
                });
    float cell = inner / 3.0F;
    float valueTop = wellTop + (PLACE_WELL - monoLine) * 0.5F;
    for (int i = 0; i < 3; i++) {
      float cellLeft = cardX + pad + i * cell;
      if (i > 0) {
        c.roundRect(cellLeft, wellTop + 6.0F, 1.0F, PLACE_WELL - 12.0F, 0.0F, t.hair());
      }
      c.text(FontStyle.MONO, AXES[i], cellLeft + 9.0F, valueTop, t.grey());
      c.text(
          FontStyle.MONO,
          values[i],
          cellLeft + cell - 9.0F - c.textWidth(FontStyle.MONO, values[i]),
          valueTop,
          t.ink());
    }
    float footTop = wellTop + PLACE_WELL + 6.0F;
    float footHeight = top + tall - 8.0F - footTop;
    float footMiddle = footTop + footHeight * 0.5F;
    float edge = cardX + PLACE_WIDTH - 6.0F;
    if (!mine && line != null) {
      if (friends.parcel(line) == Friends.Parcel.KEPT) {
        String added = Lang.tr("Added");
        edge -= c.textWidth(FontStyle.MONO, added) + 6.0F;
        c.text(FontStyle.MONO, added, edge, footMiddle - monoLine * 0.5F, t.grey());
      } else {
        edge = hits.pill(c, t, ADD, line, Lang.tr("Add"), edge, footMiddle, mx, my, t.ink(), true);
      }
    }
    int metres = friends.metresTo(note);
    Integer known = distances.get(key);
    String info = infos.get(key);
    if (info == null || known == null || known != metres) {
      String away =
          switch (metres) {
            case Friends.ELSEWHERE -> "";
            case Friends.OTHER_SERVER -> Lang.tr("Another server");
            case Friends.OTHER_DIMENSION -> Lang.tr("Another dimension");
            default ->
                metres < 1000
                    ? Lang.format("{0} m away", metres)
                    : Lang.format("{0} km away", metres / 1000 + "." + metres % 1000 / 100);
          };
      info =
          note.server().isEmpty()
              ? away
              : away.isEmpty() ? note.server() : away + " · " + note.server();
      infos.put(key, info);
      distances.put(key, metres);
    }
    c.pushClip(cardX + pad, footTop, Math.max(0.0F, edge - 8.0F - cardX - pad), footHeight);
    c.text(
        FontStyle.SMALL,
        info,
        cardX + pad,
        footMiddle - smallLine * 0.5F,
        metres >= 0 ? t.ink2() : t.grey());
    c.popClip();
    return cardX;
  }

  private void send() {
    Friends.Friend friend = friends.current();
    String text = field.take();
    if (friend != null && !text.isEmpty()) {
      friends.actions().send(friend.uuid, Note.text(text));
    }
  }

  @Override
  public boolean scroll(float mx, float my, float amount) {
    if (!contains(mx, my)) {
      return false;
    }
    if (tray > 0.5F && my >= trayTop && my < trayTop + tray) {
      strip = Math.max(0.0F, Math.min(stripOverflow, strip - amount * SCROLL_STEP));
      return true;
    }
    if (overflow <= 0.0F) {
      return false;
    }
    scroll = Math.max(0.0F, Math.min(overflow, scroll + amount * SCROLL_STEP));
    return true;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      field.focus(false);
      armed = 0.0F;
      return false;
    }
    int index = hits.at(mx, my);
    if (index == Hits.NONE || hits.kind(index) != REMOVE) {
      armed = 0.0F;
    }
    field.focus(friends.current() != null);
    if (index != Hits.NONE && hits.kind(index) == FIELD) {
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
    Friends.Friend friend = friends.current();
    if (index == Hits.NONE || friend == null) {
      return;
    }
    Object target = hits.target(index);
    switch (hits.kind(index)) {
      case SEND -> send();
      case PLUS -> open(mode == CLOSED ? ACTIONS : CLOSED);
      case BACK -> open(ACTIONS);
      case SHARE -> open(AVATARS);
      case CONFIG -> open(CONFIGS);
      case POINT -> open(WAYPOINTS);
      case WAVE -> {
        friends.actions().send(friend.uuid, Note.wave());
        open(CLOSED);
      }
      case INVITE -> {
        friends.actions().invite(friend.uuid);
        open(CLOSED);
      }
      case SPOT -> {
        friends.actions().shareSpot(friend.uuid);
        open(CLOSED);
      }
      case PICK -> {
        Friends.Share kind =
            mode == AVATARS
                ? Friends.Share.AVATAR
                : mode == CONFIGS ? Friends.Share.CONFIG : Friends.Share.WAYPOINT;
        friends.actions().share(friend.uuid, kind, ((Friends.Item) target).id());
        open(CLOSED);
      }
      case DROP -> friends.dismiss(friend.uuid, ((Friends.Pending) target).token);
      case JOIN -> friends.actions().join(friend.server);
      case ACCEPT -> friends.actions().join(((Friends.Line) target).note().text());
      case APPLY -> friends.actions().config((Friends.Line) target, true);
      case SAVE -> friends.actions().config((Friends.Line) target, false);
      case ADD -> friends.actions().waypoint((Friends.Line) target);
      case TRY -> friends.actions().tryOn((Friends.Line) target);
      case KEEP -> friends.actions().keep((Friends.Line) target);
      case REMOVE -> {
        if (armed > 0.0F) {
          armed = 0.0F;
          friends.actions().remove(friend.uuid);
        } else {
          armed = ARMED_SECONDS;
        }
      }
      default -> {}
    }
  }

  @Override
  public void finishEditing() {
    field.focus(false);
    friends.watching(false);
  }

  @Override
  public boolean keyDown(int keyCode, boolean control, boolean shift) {
    if (keyCode == Field.ESCAPE && mode != CLOSED) {
      open(CLOSED);
      return true;
    }
    if (field.submits(keyCode)) {
      send();
      return true;
    }
    return field.key(keyCode, control, shift);
  }

  @Override
  public boolean charTyped(int codepoint) {
    return field.type(codepoint);
  }
}
