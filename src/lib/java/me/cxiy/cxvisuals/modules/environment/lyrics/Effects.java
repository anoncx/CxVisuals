package me.cxiy.cxvisuals.modules.environment.lyrics;

import me.cxiy.cxvisuals.modules.environment.EnvironmentModule.Arrive;
import me.cxiy.cxvisuals.modules.environment.EnvironmentModule.Leave;

public final class Effects {
  public static final float ARRIVE = 0.35F;
  public static final float EVAPORATE = 0.9F;
  public static final float PLAIN = 0.5F;
  public static final float STAGGER = 0.03F;
  public static final float MAX_STAGGER = 0.45F;

  private static final float CONDENSE_LIFT = 6.0F;
  private static final float EVAPORATE_LIFT = 14.0F;
  private static final float EVAPORATE_DRIFT = 4.0F;
  private static final float DRIFT_PHASE = 1.7F;
  private static final float CONDENSE_ALPHA = 3.0F;

  public static final class Sample {
    public float erosion;
    public float lift;
    public float drift;
    public float alpha;
  }

  private Effects() {}

  private static float delay(boolean calm, int letter, int letters) {
    if (calm || letters <= 1) {
      return 0.0F;
    }
    return Math.min(STAGGER, MAX_STAGGER / (letters - 1)) * letter;
  }

  private static float span(Leave effect, boolean calm) {
    return !calm && effect == Leave.EVAPORATE ? EVAPORATE : PLAIN;
  }

  public static float arriveSeconds(boolean calm, int letters) {
    return ARRIVE + delay(calm, letters - 1, letters);
  }

  public static float leaveSeconds(Leave effect, boolean calm, int letters) {
    return span(effect, calm) + delay(calm, letters - 1, letters);
  }

  public static void sample(
      Arrive arrive,
      Leave leave,
      boolean calm,
      int letter,
      int letters,
      float age,
      float leaving,
      Sample out) {
    float wait = delay(calm, letter, letters);
    float a = clamp((age - wait) / ARRIVE);
    float eased = smooth(a);
    float erosion = 0.0F;
    float lift = 0.0F;
    float drift = 0.0F;
    float alpha;
    Arrive in = calm ? Arrive.FADE : arrive;
    switch (in) {
      case CONDENSE -> {
        erosion = 1.0F - eased;
        lift = (1.0F - eased) * CONDENSE_LIFT;
        alpha = Math.min(1.0F, a * CONDENSE_ALPHA);
      }
      case DISSOLVE -> {
        erosion = 1.0F - eased;
        alpha = a > 0.0F ? 1.0F : 0.0F;
      }
      default -> alpha = eased;
    }
    if (leaving >= 0.0F) {
      Leave away = calm ? Leave.FADE : leave;
      float l = clamp((leaving - wait) / span(leave, calm));
      switch (away) {
        case EVAPORATE -> {
          erosion = Math.max(erosion, smooth(l));
          lift += l * l * EVAPORATE_LIFT;
          drift += (float) Math.sin(letter * DRIFT_PHASE) * l * EVAPORATE_DRIFT;
          alpha = Math.min(alpha, 1.0F - l * l);
        }
        case DISSOLVE -> erosion = Math.max(erosion, smooth(l));
        default -> alpha = Math.min(alpha, 1.0F - smooth(l));
      }
    }
    out.erosion = erosion;
    out.lift = lift;
    out.drift = drift;
    out.alpha = alpha;
  }

  private static float clamp(float value) {
    return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
  }

  private static float smooth(float t) {
    return t * t * (3.0F - 2.0F * t);
  }
}
