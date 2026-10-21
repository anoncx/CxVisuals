package me.cxiy.cxvisuals.gui.accounts;

import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Presence;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.WindowFrame;
import me.cxiy.cxvisuals.modules.accounts.Entry;
import me.cxiy.cxvisuals.modules.accounts.Hub;
import me.cxiy.cxvisuals.modules.accounts.Vault;

public final class AccountsView {
  public static final int KEY_ESCAPE = 256;
  public static final int KEY_ENTER = 257;
  public static final int KEY_DELETE = 261;
  public static final int KEY_DOWN = 264;
  public static final int KEY_UP = 265;
  public static final int KEY_F = 70;
  public static final int KEY_N = 78;

  private static final float WIDTH = 880.0F;
  private static final float HEIGHT = 540.0F;
  private static final float MARGIN = 16.0F;
  private static final float PAD = 20.0F;
  private static final float HEADER = 46.0F;
  private static final float LIST = 300.0F;
  private static final float GAP = 22.0F;
  private static final float CLOSE = 26.0F;
  private static final float NOTICE_GAP = 16.0F;
  private static final long NOTICE_NANOS = 4_500_000_000L;

  private enum Right {
    DETAIL,
    ADD,
    SECURITY
  }

  private final Hub hub;
  private final Supplier<WindowMotion> motion;
  private final Presence presence = new Presence();
  private final WindowFrame frame = new WindowFrame();
  private final Anim closeHover = Anim.hover(0.0F);
  private final Anim notice = Anim.state(0.0F);
  private final ListPane list;
  private final DetailPane detail;
  private final AddPane add;
  private final VaultPane vault;
  private final Pill security = new Pill(Lang.key("Security"), Pill.Tone.QUIET).height(28.0F);
  private Canvas screen;
  private Right right = Right.DETAIL;
  private float screenW = WIDTH;
  private float screenH = HEIGHT;
  private float fit = 1.0F;
  private float originX;
  private float originY;
  private float noticeLeft;
  private float noticeRight;
  private boolean closing;
  private long noticeAt;
  private Hub.Status shown;

  public AccountsView(Hub hub, Supplier<WindowMotion> motion) {
    this.hub = hub;
    this.motion = motion;
    this.list =
        new ListPane(
            hub,
            new ListPane.Host() {
              @Override
              public void select(String id) {
                choose(id);
              }

              @Override
              public void use(Entry entry) {
                choose(entry.id);
                detail.request(entry);
              }

              @Override
              public void add() {
                startAdd();
              }
            });
    this.detail = new DetailPane(hub, this::close);
    this.add =
        new AddPane(
            hub,
            new AddPane.Host() {
              @Override
              public void added(Entry entry) {
                right = Right.DETAIL;
                if (entry != null) {
                  choose(entry.id);
                }
              }

              @Override
              public void leave() {
                right = Right.DETAIL;
                add.reset();
              }
            });
    this.vault = new VaultPane(hub, () -> right = Right.DETAIL);
    presence.snap(0.0F);
  }

  public void open() {
    closing = false;
    presence.snap(0.0F);
    right = Right.DETAIL;
    hub.start();
    hub.describeVault();
    Entry active = hub.active();
    list.selected(active == null ? "" : active.id);
    vault.reset();
    add.reset();
    vault.focusDefault();
  }

  public void close() {
    closing = true;
    hub.cancelMicrosoft();
  }

  public boolean closed() {
    return closing && presence.gone();
  }

  public void resize(float width, float height) {
    screenW = width;
    screenH = height;
    fit =
        Math.max(
            0.1F,
            Math.min(
                1.0F,
                Math.min((width - MARGIN * 2.0F) / WIDTH, (height - MARGIN * 2.0F) / HEIGHT)));
    originX = (width - WIDTH) * 0.5F;
    originY = (height - HEIGHT) * 0.5F;
  }

  private float localX(float mx) {
    return screenW * 0.5F + (mx - screenW * 0.5F) / fit;
  }

  private float localY(float my) {
    return screenH * 0.5F + (my - screenH * 0.5F) / fit;
  }

  private void choose(String id) {
    list.selected(id);
    detail.bind(hub.book().find(id));
  }

  private void startAdd() {
    right = Right.ADD;
    add.reset();
    add.focusFirst();
  }

  private Entry current() {
    Entry entry = hub.book().find(list.selected());
    if (entry == null && !list.rows().isEmpty()) {
      entry = list.rows().get(0);
      list.selected(entry.id);
    }
    return entry;
  }

