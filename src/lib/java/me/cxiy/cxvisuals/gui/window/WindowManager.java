package me.cxiy.cxvisuals.gui.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Sound;
import me.cxiy.cxvisuals.gui.Theme;

public final class WindowManager {
  private static final float GAP = 12.0F;
  private static final float MARGIN = 16.0F;
  private static final float SNAP = 14.0F;
  private static final float CASCADE = 32.0F;
  private static final float PUSH_RESPONSE = 16.0F;
  private static final float PUSH_SETTLED = 0.4F;
  private static final float COLUMN_OVERLAP = 0.4F;
  private static final int FREE = -1;

  private final List<FloatingWindow> windows = new ArrayList<>();
  private final List<FloatingWindow> view = Collections.unmodifiableList(windows);
  private final FloatingWindow.Look look = new FloatingWindow.Look();
  private int[] column = new int[0];
  private int[] order = new int[0];
  private float[] goals = new float[0];
  private boolean across;
  private FloatingWindow focused;

  public List<FloatingWindow> windows() {
    return view;
  }

  public void across(boolean on) {
    across = on;
  }

  public boolean empty() {
    return windows.isEmpty();
  }

  public boolean live() {
    for (int i = 0; i < windows.size(); i++) {
      if (!windows.get(i).closing()) {
        return true;
      }
    }
    return false;
  }

  public FloatingWindow find(String id) {
    for (int i = 0; i < windows.size(); i++) {
      FloatingWindow window = windows.get(i);
      if (window.id().equals(id) && !window.closing()) {
        return window;
      }
    }
    return null;
  }

  public void raise(FloatingWindow window) {
    if (windows.remove(window)) {
      windows.add(window);
    }
    focused = window;
  }

  public void open(FloatingWindow window, Rect avoid, Canvas c, float screenW, float screenH) {
    windows.add(window);
    focused = window;
    place(window, windows.size() - 1, avoid, c, screenW, screenH);
  }

  public void relayout(Rect avoid, Canvas c, float screenW, float screenH) {
    for (int i = 0; i < windows.size(); i++) {
      place(windows.get(i), i, avoid, c, screenW, screenH);
    }
  }

  public void closeAll() {
    for (int i = 0; i < windows.size(); i++) {
      windows.get(i).dismiss();
    }
  }

  public void blur() {
    for (int i = 0; i < windows.size(); i++) {
      windows.get(i).blur();
    }
    focused = null;
  }

  public boolean owns(float rawX, float rawY) {
    return topmost(rawX, rawY) >= 0;
  }

  private int topmost(float rawX, float rawY) {
    for (int i = windows.size() - 1; i >= 0; i--) {
      if (windows.get(i).hit(rawX, rawY)) {
        return i;
      }
    }
    return -1;
  }

  public void draw(
      Canvas c,
      Theme t,
      WindowMotion style,
      boolean spatial,
      float strength,
      float screenW,
      float screenH,
      float rawX,
      float rawY,
      float dt) {
    int hovered = topmost(rawX, rawY);
    flow(screenW, screenH, dt);
    for (int i = 0; i < windows.size(); i++) {
      FloatingWindow window = windows.get(i);
      if (!window.dragging()) {
        window.moveTo(clampX(window, window.x(), screenW), clampY(window, window.y(), screenH));
      }
      look.set(window == focused, i == hovered, spatial, strength);
      window.draw(c, t, style, look, screenW, screenH, rawX, rawY, dt);
    }
    for (int i = windows.size() - 1; i >= 0; i--) {
      FloatingWindow window = windows.get(i);
      if (window.gone()) {
        windows.remove(i);
        if (focused == window) {
          focused = null;
        }
      }
    }
  }

