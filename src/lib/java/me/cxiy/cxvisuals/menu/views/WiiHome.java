package me.cxiy.cxvisuals.menu.views;

import java.util.List;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.menu.model.Pulse;
import me.cxiy.cxvisuals.menu.model.QuickPlay;
import me.cxiy.cxvisuals.menu.model.TextStore;
import me.cxiy.cxvisuals.menu.model.TitleSource;

public final class WiiHome extends Page {
  private static final int COLUMNS = Channels.COLUMNS;
  private static final int ROWS = Channels.ROWS;
  private static final int PER_PAGE = Channels.PER_PAGE;
  private static final int RIGHT = 262;
  private static final int LEFT = 263;
  private static final int DOWN = 264;
  private static final int UP = 265;
  private static final int DELETE = 261;
  private static final int BACKSPACE = 259;
  private static final int NONE = -1;
  private static final float GAP = 14.0F;
  private static final float ASPECT = 1.8F;
  private static final float MIN_TILE = 40.0F;
  private static final float SLOP = 10.0F;
  private static final float PAGE_SLOP = 14.0F;
  private static final float WAVE_SPEED = 7.0F;
  private static final float HOVER_GAP = 0.14F;
  private static final float PEEK = 0.45F;
  private static final float EDGE_ZONE = 0.7F;
  private static final float EDGE_HOLD = 0.55F;
  private static final float IDLE_AFTER = 6.0F;
  private static final float IDLE_RAMP = 3.0F;
  private static final float BREATH_SPEED = 1.3F;
  private static final float BREATH_SPREAD = 0.5F;
  private static final float TARGET_GROW = 3.0F;
  private static final int STAGGER_DIVISOR = 2;

  private final Channels channels;
  private final Sfx sfx;
  private final Pulse pulse;
  private final Telemetry telemetry = new Telemetry();
  private final Pager pager = new Pager();
  private final LaunchCard card = new LaunchCard();
  private final WidgetPicker picker = new WidgetPicker();
  private final ChannelTile.Look look = new ChannelTile.Look();
  private final Anim[] hovers = new Anim[PER_PAGE];
  private int selected = NONE;
  private int pressed = NONE;
  private int heard = NONE;
  private int lifted = NONE;
  private int removing = NONE;
  private boolean dragging;
  private boolean armed;
  private float lastTick = -HOVER_GAP;
  private float idle;
  private float lastMouseX;
  private float lastMouseY;
  private float pressX;
  private float pressY;
  private float dragX;
  private float dragY;
  private float grabX;
  private float grabY;
  private float edge;
  private float gridX;
  private float gridY;
  private float tileW;
  private float tileH;
  private float travel;

  public WiiHome(TitleSource source, QuickPlay quick, Sfx sfx, TextStore store, Pulse pulse) {
    this.channels = new Channels(source, quick, store);
    this.sfx = sfx;
    this.pulse = pulse;
    for (int i = 0; i < PER_PAGE; i++) {
      hovers[i] = Anim.hover(0.0F);
    }
  }

  public int pages() {
    return channels.pages();
  }

  public int page() {
    return pager.page();
  }

  public boolean launching() {
    return card.active();
  }

  @Override
  public boolean plated() {
    return false;
  }

  @Override
  public void measure(Canvas c, float roomW, float roomH) {
    wantW = roomW;
    wantH = roomH;
  }

  private float tileX(int slot) {
    return gridX + slot % COLUMNS * (tileW + GAP);
  }

  private float tileY(int slot) {
    return gridY + slot / COLUMNS * (tileH + GAP);
  }

  private void layout() {
    float availW = pw - Pager.ROOM * 2.0F;
    float availH = ph - Pager.FOOT;
    tileW =
        Math.max(
            MIN_TILE,
            Math.min(
                (availW - GAP * (COLUMNS - 1)) / COLUMNS,
                (availH - GAP * (ROWS - 1)) / ROWS * ASPECT));
    tileH = tileW / ASPECT;
    float gridW = tileW * COLUMNS + GAP * (COLUMNS - 1);
    float gridH = tileH * ROWS + GAP * (ROWS - 1);
    gridX = px + (pw - gridW) * 0.5F;
    gridY = py + (availH - gridH) * 0.5F;
    travel = gridW + GAP;
    pager.place(px, pw, gridY + gridH * 0.5F);
    card.place(px, py, pw, ph);
    picker.place(px, py, pw, ph);
  }

  private boolean overRemove(int index, float mx, float my) {
    int slot = index % PER_PAGE;
    return ChannelTile.overRemove(mx, my, tileX(slot), tileY(slot), tileW);
  }

