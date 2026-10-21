package me.cxiy.cxvisuals.modules.environment.particles;

import java.util.SplittableRandom;
import me.cxiy.cxvisuals.modules.environment.View;

public final class Fireworks {
  public static final int SPARKS = 1400;
  public static final int SHELLS = 10;
  public static final int QUADS = SPARKS + SHELLS * 2;

  private static final float CLEAR = 0.3F;
  private static final float GRAVITY = 9.0F;
  private static final float FALL = 3.4F;
  private static final float NEAREST = 45.0F;
  private static final float SPREAD = 55.0F;
  private static final float SHORTEST = 24.0F;
  private static final float HAZE_SHARE = 0.7F;
  private static final float ROCKET_STEP = 0.025F;
  private static final float GLITTER_STEP = 0.07F;
  private static final float FLASH = 0.35F;
  private static final float FLASH_SIZE = 12.0F;
  private static final float SKY = 1.6F;
  private static final float SKY_SIZE = 38.0F;
  private static final float HOT = 0.12F;
  private static final float BLOOM = 0.9F;
  private static final float GONE = 0.004F;
  private static final float SITE_ARC = 0.4F;
  private static final float SITE_DEPTH = 16.0F;
  private static final float SEEDS = 32767.0F;
  private static final int FINALE = 4;
  private static final int GOLD = 0xFFC864;
  private static final int GLINT = 0xFFF2D6;
  private static final Burst[] BURSTS = Burst.values();
  private static final int[] COLOURS = {
    0xFF4D6A, 0x5AC8FF, 0xFFD25A, 0x9DFF6A, 0xC77DFF, 0xFFFFFF, 0xFF8A3D, 0x5AFFD6, 0xFF6FD8
  };

  private enum Trait {
    STAR,
    GLITTER,
    CRACKLE,
    SPLIT,
    EMBER
  }

  private enum Burst {
    PEONY(96, 12.5F, 0.2F, 1.5F, 0.5F, 1.5F, 1.0F, 0, Trait.STAR),
    CHRYSANTHEMUM(120, 14.0F, 0.12F, 1.9F, 0.5F, 1.3F, 2.6F, 36, Trait.STAR),
    WILLOW(44, 9.5F, 0.2F, 3.4F, 0.6F, 1.8F, 1.0F, 0, Trait.GLITTER),
    RING(72, 13.0F, 0.03F, 1.6F, 0.3F, 1.4F, 1.2F, 24, Trait.STAR),
    CRACKLE(84, 12.0F, 0.25F, 1.5F, 0.4F, 1.5F, 1.0F, 0, Trait.CRACKLE),
    CROSSETTE(14, 12.5F, 0.1F, 0.9F, 0.15F, 1.0F, 1.8F, 0, Trait.SPLIT),
    PALM(9, 15.0F, 0.1F, 1.7F, 0.3F, 1.2F, 1.6F, 0, Trait.GLITTER);

    final int count;
    final float speed;
    final float jitter;
    final float life;
    final float lifeSpread;
    final float drag;
    final float streak;
    final int pistil;
    final Trait trait;

    Burst(
        int count,
        float speed,
        float jitter,
        float life,
        float lifeSpread,
        float drag,
        float streak,
        int pistil,
        Trait trait) {
      this.count = count;
      this.speed = speed;
      this.jitter = jitter;
      this.life = life;
      this.lifeSpread = lifeSpread;
      this.drag = drag;
      this.streak = streak;
      this.pistil = pistil;
      this.trait = trait;
    }
  }

  private static final class Shell {
    double x;
    double y;
    double z;
    float vy;
    float fuse;
    float age;
    float trail;
    int colour;
    int accent;
    Burst burst;
    boolean popped;
    boolean alive;
  }

  private static final class Spark {
    double x;
    double y;
    double z;
    float vx;
    float vy;
    float vz;
    float age;
    float life;
    float drag;
    float fall;
    float streak;
    float seed;
    float trail;
    int from;
    int to;
    Trait trait;
    boolean alive;
  }

  private final Shell[] shells = new Shell[SHELLS];
  private final Spark[] sparks = new Spark[SPARKS];
  private final SplittableRandom random;
  private float wait = 6.0F;
  private int queued;
  private float gap;
  private int alive;
  private int burning;
  private int cursor;
  private boolean sited;
  private float siteAngle;
  private float siteDistance;