  private void flow(float screenW, float screenH, float dt) {
    int count = windows.size();
    if (column.length < count) {
      column = new int[count * 2];
      order = new int[count * 2];
      goals = new float[count * 2];
    }
    int columns = 0;
    for (int i = 0; i < count; i++) {
      FloatingWindow window = windows.get(i);
      column[i] = FREE;
      if (window.closing() || window.dragging()) {
        continue;
      }
      for (int j = 0; j < i && column[i] == FREE; j++) {
        if (column[j] != FREE && sameColumn(windows.get(j), window)) {
          column[i] = column[j];
        }
      }
      if (column[i] == FREE) {
        column[i] = columns++;
      }
    }
    float limitW = screenW - MARGIN * 2.0F;
    float limitH = screenH - MARGIN * 2.0F;
    for (int i = 0; i < count; i++) {
      if (column[i] == FREE) {
        windows.get(i).fit(limitW, limitH, dt);
      }
    }
    for (int id = 0; id < columns; id++) {
      stack(id, count, limitW, limitH, dt);
    }
  }

  private void stack(int id, int count, float limitW, float limitH, float dt) {
    int members = 0;
    for (int i = 0; i < count; i++) {
      if (column[i] == id) {
        int at = members++;
        while (at > 0 && windows.get(order[at - 1]).y() > windows.get(i).y()) {
          order[at] = order[at - 1];
          at--;
        }
        order[at] = i;
      }
    }
    for (int m = 0; m < members; m++) {
      goals[m] = -1.0F;
    }
    float room = limitH - GAP * (members - 1);
    float remaining = room;
    int left = members;
    float used = 0.0F;
    for (int turn = 0; turn < members; turn++) {
      int pick = -1;
      float least = Float.MAX_VALUE;
      for (int m = 0; m < members; m++) {
        if (goals[m] >= 0.0F) {
          continue;
        }
        float wanted = windows.get(order[m]).wanted(limitH);
        if (wanted < least) {
          least = wanted;
          pick = m;
        }
      }
      float share = remaining / left;
      float take = Math.min(least, share);
      goals[pick] = Math.max(1.0F, take);
      remaining -= take;
      left--;
    }
    for (int m = 0; m < members; m++) {
      FloatingWindow window = windows.get(order[m]);
      float height = goals[m];
      window.fit(limitW, height, dt);
      used += window.height();
    }
    used += GAP * (members - 1);
    float top = windows.get(order[0]).y();
    float cursor = Math.max(MARGIN, Math.min(top, limitH + MARGIN - used));
    for (int m = 0; m < members; m++) {
      FloatingWindow window = windows.get(order[m]);
      float next =
          window.y() + (cursor - window.y()) * (1.0F - (float) Math.exp(-dt * PUSH_RESPONSE));
      window.moveTo(window.x(), Math.abs(cursor - next) < PUSH_SETTLED ? cursor : next);
      cursor += window.height() + GAP;
    }
  }

  private static boolean sameColumn(FloatingWindow a, FloatingWindow b) {
    return sameColumn(a, b.x(), b.width());
  }

  private static boolean sameColumn(FloatingWindow a, float x, float width) {
    float overlap = Math.min(a.x() + a.width(), x + width) - Math.max(a.x(), x);
    return overlap > Math.min(a.width(), width) * COLUMN_OVERLAP;
  }

  public boolean mouseDown(float rawX, float rawY) {
    int index = topmost(rawX, rawY);
    if (index < 0) {
      blur();
      return false;
    }
    FloatingWindow window = windows.get(index);
    for (int other = 0; other < windows.size(); other++) {
      if (windows.get(other) != window) {
        windows.get(other).blur();
      }
    }
    raise(window);
    window.mouseDown(rawX, rawY);
    return true;
  }

  public boolean mouseUp(float rawX, float rawY) {
    boolean dropped = false;
    for (int i = 0; i < windows.size(); i++) {
      FloatingWindow window = windows.get(i);
      dropped |= window.dragging();
      window.mouseUp(rawX, rawY);
    }
    return dropped;
  }