  private boolean busy() {
    return card.active() || picker.active();
  }

  private int rawSlotAt(float mx, float my) {
    if (frozen || busy()) {
      return NONE;
    }
    int base = pager.page() * PER_PAGE;
    for (int slot = 0; slot < PER_PAGE; slot++) {
      if (Kit.inside(mx, my, tileX(slot), tileY(slot), tileW, tileH)) {
        return base + slot;
      }
    }
    return NONE;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    age += dt;
    telemetry.update();
    if (mouseX != lastMouseX || mouseY != lastMouseY) {
      lastMouseX = mouseX;
      lastMouseY = mouseY;
      idle = 0.0F;
    }
    idle += dt;
    if (channels.poll(dt)) {
      selected = selected >= channels.size() ? NONE : selected;
      pager.select(pager.page(), channels.pages());
    }
    layout();
    pager.update(dt);
    Channel done = card.arrived();
    if (done != null && done.enabled) {
      done.start.run();
    }
    int hover = lifted != NONE || dragging ? NONE : rawSlotAt(mouseX, mouseY);
    int lit = hover != NONE ? hover : selected;
    listen(hover != NONE && !channels.get(hover).empty() ? hover : NONE);
    if (lifted != NONE) {
      edgeFlip(dt);
    }
    float breath = Math.max(0.0F, Math.min(1.0F, (idle - IDLE_AFTER) / IDLE_RAMP));
    c.pushClip(px, py, pw, ph);
    float position = pager.position();
    int first = (int) Math.floor(position);
    for (int owner = Math.max(0, first - 1);
        owner <= first + 2 && owner < channels.pages();
        owner++) {
      float distance = Math.abs(owner - position);
      if (distance >= 2.0F) {
        continue;
      }
      tiles(
          c,
          t,
          owner,
          (owner - position) * travel,
          Math.max(1.0F - distance, PEEK),
          hover,
          lit,
          breath,
          mouseX,
          mouseY,
          dt);
    }
    if (lifted != NONE) {
      carry(c, t);
    }
    c.popClip();
    pager.draw(c, t, mouseX, mouseY, channels.pages(), !busy() && lifted == NONE, dt);
    pager.dots(c, t, px, py + ph - Pager.FOOT * 0.5F, pw, channels.pages());
    card.draw(c, t, mouseX, mouseY, dt);
    picker.draw(c, t, mouseX, mouseY, dt);
  }

  private void listen(int lit) {
    if (lit != heard && lit != NONE && !busy() && age - lastTick >= HOVER_GAP) {
      lastTick = age;
      sfx.hover(lit % COLUMNS);
    }
    heard = lit;
  }

  private void edgeFlip(float dt) {
    float zone = Pager.ROOM * EDGE_ZONE;
    int side = dragX < px + zone ? -1 : dragX > px + pw - zone ? 1 : 0;
    if (side == 0) {
      edge = 0.0F;
      return;
    }
    edge += dt;
    if (edge >= EDGE_HOLD) {
      edge = 0.0F;
      turn(side);
    }
  }

  private void turn(int side) {
    int before = pager.page();
    pager.step(side, channels.pages());
    chime(before);
  }

  private void chime(int before) {
    if (pager.page() != before) {
      sfx.turn();
    }
  }

  private void tiles(
      Canvas c,
      Theme t,
      int owner,
      float shift,
      float visible,
      int hover,
      int lit,
      float breath,
      float mouseX,
      float mouseY,
      float dt) {
    boolean current = owner == pager.page();
    for (int slot = 0; slot < PER_PAGE; slot++) {
      int index = owner * PER_PAGE + slot;
      float left = tileX(slot) + shift;
      if (left + tileW < px || left > px + pw) {
        continue;
      }
      Channel channel = channels.get(index);
      Anim anim = hovers[slot];
      if (current) {
        anim.set(index == hover || index == lit && hover == NONE && lifted == NONE ? 1.0F : 0.0F);
        anim.update(dt);
      }
      float glow = current ? anim.get() : 0.0F;
      look.glow = channel.empty() ? 0.0F : glow;
      look.add = channel.empty() && current && lifted == NONE && !busy() ? glow : 0.0F;
      look.alpha =
          Math.min(visible, Kit.reveal(age, slot / STAGGER_DIVISOR))
              * (1.0F - card.hidden(channel));
      look.wave = glow > 0.5F && !channel.empty() ? age * WAVE_SPEED : Pictograms.NO_WAVE;
      look.breathe =
          breath <= 0.0F
              ? 0.0F
              : breath
                  * (0.5F
                      + 0.5F
                          * (float)
                              Math.sin(
                                  age * BREATH_SPEED
                                      - (slot % COLUMNS + slot / COLUMNS) * BREATH_SPREAD));
      look.down = pressed == index && lifted == NONE;
      look.focus = current && selected == index && hover == NONE && !busy();
      look.removable = channel.widget != null;
      look.removeHover =
          look.removable
              && index == hover
              && ChannelTile.overRemove(mouseX, mouseY, tileX(slot) + shift, tileY(slot), tileW);
      look.ghost = index == lifted;
      ChannelTile.draw(
          c, t, channel, telemetry, pulse, tileX(slot) + shift, tileY(slot), tileW, tileH, look);
    }
    if (lifted != NONE && current) {
      int target = rawSlotAt(dragX, dragY);
      if (target != NONE && target != lifted && !channels.pinned(target)) {
        int slot = target % PER_PAGE;
        c.roundRectBorder(
            tileX(slot) + shift - TARGET_GROW,
            tileY(slot) - TARGET_GROW,
            tileW + TARGET_GROW * 2.0F,
            tileH + TARGET_GROW * 2.0F,
            ChannelTile.RADIUS + TARGET_GROW,
            1.5F,
            t.ink());
      }
    }
  }

