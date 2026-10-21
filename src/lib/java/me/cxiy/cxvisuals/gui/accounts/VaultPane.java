package me.cxiy.cxvisuals.gui.accounts;

import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.Field;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.accounts.Hub;
import me.cxiy.cxvisuals.modules.accounts.Vault;

final class VaultPane {
  private enum Step {
    HOME,
    TO_PASSWORD,
    TO_DEVICE,
    CHANGE
  }

  private static final float CARD_H = 112.0F;
  private static final float CARD_GAP = 12.0F;
  private static final float FIELD_W = 320.0F;
  private static final long ARM_NANOS = 3_000_000_000L;

  private final Hub hub;
  private final Runnable done;
  private final Field first = new Field(Lang.key("Password"), 128).masked(true);
  private final Field second = new Field(Lang.key("Repeat the password"), 128).masked(true);
  private final Field third = new Field(Lang.key("Current password"), 128).masked(true);
  private final Pill create =
      new Pill(Lang.key("Create the vault"), Pill.Tone.PRIMARY).minimum(160.0F);
  private final Pill unlock = new Pill(Lang.key("Unlock"), Pill.Tone.PRIMARY).minimum(120.0F);
  private final Pill erase = new Pill(Lang.key("Erase the vault"), Pill.Tone.DANGER);
  private final Pill lock = new Pill(Lang.key("Lock now"), Pill.Tone.QUIET);
  private final Pill toPassword = new Pill(Lang.key("Use a master password"), Pill.Tone.QUIET);
  private final Pill toDevice = new Pill(Lang.key("Use this device instead"), Pill.Tone.QUIET);
  private final Pill change = new Pill(Lang.key("Change the password"), Pill.Tone.QUIET);
  private final Pill apply = new Pill(Lang.key("Apply"), Pill.Tone.PRIMARY).minimum(110.0F);
  private final Pill back = new Pill(Lang.key("Back"), Pill.Tone.QUIET);
  private final Anim deviceCard = Anim.state(1.0F);
  private final Anim passwordCard = Anim.state(0.0F);
  private Step step = Step.HOME;
  private Vault.Method choice = Vault.Method.DEVICE;
  private long armedAt;
  private float x;
  private float y;
  private float w;
  private float h;
  private float deviceX;
  private float passwordX;
  private float cardY;
  private float cardW;
  private float firstY;
  private float secondY;
  private float thirdY;

  VaultPane(Hub hub, Runnable done) {
    this.hub = hub;
    this.done = done;
  }

  void reset() {
    step = Step.HOME;
    armedAt = 0L;
    first.set("");
    second.set("");
    third.set("");
    first.focus(false);
    second.focus(false);
    third.focus(false);
  }

  boolean typing() {
    return first.focused() || second.focused() || third.focused();
  }

  private static char[] chars(Field field) {
    return field.text().toCharArray();
  }

  private boolean passwordsOk() {
    String a = first.text();
    return a.length() >= Vault.MIN_PASSWORD && a.equals(second.text());
  }

  void draw(
      Canvas c,
      Theme t,
      float px,
      float py,
      float pw,
      float ph,
      boolean manage,
      float mx,
      float my,
      float dt) {
    x = px;
    y = py;
    w = pw;
    h = ph;
    Vault.State state = hub.vaultState();
    if (state == Vault.State.NONE) {
      drawSetup(c, t, mx, my, dt);
    } else if (state == Vault.State.LOCKED) {
      drawLocked(c, t, mx, my, dt);
    } else if (manage) {
      drawManage(c, t, mx, my, dt);
    }
  }

  private float heading(Canvas c, Theme t, String caption, String title, float top) {
    c.text(FontStyle.CAPTION, Lang.tr(caption).toUpperCase(Lang.locale()), x, top, t.red());
    float titleY = top + c.lineHeight(FontStyle.CAPTION) + 4.0F;
    c.text(FontStyle.DISPLAY, c.fit(FontStyle.DISPLAY, Lang.tr(title), w), x, titleY, t.ink());
    return titleY + c.lineHeight(FontStyle.DISPLAY) + 6.0F;
  }

  private float paragraph(Canvas c, Theme t, String text, float top, float width, int ink) {
    List<String> lines = Words.wrap(c, FontStyle.SMALL, Lang.tr(text), width);
    float line = c.lineHeight(FontStyle.SMALL) + 3.0F;
    for (int i = 0; i < lines.size(); i++) {
      c.text(FontStyle.SMALL, lines.get(i), x, top + i * line, ink);
    }
    return top + lines.size() * line;
  }