  public void mouseDrag(float rawX, float rawY, Rect avoid, float screenW, float screenH) {
    for (int i = 0; i < windows.size(); i++) {
      FloatingWindow window = windows.get(i);
      if (window.dragging()) {
        window.project(rawX, rawY);
        window.markMoved();
        snap(window, window.dragX(), window.dragY(), avoid, screenW, screenH);
      } else {
        window.mouseDrag(rawX, rawY);
      }
    }
  }

  public boolean scroll(float rawX, float rawY, float amount) {
    int index = topmost(rawX, rawY);
    if (index < 0) {
      return false;
    }
    windows.get(index).scroll(rawX, rawY, amount);
    return true;
  }

  public boolean secondaryDown(float rawX, float rawY) {
    int index = topmost(rawX, rawY);
    if (index < 0) {
      return false;
    }
    windows.get(index).secondaryDown(rawX, rawY);
    return true;
  }

  public boolean typing() {
    return focused != null && !focused.closing() && focused.typing();
  }

  public boolean closeFocused() {
    if (focused == null || focused.closing()) {
      return false;
    }
    focused.close();
    focused = null;
    return true;
  }

  public boolean keyDown(int keyCode, int scanCode, boolean control, boolean shift) {
    return focused != null && focused.keyDown(keyCode, scanCode, control, shift);
  }

  public boolean charTyped(int codepoint) {
    return focused != null && focused.charTyped(codepoint);
  }

  private float clampX(FloatingWindow window, float x, float screenW) {
    return Math.max(MARGIN, Math.min(screenW - window.width() - MARGIN, x));
  }

  private float clampY(FloatingWindow window, float y, float screenH) {
    return Math.max(MARGIN, Math.min(screenH - window.height() - MARGIN, y));
  }

  private void snap(
      FloatingWindow window, float x, float y, Rect avoid, float screenW, float screenH) {
    float w = window.width();
    float h = window.height();
    float snappedX = clampX(window, x, screenW);
    float snappedY = clampY(window, y, screenH);
    float bestX = SNAP;
    float bestY = SNAP;
    float[] columns = {
      MARGIN,
      screenW - w - MARGIN,
      avoid.right() + GAP,
      avoid.x() - GAP - w,
      avoid.x(),
      avoid.right() - w
    };
    float[] rows = {MARGIN, screenH - h - MARGIN, avoid.y(), avoid.bottom() - h};
    for (float anchor : columns) {
      float gap = Math.abs(anchor - snappedX);
      if (gap < bestX) {
        bestX = gap;
        snappedX = anchor;
      }
    }
    for (float anchor : rows) {
      float gap = Math.abs(anchor - snappedY);
      if (gap < bestY) {
        bestY = gap;
        snappedY = anchor;
      }
    }
    for (int i = 0; i < windows.size(); i++) {
      FloatingWindow other = windows.get(i);
      if (other == window || other.closing()) {
        continue;
      }
      float[] sideways = {
        other.x() + other.width() + GAP,
        other.x() - GAP - w,
        other.x(),
        other.x() + other.width() - w
      };
      float[] vertical = {
        other.y(),
        other.y() + other.height() - h,
        other.y() + other.height() + GAP,
        other.y() - GAP - h
      };
      for (float anchor : sideways) {
        float gap = Math.abs(anchor - x);
        if (gap < bestX) {
          bestX = gap;
          snappedX = anchor;
        }
      }
      for (float anchor : vertical) {
        float gap = Math.abs(anchor - y);
        if (gap < bestY) {
          bestY = gap;
          snappedY = anchor;
        }
      }
    }
    boolean locked = bestX < SNAP || bestY < SNAP;
    if (locked && !window.snapped()) {
      Sound.play(Sound.Cue.STEP);
    }
    window.snapped(locked);
    window.moveTo(clampX(window, snappedX, screenW), clampY(window, snappedY, screenH));
  }

