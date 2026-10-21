package me.cxiy.cxvisuals.gui.accounts;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Field;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Segmented;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.accounts.Entry;
import me.cxiy.cxvisuals.modules.accounts.Hub;
import me.cxiy.cxvisuals.modules.accounts.Ids;
import me.cxiy.cxvisuals.modules.accounts.Kind;
import me.cxiy.cxvisuals.modules.accounts.Microsoft;

final class AddPane {
  interface Host {
    void added(Entry entry);

    void leave();
  }

  private static final int MICROSOFT = 0;
  private static final int OFFLINE = 1;
  private static final int TOKEN = 2;
  private static final float DOT = 6.0F;

  private final Hub hub;
  private final Host host;
  private final Pick tab = new Pick(Lang.key("Microsoft"), Lang.key("Offline"), Lang.key("Token"));
  private final Segmented tabs = new Segmented(tab);
  private final Field name = new Field(Lang.key("Username"), Ids.maxName());
  private final Field token = new Field(Lang.key("Access token"), 2048).masked(true);
  private final Pill sign =
      new Pill(Lang.key("Sign in with Microsoft"), Pill.Tone.PRIMARY).minimum(190.0F);
  private final Pill copyCode = new Pill(Lang.key("Copy code"), Pill.Tone.QUIET);
  private final Pill openPage = new Pill(Lang.key("Open page"), Pill.Tone.QUIET);
  private final Pill cancel = new Pill(Lang.key("Cancel"), Pill.Tone.QUIET);
  private final Pill submit = new Pill(Lang.key("Add account"), Pill.Tone.PRIMARY).minimum(132.0F);
  private final Pill close = new Pill(Lang.key("Cancel"), Pill.Tone.QUIET);
  private final Set<String> before = new HashSet<>();
  private Canvas screen;
  private boolean watching;
  private boolean announced;
  private float clock;
  private float x;
  private float y;
  private float w;
  private float h;
  private float nameY;
  private float tokenY;
  private String problem = "";

  AddPane(Hub hub, Host host) {
    this.hub = hub;
    this.host = host;
  }

  void reset() {
    hub.cancelMicrosoft();
    watching = false;
    announced = false;
    problem = "";
    name.set("");
    token.set("");
    name.focus(false);
    token.focus(false);
  }

  boolean typing() {
    return name.focused() || token.focused();
  }

  void blurFields() {
    name.focus(false);
    token.focus(false);
  }

  private void remember() {
    before.clear();
    for (Entry entry : hub.book().all()) {
      before.add(entry.id);
    }
    watching = true;
  }

  private Entry fresh() {
    for (Entry entry : hub.book().all()) {
      if (!before.contains(entry.id)) {
        return entry;
      }
    }
    return null;
  }

  private void watch() {
    if (!watching || hub.busy() || hub.signing()) {
      return;
    }
    watching = false;
    Entry entry = fresh();
    if (entry != null) {
      reset();
      host.added(entry);
    }
  }

  void draw(
      Canvas c,
      Theme t,
      float px,
      float py,
      float pw,
      float ph,
      boolean closable,
      float mx,
      float my,
      float dt) {
    screen = c;
    x = px;
    y = py;
    w = pw;
    h = ph;
    clock += dt;
    watch();
    c.text(FontStyle.DISPLAY, Lang.tr("Add an account"), x, y - 2.0F, t.ink());
    if (closable) {
      close.draw(c, t, x + w - close.width(c), y + 2.0F, mx, my, dt);
    }
    float tabsY = y + c.lineHeight(FontStyle.DISPLAY) + 10.0F;
    tabs.measure(c);
    tabs.place(x, tabsY);
    tabs.draw(c, t, mx, my, dt);
    float top = tabsY + tabs.h + 24.0F;
    switch (tab.index()) {
      case MICROSOFT -> drawMicrosoft(c, t, top, mx, my, dt);
      case OFFLINE -> drawOffline(c, t, top, mx, my, dt);
      default -> drawToken(c, t, top, mx, my, dt);
    }
  }

  private void paragraph(Canvas c, Theme t, String text, float top, FontStyle font, int ink) {
    List<String> lines = Words.wrap(c, font, Lang.tr(text), w);
    float line = c.lineHeight(font) + 3.0F;
    for (int i = 0; i < lines.size(); i++) {
      c.text(font, lines.get(i), x, top + i * line, ink);
    }
  }

