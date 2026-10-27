package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.download.Recording;

public final class DownloadPanel extends Widget {
  private static final float HEIGHT = 150.0F;
  private static final float LEFT = 8.0F;
  private static final float BUTTON = 30.0F;
  private static final float BUTTON_PAD = 16.0F;
  private static final float GAP = 8.0F;
  private static final float PULSE = 1.4F;
  private static final String CHUNKS = Lang.key("CHUNKS");
  private static final String SAVE_NOW = Lang.key("Save loaded chunks");
  private static final String START = Lang.key("Start recording");
  private static final String STOP = Lang.key("Stop and save");
  private static final String DISCARD = Lang.key("Discard");
  private static final String IDLE_TEXT =
      Lang.key("Save what is loaded now, or record everything you load.");
  private static final String RECORDING_TEXT = Lang.key("Recording. Every chunk you load is kept.");
  private static final String SAVING_TEXT = Lang.key("Writing the world…");
  private static final String SAVED_TEXT = Lang.key("Saved as {0}. Open it from Singleplayer.");
  private static final String FAILED_TEXT = Lang.key("The world couldn't be saved.");

  private final Recording recording;
  private final Anim[] hovers = {Anim.hover(0.0F), Anim.hover(0.0F), Anim.hover(0.0F)};
  private final Anim live = Anim.state(0.0F);
  private final float[] buttonX = new float[3];
  private final float[] buttonW = new float[3];
  private final String[] labels = new String[3];
  private int buttons;
  private float clock;
  private int shownChunks = -1;
  private String count = "";

  public DownloadPanel(Recording recording) {
    this.recording = recording;
    this.h = HEIGHT;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    clock += dt;
    Recording.State state = recording.state();
    live.set(state == Recording.State.RECORDING ? 1.0F : 0.0F);
    live.update(dt);
    int chunks = recording.chunks();
    if (chunks != shownChunks) {
      shownChunks = chunks;
      count = String.format(java.util.Locale.ROOT, "%,d", chunks);
    }
    float top = y + 10.0F;
    c.text(FontStyle.DOTS, count, x + LEFT, top, t.ink());
    float dotsH = c.lineHeight(FontStyle.DOTS);
    String unit = Lang.tr(CHUNKS) + " · " + Recording.size(recording.bytes());
    c.text(FontStyle.MONO, unit, x + LEFT, top + dotsH + 2.0F, t.grey());
    float glow = live.get();
    if (glow > 0.01F) {
      String rec = "REC";
      float pillW = c.textWidth(FontStyle.CAPTION, rec) + 28.0F;
      float pillH = 24.0F;
      float pillX = x + w - LEFT - pillW;
      c.pushAlpha(glow);
      c.roundRect(pillX, top, pillW, pillH, pillH * 0.5F, Theme.withAlpha(t.red(), 0.14F));
      float pulse = 0.55F + 0.45F * (float) Math.cos(clock * Math.PI * 2.0 / PULSE);
      c.roundRect(
          pillX + 10.0F,
          top + pillH * 0.5F - 3.5F,
          7.0F,
          7.0F,
          3.5F,
          Theme.withAlpha(t.red(), pulse));
      c.text(
          FontStyle.CAPTION,
          rec,
          pillX + 22.0F,
          centered(c, FontStyle.CAPTION, top, pillH),
          t.red());
      c.popAlpha();
    }
    String body =
        switch (state) {
          case RECORDING -> Lang.tr(RECORDING_TEXT);
          case SAVING -> Lang.tr(SAVING_TEXT);
          case SAVED -> Lang.format(SAVED_TEXT, recording.world());
          case FAILED -> Lang.tr(FAILED_TEXT);
          case IDLE -> Lang.tr(IDLE_TEXT);
        };
    if (!recording.notice().isEmpty() && state != Recording.State.SAVED) {
      body = recording.notice();
    }
    float bodyTop = top + dotsH + c.lineHeight(FontStyle.MONO) + 12.0F;
    c.text(
        FontStyle.SMALL,
        c.fit(FontStyle.SMALL, body, w - LEFT * 2.0F),
        x + LEFT,
        bodyTop,
        state == Recording.State.FAILED ? t.alert() : t.ink2());

    buttons = 0;
    if (state == Recording.State.RECORDING) {
      add(Lang.tr(STOP));
      add(Lang.tr(DISCARD));
    } else if (state != Recording.State.SAVING) {
      add(Lang.tr(SAVE_NOW));
      add(Lang.tr(START));
    }
    float buttonTop = y + HEIGHT - BUTTON - 10.0F;
    float left = x + LEFT;
    for (int i = 0; i < buttons; i++) {
      buttonW[i] = (float) Math.ceil(c.textWidth(FontStyle.LABEL, labels[i]) + BUTTON_PAD * 2.0F);
      buttonX[i] = left;
      left += buttonW[i] + GAP;
      boolean over = in(mouseX, mouseY, buttonX[i], buttonTop, buttonW[i], BUTTON);
      hovers[i].set(over ? 1.0F : 0.0F);
      hovers[i].update(dt);
      boolean primary = i == 0;
      int fill =
          primary
              ? Theme.mix(t.ink(), t.ink2(), 0.15F * hovers[i].get())
              : Theme.mix(t.surface2(), t.ink(), 0.08F * hovers[i].get());
      Depth.raised(c, t, buttonX[i], buttonTop, buttonW[i], BUTTON, BUTTON * 0.5F, fill);
      c.text(
          FontStyle.LABEL,
          labels[i],
          buttonX[i] + (buttonW[i] - c.textWidth(FontStyle.LABEL, labels[i])) * 0.5F,
          centered(c, FontStyle.LABEL, buttonTop, BUTTON),
          primary ? t.paper() | 0xFF000000 : t.ink());
    }
  }

  private void add(String label) {
    labels[buttons++] = label;
  }

  private static boolean in(float mx, float my, float x, float y, float w, float h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      return false;
    }
    float buttonTop = y + HEIGHT - BUTTON - 10.0F;
    Recording.State state = recording.state();
    for (int i = 0; i < buttons; i++) {
      if (in(mx, my, buttonX[i], buttonTop, buttonW[i], BUTTON)) {
        Sound.play(Sound.Cue.STEP);
        if (state == Recording.State.RECORDING) {
          if (i == 0) {
            recording.actions().finish();
          } else {
            recording.actions().discard();
          }
        } else if (i == 0) {
          recording.actions().snapshot();
        } else {
          recording.actions().start();
        }
        return true;
      }
    }
    return true;
  }
}