  private void place(
      FloatingWindow window, int index, Rect avoid, Canvas c, float screenW, float screenH) {
    if (across && placeAcross(window, index, avoid, c, screenW, screenH)) {
      return;
    }
    float rightRoom = screenW - MARGIN - (avoid.right() + GAP);
    float leftRoom = avoid.x() - GAP - MARGIN;
    boolean preferRight = rightRoom >= leftRoom || rightRoom >= window.preferredWidth();
    float minimum = window.minimumWidth();
    if (rightRoom >= minimum && leftRoom >= minimum) {
      preferRight = onSide(index, avoid, true) <= onSide(index, avoid, false);
    }
    if (placeBeside(window, index, avoid, c, screenW, screenH, preferRight)
        || placeBeside(window, index, avoid, c, screenW, screenH, !preferRight)) {
      return;
    }
    window.resize(window.preferredWidth());
    arrange(window, c, screenW, screenH);
    float offset = CASCADE * index;
    float x = avoid.right() - window.width() - CASCADE + offset;
    window.moveTo(
        clampX(window, x, screenW), clampY(window, avoid.y() + CASCADE + offset, screenH));
  }

  private boolean placeAcross(
      FloatingWindow window, int index, Rect avoid, Canvas c, float screenW, float screenH) {
    window.resize(window.preferredWidth());
    arrange(window, c, screenW, screenH);
    float x = avoid.right() + GAP;
    boolean moved = true;
    while (moved) {
      moved = false;
      for (int i = 0; i < index; i++) {
        FloatingWindow other = windows.get(i);
        if (!other.closing() && x < other.x() + other.width() && x + window.width() > other.x()) {
          x = other.x() + other.width() + GAP;
          moved = true;
        }
      }
    }
    float room = screenW - MARGIN - x;
    if (room < window.width()) {
      if (room < window.minimumWidth()) {
        return false;
      }
      window.resize(room);
      arrange(window, c, screenW, screenH);
    }
    window.moveTo(x, MARGIN);
    return true;
  }

  private int onSide(int index, Rect avoid, boolean right) {
    int count = 0;
    for (int i = 0; i < index; i++) {
      FloatingWindow other = windows.get(i);
      if (other.closing()) {
        continue;
      }
      boolean beside = other.x() + other.width() * 0.5F > avoid.x() + avoid.w() * 0.5F;
      if (beside == right) {
        count++;
      }
    }
    return count;
  }

  private boolean placeBeside(
      FloatingWindow window,
      int index,
      Rect avoid,
      Canvas c,
      float screenW,
      float screenH,
      boolean right) {
    float room = right ? screenW - MARGIN - (avoid.right() + GAP) : avoid.x() - GAP - MARGIN;
    if (room < window.minimumWidth()) {
      return false;
    }
    window.resize(room);
    arrange(window, c, screenW, screenH);
    float x = right ? avoid.right() + GAP : avoid.x() - GAP - window.width();
    float y = Math.max(MARGIN, avoid.y());
    boolean moved = true;
    while (moved) {
      moved = false;
      Rect candidate = new Rect(x, y, window.width(), window.height());
      for (int i = 0; i < index; i++) {
        FloatingWindow other = windows.get(i);
        if (!other.closing() && candidate.overlaps(other.bounds())) {
          y = other.y() + other.height() + GAP;
          moved = true;
          break;
        }
      }
    }
    int stacked = 0;
    for (int i = 0; i < index; i++) {
      FloatingWindow other = windows.get(i);
      if (!other.closing() && sameColumn(other, x, window.width())) {
        stacked++;
      }
    }
    if ((stacked + 1) * FloatingWindow.MIN_HEIGHT + stacked * GAP > screenH - MARGIN * 2.0F) {
      return false;
    }
    window.moveTo(x, y);
    return true;
  }

  private void arrange(FloatingWindow window, Canvas c, float screenW, float screenH) {
    window.arrange(c);
    window.fit(screenW - MARGIN * 2.0F, screenH - MARGIN * 2.0F, 1.0F);
  }
}
