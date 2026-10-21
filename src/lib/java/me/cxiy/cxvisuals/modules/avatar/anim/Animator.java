package me.cxiy.cxvisuals.modules.avatar.anim;

import java.util.Arrays;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;

public final class Animator {
  private static final float RADIANS = (float) (Math.PI / 180.0);
  private static final float LIMB = 0.6662F;
  private static final float LONGEST_STEP = 0.1F;
  private static final float FADE_TIME = 0.2F;
  private static final float STILL_TIME = 1.0F;
  private static final float BOB = 0.06F;
  private static final float WADDLE = 6.0F;
  private static final float WALK_TILT = 5.0F;
  private static final float LEAN = 15.0F;
  private static final float CROUCH_SQUASH = 0.15F;
  private static final float CROUCH_TILT = 12.0F;
  private static final float BREATH = 0.012F;
  private static final float BREATH_RATE = (float) (Math.PI * 2.0 * 0.25);
  private static final float LANDING = 4.0F;
  private static final float MOST_SQUASH = 6.0F;
  private static final float STRETCH = 0.35F;
  private static final float SHAKE = 4.0F;
  private static final float WALK_REFERENCE = 0.216F;
  private static final float RUN_REFERENCE = 0.28F;
  private static final float WALKING = 0.02F;
  private static final float RUNNING = 0.25F;
  private static final int WALK = ClipSet.State.WALK.ordinal();
  private static final int RUN = ClipSet.State.RUN.ordinal();
  private static final int JUMP = ClipSet.State.JUMP.ordinal();
  private static final int FALL = ClipSet.State.FALL.ordinal();
  private static final int SNEAK = ClipSet.State.SNEAK.ordinal();
  private static final int SWIM = ClipSet.State.SWIM.ordinal();
  private static final int FLY = ClipSet.State.FLY.ordinal();
  private static final int IDLE = ClipSet.State.IDLE.ordinal();
  private static final int ATTACK = ClipSet.State.ATTACK.ordinal();
  private static final int HURT = ClipSet.State.HURT.ordinal();
  private static final int DEATH = ClipSet.State.DEATH.ordinal();

  private static final class Spring {
    final float omega;
    float value;
    float velocity;

    Spring(float omega) {
      this.omega = omega;
    }

    void step(float target, float dt) {
      float offset = value - target;
      float decay = (float) Math.exp(-omega * dt);
      float drive = velocity + omega * offset;
      value = target + (offset + drive * dt) * decay;
      velocity = (velocity - omega * drive * dt) * decay;
    }
  }

  private final BakedAvatar avatar;
  private final Skeleton skeleton;
  private ClipSet set;
  private final float[] root = new float[16];
  private final float[] palette;
  private final float[] local;
  private final float[] blend;
  private final float[] rest;
  private final float[] globals;
  private final float[] a = new float[16];
  private final float[] b = new float[16];
  private final Spring squash = new Spring(18.0F);
  private final Spring stretch = new Spring(12.0F);
  private final Spring crouch = new Spring(14.0F);
  private final Spring lean = new Spring(10.0F);
  private final Spring fly = new Spring(6.0F);
  private float time;
  private float shake;
  private boolean wasGround = true;
  private float lastVertical;
  private float lastAttack;
  private boolean lastHurt;
  private int current = -1;
  private int previous = -1;
  private float currentTime;
  private float previousTime;
  private float fade = 1.0F;
  private int oneShot = -1;
  private boolean driven;
  private boolean restPosed;
  private final int[] cursor = new int[ClipSet.EVENTS];
  private int seed = 0x9E3779B9;
  private int base = -1;
  private int baseClip = -1;
  private float still;
  private int auditionEvent = -1;
  private int auditionClip = -1;

  public Animator(BakedAvatar avatar) {
    this.avatar = avatar;
    this.skeleton = avatar.skeleton;
    this.set = ClipSet.of(avatar.clips);
    int nodes = skeleton.count() * Skeleton.STRIDE;
    this.local = new float[nodes];
    this.blend = new float[nodes];
    this.rest = new float[nodes];
    this.globals = new float[skeleton.count() * 16];
    this.palette = new float[skeleton.paletteSize() * 16];
    skeleton.rest(rest);
    Affine.identity(root);
    Arrays.fill(cursor, -1);
  }

  public float[] root() {
    return root;
  }

  public float[] palette() {
    return palette;
  }

  public boolean clipDriven() {
    return driven;
  }

  public float fade() {
    return fade;
  }

  public int playing() {
    return current;
  }

  public void clips(ClipSet next) {
    set = next;
    Arrays.fill(cursor, -1);
    base = -1;
    baseClip = -1;
    oneShot = -1;
  }

  public void audition(int event, int clip) {
    auditionEvent = event;
    auditionClip = clip;
  }

