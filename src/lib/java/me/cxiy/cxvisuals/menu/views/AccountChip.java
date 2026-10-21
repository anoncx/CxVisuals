package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.social.Account;

final class AccountChip {
  static final float HEIGHT = 56.0F;

  private static final float FACE = 32.0F;
  private static final float FACE_RADIUS = 9.0F;
  private static final float FACE_INSET = 8.0F;
  private static final float GAP = 10.0F;
  private static final float DOT = 5.0F;
  private static final String CONNECT = Lang.key("Connect");
  private static final String RETRY = Lang.key("Retry");
  private static final String FALLBACK = Lang.key("Player");

  private final Account account;
  private final Anim hover = Anim.hover(0.0F);
  private String shownName = "";
  private String initial = "";
  private float x;
  private float y;
  private float w;
  private float clock;
  private boolean held;

  AccountChip(Account account) {
    this.account = account;
  }

  private boolean actionable() {
    if (account == null) {
      return false;
    }
    Account.State state = account.state();
    return state == Account.State.SIGNED_OUT || state == Account.State.STOPPED;
  }

  private int dot(Theme t) {
    return switch (account.state()) {
      case ONLINE -> account.paused() ? t.dotOff() : t.red();
      case RETRYING, STOPPED -> t.alert();
      case CONNECTING ->
          Theme.mix(t.dotOff(), t.ink(), 0.5F + 0.5F * (float) Math.sin(clock * 5.0F));
      case SIGNED_OUT -> t.dotOff();
    };
  }

  void draw(
      Canvas c,
      Theme t,
      String player,
      float x,
      float y,
      float w,
      float open,
      float mouseX,
      float mouseY,
      float dt) {
    this.x = x;
    this.y = y;
    this.w = w;
    clock += dt;
    String name =
        account != null && !account.name().isEmpty()
            ? account.name()
            : player.isEmpty() ? Lang.tr(FALLBACK) : player;
    if (!name.equals(shownName)) {
      shownName = name;
      initial = name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase();
    }
    boolean action = actionable();
    hover.set(action && Kit.inside(mouseX, mouseY, x, y, w, HEIGHT) ? 1.0F : 0.0F);
    hover.update(dt);
    float lit = Math.max(hover.get(), held ? 1.0F : 0.0F);
    if (lit > 0.01F) {
      c.roundRect(x, y + 4.0F, w, HEIGHT - 8.0F, 12.0F, Theme.withAlpha(t.ink(), 0.07F * lit));
    }
    float faceX = x + FACE_INSET;
    float faceY = y + (HEIGHT - FACE) * 0.5F;
    if (account == null || !c.face(account.entityId(), faceX, faceY, FACE, FACE_RADIUS)) {
      Depth.raised(c, t, faceX, faceY, FACE, FACE, FACE_RADIUS, t.surface2());
      c.text(
          FontStyle.DOTS,
          initial,
          faceX + (FACE - c.textWidth(FontStyle.DOTS, initial)) * 0.5F,
          faceY + (FACE - c.lineHeight(FontStyle.DOTS)) * 0.5F,
          t.ink());
    }
    if (open <= 0.01F) {
      return;
    }
    float textX = faceX + FACE + GAP;
    float rowLine = c.lineHeight(FontStyle.ROW);
    float monoLine = c.lineHeight(FontStyle.MONO);
    float textTop = y + (HEIGHT - rowLine - monoLine - 2.0F) * 0.5F;
    c.pushAlpha(open);
    c.pushClip(textX, y, Math.max(0.0F, x + w - FACE_INSET - textX), HEIGHT);
    c.text(
        FontStyle.ROW,
        name,
        textX,
        account == null ? y + (HEIGHT - rowLine) * 0.5F : textTop,
        t.ink());
    if (account != null) {
      float statusTop = textTop + rowLine + 2.0F;
      String status =
          !action
              ? account.status()
              : Lang.tr(account.state() == Account.State.SIGNED_OUT ? CONNECT : RETRY);
      c.roundRect(textX, statusTop + (monoLine - DOT) * 0.5F, DOT, DOT, DOT * 0.5F, dot(t));
      c.text(FontStyle.MONO, status, textX + DOT + 6.0F, statusTop, action ? t.ink2() : t.grey());
    }
    c.popClip();
    c.popAlpha();
  }

  boolean mouseDown(float mx, float my) {
    if (!actionable() || !Kit.inside(mx, my, x, y, w, HEIGHT)) {
      return false;
    }
    held = true;
    return true;
  }

  void mouseUp(float mx, float my) {
    if (!held) {
      return;
    }
    held = false;
    if (actionable() && Kit.inside(mx, my, x, y, w, HEIGHT)) {
      account.actions().connect();
    }
  }
}