  private void carry(Canvas c, Theme t) {
    look.carried();
    ChannelTile.draw(
        c,
        t,
        channels.get(lifted),
        telemetry,
        pulse,
        dragX - grabX,
        dragY - grabY,
        tileW,
        tileH,
        look);
  }

  private void launch(int index) {
    Channel channel = index == NONE ? null : channels.get(index);
    if (channel == null || !channel.launchable()) {
      return;
    }
    int slot = index % PER_PAGE;
    selected = index;
    sfx.press();
    card.show(channel, tileX(slot), tileY(slot), tileW, tileH, hasOther(index));
  }

  private boolean hasOther(int from) {
    for (int i = 0; i < channels.size(); i++) {
      if (i != from && channels.get(i).launchable()) {
        return true;
      }
    }
    return false;
  }

  private void activate(int index, float mx, float my) {
    Channel channel = channels.get(index);
    if (channel.empty()) {
      add(index);
    } else if (channel.widget != null) {
      int slot = index % PER_PAGE;
      if (channel.widget.press(pulse, mx - tileX(slot), my - tileY(slot), tileW, tileH)) {
        sfx.press();
      }
    } else {
      launch(index);
    }
  }

  private void browse(int step) {
    Channel shown = card.channel();
    int index = NONE;
    for (int i = 0; i < channels.size(); i++) {
      if (channels.get(i) == shown) {
        index = i;
      }
    }
    for (int hop = 1; hop <= channels.size() && index != NONE; hop++) {
      int next = Math.floorMod(index + step * hop, channels.size());
      if (channels.get(next).launchable()) {
        selected = next;
        pager.select(next / PER_PAGE, channels.pages());
        card.browse(channels.get(next), step);
        sfx.turn();
        return;
      }
    }
  }

  private void start() {
    if (card.channel() != null && card.channel().enabled) {
      sfx.launch();
      card.depart();
    } else {
      dismiss();
    }
  }

  private void dismiss() {
    sfx.back();
    card.close();
  }

  private void add(int index) {
    List<Widget> open = channels.available();
    if (index != NONE && channels.get(index).empty() && !open.isEmpty()) {
      sfx.press();
      picker.show(index, open);
    }
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (frozen) {
      return false;
    }
    idle = 0.0F;
    if (picker.interactive()) {
      picker.press(mx, my);
      return true;
    }
    if (card.interactive()) {
      card.press(mx, my);
      return true;
    }
    if (busy()) {
      return true;
    }
    int arrow = pager.hit(mx, my, channels.pages());
    if (arrow != Pager.NONE) {
      pager.press(arrow);
      return true;
    }
    int index = rawSlotAt(mx, my);
    pressX = mx;
    pressY = my;
    dragging = false;
    pressed = index;
    removing = NONE;
    if (index != NONE && channels.get(index).widget != null && overRemove(index, mx, my)) {
      removing = index;
      pressed = NONE;
    }
    armed = index == NONE;
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    idle = 0.0F;
    if (lifted != NONE) {
      dragX = mx;
      dragY = my;
      return;
    }
    float moved = (float) Math.hypot(mx - pressX, my - pressY);
    if (pressed != NONE && !busy() && moved > SLOP && channels.movable(pressed)) {
      lifted = pressed;
      int slot = lifted % PER_PAGE;
      grabX = pressX - tileX(slot);
      grabY = pressY - tileY(slot);
      dragX = mx;
      dragY = my;
      pressed = NONE;
      edge = 0.0F;
      sfx.hover(0);
      return;
    }
    if (!armed || busy() || channels.pages() < 2) {
      return;
    }
    float across = mx - pressX;
    if (!dragging && Math.abs(across) > PAGE_SLOP) {
      dragging = true;
    }
    if (dragging) {
      pager.drag(across / travel, channels.pages());
    }
  }