  public Fireworks(long seed) {
    random = new SplittableRandom(seed);
    for (int i = 0; i < SHELLS; i++) {
      shells[i] = new Shell();
    }
    for (int i = 0; i < SPARKS; i++) {
      sparks[i] = new Spark();
    }
  }

  public int alive() {
    return alive;
  }

  public void clear() {
    for (Shell shell : shells) {
      shell.alive = false;
    }
    for (Spark spark : sparks) {
      spark.alive = false;
    }
    alive = 0;
    burning = 0;
    queued = 0;
  }

  public void show() {
    queued = 6 + random.nextInt(8);
    gap = 0.0F;
    sited = false;
  }

  public void update(View view, float allow, float rate, float horizon, float dt, Ground ground) {
    alive = 0;
    for (Shell shell : shells) {
      if (shell.alive) {
        fly(shell, dt);
        if (shell.alive) {
          alive++;
        }
      }
    }
    for (Spark spark : sparks) {
      if (spark.alive) {
        drift(spark, dt);
        if (spark.alive) {
          alive++;
        }
      }
    }
    if (allow < CLEAR) {
      queued = 0;
      return;
    }
    if (queued > 0) {
      gap -= dt;
      if (gap <= 0.0F) {
        launch(view, horizon, ground);
        queued--;
        gap =
            queued <= FINALE
                ? 0.12F + random.nextFloat() * 0.2F
                : 0.35F + random.nextFloat() * 1.05F;
      }
      return;
    }
    wait -= dt * rate * allow;
    if (wait <= 0.0F) {
      wait = 25.0F + random.nextFloat() * 50.0F;
      show();
    }
  }

  private void launch(View view, float horizon, Ground ground) {
    Shell shell = null;
    for (Shell candidate : shells) {
      if (!candidate.alive) {
        shell = candidate;
        break;
      }
    }
    if (shell == null) {
      return;
    }
    if (!sited) {
      float farthest = Math.max(SHORTEST, Math.min(NEAREST + SPREAD, horizon * HAZE_SHARE));
      float nearest = Math.min(NEAREST, farthest * 0.6F);
      siteAngle = (float) Math.atan2(view.fz, view.fx) + (random.nextFloat() - 0.5F) * 1.4F;
      siteDistance = nearest + random.nextFloat() * (farthest - nearest);
      sited = true;
    }
    float angle = siteAngle + (random.nextFloat() - 0.5F) * SITE_ARC;
    float distance = Math.max(SHORTEST, siteDistance + (random.nextFloat() - 0.5F) * SITE_DEPTH);
    shell.x = view.x + Waves.cos(angle) * distance;
    shell.z = view.z + Waves.sin(angle) * distance;
    shell.y =
        Math.clamp(ground.floor(shell.x, view.y + 40.0, shell.z), view.y - 30.0, view.y + 10.0);
    shell.vy = 22.0F + random.nextFloat() * 6.0F;
    shell.fuse = 1.5F + random.nextFloat() * 0.6F;
    shell.age = 0.0F;
    shell.trail = 0.0F;
    shell.colour = COLOURS[random.nextInt(COLOURS.length)];
    shell.accent = COLOURS[random.nextInt(COLOURS.length)];
    shell.burst = BURSTS[random.nextInt(BURSTS.length)];
    shell.popped = false;
    shell.alive = true;
  }

  private void fly(Shell shell, float dt) {
    shell.age += dt;
    if (shell.age < shell.fuse) {
      shell.vy -= GRAVITY * dt;
      shell.y += shell.vy * dt;
      shell.trail += dt;
      while (shell.trail >= ROCKET_STEP) {
        shell.trail -= ROCKET_STEP;
        Spark spark = spark();
        if (spark == null) {
          break;
        }
        place(
            spark,
            shell.x,
            shell.y,
            shell.z,
            (random.nextFloat() - 0.5F) * 0.8F,
            -1.0F - random.nextFloat() * 1.5F,
            (random.nextFloat() - 0.5F) * 0.8F,
            0.45F + random.nextFloat() * 0.35F,
            2.0F,
            1.2F,
            0.0F,
            GOLD,
            GOLD,
            Trait.EMBER);
      }
    } else if (!shell.popped) {
      shell.popped = true;
      explode(shell);
    } else if (shell.age > shell.fuse + SKY) {
      shell.alive = false;
    }
  }

