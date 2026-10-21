package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.social.Account;

public final class AccountCard extends Widget {
  public static final String PITCH =
      Lang.key(
          "Players nearby see the avatar you wear and the song you play. Verified with your Minecraft account, no password.");

  private static final float TOP = 48.0F;
  private static final float FACE = 44.0F;
  private static final float FACE_RADIUS = 12.0F;
  private static final float LEFT = 8.0F;
  private static final float RIGHT = 6.0F;
  private static final float GAP = 12.0F;
  private static final float BUTTON = 32.0F;
  private static final float BUTTON_PAD = 18.0F;
  private static final float DOT = 6.0F;
  private static final float NOTE_TOP = 10.0F;
  private static final float NOTE_BOTTOM = 2.0F;
  private static final float LINE_GAP = 1.0F;
  private static final float BEAT = 0.9F;
  private static final int BEADS = 3;
  private static final float BEAD = 4.0F;
  private static final float BEAD_GAP = 5.0F;

  private final Account account;
  private final Anim press = Anim.press(0.0F);
  private final Anim hover = Anim.hover(0.0F);
  private final Anim busy = Anim.reveal(0.0F);
  private final Anim note;
  private final List<String> lines = new ArrayList<>(3);
  private String wrapped = "";
  private float wrappedRoom = -1.0F;
  private float noteHeight;
  private float buttonWidth = 96.0F;
  private float phase;
  private boolean pressed;

  public AccountCard(Account account) {
    this.account = account;
    this.note = Anim.reveal(noteText().isEmpty() ? 0.0F : 1.0F);
    this.h = TOP;
  }

  private String noteText() {
    return account.state() == Account.State.SIGNED_OUT ? Lang.tr(PITCH) : account.fault();
  }

  private String label() {
    return switch (account.state()) {
      case SIGNED_OUT -> Lang.tr("Connect");
      case STOPPED -> Lang.tr("Try again");
      case CONNECTING -> "";
      case ONLINE, RETRYING -> Lang.tr("Disconnect");
    };
  }

  private boolean primary() {
    return account.state() == Account.State.SIGNED_OUT || account.state() == Account.State.STOPPED;
  }

  public float buttonX() {
    return x + w - RIGHT - buttonWidth * 0.5F;
  }

  public float buttonY() {
    return y + TOP * 0.5F;
  }

  @Override
  public float measure(Canvas c) {
    String label = label();
    if (!label.isEmpty()) {
      buttonWidth = (float) Math.ceil(c.textWidth(FontStyle.LABEL, label) + BUTTON_PAD * 2.0F);
    }
    String text = noteText();
    float room = w - LEFT - RIGHT;
    if (!text.isEmpty() && (!text.equals(wrapped) || room != wrappedRoom)) {
      wrapped = text;
      wrappedRoom = room;
      wrap(c, text, room);
      noteHeight =
          NOTE_TOP + lines.size() * (c.lineHeight(FontStyle.SMALL) + LINE_GAP) + NOTE_BOTTOM;
    }
    h = TOP + noteHeight * Math.max(0.0F, Math.min(1.0F, note.get()));
    return w;
  }