  private float paragraphHeight(Canvas c, String text, FontStyle font) {
    return Words.wrap(c, font, Lang.tr(text), w).size() * (c.lineHeight(font) + 3.0F);
  }

  private void drawMicrosoft(Canvas c, Theme t, float top, float mx, float my, float dt) {
    Microsoft.Code code = hub.code();
    if (!hub.signing()) {
      String text =
          Lang.key(
              "Sign in with the Microsoft account that owns Minecraft. You confirm on microsoft.com, so your password never reaches CxVisuals, and only a revocable sign-in is kept in your vault.");
      paragraph(c, t, text, top, FontStyle.SMALL, t.ink2());
      float rowY = top + paragraphHeight(c, text, FontStyle.SMALL) + 18.0F;
      sign.draw(c, t, x, rowY, mx, my, dt);
      return;
    }
    if (code == null) {
      String line = Lang.tr("Asking Microsoft for a login code");
      c.text(FontStyle.ROW, line, x, top, t.ink());
      waiting(
          c,
          t,
          x + c.textWidth(FontStyle.ROW, line) + 14.0F,
          top + c.lineHeight(FontStyle.ROW) * 0.5F);
      cancel.draw(c, t, x, top + 44.0F, mx, my, dt);
      return;
    }
    if (!announced) {
      announced = true;
      if (hub.module().copyCode.get()) {
        c.clipboardText(code.user());
      }
      if (hub.module().openBrowser.get()) {
        hub.open(code.link());
      }
    }
    c.text(FontStyle.CAPTION, Lang.tr("Your login code"), x, top, t.grey());
    float codeY = top + c.lineHeight(FontStyle.CAPTION) + 6.0F;
    c.text(FontStyle.HERO, code.user(), x, codeY, t.ink());
    float after = codeY + c.lineHeight(FontStyle.HERO) + 10.0F;
    String steps =
        Lang.key(
            "Enter the code on the page that just opened. If it did not open, use the button below.");
    paragraph(c, t, steps, after, FontStyle.SMALL, t.ink2());
    float rowY = after + paragraphHeight(c, steps, FontStyle.SMALL) + 18.0F;
    float copyW = copyCode.width(c);
    float openW = openPage.width(c);
    copyCode.draw(c, t, x, rowY, copyW, mx, my, dt);
    openPage.draw(c, t, x + copyW + 8.0F, rowY, openW, mx, my, dt);
    cancel.draw(c, t, x + copyW + openW + 16.0F, rowY, mx, my, dt);
    String waitingText = Lang.tr("Waiting for you to finish");
    float waitY = rowY + Pill.HEIGHT + 22.0F;
    c.text(FontStyle.CAPTION, waitingText, x, waitY, t.grey());
    waiting(
        c,
        t,
        x + c.textWidth(FontStyle.CAPTION, waitingText) + 12.0F,
        waitY + c.lineHeight(FontStyle.CAPTION) * 0.5F);
  }

  private void waiting(Canvas c, Theme t, float cx, float cy) {
    for (int i = 0; i < 3; i++) {
      float wave = 0.5F + 0.5F * (float) Math.sin(clock * 5.0F - i * 0.9F);
      int ink = i == 0 ? Theme.mix(t.ink(), t.red(), wave) : t.ink();
      c.roundRect(
          cx + i * (DOT + 5.0F),
          cy - DOT * 0.5F,
          DOT,
          DOT,
          DOT * 0.5F,
          Theme.withAlpha(ink, 0.25F + 0.75F * wave));
    }
  }

  private void drawOffline(Canvas c, Theme t, float top, float mx, float my, float dt) {
    String text =
        Lang.key(
            "An offline account is just a name. It has no skin and only joins servers that run in offline mode.");
    paragraph(c, t, text, top, FontStyle.SMALL, t.ink2());
    float fieldY = top + paragraphHeight(c, text, FontStyle.SMALL) + 16.0F;
    nameY = fieldY;
    name.draw(c, t, x, fieldY, Math.min(w, 360.0F), dt);
    String typed = name.text().strip();
    boolean valid = Ids.validName(typed);
    if (!typed.isEmpty() && !valid) {
      problem =
          Lang.format(
              "Use {0} to {1} letters, digits or underscores.", Ids.minName(), Ids.maxName());
    } else {
      problem = "";
    }
    float noteY = fieldY + Field.HEIGHT + 8.0F;
    if (!problem.isEmpty()) {
      c.text(FontStyle.CAPTION, problem, x + 4.0F, noteY, t.alert());
    }
    submit
        .enabled(valid)
        .draw(c, t, x, noteY + c.lineHeight(FontStyle.CAPTION) + 14.0F, mx, my, dt);
  }