  public void draw(Canvas c, Theme t, float rawX, float rawY, float dt) {
    screen = c;
    float mx = localX(rawX);
    float my = localY(rawY);
    WindowMotion style = motion.get();
    presence.step(style, !closing, dt);
    if (presence.phase() < 0.01F) {
      return;
    }
    if (!frame.open(
        c, t, presence, style, originX, originY, WIDTH, HEIGHT, screenW, screenH, fit)) {
      return;
    }
    content(c, t, mx, my, dt);

    frame.close(c);
  }

  private void content(Canvas c, Theme t, float mx, float my, float dt) {
    float left = originX + PAD;
    float top = originY + PAD;
    float bodyTop = top + HEADER;
    float bodyHeight = originY + HEIGHT - PAD - bodyTop;
    header(c, t, left, top, mx, my, dt);
    Vault.State state = hub.vaultState();
    if (state != Vault.State.OPEN) {
      vault.draw(
          c, t, left, bodyTop + 8.0F, WIDTH - PAD * 2.0F, bodyHeight - 8.0F, false, mx, my, dt);
      notice(c, t, dt);
      return;
    }
    if (hub.book().size() == 0 && right == Right.DETAIL) {
      right = Right.ADD;
    }
    float rightX = left + LIST + GAP;
    float rightW = originX + WIDTH - PAD - rightX;
    list.draw(c, t, left, bodyTop, LIST, bodyHeight, mx, my, dt);
    c.roundRect(rightX - GAP * 0.5F - 0.5F, bodyTop, 1.0F, bodyHeight, 0.5F, t.hair());
    Entry entry = current();
    detail.bind(entry);
    switch (right) {
      case ADD ->
          add.draw(c, t, rightX, bodyTop, rightW, bodyHeight, hub.book().size() > 0, mx, my, dt);
      case SECURITY -> vault.draw(c, t, rightX, bodyTop, rightW, bodyHeight, true, mx, my, dt);
      default -> detail.draw(c, t, rightX, bodyTop, rightW, bodyHeight, mx, my, dt);
    }
    notice(c, t, dt);
  }

  private void header(Canvas c, Theme t, float left, float top, float mx, float my, float dt) {
    String title = Lang.tr("Accounts").toUpperCase(Lang.locale());
    c.text(
        FontStyle.DOTS,
        title,
        left,
        top + (HEADER - 14.0F - c.lineHeight(FontStyle.DOTS)) * 0.5F,
        t.ink());
    noticeLeft = left + c.textWidth(FontStyle.DOTS, title) + NOTICE_GAP;
    float closeX = originX + WIDTH - PAD - CLOSE;
    noticeRight = closeX - NOTICE_GAP;
    float closeY = top + 1.0F;
    boolean over = mx >= closeX && mx < closeX + CLOSE && my >= closeY && my < closeY + CLOSE;
    closeHover.set(over ? 1.0F : 0.0F);
    closeHover.update(dt);
    c.roundRect(
        closeX,
        closeY,
        CLOSE,
        CLOSE,
        CLOSE * 0.5F,
        Theme.mix(t.surface2(), t.ink(), 0.12F * closeHover.get()));
    String glyph = "×";
    c.text(
        FontStyle.ROW,
        glyph,
        closeX + (CLOSE - c.textWidth(FontStyle.ROW, glyph)) * 0.5F,
        closeY + (CLOSE - c.lineHeight(FontStyle.ROW)) * 0.5F - 1.0F,
        t.ink());
    if (hub.vaultState() == Vault.State.OPEN) {
      security.label(right == Right.SECURITY ? Lang.key("Done") : Lang.key("Security"));
      float width = security.width(c);
      security.draw(c, t, closeX - 10.0F - width, closeY - 1.0F, width, mx, my, dt);
      noticeRight = closeX - 10.0F - width - NOTICE_GAP;
    }
  }

