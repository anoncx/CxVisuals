package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.Detail;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class TargetElement extends HudElement {
  private static final float MIN_WIDTH = 176.0F;
  private static final float MAX_WIDTH = 280.0F;
  private static final float FACE = 28.0F;
  private static final float FACE_RADIUS = 8.0F;
  private static final float HEADER = 44.0F;
  private static final float TEXT_GAP = 9.0F;
  private static final float GAP = 14.0F;
  private static final float BAR_BLOCK = 14.0F;
  private static final float GEAR_EDGE = HudPaint.INSET - (HudPaint.CELL_WIDTH - 16.0F) * 0.5F;
  private static final float FOOT = 7.0F;
  private static final float LOW = 0.3F;
  private static final int SEGMENTS = 10;
  private static final String MISSING = "?";

  public final EnumSetting<Detail> detail = detail();

  private String name = "";
  private String initial = MISSING;
  private String distance = "";
  private String health = "";
  private String armor = "";
  private boolean styled;
  private int lastId = HudData.NONE;
  private int lastDistance = Integer.MIN_VALUE;
  private int lastHealth = Integer.MIN_VALUE;
  private int lastArmor = Integer.MIN_VALUE;
  private boolean stale = true;
  private boolean precise;
  private float nameWidth;
  private float distanceWidth;
  private float healthWidth;
  private float armorWidth;
  private float nameRoom;
  private int worn;
  private boolean vitals;

  public TargetElement(Settings settings) {
    super(settings, "target", "Target", Category.INFO, false, Anchor.CENTER, 0.0, 70.0);
  }

  public String label() {
    return name;
  }

  @Override
  public float radius() {
    return 18.0F;
  }

  @Override
  public boolean elastic() {
    return true;
  }

  @Override
  public boolean present(HudData data) {
    return data.targetId != HudData.NONE;
  }

  private void refresh(HudData data) {
    boolean full = detail.get() == Detail.FULL;
    if (data.targetId != lastId || !data.targetName.equals(name) || full != precise) {
      precise = full;
      lastId = data.targetId;
      name = data.targetName;
      initial = name.isEmpty() ? MISSING : name.substring(0, 1).toUpperCase();
      lastDistance = Integer.MIN_VALUE;
      stale = true;
    }
    int reach = Math.round(data.targetDistance * (full ? 10.0F : 1.0F));
    if (reach != lastDistance) {
      lastDistance = reach;
      distance = full ? reach / 10 + "." + reach % 10 + " m" : reach + " m";
      stale = true;
    }
    vitals = full && data.targetHealth >= 0.0F;
    if (!vitals) {
      return;
    }
    int hearts = Math.round(data.targetHealth * 10.0F);
    int key = hearts * 31 + Math.round(data.targetMax) * 7 + Math.round(data.targetAbsorb);
    if (key != lastHealth) {
      lastHealth = key;
      health =
          tenths(hearts)
              + " / "
              + Math.round(data.targetMax)
              + (data.targetAbsorb >= 1.0F ? " +" + Math.round(data.targetAbsorb) : "");
      stale = true;
    }
    if (data.targetArmor != lastArmor) {
      lastArmor = data.targetArmor;
      armor = Integer.toString(lastArmor);
      stale = true;
    }
  }

  @Override
  public void measure(Canvas c, HudData data) {
    refresh(data);
    if (stale) {
      stale = false;
      float server = data.targetPlayer ? c.nameWidth(data.targetId) : -1.0F;
      styled = server >= 0.0F;
      nameWidth = styled ? server : c.textWidth(FontStyle.LABEL, name);
      distanceWidth = c.textWidth(FontStyle.MONO, distance);
      healthWidth = vitals ? c.textWidth(FontStyle.MONO, health) : 0.0F;
      armorWidth = vitals ? c.textWidth(FontStyle.MONO, armor) : 0.0F;
    }
    worn = 0;
    for (int i = HudData.TARGET_FIRST; i < HudData.SLOTS; i++) {
      if (data.gear[i]) {
        worn++;
      }
    }
    float wanted = HudPaint.INSET * 2.0F + FACE + TEXT_GAP + nameWidth + GAP + distanceWidth;
    w =
        (float)
            Math.ceil(
                Math.max(
                    MIN_WIDTH,
                    Math.min(
                        MAX_WIDTH,
                        Math.max(wanted, worn * HudPaint.CELL_WIDTH + GEAR_EDGE * 2.0F))));
    nameRoom = w - HudPaint.INSET * 2.0F - FACE - TEXT_GAP - GAP - distanceWidth;
    h =
        HEADER
            + (vitals ? BAR_BLOCK + HudPaint.ROW * 2.0F : 0.0F)
            + (worn > 0 ? HudPaint.CELL_HEIGHT : 0.0F)
            + FOOT;
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    HudPaint.plate(c, t, this, x, y, w, h, radius());
    float faceLeft = x + HudPaint.INSET;
    float faceTop = y + (HEADER - FACE) * 0.5F;
    if (!data.targetPlayer || !c.face(data.targetId, faceLeft, faceTop, FACE, FACE_RADIUS)) {
      c.roundRect(faceLeft, faceTop, FACE, FACE, FACE_RADIUS, t.surface2());
      c.text(
          FontStyle.ROW,
          initial,
          faceLeft + (FACE - c.textWidth(FontStyle.ROW, initial)) * 0.5F,
          HudPaint.centred(c, FontStyle.ROW, faceTop, FACE),
          t.ink2());
    }
    c.roundRectBorder(faceLeft, faceTop, FACE, FACE, FACE_RADIUS, 1.0F, t.hair());
    float textLeft = faceLeft + FACE + TEXT_GAP;
    boolean clipped = nameWidth > nameRoom;
    if (clipped) {
      c.pushClip(textLeft, y, nameRoom, HEADER);
    }
    if (!styled || !c.name(data.targetId, textLeft, faceTop, FACE)) {
      c.text(
          FontStyle.LABEL,
          name,
          textLeft,
          HudPaint.centred(c, FontStyle.LABEL, faceTop, FACE),
          t.ink());
    }
    if (clipped) {
      c.popClip();
    }
    c.text(
        FontStyle.MONO,
        distance,
        x + w - HudPaint.INSET - distanceWidth,
        HudPaint.centred(c, FontStyle.MONO, faceTop, FACE),
        t.grey());
    float cursor = y + HEADER;
    if (vitals) {
      float fraction = data.targetMax > 0.0F ? data.targetHealth / data.targetMax : 0.0F;
      c.roundRect(
          x + HudPaint.INSET, cursor - 2.0F, w - HudPaint.INSET * 2.0F, 1.0F, 0.0F, t.hair());
      HudPaint.segments(
          c,
          x + HudPaint.INSET,
          cursor + 4.0F,
          w - HudPaint.INSET * 2.0F,
          4.0F,
          SEGMENTS,
          fraction,
          fraction < LOW ? t.red() : t.ink(),
          t.dotOff());
      cursor += BAR_BLOCK;
      HudPaint.pair(c, t, x, cursor, w, Lang.tr("Health"), health, healthWidth, t.ink());
      HudPaint.pair(
          c, t, x, cursor + HudPaint.ROW, w, Lang.tr("Armor"), armor, armorWidth, t.ink());
      cursor += HudPaint.ROW * 2.0F;
    }
    if (worn == 0) {
      return;
    }
    boolean wear = detail.get() == Detail.FULL;
    int placed = 0;
    for (int i = HudData.TARGET_FIRST; i < HudData.SLOTS; i++) {
      if (data.gear[i]) {
        HudPaint.gear(c, t, data, i, x + GEAR_EDGE + placed * HudPaint.CELL_WIDTH, cursor, wear);
        placed++;
      }
    }
  }

  private static String tenths(int scaled) {
    return scaled % 10 == 0 ? Integer.toString(scaled / 10) : scaled / 10 + "." + scaled % 10;
  }
}
