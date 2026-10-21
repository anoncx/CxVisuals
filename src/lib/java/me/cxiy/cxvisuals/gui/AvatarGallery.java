package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class AvatarGallery extends Widget {
  public static final float GAP = 10.0F;

  private static final float STAGE = 196.0F;
  private static final float STAGE_RADIUS = 16.0F;
  private static final float STAGE_INSET = 14.0F;
  private static final float MIN_CARD = 92.0F;
  private static final float LABEL = 24.0F;
  private static final float CARD_RADIUS = 14.0F;
  private static final float PREVIEW_INSET = 6.0F;
  private static final float SPIN = 0.35F;
  private static final float DRAG_TURN = 0.012F;
  private static final float FRICTION = 3.2F;
  private static final float CARD_YAW = -0.62F;
  private static final float TOAST_TIME = 4.0F;
  private static final float MENU_BUTTON = 24.0F;
  private static final float BADGE = 6.0F;
  private static final int PULSE_DOTS = 5;

  private final Gallery gallery;
  private final PanelHost host;
  private final String tunePage;
  private final List<Anim> hover = new ArrayList<>();
  private final List<Anim> lit = new ArrayList<>();
  private final Anim stageSwap = Anim.pop(1.0F);
  private final Anim menu = Anim.reveal(0.0F);
  private final Anim toast = Anim.move(0.0F);
  private int columns = 1;
  private float card;
  private float yaw;
  private float spin;
  private float dragged;
  private boolean dragging;
  private float dragX;
  private int pressed = -2;
  private int menuCard = -1;
  private int menuShown = -1;
  private int menuPressed;
  private int shownSelection = Integer.MIN_VALUE;
  private int noticeSeen;
  private String toastText = "";
  private float toastAge = TOAST_TIME;
  private float clock;

  public AvatarGallery(Gallery gallery, PanelHost host, String tunePage) {
    this.gallery = gallery;
    this.host = host;
    this.tunePage = tunePage;
    this.noticeSeen = gallery.noticeId();
    this.h = STAGE;
  }

  public int cards() {
    return gallery.count() + 2;
  }

  public int columns() {
    return columns;
  }

  public float stageHeight() {
    return STAGE;
  }

  public float cardWidth() {
    return card;
  }

  public float cardHeight() {
    return card + LABEL;
  }

  public float cardX(int index) {
    return x + (index % columns) * (card + GAP);
  }

  public float cardY(int index) {
    return y + STAGE + GAP + (index / columns) * (cardHeight() + GAP);
  }

  public float yaw() {
    return yaw;
  }

  public boolean menuOpen() {
    return menuCard >= 0;
  }

  public float menuX() {
    return cardX(menuCard) + card * 0.5F;
  }

  public float menuTuneY() {
    return menuTop() + MENU_BUTTON * 0.5F;
  }

  public float menuDeleteY() {
    return menuTop() + MENU_BUTTON + 6.0F + MENU_BUTTON * 0.5F;
  }

  private float menuTop() {
    return cardY(menuCard) + (cardHeight() - MENU_BUTTON * 2.0F - 6.0F) * 0.5F;
  }

  private void layout() {
    int count = cards();
    columns = Math.max(1, (int) ((w + GAP) / (MIN_CARD + GAP)));
    card = (w - GAP * (columns - 1)) / columns;
    int rows = (count + columns - 1) / columns;
    h = STAGE + GAP + rows * cardHeight() + (rows - 1) * GAP;
    while (hover.size() < count) {
      hover.add(Anim.state(0.0F));
      lit.add(Anim.reveal(0.0F));
    }
    if (menuCard >= count - 1) {
      menuCard = -1;
    }
  }

  @Override
  public float measure(Canvas c) {
    layout();
    return w;
  }

  private static float clamp01(float value) {
    return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    layout();
    clock += dt;
    int selection = gallery.selected();
    if (selection != shownSelection) {
      if (shownSelection != Integer.MIN_VALUE) {
        stageSwap.snap(0.0F);
        stageSwap.set(1.0F);
      }
      shownSelection = selection;
    }
    stageSwap.update(dt);
    if (gallery.noticeId() != noticeSeen) {
      noticeSeen = gallery.noticeId();
      toastText = gallery.notice();
      toastAge = 0.0F;
    }
    toastAge += dt;
    toast.set(toastAge < TOAST_TIME && !toastText.isEmpty() ? 1.0F : 0.0F);
    toast.update(dt);
    menu.set(menuCard >= 0 ? 1.0F : 0.0F);
    menu.update(dt);

    if (dragging) {
      float velocity = dt > 0.0F ? dragged / dt : 0.0F;
      spin = velocity;
      yaw += dragged;
      dragged = 0.0F;
    } else {
      spin += (SPIN - spin) * (1.0F - (float) Math.exp(-dt * FRICTION));
      yaw += spin * dt;
    }

    drawStage(c, t, selection, dt);
    int count = cards();
    for (int i = 0; i < count; i++) {
      drawCard(c, t, i, selection, mouseX, mouseY, dt);
    }
  }

  private void drawStage(Canvas c, Theme t, int selection, float dt) {
    float sw = w;
    Depth.inset(c, t, x, y, sw, STAGE, STAGE_RADIUS, t.well());
    c.pushClip(x, y, sw, STAGE);
    Stage.floor(c, t, x, y, sw, STAGE);
    float pop = stageSwap.get();
    float settle = clamp01(pop);
    boolean ready =
        selection >= 0 && selection < gallery.count() && gallery.state(selection) == Gallery.READY;
    float shadowW = sw * 0.34F;
    float floorY = y + STAGE * 0.84F;
    c.pushAlpha(settle);
    c.shadow(
        x + (sw - shadowW) * 0.5F,
        floorY - 4.0F,
        shadowW,
        8.0F,
        4.0F,
        14.0F,
        0.0F,
        ready ? 0x66000000 : 0x00000000);
    c.popAlpha();
    if (ready) {
      float size = STAGE - 16.0F;
      float scale = 0.9F + 0.1F * pop;
      c.pushTransform(scale, x + sw * 0.5F, floorY);
      c.pushAlpha(settle);
      c.model(
          gallery.handle(selection),
          x + (sw - size * 1.6F) * 0.5F,
          floorY + size * 0.1F - size,
          size * 1.6F,
          size,
          yaw,
          true,
          Canvas.AUDITION_NONE);
      c.popAlpha();
      c.popTransform();
    } else if (selection >= 0 && selection < gallery.count()) {
      pulse(c, t, x + sw * 0.5F, y + STAGE * 0.5F, 6.0F);
    }

    String title =
        selection >= 0 && selection < gallery.count()
            ? gallery.name(selection).toUpperCase(Locale.ROOT)
            : Lang.tr("OFF");
    c.pushAlpha(settle);
    c.text(FontStyle.DOTS, title, x + STAGE_INSET, y + STAGE_INSET - 2.0F, t.ink());
    String meta =
        selection >= 0 && selection < gallery.count()
            ? gallery.meta(selection)
            : Lang.tr("VANILLA PLAYER");
    float metaY = y + STAGE_INSET + c.lineHeight(FontStyle.DOTS) + 2.0F;
    float metaX = x + STAGE_INSET;
    if (selection >= 0 && selection < gallery.count() && gallery.animated(selection)) {
      float pulse = 0.65F + 0.35F * (float) Math.sin(clock * 3.0F);
      c.roundRect(
          metaX,
          metaY + (c.lineHeight(FontStyle.MONO) - BADGE) * 0.5F,
          BADGE,
          BADGE,
          BADGE * 0.5F,
          Theme.withAlpha(t.red(), pulse));
      metaX += BADGE + 6.0F;
    }
    c.text(FontStyle.MONO, meta, metaX, metaY, t.grey());
    c.popAlpha();

    float shown = clamp01(toast.get());
    if (shown > 0.004F) {
      float tw = Math.min(sw - 28.0F, c.textWidth(FontStyle.SMALL, toastText) + 28.0F);
      float th = 28.0F;
      float tx = x + (sw - tw) * 0.5F;
      float ty = y + STAGE - th - 12.0F + (1.0F - shown) * 10.0F;
      c.pushAlpha(shown);
      c.shadow(tx, ty, tw, th, th * 0.5F, 12.0F, 4.0F, t.shadow());
      Depth.raised(c, t, tx, ty, tw, th, th * 0.5F, t.surface2());
      c.roundRect(tx + 12.0F, ty + (th - BADGE) * 0.5F, BADGE, BADGE, BADGE * 0.5F, t.red());
      c.pushClip(tx, ty, tw - 10.0F, th);
      c.text(
          FontStyle.SMALL, toastText, tx + 24.0F, centered(c, FontStyle.SMALL, ty, th), t.ink2());
      c.popClip();
      c.popAlpha();
    }
    c.popClip();
  }

  private void pulse(Canvas c, Theme t, float cx, float cy, float spacing) {
    float half = (PULSE_DOTS - 1) * spacing * 0.5F;
    for (int row = 0; row < PULSE_DOTS; row++) {
      for (int col = 0; col < PULSE_DOTS; col++) {
        int order = row * PULSE_DOTS + col;
        float wave = 0.5F + 0.5F * (float) Math.sin(clock * 6.0F - order * 0.45F);
        int color = Theme.mix(t.dotOff(), t.ink2(), wave * wave);
        c.roundRect(
            cx - half + col * spacing - 1.25F,
            cy - half + row * spacing - 1.25F,
            2.5F,
            2.5F,
            1.25F,
            color);
      }
    }
  }

  private void drawCard(
      Canvas c, Theme t, int index, int selection, float mouseX, float mouseY, float dt) {
    float cx = cardX(index);
    float cy = cardY(index);
    float ch = cardHeight();
    int last = cards() - 1;
    int avatar = index - 1;
    boolean isImport = index == last;
    boolean isOff = index == 0;
    boolean selected = isOff ? selection < 0 : !isImport && avatar == selection;
    boolean over =
        mouseX >= cx && mouseX < cx + card && mouseY >= cy && mouseY < cy + ch && menuCard < 0;
    Anim hovered = hover.get(index);
    Anim chosen = lit.get(index);
    hovered.set(over ? 1.0F : 0.0F);
    chosen.set(selected ? 1.0F : 0.0F);
    hovered.update(dt);
    chosen.update(dt);
    float lift = hovered.get();
    float on = clamp01(chosen.get());

    c.pushTransform(1.0F + 0.03F * lift, cx + card * 0.5F, cy + ch * 0.5F);
    if (lift > 0.01F || on > 0.01F) {
      c.shadow(
          cx,
          cy,
          card,
          ch,
          CARD_RADIUS,
          16.0F,
          6.0F,
          Theme.withAlpha(t.shadow(), Math.max(lift, on) * 0.9F));
    }
    int base = Theme.mix(t.surface2(), t.raised(), on * 0.35F);
    if (isImport) {
      Depth.inset(
          c, t, cx, cy, card, ch, CARD_RADIUS, Theme.mix(t.well(), t.surface2(), lift * 0.6F));
      dottedRing(c, t, cx + 5.0F, cy + 5.0F, card - 10.0F, ch - 10.0F, CARD_RADIUS - 4.0F, lift);
      float arm = 16.0F;
      float bar = 2.5F;
      float mx = cx + card * 0.5F;
      float my = cy + card * 0.5F;
      int ink = Theme.mix(t.grey(), t.ink(), lift);
      c.roundRect(mx - arm * 0.5F, my - bar * 0.5F, arm, bar, bar * 0.5F, ink);
      c.roundRect(mx - bar * 0.5F, my - arm * 0.5F, bar, arm, bar * 0.5F, ink);
      label(c, t, Lang.tr("Import"), cx, cy, Theme.mix(t.grey(), t.ink(), lift));
    } else {
      Depth.raised(c, t, cx, cy, card, ch, CARD_RADIUS, base);
      float inner = card - PREVIEW_INSET * 2.0F;
      Depth.inset(
          c, t, cx + PREVIEW_INSET, cy + PREVIEW_INSET, inner, inner, CARD_RADIUS - 5.0F, t.well());
      if (isOff) {
        float tw = c.textWidth(FontStyle.DOTS, Lang.tr("OFF"));
        c.text(
            FontStyle.DOTS,
            Lang.tr("OFF"),
            cx + (card - tw) * 0.5F,
            cy + PREVIEW_INSET + (inner - c.lineHeight(FontStyle.DOTS)) * 0.5F,
            Theme.mix(t.grey(), t.ink(), Math.max(on, lift)));
        label(c, t, Lang.tr("Player"), cx, cy, Theme.mix(t.grey(), t.ink(), Math.max(on, lift)));
      } else {
        int state = gallery.state(avatar);
        float px = cx + PREVIEW_INSET;
        float py = cy + PREVIEW_INSET;
        if (state == Gallery.READY) {
          float spinYaw = CARD_YAW + (lift > 0.01F ? clock * 1.2F : 0.0F);
          c.pushClip(px, py, inner, inner);
          c.model(
              gallery.handle(avatar),
              px + 4.0F,
              py + 4.0F,
              inner - 8.0F,
              inner - 8.0F,
              spinYaw,
              over,
              Canvas.AUDITION_NONE);
          c.popClip();
        } else if (state == Gallery.BUSY) {
          pulse(c, t, px + inner * 0.5F, py + inner * 0.5F, inner / 9.0F);
        } else {
          float bang = 3.0F;
          float mx = px + inner * 0.5F;
          float my = py + inner * 0.5F;
          c.roundRect(mx - bang * 0.5F, my - 12.0F, bang, 15.0F, bang * 0.5F, t.red());
          c.roundRect(mx - bang * 0.5F, my + 6.0F, bang, bang, bang * 0.5F, t.red());
        }
        String name = gallery.name(avatar);
        int ink =
            state == Gallery.BROKEN ? t.grey() : Theme.mix(t.ink2(), t.ink(), Math.max(on, lift));
        label(c, t, name, cx, cy, ink);
      }
      if (on > 0.01F) {
        c.roundRect(
            cx + card - PREVIEW_INSET - BADGE - 6.0F,
            cy + PREVIEW_INSET + 6.0F,
            BADGE,
            BADGE,
            BADGE * 0.5F,
            Theme.withAlpha(t.red(), on));
      }
    }
    c.popTransform();

    if (index == menuCard || (menuCard < 0 && index == menuShown && menu.get() > 0.01F)) {
      drawMenu(c, t, index, mouseX, mouseY);
    }
  }

  private void drawMenu(Canvas c, Theme t, int index, float mouseX, float mouseY) {
    float shown = clamp01(menu.get());
    if (shown <= 0.004F) {
      return;
    }
    float cx = cardX(index);
    float cy = cardY(index);
    float ch = cardHeight();
    c.pushAlpha(shown);
    c.roundRect(cx, cy, card, ch, CARD_RADIUS, Theme.withAlpha(t.paper(), 0.78F));
    float bw = card - 16.0F;
    float top = cy + (ch - MENU_BUTTON * 2.0F - 6.0F) * 0.5F;
    String[] labels = {Lang.tr("Tune"), Lang.tr("Delete")};
    for (int i = 0; i < 2; i++) {
      float by = top + i * (MENU_BUTTON + 6.0F) + (1.0F - shown) * 6.0F;
      float bx = cx + 8.0F;
      boolean over = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + MENU_BUTTON;
      int fill = Theme.mix(t.surface2(), t.raised(), over ? 0.5F : 0.0F);
      Depth.raised(c, t, bx, by, bw, MENU_BUTTON, MENU_BUTTON * 0.5F, fill);
      String text = labels[i];
      float tw = c.textWidth(FontStyle.LABEL, text);
      c.text(
          FontStyle.LABEL,
          text,
          bx + (bw - tw) * 0.5F,
          centered(c, FontStyle.LABEL, by, MENU_BUTTON),
          i == 1 ? t.alert() : t.ink());
    }
    c.popAlpha();
  }

  private void label(Canvas c, Theme t, String text, float cx, float cy, int ink) {
    String shown = c.fit(FontStyle.LABEL, text, card - 12.0F);
    float tw = c.textWidth(FontStyle.LABEL, shown);
    float top = cy + card;
    c.text(
        FontStyle.LABEL,
        shown,
        cx + (card - tw) * 0.5F,
        centered(c, FontStyle.LABEL, top - 2.0F, LABEL),
        ink);
  }

  private void dottedRing(
      Canvas c, Theme t, float rx, float ry, float rw, float rh, float radius, float lift) {
    int color = Theme.mix(t.dotOff(), t.grey(), 0.4F + 0.6F * lift);
    float step = 7.0F;
    float straightW = rw - radius * 2.0F;
    float straightH = rh - radius * 2.0F;
    float arc = (float) (Math.PI * 0.5 * radius);
    float perimeter = 2.0F * (straightW + straightH) + 4.0F * arc;
    int dots = Math.max(8, (int) (perimeter / step));
    float spacing = perimeter / dots;
    float shift = clock * 6.0F * lift;
    for (int i = 0; i < dots; i++) {
      float d = (i * spacing + shift) % perimeter;
      float px;
      float py;
      if (d < straightW) {
        px = rx + radius + d;
        py = ry;
      } else if ((d -= straightW) < arc) {
        double angle = -Math.PI * 0.5 + d / radius;
        px = rx + rw - radius + (float) Math.cos(angle) * radius;
        py = ry + radius + (float) Math.sin(angle) * radius;
      } else if ((d -= arc) < straightH) {
        px = rx + rw;
        py = ry + radius + d;
      } else if ((d -= straightH) < arc) {
        double angle = d / radius;
        px = rx + rw - radius + (float) Math.cos(angle) * radius;
        py = ry + rh - radius + (float) Math.sin(angle) * radius;
      } else if ((d -= arc) < straightW) {
        px = rx + rw - radius - d;
        py = ry + rh;
      } else if ((d -= straightW) < arc) {
        double angle = Math.PI * 0.5 + d / radius;
        px = rx + radius + (float) Math.cos(angle) * radius;
        py = ry + rh - radius + (float) Math.sin(angle) * radius;
      } else if ((d -= arc) < straightH) {
        px = rx;
        py = ry + rh - radius - d;
      } else {
        d -= straightH;
        double angle = Math.PI + d / radius;
        px = rx + radius + (float) Math.cos(angle) * radius;
        py = ry + radius + (float) Math.sin(angle) * radius;
      }
      c.roundRect(px - 1.0F, py - 1.0F, 2.0F, 2.0F, 1.0F, color);
    }
  }

  private int cardAt(float mx, float my) {
    if (columns <= 0 || card <= 0.0F || my < y + STAGE + GAP) {
      return -1;
    }
    for (int i = 0; i < cards(); i++) {
      float cx = cardX(i);
      float cy = cardY(i);
      if (mx >= cx && mx < cx + card && my >= cy && my < cy + cardHeight()) {
        return i;
      }
    }
    return -1;
  }

  private boolean onStage(float mx, float my) {
    return mx >= x && mx < x + w && my >= y && my < y + STAGE;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (menuCard >= 0) {
      menuPressed = menuButton(mx, my);
      if (menuPressed < 0) {
        closeMenu();
      }
      pressed = -3;
      return contains(mx, my) || menuPressed >= 0;
    }
    if (onStage(mx, my)) {
      dragging = true;
      dragX = mx;
      dragged = 0.0F;
      pressed = -1;
      return true;
    }
    int hit = cardAt(mx, my);
    if (hit < 0) {
      return false;
    }
    pressed = hit;
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (dragging) {
      dragged += (mx - dragX) * DRAG_TURN;
      dragX = mx;
    }
  }

  @Override
  public void mouseUp(float mx, float my) {
    if (dragging) {
      dragging = false;
      yaw += dragged;
      dragged = 0.0F;
      pressed = -2;
      return;
    }
    if (pressed == -3) {
      pressed = -2;
      if (menuPressed >= 0 && menuButton(mx, my) == menuPressed) {
        int avatar = menuCard - 1;
        closeMenu();
        if (menuPressed == 0) {
          gallery.select(avatar);
          host.openTab(tunePage);
        } else {
          gallery.delete(avatar);
        }
      }
      menuPressed = -1;
      return;
    }
    int hit = cardAt(mx, my);
    int was = pressed;
    pressed = -2;
    if (was < 0 || hit != was) {
      return;
    }
    int last = cards() - 1;
    if (was == 0) {
      gallery.select(-1);
    } else if (was == last) {
      gallery.requestImport();
    } else {
      gallery.select(was - 1);
    }
  }

  @Override
  public boolean secondaryDown(float mx, float my) {
    int hit = cardAt(mx, my);
    if (hit <= 0 || hit >= cards() - 1) {
      if (menuCard >= 0) {
        closeMenu();
        return true;
      }
      return false;
    }
    menuCard = hit;
    menuShown = hit;
    menuPressed = -1;
    return true;
  }

  private void closeMenu() {
    menuShown = menuCard;
    menuCard = -1;
  }

  private int menuButton(float mx, float my) {
    if (menuCard < 0) {
      return -1;
    }
    float bx = cardX(menuCard) + 8.0F;
    float bw = card - 16.0F;
    if (mx < bx || mx >= bx + bw) {
      return -1;
    }
    float top = menuTop();
    for (int i = 0; i < 2; i++) {
      float by = top + i * (MENU_BUTTON + 6.0F);
      if (my >= by && my < by + MENU_BUTTON) {
        return i;
      }
    }
    return -1;
  }
}
