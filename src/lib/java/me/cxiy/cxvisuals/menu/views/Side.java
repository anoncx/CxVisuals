package me.cxiy.cxvisuals.menu.views;

import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.ChatPanel;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.FriendsPanel;
import me.cxiy.cxvisuals.gui.PanelHost;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.Widget;
import me.cxiy.cxvisuals.modules.friends.Friends;
import me.cxiy.cxvisuals.modules.friends.FriendsModule;
import me.cxiy.cxvisuals.modules.hud.media.MediaCommand;
import me.cxiy.cxvisuals.modules.hud.media.MediaGlyphs;
import me.cxiy.cxvisuals.modules.hud.media.MediaHub;
import me.cxiy.cxvisuals.modules.hud.media.MediaSnapshot;

public class Side implements PanelHost {
  public static final float WIDTH = 340.0F;

  private static final float CARD_GAP = 14.0F;
  private static final float MEDIA = 92.0F;
  private static final float COVER = 60.0F;
  private static final float COVER_RADIUS = 13.0F;
  private static final float GLYPH = 18.0F;
  private static final float GLYPH_GAP = 14.0F;
  private static final float GLYPH_HIT = 30.0F;
  private static final float TRACK = 3.0F;
  private static final float FRIENDS_HEADER = 42.0F;
  private static final float FRIENDS_TALLEST = 420.0F;
  private static final float FRIENDS_LEAST = 122.0F;
  private static final float FRIENDS_FOOT = 8.0F;
  private static final float SCROLL_STEP = 40.0F;
  private static final int NO_USER = -1;
  private static final String IDLE = Lang.key("Nothing playing");
  private static final String BACK = Lang.key("Back");
  private static final String HEADING = Lang.key("Friends");
  private static final MediaGlyphs.Glyph[] TRANSPORT = {
    MediaGlyphs.Glyph.PREVIOUS, MediaGlyphs.Glyph.PLAY, MediaGlyphs.Glyph.NEXT
  };
  private static final MediaCommand[] COMMANDS = {
    MediaCommand.PREVIOUS, MediaCommand.PLAY_PAUSE, MediaCommand.NEXT
  };

  private final Friends friends;
  private final Supplier<MediaHub> hubs;
  private final FriendsPanel people;
  private final ChatPanel chat;
  private final float[] glyphX = new float[TRANSPORT.length];
  private MediaHub hub;
  private int user = NO_USER;
  private int pressedGlyph = -1;
  private int online;
  private String count = "";
  private float x;
  private float glyphY;
  private float friendsTop;
  private float friendsHeight;
  private float peopleHeight;
  private float peopleScroll;
  private float backX;
  private float backW;
  private boolean inChat;
  private boolean active;
  private boolean friendsShown;
  private Widget heldPanel;

  public Side(Friends friends, Supplier<MediaHub> hubs) {
    this.friends = friends;
    this.hubs = hubs;
    this.people = friends == null ? null : new FriendsPanel(friends, this);
    this.chat = friends == null ? null : new ChatPanel(friends, this);
  }

  private MediaHub hub() {
    if (hub == null) {
      hub = hubs.get();
      if (hub != null) {
        user = hub.user();
      }
    }
    return hub;
  }

  public boolean present() {
    return friends != null || hub() != null;
  }

  @Override
  public void openTab(String pageId) {
    inChat = FriendsModule.CHAT.equals(pageId);
  }

  @Override
  public float room() {
    return friendsHeight - FRIENDS_HEADER - FRIENDS_FOOT;
  }

  public void rest() {
    if (!active) {
      return;
    }
    active = false;
    if (hub != null) {
      hub.want(user, false, false, false);
    }
    if (friends != null) {
      friends.watching(false);
    }
  }

  public void draw(
      Canvas c, Theme t, float x, float y, float w, float h, float mouseX, float mouseY, float dt) {
    this.x = x;
    active = true;
    float top = y;
    if (hub() != null) {
      media(c, t, x, top, mouseX, mouseY);
      top += MEDIA + CARD_GAP;
    }
    friendsShown = false;
    if (friends != null) {
      friendsTop = top;
      float room = Math.min(FRIENDS_TALLEST, y + h - top);
      friendsHeight =
          inChat || peopleHeight <= 0.0F
              ? room
              : Math.max(
                  FRIENDS_LEAST, Math.min(room, FRIENDS_HEADER + peopleHeight + FRIENDS_FOOT));
      if (friendsHeight > FRIENDS_LEAST) {
        friendsShown = true;
        friends(c, t, mouseX, mouseY, dt);
      }
    }
  }