  private void wrap(Canvas c, String text, float room) {
    lines.clear();
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
        lines.add(text.substring(start, fits));
        start = fits + 1;
        fits = i;
      }
    }
    lines.add(text.substring(start));
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    Account.State state = account.state();
    boolean connecting = state == Account.State.CONNECTING;
    press.set(pressed ? 1.0F : 0.0F);
    hover.set(!connecting && overButton(mouseX, mouseY) ? 1.0F : 0.0F);
    busy.set(connecting ? 1.0F : 0.0F);
    note.set(noteText().isEmpty() ? 0.0F : 1.0F);
    press.update(dt);
    hover.update(dt);
    busy.update(dt);
    note.update(dt);
    phase = (phase + dt / BEAT) % 1.0F;

    float faceTop = y + (TOP - FACE) * 0.5F;
    String name = account.name().isEmpty() ? Lang.tr("Player") : account.name();
    if (!c.face(account.entityId(), x + LEFT, faceTop, FACE, FACE_RADIUS)) {
      Depth.raised(c, t, x + LEFT, faceTop, FACE, FACE, FACE_RADIUS, t.surface2());
      String initial = name.substring(0, 1).toUpperCase();
      c.text(
          FontStyle.DOTS,
          initial,
          x + LEFT + (FACE - c.textWidth(FontStyle.DOTS, initial)) * 0.5F,
          centered(c, FontStyle.DOTS, faceTop, FACE),
          t.ink());
    }

    float textX = x + LEFT + FACE + GAP;
    float rowLine = c.lineHeight(FontStyle.ROW);
    float monoLine = c.lineHeight(FontStyle.MONO);
    float textTop = y + (TOP - rowLine - monoLine - 3.0F) * 0.5F;
    c.text(FontStyle.ROW, name, textX, textTop, t.ink());
    float statusTop = textTop + rowLine + 3.0F;
    int dot =
        switch (state) {
          case ONLINE -> account.paused() ? t.dotOff() : t.red();
          case RETRYING, STOPPED -> t.alert();
          case CONNECTING -> Theme.withAlpha(t.ink(), 0.35F + 0.65F * pulse(phase));
          case SIGNED_OUT -> t.dotOff();
        };
    c.roundRect(textX, statusTop + (monoLine - DOT) * 0.5F, DOT, DOT, DOT * 0.5F, dot);
    c.text(FontStyle.MONO, account.status(), textX + DOT + 7.0F, statusTop, t.grey());

    float left = x + w - RIGHT - buttonWidth;
    float top = y + (TOP - BUTTON) * 0.5F;
    float waiting = Math.max(0.0F, Math.min(1.0F, busy.get()));
    boolean primary = primary();
    int base = Theme.mix(primary ? t.ink() : t.surface2(), t.surface2(), waiting);
    int fill = Theme.mix(base, primary ? t.paper() : t.ink(), 0.08F * hover.get());
    c.pushTransform(1.0F - 0.05F * press.get(), left + buttonWidth * 0.5F, top + BUTTON * 0.5F);
    Depth.raised(c, t, left, top, buttonWidth, BUTTON, BUTTON * 0.5F, fill);
    String label = label();
    if (!label.isEmpty() && waiting < 1.0F) {
      c.pushAlpha(1.0F - waiting);
      c.text(
          FontStyle.LABEL,
          label,
          left + (buttonWidth - c.textWidth(FontStyle.LABEL, label)) * 0.5F,
          centered(c, FontStyle.LABEL, top, BUTTON),
          primary ? (t.paper() | 0xFF000000) : t.ink());
      c.popAlpha();
    }
    if (waiting > 0.0F) {
      float span = BEADS * BEAD + (BEADS - 1) * BEAD_GAP;
      float beadX = left + (buttonWidth - span) * 0.5F;
      float beadY = top + (BUTTON - BEAD) * 0.5F;
      for (int i = 0; i < BEADS; i++) {
        float glow = pulse((phase - i / (float) (BEADS + 1) + 1.0F) % 1.0F);
        c.roundRect(
            beadX + i * (BEAD + BEAD_GAP),
            beadY,
            BEAD,
            BEAD,
            BEAD * 0.5F,
            Theme.withAlpha(t.ink(), waiting * (0.22F + 0.78F * glow)));
      }
    }
    c.popTransform();

    float shown = Math.max(0.0F, Math.min(1.0F, note.get()));
    if (shown > 0.004F && !lines.isEmpty()) {
      float step = c.lineHeight(FontStyle.SMALL) + LINE_GAP;
      c.pushClip(x, y + TOP, w, noteHeight * shown);
      c.pushAlpha(shown);
      int ink = account.trouble() ? t.alert() : t.grey();
      for (int i = 0; i < lines.size(); i++) {
        c.text(FontStyle.SMALL, lines.get(i), x + LEFT, y + TOP + NOTE_TOP + i * step, ink);
      }
      c.popAlpha();
      c.popClip();
    }
  }

  private static float pulse(float at) {
    float wave = 1.0F - Math.abs(at * 2.0F - 1.0F);
    return wave * wave * (3.0F - 2.0F * wave);
  }

  private boolean overButton(float mx, float my) {
    float left = x + w - RIGHT - buttonWidth;
    float top = y + (TOP - BUTTON) * 0.5F;
    return mx >= left && mx < left + buttonWidth && my >= top && my < top + BUTTON;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      return false;
    }
    pressed = account.state() != Account.State.CONNECTING && overButton(mx, my);
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    if (!pressed) {
      return;
    }
    pressed = false;
    if (!overButton(mx, my)) {
      return;
    }
    if (primary()) {
      account.actions().connect();
    } else if (account.state() != Account.State.CONNECTING) {
      account.actions().disconnect();
    }
  }
}