  private void drawToken(Canvas c, Theme t, float top, float mx, float my, float dt) {
    String text =
        Lang.key(
            "Paste a Minecraft access token. It stays hidden, goes straight into your vault, and expires after about a day.");
    paragraph(c, t, text, top, FontStyle.SMALL, t.ink2());
    float fieldY = top + paragraphHeight(c, text, FontStyle.SMALL) + 16.0F;
    tokenY = fieldY;
    token.draw(c, t, x, fieldY, Math.min(w, 360.0F), dt);
    float rowY = fieldY + Field.HEIGHT + 16.0F;
    submit.enabled(!token.text().isBlank() && !hub.busy()).draw(c, t, x, rowY, mx, my, dt);
    c.text(
        FontStyle.CAPTION,
        hub.busy() ? Lang.tr("Checking the token") : "",
        x + submit.w + 14.0F,
        rowY + (Pill.HEIGHT - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        t.grey());
  }

  private void submitOffline() {
    String error = hub.addOffline(name.text());
    if (error != null) {
      hub.say(error, Hub.Tone.BAD);
      return;
    }
    Entry added = null;
    for (Entry entry : hub.book().all()) {
      if (entry.username.equals(name.text().strip()) && entry.kind() == Kind.OFFLINE) {
        added = entry;
      }
    }
    reset();
    host.added(added);
  }

  private void submitToken() {
    remember();
    hub.addToken(token.take());
  }

  boolean mouseDown(float mx, float my) {
    name.focus(false);
    token.focus(false);
    if (close.down(mx, my) || tabs.mouseDown(mx, my)) {
      return true;
    }
    switch (tab.index()) {
      case MICROSOFT -> {
        if (hub.signing()) {
          return copyCode.down(mx, my) || openPage.down(mx, my) || cancel.down(mx, my);
        }
        return sign.down(mx, my);
      }
      case OFFLINE -> {
        if (mx >= x && mx < x + Math.min(w, 360.0F) && my >= nameY && my < nameY + Field.HEIGHT) {
          name.focus(true);
          name.press(mx);
          return true;
        }
        return submit.down(mx, my);
      }
      default -> {
        if (mx >= x && mx < x + Math.min(w, 360.0F) && my >= tokenY && my < tokenY + Field.HEIGHT) {
          token.focus(true);
          token.press(mx);
          return true;
        }
        return submit.down(mx, my);
      }
    }
  }

  void mouseUp(float mx, float my) {
    name.release();
    token.release();
    tabs.mouseUp(mx, my);
    if (close.up(mx, my)) {
      host.leave();
      return;
    }
    switch (tab.index()) {
      case MICROSOFT -> {
        if (hub.signing()) {
          if (copyCode.up(mx, my) && hub.code() != null) {
            screen.clipboardText(hub.code().user());
          }
          if (openPage.up(mx, my) && hub.code() != null) {
            hub.open(hub.code().link());
          }
          if (cancel.up(mx, my)) {
            hub.cancelMicrosoft();
            announced = false;
          }
        } else if (sign.up(mx, my)) {
          remember();
          announced = false;
          hub.beginMicrosoft();
        }
      }
      case OFFLINE -> {
        if (submit.up(mx, my)) {
          submitOffline();
        }
      }
      default -> {
        if (submit.up(mx, my)) {
          submitToken();
        }
      }
    }
  }

  void mouseDrag(float mx) {
    if (name.focused()) {
      name.drag(mx);
    }
    if (token.focused()) {
      token.drag(mx);
    }
  }

  boolean keyDown(int key, boolean control, boolean shift) {
    Field field = name.focused() ? name : token.focused() ? token : null;
    if (field == null) {
      return false;
    }
    if (key == Field.ESCAPE) {
      field.focus(false);
      return true;
    }
    if (field.submits(key)) {
      if (field == name && Ids.validName(name.text().strip())) {
        submitOffline();
      } else if (field == token && !token.text().isBlank()) {
        submitToken();
      }
      return true;
    }
    field.key(key, control, shift);
    return true;
  }

  boolean charTyped(int codepoint) {
    return name.focused() && name.type(codepoint) || token.focused() && token.type(codepoint);
  }

  void focusFirst() {
    if (tab.index() == OFFLINE) {
      name.focus(true);
    } else if (tab.index() == TOKEN) {
      token.focus(true);
    }
  }
}