  private void media(Canvas c, Theme t, float x, float y, float mouseX, float mouseY) {
    hub.want(user, true, true, false);
    hub.poll();
    MediaSnapshot snapshot = hub.snapshot();
    Kit.window(c, t, x, y, WIDTH, MEDIA);
    float pad = (MEDIA - COVER) * 0.5F;
    float coverX = x + pad;
    float coverY = y + pad;
    byte[] art = hub.cover();
    if (art == null || !c.icon(art, coverX, coverY, COVER, COVER_RADIUS)) {
      c.roundRect(coverX, coverY, COVER, COVER, COVER_RADIUS, Theme.withAlpha(t.ink(), 0.10F));
      c.icon(
          MediaGlyphs.png(MediaGlyphs.Glyph.NOTE, t.grey()),
          coverX + (COVER - 22.0F) * 0.5F,
          coverY + (COVER - 22.0F) * 0.5F,
          22.0F,
          0.0F);
    }
    float textX = coverX + COVER + 14.0F;
    float right = x + WIDTH - pad;
    float controls = TRANSPORT.length * GLYPH + (TRANSPORT.length - 1) * GLYPH_GAP;
    boolean present = snapshot.present();
    glyphY = y + pad + 4.0F;
    c.pushClip(textX, y, right - controls - 12.0F - textX, MEDIA);
    c.text(
        FontStyle.ROW,
        present ? snapshot.title() : Lang.tr(IDLE),
        textX,
        y + pad + 2.0F,
        present ? t.ink() : t.grey());
    if (present) {
      c.text(
          FontStyle.SMALL,
          snapshot.artist(),
          textX,
          y + pad + 2.0F + c.lineHeight(FontStyle.ROW),
          t.grey());
    }
    c.popClip();
    for (int i = 0; i < TRANSPORT.length; i++) {
      MediaGlyphs.Glyph glyph =
          i == 1 && snapshot.playing() ? MediaGlyphs.Glyph.PAUSE : TRANSPORT[i];
      glyphX[i] = right - controls + i * (GLYPH + GLYPH_GAP);
      boolean over =
          present
              && Kit.inside(mouseX, mouseY, glyphX[i] - 6.0F, glyphY - 6.0F, GLYPH_HIT, GLYPH_HIT);
      int ink = !present ? t.dotOff() : over || pressedGlyph == i ? t.ink() : t.ink2();
      c.icon(MediaGlyphs.png(glyph, ink), glyphX[i], glyphY, GLYPH, 0.0F);
    }
    float trackY = y + MEDIA - pad - TRACK - 1.0F;
    float trackWidth = right - textX;
    c.roundRect(textX, trackY, trackWidth, TRACK, TRACK * 0.5F, t.surface2());
    if (present) {
      c.roundRect(
          textX,
          trackY,
          Math.max(TRACK, trackWidth * snapshot.progress(hub.nanos())),
          TRACK,
          TRACK * 0.5F,
          t.ink());
    }
  }