  @Override
  public void mouseUp(float mx, float my) {
    idle = 0.0F;
    if (picker.interactive()) {
      finishPicker(picker.release(mx, my));
      return;
    }
    if (card.interactive()) {
      finishCard(card.release(mx, my));
      return;
    }
    boolean wasArmed = armed;
    armed = false;
    if (lifted != NONE) {
      drop(mx, my);
      return;
    }
    if (dragging) {
      dragging = false;
      int before = pager.page();
      pager.drop(channels.pages());
      chime(before);
      return;
    }
    int arrow = pager.release();
    if (arrow != Pager.NONE) {
      if (pager.hit(mx, my, channels.pages()) == arrow) {
        pager.step(arrow, channels.pages());
        sfx.turn();
      }
      return;
    }
    int index = pressed;
    int doomed = removing;
    pressed = NONE;
    removing = NONE;
    if (doomed != NONE && rawSlotAt(mx, my) == doomed && channels.remove(doomed)) {
      sfx.back();
    } else if (index != NONE && rawSlotAt(mx, my) == index) {
      activate(index, mx, my);
    } else if (wasArmed && rawSlotAt(mx, my) == NONE) {
      selected = NONE;
    }
  }

  private void finishPicker(WidgetPicker.Result result) {
    if (result == WidgetPicker.Result.CHOSEN) {
      channels.place(picker.slot(), picker.chosen().id());
      picker.close();
      sfx.launch();
    } else if (result == WidgetPicker.Result.CANCEL) {
      picker.close();
      sfx.back();
    }
  }

  private void finishCard(int action) {
    if (action == LaunchCard.START) {
      start();
    } else if (action == LaunchCard.BACK) {
      dismiss();
    } else if (action == LaunchCard.PREVIOUS) {
      browse(-1);
    } else if (action == LaunchCard.NEXT) {
      browse(1);
    }
  }

  private void drop(float mx, float my) {
    int from = lifted;
    lifted = NONE;
    edge = 0.0F;
    int target = rawSlotAt(mx, my);
    if (target != NONE && channels.move(from, target)) {
      selected = target;
      sfx.press();
    } else {
      sfx.back();
    }
  }

  @Override
  public boolean scroll(float mx, float my, float amount) {
    if (busy() || channels.pages() < 2 || !Kit.inside(mx, my, px, py, pw, ph)) {
      return false;
    }
    turn(amount > 0.0F ? -1 : 1);
    return true;
  }

  @Override
  public boolean keyDown(int keyCode, int scanCode, int modifiers) {
    if (frozen) {
      return false;
    }
    idle = 0.0F;
    boolean confirm = keyCode == Kit.ENTER || keyCode == Kit.KEYPAD_ENTER;
    int dx = keyCode == RIGHT ? 1 : keyCode == LEFT ? -1 : 0;
    int dy = keyCode == DOWN ? 1 : keyCode == UP ? -1 : 0;
    if (picker.interactive()) {
      if (keyCode == Kit.ESCAPE) {
        finishPicker(WidgetPicker.Result.CANCEL);
      } else if (confirm) {
        finishPicker(picker.confirm());
      } else {
        picker.move(dx, dy);
      }
      return true;
    }
    if (card.active()) {
      if (card.interactive() && keyCode == Kit.ESCAPE) {
        dismiss();
      } else if (card.interactive() && confirm) {
        start();
      } else if (card.interactive() && dx != 0) {
        browse(dx);
      }
      return true;
    }
    if (confirm) {
      if (selected == NONE) {
        return false;
      }
      int slot = selected % PER_PAGE;
      activate(selected, tileX(slot) + tileW * 0.5F, tileY(slot) + tileH * 0.5F);
      return true;
    }
    if ((keyCode == DELETE || keyCode == BACKSPACE)
        && selected != NONE
        && channels.remove(selected)) {
      sfx.back();
      return true;
    }
    int step = dx + dy * COLUMNS;
    if (step == 0) {
      return false;
    }
    selected =
        selected == NONE
            ? pager.page() * PER_PAGE
            : Math.max(0, Math.min(channels.size() - 1, selected + step));
    int before = pager.page();
    pager.select(selected / PER_PAGE, channels.pages());
    chime(before);
    return true;
  }
}