  private void drawSetup(Canvas c, Theme t, float mx, float my, float dt) {
    float top = heading(c, t, Lang.key("Vault"), Lang.key("Protect your accounts"), y);
    top =
        paragraph(
                c,
                t,
                Lang.key(
                    "Everything is encrypted with AES-256-GCM before it reaches your disk. Choose how the vault opens."),
                top,
                Math.min(w, 520.0F),
                t.ink2())
            + 16.0F;
    cardW = (Math.min(w, 560.0F) - CARD_GAP) * 0.5F;
    cardY = top;
    deviceX = x;
    passwordX = x + cardW + CARD_GAP;
    deviceCard.set(choice == Vault.Method.DEVICE ? 1.0F : 0.0F);
    passwordCard.set(choice == Vault.Method.PASSWORD ? 1.0F : 0.0F);
    deviceCard.update(dt);
    passwordCard.update(dt);
    card(
        c,
        t,
        Lang.key("This device"),
        Lang.key(
            "Opens by itself on this computer and user. Nothing to remember, but the vault is useless anywhere else."),
        deviceX,
        deviceCard.get());
    card(
        c,
        t,
        Lang.key("Master password"),
        Lang.key(
            "You type it once per session. Works on any computer and keeps the vault shut until you do."),
        passwordX,
        passwordCard.get());
    float next = cardY + CARD_H + 18.0F;
    if (choice == Vault.Method.PASSWORD) {
      firstY = next;
      secondY = next + Field.HEIGHT + 8.0F;
      first.draw(c, t, x, firstY, FIELD_W, dt);
      second.draw(c, t, x, secondY, FIELD_W, dt);
      String hint =
          first.text().length() < Vault.MIN_PASSWORD
              ? Lang.format("Use at least {0} characters.", Vault.MIN_PASSWORD)
              : !first.text().equals(second.text()) && !second.text().isEmpty()
                  ? Lang.tr("The passwords do not match.")
                  : "";
      c.text(
          FontStyle.CAPTION,
          hint,
          x + FIELD_W + 14.0F,
          secondY + (Field.HEIGHT - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          t.grey());
      next = secondY + Field.HEIGHT + 16.0F;
    }
    create.enabled(choice == Vault.Method.DEVICE || passwordsOk()).draw(c, t, x, next, mx, my, dt);
    status(c, t, next);
  }

  private void card(Canvas c, Theme t, String title, String text, float cx, float amount) {
    Depth.inset(c, t, cx, cardY, cardW, CARD_H, 18.0F, t.surface2());
    if (amount > 0.01F) {
      c.roundRectBorder(
          cx, cardY, cardW, CARD_H, 18.0F, 1.0F + amount, Theme.withAlpha(t.focus(), amount));
    }
    c.roundRect(
        cx + 16.0F, cardY + 18.0F, 10.0F, 10.0F, 5.0F, Theme.mix(t.dotOff(), t.red(), amount));
    c.text(FontStyle.ROW, Lang.tr(title), cx + 34.0F, cardY + 14.0F, t.ink());
    List<String> lines = Words.wrap(c, FontStyle.CAPTION, Lang.tr(text), cardW - 32.0F);
    float line = c.lineHeight(FontStyle.CAPTION) + 2.0F;
    for (int i = 0; i < lines.size(); i++) {
      c.text(FontStyle.CAPTION, lines.get(i), cx + 16.0F, cardY + 44.0F + i * line, t.grey());
    }
  }

  private void status(Canvas c, Theme t, float top) {
    if (hub.busy()) {
      c.text(
          FontStyle.CAPTION,
          Lang.tr("Working"),
          x + 140.0F + 24.0F,
          top + (Pill.HEIGHT - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          t.grey());
    }
  }

  private void drawLocked(Canvas c, Theme t, float mx, float my, float dt) {
    boolean device = hub.vault().method() == Vault.Method.DEVICE;
    float top =
        heading(
            c,
            t,
            Lang.key("Vault"),
            device
                ? Lang.key("This vault belongs to another computer")
                : Lang.key("Unlock your accounts"),
            y);
    top =
        paragraph(
                c,
                t,
                device
                    ? Lang.key(
                        "It was created for a different computer or user, so it cannot be opened here. Erase it to start fresh.")
                    : Lang.key("Your accounts stay encrypted until you enter the master password."),
                top,
                Math.min(w, 520.0F),
                t.ink2())
            + 18.0F;
    if (!device) {
      firstY = top;
      first.draw(c, t, x, firstY, FIELD_W, dt);
      unlock
          .enabled(!first.text().isEmpty() && !hub.busy())
          .draw(c, t, x + FIELD_W + 10.0F, firstY - 1.0F, mx, my, dt);
      top += Field.HEIGHT + 22.0F;
    }
    erase
        .label(armed() ? Lang.key("Click again to erase everything") : Lang.key("Erase the vault"))
        .draw(c, t, x, top, mx, my, dt);
  }

  private boolean armed() {
    return armedAt != 0L && System.nanoTime() - armedAt < ARM_NANOS;
  }

  private void drawManage(Canvas c, Theme t, float mx, float my, float dt) {
    boolean password = hub.vault().method() == Vault.Method.PASSWORD;
    float top = heading(c, t, Lang.key("Vault"), Lang.key("Security"), y);
    String status =
        password
            ? Lang.key("Protected by your master password.")
            : Lang.key("Tied to this computer and user.");
    top = paragraph(c, t, status, top, Math.min(w, 520.0F), t.ink2()) + 18.0F;
    if (step == Step.HOME) {
      float cursor = x;
      float width = lock.width(c);
      lock.draw(c, t, cursor, top, width, mx, my, dt);
      cursor += width + 8.0F;
      Pill primary = password ? toDevice : toPassword;
      width = primary.width(c);
      primary.draw(c, t, cursor, top, width, mx, my, dt);
      cursor += width + 8.0F;
      if (password) {
        width = change.width(c);
        change.draw(c, t, cursor, top, width, mx, my, dt);
      }
      float eraseY = top + Pill.HEIGHT + 28.0F;
      erase
          .label(
              armed() ? Lang.key("Click again to erase everything") : Lang.key("Erase the vault"))
          .draw(c, t, x, eraseY, mx, my, dt);
      back.draw(c, t, x + w - back.width(c), y + 2.0F, mx, my, dt);
      return;
    }
    float next = top;
    thirdY = -1.0F;
    if (step == Step.TO_DEVICE || step == Step.CHANGE) {
      thirdY = next;
      third.draw(c, t, x, thirdY, FIELD_W, dt);
      next += Field.HEIGHT + 8.0F;
    }
    if (step == Step.TO_PASSWORD || step == Step.CHANGE) {
      firstY = next;
      secondY = next + Field.HEIGHT + 8.0F;
      first.draw(c, t, x, firstY, FIELD_W, dt);
      second.draw(c, t, x, secondY, FIELD_W, dt);
      next = secondY + Field.HEIGHT + 8.0F;
    }
    boolean ready =
        (step == Step.TO_DEVICE
                ? !third.text().isEmpty()
                : step == Step.TO_PASSWORD
                    ? passwordsOk()
                    : !third.text().isEmpty() && passwordsOk())
            && !hub.busy();
    apply.enabled(ready).draw(c, t, x, next + 8.0F, mx, my, dt);
    back.draw(c, t, x + apply.w + 8.0F, next + 8.0F, mx, my, dt);
  }

  boolean mouseDown(float mx, float my, boolean manage) {
    first.focus(false);
    second.focus(false);
    third.focus(false);
    Vault.State state = hub.vaultState();
    if (state == Vault.State.NONE) {
      if (my >= cardY && my < cardY + CARD_H) {
        if (mx >= deviceX && mx < deviceX + cardW) {
          choice = Vault.Method.DEVICE;
          return true;
        }
        if (mx >= passwordX && mx < passwordX + cardW) {
          choice = Vault.Method.PASSWORD;
          return true;
        }
      }
      if (choice == Vault.Method.PASSWORD
          && fieldAt(first, firstY, mx, my) | fieldAt(second, secondY, mx, my)) {
        return true;
      }
      return create.down(mx, my);
    }
    if (state == Vault.State.LOCKED) {
      if (fieldAt(first, firstY, mx, my)) {
        return true;
      }
      return unlock.down(mx, my) || erase.down(mx, my);
    }
    if (!manage) {
      return false;
    }
    if (step == Step.HOME) {
      return lock.down(mx, my)
          || toPassword.down(mx, my)
          || toDevice.down(mx, my)
          || change.down(mx, my)
          || erase.down(mx, my)
          || back.down(mx, my);
    }
    boolean hit = false;
    if (step == Step.TO_DEVICE || step == Step.CHANGE) {
      hit |= fieldAt(third, thirdY, mx, my);
    }
    if (step == Step.TO_PASSWORD || step == Step.CHANGE) {
      hit |= fieldAt(first, firstY, mx, my) | fieldAt(second, secondY, mx, my);
    }
    return hit || apply.down(mx, my) || back.down(mx, my);
  }

  private boolean fieldAt(Field field, float fy, float mx, float my) {
    if (mx >= x && mx < x + FIELD_W && my >= fy && my < fy + Field.HEIGHT) {
      field.focus(true);
      field.press(mx);
      return true;
    }
    return false;
  }

  void mouseUp(float mx, float my, boolean manage) {
    first.release();
    second.release();
    third.release();
    Vault.State state = hub.vaultState();
    if (state == Vault.State.NONE) {
      if (create.up(mx, my)) {
        char[] password = choice == Vault.Method.PASSWORD ? chars(first) : null;
        hub.setup(choice, password);
        reset();
      }
      return;
    }
    if (state == Vault.State.LOCKED) {
      if (unlock.up(mx, my)) {
        submitUnlock();
      }
      if (erase.up(mx, my)) {
        eraseNow();
      }
      return;
    }
    if (!manage) {
      return;
    }
    if (step == Step.HOME) {
      if (lock.up(mx, my)) {
        hub.lock();
        reset();
      }
      if (toPassword.up(mx, my) && hub.vault().method() == Vault.Method.DEVICE) {
        step = Step.TO_PASSWORD;
      }
      if (toDevice.up(mx, my) && hub.vault().method() == Vault.Method.PASSWORD) {
        step = Step.TO_DEVICE;
      }
      if (change.up(mx, my) && hub.vault().method() == Vault.Method.PASSWORD) {
        step = Step.CHANGE;
      }
      if (erase.up(mx, my)) {
        eraseNow();
      }
      if (back.up(mx, my)) {
        done.run();
      }
      return;
    }
    if (apply.up(mx, my)) {
      applyNow();
    }
    if (back.up(mx, my)) {
      reset();
    }
  }

  private void submitUnlock() {
    if (first.text().isEmpty() || hub.busy()) {
      return;
    }
    hub.unlock(chars(first));
    first.set("");
  }

  private void eraseNow() {
    if (!armed()) {
      armedAt = System.nanoTime();
      return;
    }
    reset();
    hub.reset();
  }

  private void applyNow() {
    switch (step) {
      case TO_PASSWORD -> hub.protect(Vault.Method.PASSWORD, null, chars(first));
      case TO_DEVICE -> hub.protect(Vault.Method.DEVICE, chars(third), null);
      case CHANGE -> hub.protect(Vault.Method.PASSWORD, chars(third), chars(first));
      case HOME -> {
        return;
      }
    }
    reset();
  }

  void mouseDrag(float mx) {
    for (Field field : new Field[] {first, second, third}) {
      if (field.focused()) {
        field.drag(mx);
      }
    }
  }

  boolean keyDown(int key, boolean control, boolean shift, boolean manage) {
    Field field =
        first.focused() ? first : second.focused() ? second : third.focused() ? third : null;
    if (field == null) {
      return false;
    }
    if (key == Field.ESCAPE) {
      field.focus(false);
      return true;
    }
    if (field.submits(key)) {
      Vault.State state = hub.vaultState();
      if (state == Vault.State.LOCKED) {
        submitUnlock();
      } else if (state == Vault.State.NONE && (choice == Vault.Method.DEVICE || passwordsOk())) {
        hub.setup(choice, choice == Vault.Method.PASSWORD ? chars(first) : null);
        reset();
      } else if (manage && step != Step.HOME) {
        applyNow();
      }
      return true;
    }
    field.key(key, control, shift);
    return true;
  }

  boolean charTyped(int codepoint) {
    return first.focused() && first.type(codepoint)
        || second.focused() && second.type(codepoint)
        || third.focused() && third.type(codepoint);
  }

  void focusDefault() {
    if (hub.vaultState() == Vault.State.LOCKED && hub.vault().method() == Vault.Method.PASSWORD) {
      first.focus(true);
    }
  }
}