  public void update(Motion m, float dt, double motionScale, boolean squashing, boolean clips) {
    if (dt <= 0.0F) {
      return;
    }
    float step = Math.min(dt, LONGEST_STEP);
    float scale = (float) Math.max(0.0, Math.min(2.0, motionScale));
    time += step;

    if (m.onGround && !wasGround && lastVertical < 0.0F && squashing) {
      squash.velocity -= Math.min(MOST_SQUASH, -lastVertical * LANDING) * scale;
    }
    boolean attackStarted = m.attack > 0.0F && (lastAttack <= 0.0F || m.attack < lastAttack - 0.5F);
    boolean hurtStarted = m.hurt && !lastHurt;
    boolean jumpStarted = !m.onGround && wasGround && m.verticalSpeed > 0.0F;
    if (hurtStarted) {
      shake = 1.0F;
    }
    wasGround = m.onGround;
    lastVertical = m.verticalSpeed;
    lastAttack = m.attack;
    lastHurt = m.hurt;

    squash.step(0.0F, step);
    stretch.step(
        squashing && !m.onGround && m.verticalSpeed > 0.0F
            ? Math.min(1.0F, m.verticalSpeed * 2.0F) * STRETCH * scale
            : 0.0F,
        step);
    crouch.step(m.crouching ? 1.0F : 0.0F, step);
    float leanTarget =
        clamp(-m.yawRate * Math.min(1.0F, m.speed / WALK_REFERENCE) * 0.08F, -LEAN, LEAN) * scale;
    lean.step(leanTarget, step);
    fly.step(m.flying ? 1.0F : 0.0F, step);
    shake *= (float) Math.exp(-step * 8.0F);

    boolean useClips = clips && avatar.clips.length > 0;
    driven = useClips && play(m, step, attackStarted, hurtStarted, jumpStarted);
    if (!driven) {
      current = -1;
      previous = -1;
      oneShot = -1;
      base = -1;
      fade = 1.0F;
      if (!restPosed && palette.length > 0) {
        skeleton.pose(rest, globals, palette);
        restPosed = true;
      }
    } else {
      restPosed = false;
    }

    float stride = Math.min(1.0F, m.walkSpeed) * scale;
    float phase = m.walkPos * LIMB;
    float bob = driven ? 0.0F : Math.abs((float) Math.sin(phase)) * BOB * stride;
    float waddle = driven ? 0.0F : (float) Math.sin(phase) * WADDLE * stride;
    float walkTilt = driven ? 0.0F : WALK_TILT * stride;
    float idle = 1.0F - Math.min(1.0F, m.walkSpeed);
    float breath = (float) Math.sin(time * BREATH_RATE) * BREATH * idle * scale;
    float landed = squashing ? squash.value : 0.0F;
    float sy = (1.0F + landed + stretch.value + breath) * (1.0F - CROUCH_SQUASH * crouch.value);
    sy = Math.max(0.2F, sy);
    float sxz = (float) (1.0 / Math.sqrt(Math.max(0.2F, 1.0F + landed + stretch.value + breath)));
    float tilt = (walkTilt + CROUCH_TILT * crouch.value) * RADIANS;
    float roll =
        (lean.value + waddle + (float) Math.sin(time * 45.0F) * SHAKE * shake * scale) * RADIANS;
    float glide = Math.max(m.swim, fly.value);
    float swimAngle = glide * clamp(90.0F + m.pitch, 0.0F, 180.0F) * RADIANS;

    Affine.identity(root);
    root[13] = bob;
    if (swimAngle != 0.0F) {
      float pivot = avatar.height * 0.5F;
      Affine.identity(a);
      a[13] = pivot;
      Affine.multiply(root, 0, a, 0, root, 0);
      Affine.rotationX(swimAngle, a);
      Affine.multiply(root, 0, a, 0, root, 0);
      Affine.identity(a);
      a[13] = -pivot;
      Affine.multiply(root, 0, a, 0, root, 0);
    }
    if (tilt != 0.0F) {
      Affine.rotationX(tilt, a);
      Affine.multiply(root, 0, a, 0, root, 0);
    }
    if (roll != 0.0F) {
      float c = (float) Math.cos(roll);
      float s = (float) Math.sin(roll);
      Affine.identity(a);
      a[0] = c;
      a[1] = s;
      a[4] = -s;
      a[5] = c;
      Affine.multiply(root, 0, a, 0, root, 0);
    }
    Affine.identity(b);
    b[0] = sxz;
    b[5] = sy;
    b[10] = sxz;
    Affine.multiply(root, 0, b, 0, root, 0);
  }

