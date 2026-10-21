package me.cxiy.cxvisuals.modules.idlecamera.camera;

import java.util.List;
import me.cxiy.cxvisuals.modules.idlecamera.camera.shots.CloseUpShot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.shots.CraneShot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.shots.DollyShot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.shots.HeroShot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.shots.OrbitShot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.shots.PushInShot;

public final class ShotRegistry {
  public static final ShotType ORBIT = new ShotType("orbit", "Orbit", 1.5, OrbitShot::new);
  public static final ShotType HERO = new ShotType("hero", "Hero", 1.0, HeroShot::new);
  public static final ShotType PUSH_IN = new ShotType("push_in", "Push-in", 1.4, PushInShot::new);
  public static final ShotType CRANE = new ShotType("crane", "Crane", 1.2, CraneShot::new);
  public static final ShotType DOLLY = new ShotType("dolly", "Dolly", 1.5, DollyShot::new);
  public static final ShotType CLOSE_UP =
      new ShotType("close_up", "Close-up", 0.6, CloseUpShot::new);

  public static final List<ShotType> ALL = List.of(ORBIT, HERO, PUSH_IN, CRANE, DOLLY, CLOSE_UP);

  private ShotRegistry() {}

  public static ShotType byId(String id) {
    for (int i = 0; i < ALL.size(); i++) {
      ShotType type = ALL.get(i);
      if (type.id().equals(id)) {
        return type;
      }
    }
    return null;
  }
}