  private void explode(Shell shell) {
    Burst burst = shell.burst;
    int shift = random.nextFloat() < 0.4F ? shell.accent : shell.colour;
    float tilt = random.nextFloat() * Waves.TURN;
    float lean = (random.nextFloat() - 0.5F) * 1.2F;
    float ct = Waves.cos(tilt);
    float st = Waves.sin(tilt);
    float cl = Waves.cos(lean);
    float sl = Waves.sin(lean);
    int from = burst.trait == Trait.GLITTER ? GOLD : shell.colour;
    for (int i = 0; i < burst.count; i++) {
      float dx;
      float dy;
      float dz;
      if (burst == Burst.RING) {
        float around = (float) i / burst.count * Waves.TURN;
        float rx = Waves.cos(around);
        float rz = Waves.sin(around);
        dx = rx * ct - rz * st * sl;
        dy = rz * cl;
        dz = rx * st + rz * ct * sl;
      } else {
        dy = random.nextFloat() * 2.0F - 1.0F;
        float around = random.nextFloat() * Waves.TURN;
        float flat = (float) Math.sqrt(1.0F - dy * dy);
        dx = Waves.cos(around) * flat;
        dz = Waves.sin(around) * flat;
      }
      int colour = burst == Burst.PEONY && (i & 1) == 1 ? shell.accent : from;
      if (!star(
          shell,
          burst,
          dx,
          dy,
          dz,
          1.0F,
          colour,
          burst.trait == Trait.GLITTER ? GOLD : shift,
          burst.trait)) {
        return;
      }
    }
    for (int i = 0; i < burst.pistil; i++) {
      float dy = random.nextFloat() * 2.0F - 1.0F;
      float around = random.nextFloat() * Waves.TURN;
      float flat = (float) Math.sqrt(1.0F - dy * dy);
      if (!star(
          shell,
          burst,
          Waves.cos(around) * flat,
          dy,
          Waves.sin(around) * flat,
          0.42F,
          shell.accent,
          GLINT,
          Trait.STAR)) {
        return;
      }
    }
  }

  private boolean star(
      Shell shell,
      Burst burst,
      float dx,
      float dy,
      float dz,
      float reach,
      int from,
      int to,
      Trait trait) {
    Spark spark = spark();
    if (spark == null) {
      return false;
    }
    float speed = burst.speed * reach * (1.0F + (random.nextFloat() - 0.5F) * burst.jitter);
    float life = (burst.life + random.nextFloat() * burst.lifeSpread) * (0.75F + 0.25F * reach);
    place(
        spark,
        shell.x,
        shell.y,
        shell.z,
        dx * speed,
        dy * speed,
        dz * speed,
        life,
        burst.drag,
        trait == Trait.GLITTER ? FALL * 1.25F : FALL,
        burst.streak,
        from,
        to,
        trait);
    return true;
  }

  private void split(Spark parent) {
    float turn = random.nextFloat() * Waves.TURN;
    for (int k = 0; k < 4; k++) {
      Spark spark = spark();
      if (spark == null) {
        return;
      }
      float around = turn + k * Waves.TURN * 0.25F;
      float speed = 6.5F + random.nextFloat();
      place(
          spark,
          parent.x,
          parent.y,
          parent.z,
          parent.vx * 0.3F + Waves.cos(around) * speed,
          parent.vy * 0.3F + (random.nextFloat() - 0.5F) * 2.0F,
          parent.vz * 0.3F + Waves.sin(around) * speed,
          0.7F + random.nextFloat() * 0.3F,
          1.6F,
          FALL,
          1.4F,
          parent.to,
          parent.to,
          Trait.STAR);
    }
  }

  private Spark spark() {
    if (burning >= SPARKS) {
      return null;
    }
    while (sparks[cursor].alive) {
      cursor = cursor + 1 == SPARKS ? 0 : cursor + 1;
    }
    Spark spark = sparks[cursor];
    cursor = cursor + 1 == SPARKS ? 0 : cursor + 1;
    return spark;
  }

