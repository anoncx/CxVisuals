package me.cxiy.cxvisuals.mc.modules.hands;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.modules.hands.HandView;
import me.cxiy.cxvisuals.modules.hands.HandsModule;
import me.cxiy.cxvisuals.modules.hands.PoseSink;
import me.cxiy.cxvisuals.modules.hands.SwingAnimation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

public final class Hands {
  private static final float RADIANS = (float) (Math.PI / 180.0);
  private static final long START = System.nanoTime();

  private static final class StackSink implements PoseSink {
    private final Quaternionf turn = new Quaternionf();
    private PoseStack stack;

    @Override
    public void translate(float x, float y, float z) {
      stack.translate(x, y, z);
    }

    @Override
    public void rotateX(float degrees) {
      stack.mulPose(turn.rotationX(degrees * RADIANS));
    }

    @Override
    public void rotateY(float degrees) {
      stack.mulPose(turn.rotationY(degrees * RADIANS));
    }

    @Override
    public void rotateZ(float degrees) {
      stack.mulPose(turn.rotationZ(degrees * RADIANS));
    }

    @Override
    public void scale(float factor) {
      stack.scale(factor, factor, factor);
    }
  }

  private static final StackSink SINK = new StackSink();
  private static HandsModule module;

  private Hands() {}

  public static HandsModule create(McKeyBinder keys) {
    module = new HandsModule(keys);
    return module;
  }

  public static HandsModule module() {
    return module;
  }

  private static boolean active() {
    return module != null && module.enabled.get();
  }

  private static HandView view(InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND ? module.main : module.off;
  }

  private static int side(HumanoidArm arm) {
    return arm == HumanoidArm.RIGHT ? 1 : -1;
  }

  public static void position(PoseStack stack, InteractionHand hand, HumanoidArm arm) {
    if (!active()) {
      return;
    }
    SINK.stack = stack;
    view(hand).position(SINK, side(arm));
    SINK.stack = null;
  }

  public static void item(PoseStack stack, InteractionHand hand, HumanoidArm arm) {
    if (!active()) {
      return;
    }
    SINK.stack = stack;
    int side = side(arm);
    view(hand).orient(SINK, side);
    float spin = module.idleAngle((System.nanoTime() - START) / 1.0e9);
    if (spin != 0.0F) {
      SINK.rotateY(side * spin);
    }
    SINK.stack = null;
  }

  public static boolean swing(PoseStack stack, float attack, int side) {
    if (!active()) {
      return false;
    }
    SwingAnimation animation = module.animation.get();
    if (!animation.replacesVanilla()) {
      return false;
    }
    SINK.stack = stack;
    animation.apply(SINK, attack, side, module.intensity.get().floatValue());
    SINK.stack = null;
    return true;
  }

  public static float equipHeight(float vanilla) {
    return active() ? module.equipHeight(vanilla) : vanilla;
  }

  public static int swingDuration(int vanilla) {
    return active() ? module.swingDuration(vanilla) : vanilla;
  }
}
