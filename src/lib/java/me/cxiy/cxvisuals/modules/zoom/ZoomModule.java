package me.cxiy.cxvisuals.modules.zoom;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;

public final class ZoomModule extends Module {
  public enum Mode {
    HOLD,
    TOGGLE
  }

  public static final String ID = "zoom";
  public static final String ZOOM_KEY = ID + ".zoom";
  public static final int ZOOM_KEY_DEFAULT = 67;

  private static final double NOTCH = Math.log(1.15);
  private static final double REST = 1.0e-3;
  private static final float FASTEST = 40.0F;
  private static final float SLOWEST = 6.0F;
  private static final float FLASH_SECONDS = 1.2F;
  private static final float FADE_RATE = 14.0F;
  private static final float FADE_REST = 0.004F;

  public final EnumSetting<Mode> mode;
  public final NumberSetting level;
  public final BoolSetting scroll;
  public final NumberSetting limit;
  public final BoolSetting remember;
  public final NumberSetting smoothness;
  public final BoolSetting sensitivity;
  public final BoolSetting cinematic;
  public final BoolSetting indicator;

  private boolean down;
  private boolean active;
  private double target;
  private double kept;
  private double eased;
  private float divisor = 1.0F;
  private float flash;
  private float shown;
  private String label = "";
  private int labelTenths = -1;

  public ZoomModule(KeyBinder keys) {
    super(ID, "Zoom", true, keys);
    Page zoom = section("zoom", "Zoom");

    Group control = zoom.group("Control");
    mode =
        control
            .enumOf("mode", "Mode", Mode.HOLD, "Hold", "Toggle")
            .describe("Hold zooms while the key is down. Toggle switches on each press.");
    control.key("zoom_key", "Zoom key", keys.bind(ZOOM_KEY, "Zoom", ZOOM_KEY_DEFAULT));

    Group amount = zoom.group("Level");
    level =
        amount
            .number("level", "Zoom", 4.0, 1.5, 20.0, 0.5, "×")
            .describe("How far the view zooms in when you start.");
    scroll =
        amount
            .bool("scroll", "Scroll to zoom", true)
            .describe("The mouse wheel zooms in and out while zoomed, instead of changing slots.");
    limit =
        amount
            .number("limit", "Scroll limit", 50.0, 10.0, 100.0, 5.0, "×")
            .describe("The furthest the wheel can zoom in.");
    remember =
        amount
            .bool("remember", "Remember scroll level", false)
            .describe("Zooms back to where the wheel left it. Off starts from Zoom every time.");

    Group feel = zoom.group("Feel");
    smoothness =
        feel.number("smoothness", "Smoothness", 60.0, 0.0, 100.0, 5.0, "%")
            .describe("How gently the view glides in and out. 0 snaps instantly.");
    sensitivity =
        feel.bool("sensitivity", "Slow the mouse", true)
            .describe("Scales mouse speed with the zoom so aiming stays steady.");
    cinematic =
        feel.bool("cinematic", "Cinematic camera", false)
            .describe("Smooths mouse movement while zoomed.");
    indicator =
        feel.bool("indicator", "Show zoom level", true)
            .describe("Shows the level under the crosshair when the wheel changes it.");
  }

  public void key(boolean pressed) {
    boolean edge = pressed && !down;
    down = pressed;
    boolean next = mode.get() == Mode.TOGGLE ? active != edge : pressed;
    if (next == active) {
      return;
    }
    active = next;
    if (next) {
      target = remember.get() && kept > 0.0 ? Math.min(kept, ceiling()) : Math.log(level.get());
    }
  }

  public void release() {
    down = false;
    active = false;
  }

  public boolean scroll(double notches) {
    if (!active || !scroll.get() || notches == 0.0) {
      return false;
    }
    target = Math.max(0.0, Math.min(ceiling(), target + notches * NOTCH));
    kept = target;
    flash = FLASH_SECONDS;
    return true;
  }

  public void frame(float dt) {
    double goal = active ? target : 0.0;
    if (eased != goal) {
      double percent = smoothness.get();
      if (percent <= 0.0 || !(dt > 0.0F)) {
        eased = percent <= 0.0 ? goal : eased;
      } else {
        float rate = FASTEST + (SLOWEST - FASTEST) * (float) (percent / 100.0);
        eased += (goal - eased) * (1.0 - Math.exp(-dt * rate));
        if (Math.abs(goal - eased) < REST) {
          eased = goal;
        }
      }
      divisor = (float) Math.exp(eased);
    }
    if (flash > 0.0F) {
      flash = active && dt > 0.0F ? flash - dt : active ? flash : 0.0F;
    }
    float visible = flash > 0.0F && indicator.get() ? 1.0F : 0.0F;
    if (shown != visible) {
      shown += (visible - shown) * (1.0F - (float) Math.exp(-dt * FADE_RATE));
      if (Math.abs(visible - shown) < FADE_REST) {
        shown = visible;
      }
    }
  }

  public boolean active() {
    return active;
  }

  public float divisor() {
    return divisor;
  }

  public double turnScale() {
    return sensitivity.get() ? 1.0 / divisor : 1.0;
  }

  public boolean smoothTurn() {
    return active && cinematic.get();
  }

  public float shown() {
    return shown;
  }

  public float fill() {
    double ceiling = ceiling();
    return ceiling <= 0.0 ? 0.0F : (float) Math.min(1.0, target / ceiling);
  }

  public String label() {
    int tenths = (int) Math.round(Math.exp(target) * 10.0);
    if (tenths != labelTenths) {
      labelTenths = tenths;
      label = tenths / 10 + "." + tenths % 10 + "×";
    }
    return label;
  }

  private double ceiling() {
    return Math.log(Math.max(limit.get(), level.get()));
  }

  @Override
  public String group() {
    return "Camera";
  }
}