  private void place(
      Spark spark,
      double x,
      double y,
      double z,
      float vx,
      float vy,
      float vz,
      float life,
      float drag,
      float fall,
      float streak,
      int from,
      int to,
      Trait trait) {
    spark.x = x;
    spark.y = y;
    spark.z = z;
    spark.vx = vx;
    spark.vy = vy;
    spark.vz = vz;
    spark.age = 0.0F;
    spark.life = life;
    spark.drag = drag;
    spark.fall = fall;
    spark.streak = streak;
    spark.seed = random.nextFloat();
    spark.trail = random.nextFloat() * GLITTER_STEP;
    spark.from = from;
    spark.to = to;
    spark.trait = trait;
    spark.alive = true;
    burning++;
  }

  private void drift(Spark spark, float dt) {
    spark.age += dt;
    if (spark.age > spark.life) {
      spark.alive = false;
      burning--;
      if (spark.trait == Trait.SPLIT) {
        split(spark);
      }
      return;
    }
    float slow = (float) Math.exp(-spark.drag * dt);
    spark.vx *= slow;
    spark.vy = spark.vy * slow - spark.fall * dt;
    spark.vz *= slow;
    spark.x += spark.vx * dt;
    spark.y += spark.vy * dt;
    spark.z += spark.vz * dt;
    if (spark.trait == Trait.GLITTER) {
      spark.trail += dt;
      while (spark.trail >= GLITTER_STEP) {
        spark.trail -= GLITTER_STEP;
        Spark ember = spark();
        if (ember == null) {
          break;
        }
        place(
            ember,
            spark.x,
            spark.y,
            spark.z,
            spark.vx * 0.08F + (random.nextFloat() - 0.5F) * 0.4F,
            spark.vy * 0.08F,
            spark.vz * 0.08F + (random.nextFloat() - 0.5F) * 0.4F,
            0.5F + random.nextFloat() * 0.5F,
            2.5F,
            1.4F,
            0.0F,
            random.nextFloat() < 0.3F ? GLINT : GOLD,
            GOLD,
            Trait.EMBER);
      }
    }
  }

  public void emit(View view, Sink sink, float fogStart, float fogEnd, float glow, float time) {
    if (alive == 0) {
      return;
    }
    for (Shell shell : shells) {
      if (shell.alive) {
        if (shell.age < shell.fuse) {
          rocket(shell, view, sink, fogStart, fogEnd, glow);
        } else {
          flash(shell, view, sink, fogStart, fogEnd, glow);
        }
      }
    }
    for (Spark spark : sparks) {
      if (spark.alive) {
        draw(spark, view, sink, fogStart, fogEnd, glow, time);
      }
    }
  }

  private static float fog(float fogStart, float fogEnd, float distance) {
    return 1.0F - Waves.smooth(fogStart, fogEnd, distance);
  }

  private static void billboard(
      View view,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float half,
      int argb,
      Shape shape,
      float detail,
      int seed) {
    sink.quad(
        dx,
        dy,
        dz,
        view.rx * half,
        view.ry * half,
        view.rz * half,
        view.ux * half,
        view.uy * half,
        view.uz * half,
        argb,
        shape,
        detail,
        seed);
  }

  private static void rocket(
      Shell shell, View view, Sink sink, float fogStart, float fogEnd, float glow) {
    float dx = (float) (shell.x - view.x);
    float dy = (float) (shell.y - view.y);
    float dz = (float) (shell.z - view.z);
    float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    float alpha =
        Math.min(
            1.0F, glow * fog(fogStart, fogEnd, distance) * Waves.smooth(0.0F, 0.2F, shell.age));
    if (alpha > GONE) {
      billboard(
          view,
          sink,
          dx,
          dy,
          dz,
          0.45F * (1.0F + distance / 50.0F),
          Waves.argb(GLINT, alpha),
          Shape.GLOW,
          0.3F,
          0);
    }
  }