  private void friends(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    friends.watching(inChat);
    Kit.window(c, t, x, friendsTop, WIDTH, friendsHeight);
    float headerMiddle = friendsTop + FRIENDS_HEADER * 0.5F + 3.0F;
    Friends.Friend current = friends.current();
    float headingX = x + 20.0F;
    if (inChat) {
      backW = Kit.pillWidth(c, Lang.tr(BACK)) - 10.0F;
      backX = x + 12.0F;
      boolean over = Kit.inside(mouseX, mouseY, backX, headerMiddle - 13.0F, backW, 26.0F);
      Kit.pill(
          c,
          t,
          Lang.tr(BACK),
          backX,
          headerMiddle - 13.0F,
          backW,
          26.0F,
          over ? 1.0F : 0.0F,
          0.0F,
          false,
          true);
      headingX = backX + backW + 12.0F;
    }
    c.text(
        FontStyle.ROW,
        inChat && current != null ? current.name : Lang.tr(HEADING),
        headingX,
        headerMiddle - c.lineHeight(FontStyle.ROW) * 0.5F,
        t.ink());
    if (!inChat) {
      int now = 0;
      for (Friends.Friend friend : friends.friends()) {
        if (friend.online) {
          now++;
        }
      }
      if (now != online || count.isEmpty()) {
        online = now;
        count = now + " " + Lang.tr("online");
      }
      c.text(
          FontStyle.MONO,
          count,
          x + WIDTH - 20.0F - c.textWidth(FontStyle.MONO, count),
          headerMiddle - c.lineHeight(FontStyle.MONO) * 0.5F,
          t.grey());
    }
    float bodyTop = friendsTop + FRIENDS_HEADER;
    float bodyHeight = friendsHeight - FRIENDS_HEADER - FRIENDS_FOOT;
    float bodyX = x + 8.0F;
    float bodyWidth = WIDTH - 16.0F;
    c.pushClip(bodyX, bodyTop, bodyWidth, bodyHeight);
    if (inChat) {
      chat.w = bodyWidth;
      chat.measure(c);
      chat.place(bodyX, bodyTop);
      chat.draw(c, t, mouseX, mouseY, dt);
    } else {
      people.w = bodyWidth;
      people.measure(c);
      peopleHeight = people.h;
      float reach = Math.max(0.0F, people.h - bodyHeight);
      peopleScroll = Math.max(0.0F, Math.min(reach, peopleScroll));
      people.place(bodyX, bodyTop - peopleScroll);
      boolean inside = Kit.inside(mouseX, mouseY, bodyX, bodyTop, bodyWidth, bodyHeight);
      people.draw(c, t, inside ? mouseX : Float.NEGATIVE_INFINITY, mouseY, dt);
    }
    c.popClip();
  }

  private Widget panel() {
    return friends == null ? null : inChat ? chat : people;
  }

  private boolean inFriends(float mx, float my) {
    return friendsShown
        && Kit.inside(
            mx,
            my,
            x + 8.0F,
            friendsTop + FRIENDS_HEADER,
            WIDTH - 16.0F,
            friendsHeight - FRIENDS_HEADER - FRIENDS_FOOT);
  }

  public void blur() {
    Widget panel = panel();
    if (panel != null) {
      panel.finishEditing();
    }
  }

  public boolean mouseDown(float mx, float my) {
    if (hub != null && hub.snapshot().present()) {
      for (int i = 0; i < TRANSPORT.length; i++) {
        if (Kit.inside(mx, my, glyphX[i] - 6.0F, glyphY - 6.0F, GLYPH_HIT, GLYPH_HIT)) {
          pressedGlyph = i;
          return true;
        }
      }
    }
    if (friendsShown && inChat && Kit.inside(mx, my, backX, friendsTop, backW, FRIENDS_HEADER)) {
      chat.finishEditing();
      inChat = false;
      return true;
    }
    Widget panel = panel();
    if (panel != null) {
      if (inFriends(mx, my) && panel.mouseDown(mx, my)) {
        heldPanel = panel;
        return true;
      }
      panel.finishEditing();
    }
    return false;
  }

  public void mouseUp(float mx, float my) {
    if (pressedGlyph >= 0) {
      int index = pressedGlyph;
      pressedGlyph = -1;
      if (Kit.inside(mx, my, glyphX[index] - 6.0F, glyphY - 6.0F, GLYPH_HIT, GLYPH_HIT)) {
        hub.send(COMMANDS[index], 0L);
      }
    }
    if (heldPanel != null) {
      Widget panel = heldPanel;
      heldPanel = null;
      panel.mouseUp(mx, my);
    }
  }

  public void mouseDrag(float mx, float my) {
    if (heldPanel != null) {
      heldPanel.mouseDrag(mx, my);
    }
  }

  public boolean scroll(float mx, float my, float amount) {
    if (!inFriends(mx, my)) {
      return false;
    }
    if (inChat) {
      return chat.scroll(mx, my, amount);
    }
    peopleScroll -= amount * SCROLL_STEP;
    return true;
  }

  public boolean keyDown(int keyCode, int modifiers) {
    Widget panel = panel();
    if (panel == null || !friendsShown) {
      return false;
    }
    if (panel.keyDown(keyCode, (modifiers & Kit.CONTROL) != 0, (modifiers & Kit.SHIFT) != 0)) {
      return true;
    }
    if (keyCode == Kit.ESCAPE && inChat) {
      chat.finishEditing();
      inChat = false;
      return true;
    }
    return typing();
  }

  public boolean charTyped(int codepoint) {
    Widget panel = panel();
    return panel != null && friendsShown && panel.charTyped(codepoint);
  }

  public boolean typing() {
    return friends != null && friendsShown && (inChat ? chat.typing() : people.typing());
  }
}
