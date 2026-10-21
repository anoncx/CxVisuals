package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.menu.model.Control;
import me.cxiy.cxvisuals.menu.model.Glyph;
import me.cxiy.cxvisuals.menu.model.Pulse;
import me.cxiy.cxvisuals.menu.model.TitleSource;
import me.cxiy.cxvisuals.menu.nav.Section;
import me.cxiy.cxvisuals.modules.social.Account;

public final class Dock {
  public static final float HEIGHT = 64.0F;

  private static final float PAD = 12.0F;
  private static final float GAP = 10.0F;
  private static final float CHIP = 48.0F;
  private static final float CENTRE_GAP = 2.0F;
  private static final String HOME_LABEL = Lang.key("Menu");
  private static final String OPTIONS_LABEL = Lang.key("Options");
  private static final String QUIT_LABEL = Lang.key("Quit Game");

  private final AccountChip chip;
  private final DockButton home;
  private final DockButton options;
  private final DockButton quit;
  private final DockPlate plate = new DockPlate();
  private final LyricLine lyric = new LyricLine();
  private final Pulse pulse;
  private final Timekeeper clock = new Timekeeper();
  private TitleSource source;
  private Runnable goHome;
  private Control optionsControl;
  private Control quitControl;
  private Section section = Section.HOME;
  private float x;
  private float y;
  private float w;
  private float h;

  public Dock(Account account, Sfx sfx, Pulse pulse) {
    this.pulse = pulse;
    chip = new AccountChip(account);
    home = new DockButton(Glyph.HOME, sfx);
    options = new DockButton(Glyph.OPTIONS, sfx);
    quit = new DockButton(Glyph.QUIT, sfx);
  }

  public void bind(TitleSource next, Runnable homeAction) {
    goHome = homeAction;
    if (next != source) {
      source = next;
      reload();
    }
  }

  public void section(Section next) {
    section = next;
  }

  private void reload() {
    if (source == null) {
      return;
    }
    source.nav();
    optionsControl = source.options();
    quitControl = source.quit();
  }

  public void rest() {
    pulse.rest();
  }

  public boolean contains(float mx, float my) {
    return Kit.inside(mx, my, x, y, w, h);
  }

  public void draw(
      Canvas c, Theme t, float x, float y, float w, float h, float mouseX, float mouseY, float dt) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
    clock.update();
    String time = clock.time();
    String day = clock.day();
    plate.draw(c, t, x, y, w, h);
    float top = y + (h - DockButton.SIZE) * 0.5F;
    float left = x + PAD;
    if (section != Section.HOME) {
      home.draw(c, t, Lang.tr(HOME_LABEL), left, top, goHome != null, mouseX, mouseY, dt);
      left += DockButton.SIZE + GAP;
    }
    options.draw(
        c, t, Lang.tr(OPTIONS_LABEL), left, top, optionsControl != null, mouseX, mouseY, dt);
    float right = x + w - PAD - DockButton.SIZE;
    quit.draw(c, t, Lang.tr(QUIT_LABEL), right, top, quitControl != null, mouseX, mouseY, dt);
    chip.draw(
        c,
        t,
        source == null ? "" : source.player(),
        right - GAP - CHIP,
        top + (DockButton.SIZE - AccountChip.HEIGHT) * 0.5F,
        CHIP,
        0.0F,
        mouseX,
        mouseY,
        dt);
    float timeLine = c.lineHeight(FontStyle.DOTS);
    float dayLine = c.lineHeight(FontStyle.CAPTION);
    float stackTop = y + (h - timeLine - dayLine - CENTRE_GAP) * 0.5F + DockPlate.DIP * 0.5F;
    c.text(
        FontStyle.DOTS,
        time,
        x + (w - c.textWidth(FontStyle.DOTS, time)) * 0.5F,
        stackTop,
        t.ink());
    float captionY = stackTop + timeLine + CENTRE_GAP;
    lyric.update(pulse.lyric(), dt);
    float words = lyric.presence();
    if (words < 1.0F) {
      c.pushAlpha(1.0F - words);
      c.text(
          FontStyle.CAPTION,
          day,
          x + (w - c.textWidth(FontStyle.CAPTION, day)) * 0.5F,
          captionY,
          t.grey());
      c.popAlpha();
    }
    float margin = PAD + DockButton.SIZE + GAP + CHIP + GAP;
    lyric.draw(c, t, x + margin, captionY, w - margin * 2.0F);
  }

  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      return false;
    }
    if (section != Section.HOME && goHome != null && home.contains(mx, my)) {
      home.press();
    } else if (optionsControl != null && options.contains(mx, my)) {
      options.press();
    } else if (quitControl != null && quit.contains(mx, my)) {
      quit.press();
    } else {
      chip.mouseDown(mx, my);
    }
    return true;
  }

  public void mouseUp(float mx, float my) {
    if (home.release(mx, my)) {
      goHome.run();
      reload();
    } else if (options.release(mx, my)) {
      run(true);
    } else if (quit.release(mx, my)) {
      run(false);
    }
    chip.mouseUp(mx, my);
  }

  private void run(boolean wantOptions) {
    if (section != Section.HOME && goHome != null) {
      goHome.run();
      reload();
    }
    Control target = wantOptions ? optionsControl : quitControl;
    if (target != null && target.enabled()) {
      target.press();
    }
  }
}
