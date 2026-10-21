package me.cxiy.cxvisuals.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.modules.social.Account;

public final class PlayerList extends Widget {
  private static final float ROW = 44.0F;
  private static final float FACE = 28.0F;
  private static final float FACE_RADIUS = 8.0F;
  private static final float LEFT = 8.0F;
  private static final float RIGHT = 6.0F;
  private static final float GAP = 10.0F;
  private static final float PILL = 26.0F;
  private static final float PILL_PAD = 13.0F;
  private static final int NONE = -1;

  private final Supplier<List<Account.Player>> source;
  private final String action;
  private final String empty;
  private final Consumer<Account.Player> pick;
  private float pillWidth = -1.0F;
  private String pressed;

  public PlayerList(
      Supplier<List<Account.Player>> source,
      String action,
      String empty,
      Consumer<Account.Player> pick) {
    this.source = source;
    this.action = action;
    this.empty = empty;
    this.pick = pick;
    this.h = ROW;
  }

  public float pillX() {
    return x + w - RIGHT - pillWidth * 0.5F;
  }

  public float rowY(int row) {
    return y + row * ROW + ROW * 0.5F;
  }

  @Override
  public float measure(Canvas c) {
    if (pillWidth < 0.0F) {
      pillWidth = (float) Math.ceil(c.textWidth(FontStyle.CAPTION, action) + PILL_PAD * 2.0F);
    }
    h = Math.max(1, source.get().size()) * ROW;
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    measure(c);
    List<Account.Player> players = source.get();
    if (players.isEmpty()) {
      c.text(FontStyle.SMALL, empty, x + LEFT, centered(c, FontStyle.SMALL, y, ROW), t.grey());
      return;
    }
    float rowLine = c.lineHeight(FontStyle.ROW);
    float smallLine = c.lineHeight(FontStyle.SMALL);
    float pillLeft = x + w - RIGHT - pillWidth;
    float nameX = x + LEFT + FACE + GAP;
    for (int row = 0; row < players.size(); row++) {
      Account.Player player = players.get(row);
      float top = y + row * ROW;
      if (row > 0) {
        c.roundRect(x + LEFT, top, w - LEFT, 1.0F, 0.0F, t.hair());
      }
      float faceTop = top + (ROW - FACE) * 0.5F;
      if (player.entityId() == Account.NO_ENTITY
          || !c.face(player.entityId(), x + LEFT, faceTop, FACE, FACE_RADIUS)) {
        c.roundRect(x + LEFT, faceTop, FACE, FACE, FACE_RADIUS, t.surface2());
        String initial =
            player.name().isEmpty() ? "?" : player.name().substring(0, 1).toUpperCase();
        c.text(
            FontStyle.MONO,
            initial,
            x + LEFT + (FACE - c.textWidth(FontStyle.MONO, initial)) * 0.5F,
            centered(c, FontStyle.MONO, faceTop, FACE),
            t.grey());
      }
      if (player.detail().isEmpty()) {
        c.text(FontStyle.ROW, player.name(), nameX, top + (ROW - rowLine) * 0.5F, t.ink());
      } else {
        float textTop = top + (ROW - rowLine - smallLine - 1.0F) * 0.5F;
        c.text(FontStyle.ROW, player.name(), nameX, textTop, t.ink());
        c.pushClip(nameX, top, pillLeft - GAP - nameX, ROW);
        c.text(FontStyle.SMALL, player.detail(), nameX, textTop + rowLine + 1.0F, t.grey());
        c.popClip();
      }
      float pillTop = top + (ROW - PILL) * 0.5F;
      boolean over =
          mouseX >= pillLeft
              && mouseX < pillLeft + pillWidth
              && mouseY >= pillTop
              && mouseY < pillTop + PILL;
      boolean down = player.uuid().equals(pressed);
      int fill = Theme.mix(t.surface2(), t.ink(), down ? 0.16F : over ? 0.08F : 0.0F);
      Depth.raised(c, t, pillLeft, pillTop, pillWidth, PILL, PILL * 0.5F, fill);
      c.text(
          FontStyle.CAPTION,
          action,
          pillLeft + PILL_PAD,
          centered(c, FontStyle.CAPTION, pillTop, PILL),
          t.ink());
    }
  }

  private int rowAt(float mx, float my) {
    float pillLeft = x + w - RIGHT - pillWidth;
    if (mx < pillLeft || mx >= pillLeft + pillWidth) {
      return NONE;
    }
    int row = (int) Math.floor((my - y) / ROW);
    float pillTop = y + row * ROW + (ROW - PILL) * 0.5F;
    return row >= 0 && row < source.get().size() && my >= pillTop && my < pillTop + PILL
        ? row
        : NONE;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    pressed = null;
    if (!contains(mx, my)) {
      return false;
    }
    int row = rowAt(mx, my);
    if (row != NONE) {
      pressed = source.get().get(row).uuid();
    }
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    String target = pressed;
    pressed = null;
    int row = rowAt(mx, my);
    if (target == null || row == NONE) {
      return;
    }
    Account.Player player = source.get().get(row);
    if (player.uuid().equals(target)) {
      pick.accept(player);
    }
  }
}