  private boolean play(
      Motion m, float step, boolean attackStarted, boolean hurtStarted, boolean jumpStarted) {
    int target;
    float rate = 1.0F;
    boolean loop = true;
    boolean rotate = true;
    if (auditionClip >= 0) {
      target = auditionClip < avatar.clips.length ? auditionClip : -1;
      oneShot = -1;
      base = -1;
      rotate = false;
    } else if (auditionEvent >= 0) {
      oneShot = -1;
      target = hold(auditionEvent);
    } else if (m.deathTime > 0.0F && set.own(DEATH)) {
      oneShot = -1;
      target = hold(DEATH);
      loop = false;
    } else {
      if (attackStarted && set.own(ATTACK)) {
        oneShot = pick(ATTACK);
      } else if (hurtStarted && set.own(HURT)) {
        oneShot = pick(HURT);
      } else if (jumpStarted && set.own(JUMP)) {
        oneShot = pick(JUMP);
        base = JUMP;
        baseClip = oneShot;
      }
      if (oneShot >= 0 && current == oneShot && currentTime >= avatar.clips[oneShot].duration()) {
        oneShot = -1;
      }
      if (oneShot >= 0) {
        target = oneShot;
        loop = false;
        rotate = false;
      } else {
        int state = base(m);
        target = hold(state);
        if (state == WALK || state == RUN) {
          float reference = state == RUN && set.own(RUN) ? RUN_REFERENCE : WALK_REFERENCE;
          rate = clamp(m.speed / reference, 0.5F, 2.0F);
        }
      }
    }
    if (target < 0) {
      return false;
    }
    if (target != current) {
      boolean first = current < 0;
      previous = current;
      previousTime = currentTime;
      current = target;
      currentTime = 0.0F;
      still = 0.0F;
      fade = first ? 1.0F : 0.0F;
    }
    float duration = avatar.clips[current].duration();
    float delta = step * rate;
    boolean ended;
    if (duration > 0.0F) {
      ended = currentTime + delta >= duration;
    } else {
      still += step;
      ended = still >= STILL_TIME;
    }
    currentTime = advance(currentTime, delta, duration, loop);
    if (loop && rotate && ended && set.clips(base).length > 1) {
      still = 0.0F;
      baseClip = pick(base);
    }
    if (fade < 1.0F) {
      fade = Math.min(1.0F, fade + step / FADE_TIME);
      if (previous >= 0) {
        previousTime = advance(previousTime, step, avatar.clips[previous].duration(), true);
      }
    }
    System.arraycopy(rest, 0, local, 0, rest.length);
    avatar.clips[current].sample(currentTime, local);
    if (fade < 1.0F && previous >= 0) {
      System.arraycopy(rest, 0, blend, 0, rest.length);
      avatar.clips[previous].sample(previousTime, blend);
      mix(blend, local, fade, local);
    }
    skeleton.pose(local, globals, palette);
    return true;
  }

  private int hold(int state) {
    if (state != base) {
      base = state;
      baseClip = pick(state);
    }
    return baseClip;
  }

  private int pick(int event) {
    int[] options = set.clips(event);
    int count = options.length;
    if (count == 0) {
      return -1;
    }
    if (count == 1) {
      return options[0];
    }
    int at = cursor[event];
    if (set.random(event)) {
      seed ^= seed << 13;
      seed ^= seed >>> 17;
      seed ^= seed << 5;
      at =
          at < 0
              ? Integer.remainderUnsigned(seed, count)
              : (at + 1 + Integer.remainderUnsigned(seed, count - 1)) % count;
    } else {
      at = (at + 1) % count;
    }
    cursor[event] = at;
    return options[at];
  }

  private static int base(Motion m) {
    if (m.swim > 0.5F) {
      return SWIM;
    }
    if (m.flying) {
      return FLY;
    }
    if (!m.onGround && !m.passenger) {
      return m.verticalSpeed > 0.0F ? JUMP : FALL;
    }
    if (m.crouching) {
      return SNEAK;
    }
    if (m.speed > RUNNING) {
      return RUN;
    }
    return m.speed > WALKING ? WALK : IDLE;
  }

  private static float advance(float time, float delta, float duration, boolean loop) {
    float next = time + delta;
    if (duration <= 0.0F) {
      return 0.0F;
    }
    if (!loop) {
      return Math.min(next, duration);
    }
    return next % duration;
  }

  private static void mix(float[] from, float[] to, float t, float[] out) {
    for (int o = 0; o < from.length; o += Skeleton.STRIDE) {
      for (int i = 0; i < 3; i++) {
        out[o + i] = from[o + i] + (to[o + i] - from[o + i]) * t;
        out[o + 7 + i] = from[o + 7 + i] + (to[o + 7 + i] - from[o + 7 + i]) * t;
      }
      Affine.slerp(from, o + 3, to, o + 3, t, out, o + 3);
    }
  }

  private static float clamp(float value, float low, float high) {
    return value < low ? low : Math.min(value, high);
  }
}
