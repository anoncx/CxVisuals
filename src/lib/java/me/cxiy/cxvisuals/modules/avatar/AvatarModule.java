package me.cxiy.cxvisuals.modules.avatar;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.KeyHandle;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;
import me.cxiy.cxvisuals.core.settings.TextSetting;
import me.cxiy.cxvisuals.modules.avatar.bake.Tune;
import me.cxiy.cxvisuals.modules.avatar.format.RawModel;

public final class AvatarModule extends Module {
  public enum Detail {
    LOW(10_000, 512),
    MEDIUM(25_000, 1024),
    HIGH(50_000, 1024);

    final int triangles;
    final int edge;

    Detail(int triangles, int edge) {
      this.triangles = triangles;
      this.edge = edge;
    }
  }

  public enum Turn {
    NONE,
    QUARTER,
    HALF,
    THREE_QUARTERS
  }

  public static final String ID = "avatar";
  public static final String MODEL_PAGE = "model";

  public final TextSetting selected;
  public final PanelSetting gallery;
  public final EnumSetting<RawModel.Axis> up;
  public final EnumSetting<Turn> turn;
  public final NumberSetting size;
  public final NumberSetting lift;
  public final BoolSetting clips;
  public final PanelSetting animations;
  public final NumberSetting motion;
  public final BoolSetting squash;
  public final EnumSetting<Detail> detail;
  private final KeyHandle nextKey;

  public AvatarModule(KeyBinder keys) {
    super(ID, "Avatar", false, keys);
    selected = settings.text("selected", "");
    gallery = section("wardrobe", "Wardrobe").group("Avatars").panel("gallery");

    Page model = section(MODEL_PAGE, "Model");
    Group fit = model.group("Fit").unsaved();
    up =
        fit.enumOf("up", "Up axis", RawModel.Axis.Y, "Y up", "Z up")
            .describe("Pick Z up if the model lies on its back.");
    turn =
        fit.enumOf("turn", "Turn", Turn.NONE, "0°", "90°", "180°", "270°")
            .describe("Turns the model to face where you walk.");
    size = fit.number("size", "Size", 100.0, Tune.MIN_SIZE, Tune.MAX_SIZE, 5.0, "%");
    lift =
        fit.number("lift", "Lift", 0.0, -Tune.MAX_LIFT, Tune.MAX_LIFT, 0.05, "")
            .describe("Raises or sinks the model, in blocks.");
    Group animation = model.group("Animation").unsaved();
    clips =
        animation
            .bool("clips", "Use model animations", true)
            .describe("Plays the model's own idle, walk and run clips when it has them.");
    animations = animation.panel("animations");

    Page moves = section("motion", "Motion");
    Group feel = moves.group("Feel");
    motion =
        feel.number("motion", "Motion", 100.0, 0.0, 200.0, 5.0, "%")
            .describe("Bob, waddle and lean while you move.");
    squash = feel.bool("squash", "Squash & stretch", true);
    detail =
        moves
            .group("Performance")
            .enumOf("detail", "Detail", Detail.MEDIUM, "Low", "Medium", "High")
            .describe("Most triangles drawn: 10K, 25K or 50K.");
    nextKey = keys.bind(ID + ".next", "Next avatar", KeyBinder.UNBOUND);
    moves.group("Keys").key("next_key", "Next avatar", nextKey);
  }

  public KeyHandle nextKey() {
    return nextKey;
  }

  public Tune tune() {
    return new Tune(up.get(), turn.get().ordinal(), size.get(), lift.get(), clips.get());
  }

  public void show(Tune tune) {
    up.set(tune.up());
    turn.set(Turn.values()[tune.turn()]);
    size.set(tune.size());
    lift.set(tune.lift());
    clips.set(tune.clips());
  }

  public int triangles() {
    return detail.get().triangles;
  }

  public int textureEdge() {
    return detail.get().edge;
  }

  public double motionScale() {
    return motion.get() / 100.0;
  }

  @Override
  public String group() {
    return "Player";
  }
}