  private void notice(Canvas c, Theme t, float dt) {
    Hub.Status status = hub.status();
    if (status != null && status != shown) {
      shown = status;
      noticeAt = System.nanoTime();
    }
    boolean live = status != null && System.nanoTime() - noticeAt < NOTICE_NANOS;
    notice.set(live ? 1.0F : 0.0F);
    notice.update(dt);
    if (notice.get() < 0.01F || shown == null) {
      return;
    }
    String text = shown.text();
    float textW = c.textWidth(FontStyle.CAPTION, text);
    float span = noticeRight - noticeLeft;
    float width = Math.min(span, textW + 42.0F);
    float height = 28.0F;
    float px = noticeLeft + (span - width) * 0.5F;
    float py = originY + PAD + 4.0F - (1.0F - notice.get()) * 8.0F;
    c.pushAlpha(notice.get());
    c.shadow(px, py, width, height, height * 0.5F, 12.0F, 4.0F, t.cardShadow());
    Depth.raised(c, t, px, py, width, height, height * 0.5F, t.surface());
    int tone =
        shown.tone() == Hub.Tone.GOOD
            ? 0xFF3DDC5A
            : shown.tone() == Hub.Tone.BAD ? t.alert() : t.grey();
    c.roundRect(px + 12.0F, py + (height - 6.0F) * 0.5F, 6.0F, 6.0F, 3.0F, tone);
    c.pushClip(px + 24.0F, py, width - 32.0F, height);
    c.text(
        FontStyle.CAPTION,
        text,
        px + 26.0F,
        py + (height - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        t.ink());
    c.popClip();
    c.popAlpha();
  }

  public boolean outside(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    return mx < originX || mx >= originX + WIDTH || my < originY || my >= originY + HEIGHT;
  }

  public boolean mouseDown(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    float closeX = originX + WIDTH - PAD - CLOSE;
    float closeY = originY + PAD + 1.0F;
    if (mx >= closeX && mx < closeX + CLOSE && my >= closeY && my < closeY + CLOSE) {
      close();
      return true;
    }
    Vault.State state = hub.vaultState();
    if (state != Vault.State.OPEN) {
      vault.mouseDown(mx, my, false);
      return mx >= originX && mx < originX + WIDTH && my >= originY && my < originY + HEIGHT;
    }
    if (security.down(mx, my)) {
      return true;
    }
    boolean handled = list.mouseDown(mx, my);
    if (!handled) {
      switch (right) {
        case ADD -> handled = add.mouseDown(mx, my);
        case SECURITY -> handled = vault.mouseDown(mx, my, true);
        default -> handled = detail.mouseDown(mx, my);
      }
    } else {
      add.blurFields();
    }
    return handled
        || mx >= originX && mx < originX + WIDTH && my >= originY && my < originY + HEIGHT;
  }

  public void mouseUp(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    Vault.State state = hub.vaultState();
    if (state != Vault.State.OPEN) {
      vault.mouseUp(mx, my, false);
      return;
    }
    if (security.up(mx, my)) {
      if (right == Right.SECURITY) {
        right = Right.DETAIL;
      } else {
        right = Right.SECURITY;
        vault.reset();
      }
      return;
    }
    list.mouseUp(mx, my);
    switch (right) {
      case ADD -> add.mouseUp(mx, my);
      case SECURITY -> vault.mouseUp(mx, my, true);
      default -> detail.mouseUp(screen, mx, my);
    }
  }

  public void mouseDrag(float rawX) {
    float mx = localX(rawX);
    list.mouseDrag(mx);
    add.mouseDrag(mx);
    vault.mouseDrag(mx);
  }

  public void scroll(float rawX, float rawY, float amount) {
    list.scroll(localX(rawX), localY(rawY), amount);
  }

  public boolean keyDown(int key, boolean control, boolean shift) {
    Vault.State state = hub.vaultState();
    if (state != Vault.State.OPEN) {
      if (vault.keyDown(key, control, shift, false)) {
        return true;
      }
      if (key == KEY_ESCAPE) {
        close();
        return true;
      }
      return false;
    }
    if (vault.typing() && vault.keyDown(key, control, shift, true)
        || add.typing() && add.keyDown(key, control, shift)
        || list.typing() && list.keyDown(key, control, shift)) {
      return true;
    }
    if (control && key == KEY_F) {
      right = right == Right.SECURITY ? Right.DETAIL : right;
      list.focusSearch();
      return true;
    }
    if (control && key == KEY_N) {
      startAdd();
      return true;
    }
    switch (key) {
      case KEY_ESCAPE -> {
        if (detail.confirming()) {
          detail.cancel();
        } else if (right != Right.DETAIL && hub.book().size() > 0) {
          right = Right.DETAIL;
          add.reset();
        } else {
          close();
        }
      }
      case KEY_DOWN -> list.move(1);
      case KEY_UP -> list.move(-1);
      case KEY_ENTER -> {
        Entry entry = current();
        if (entry != null && right == Right.DETAIL) {
          detail.request(entry);
        }
      }
      case KEY_DELETE -> {
        Entry entry = current();
        if (entry != null && right == Right.DETAIL) {
          detail.remove(entry);
        }
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  public boolean charTyped(int codepoint) {
    if (hub.vaultState() != Vault.State.OPEN) {
      return vault.charTyped(codepoint);
    }
    return vault.charTyped(codepoint) || add.charTyped(codepoint) || list.charTyped(codepoint);
  }

  public boolean typing() {
    return vault.typing() || add.typing() || list.typing();
  }
}