  private static void flash(
      Shell shell, View view, Sink sink, float fogStart, float fogEnd, float glow) {
    float dx = (float) (shell.x - view.x);
    float dy = (float) (shell.y - view.y);
    float dz = (float) (shell.z - view.z);
    float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    float since = shell.age - shell.fuse;
    float clear = glow * fog(fogStart, fogEnd, distance);
    float sky = 1.0F - since / SKY;
    float wash = Math.min(1.0F, sky * sky * 0.14F * clear);
    if (wash > GONE) {
      float half = SKY_SIZE * (0.8F + 0.2F * Waves.smooth(0.0F, 0.5F, since));
      billboard(view, sink, dx, dy, dz, half, Waves.argb(shell.colour, wash), Shape.HALO, 0.0F, 0);
    }
    float burst = Math.max(0.0F, 1.0F - since / FLASH);
    float alpha = Math.min(1.0F, burst * burst * 0.75F * clear);
    if (alpha > GONE) {
      billboard(
          view,
          sink,
          dx,
          dy,
          dz,
          FLASH_SIZE * (1.4F - burst * 0.4F),
          Waves.argb(Waves.mix(shell.colour, 0xFFFFFF, 0.5F), alpha),
          Shape.HALO,
          0.0F,
          0);
    }
  }

  private static void draw(
      Spark spark, View view, Sink sink, float fogStart, float fogEnd, float glow, float time) {
    float dx = (float) (spark.x - view.x);
    float dy = (float) (spark.y - view.y);
    float dz = (float) (spark.z - view.z);
    if (dx * view.fx + dy * view.fy + dz * view.fz < 0.0F) {
      return;
    }
    float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    float progress = spark.age / spark.life;
    float alpha =
        (1.0F - Waves.smooth(0.55F, 1.0F, progress)) * glow * fog(fogStart, fogEnd, distance);
    int seed = (int) (spark.seed * SEEDS);
    float grow = 1.0F + distance / 50.0F;
    if (spark.trait == Trait.EMBER) {
      float twinkle = 0.55F + 0.45F * Waves.sin(time * 30.0F + spark.seed * 70.0F);
      alpha = Math.min(1.0F, alpha * twinkle);
      if (alpha > GONE) {
        billboard(
            view,
            sink,
            dx,
            dy,
            dz,
            0.22F * grow,
            Waves.argb(spark.from, alpha),
            Shape.GLOW,
            0.0F,
            seed);
      }
      return;
    }
    if (spark.trait == Trait.CRACKLE && progress > 0.5F) {
      float strobe = time * 9.0F + spark.seed * 13.0F;
      if (strobe - (float) Math.floor(strobe) < 0.3F) {
        billboard(
            view,
            sink,
            dx,
            dy,
            dz,
            0.55F * grow,
            Waves.argb(0xFFFFFF, Math.min(1.0F, alpha * 1.4F)),
            Shape.GLOW,
            0.8F,
            seed);
      }
      return;
    }
    alpha = Math.min(1.0F, alpha);
    if (alpha <= GONE) {
      return;
    }
    float sx = spark.vx * view.rx + spark.vy * view.ry + spark.vz * view.rz;
    float sy = spark.vx * view.ux + spark.vy * view.uy + spark.vz * view.uz;
    float across = (float) Math.sqrt(sx * sx + sy * sy);
    if (across < 1.0e-3F) {
      sx = 0.0F;
      sy = 1.0F;
      across = 1.0F;
    }
    sx /= across;
    sy /= across;
    float speed =
        (float) Math.sqrt(spark.vx * spark.vx + spark.vy * spark.vy + spark.vz * spark.vz);
    float half = 0.14F * grow;
    float along = half * (1.5F + speed * 0.35F * spark.streak);
    float wide = half * (1.0F + BLOOM * 2.5F);
    int base =
        spark.from == spark.to
            ? spark.from
            : Waves.mix(spark.from, spark.to, Waves.smooth(0.4F, 0.6F, progress));
    int colour = Waves.mix(base, 0xFFFFFF, Math.max(0.0F, 1.0F - spark.age / HOT));
    sink.quad(
        dx,
        dy,
        dz,
        (view.rx * sx + view.ux * sy) * along,
        (view.ry * sx + view.uy * sy) * along,
        (view.rz * sx + view.uz * sy) * along,
        (view.ux * sx - view.rx * sy) * wide,
        (view.uy * sx - view.ry * sy) * wide,
        (view.uz * sx - view.rz * sy) * wide,
        Waves.argb(colour, alpha),
        Shape.SPARK,
        BLOOM,
        seed);
  }
}
