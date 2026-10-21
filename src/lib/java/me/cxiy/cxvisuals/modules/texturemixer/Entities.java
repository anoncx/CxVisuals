package me.cxiy.cxvisuals.modules.texturemixer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Entities {
  private record Spec(String id, String texture, float zoom, Supplier<Shape> shape) {}

  private static final List<Spec> SPECS =
      List.of(
          new Spec(
              "end_crystal",
              "entity/end_crystal/end_crystal",
              1.45F,
              () ->
                  new Models("entity/end_crystal/end_crystal", 128, 64)
                      .shift(8.0F, 14.0F, 8.0F)
                      .cuboid(-12, 12, -12, 24, 8, 24, 0, 32, 0.0F)
                      .cuboid(-8, -8, -8, 16, 16, 16, 64, 0, -1.875F)
                      .cuboid(-8, -8, -8, 16, 16, 16, 0, 0, -1.0F)
                      .cuboid(-8, -8, -8, 16, 16, 16, 0, 0, 0.0F)
                      .build()),
          new Spec(
              "shield",
              "entity/shield/shield_base_nopattern",
              1.25F,
              () ->
                  new Models("entity/shield/shield_base_nopattern", 64, 64)
                      .shift(8.0F, 8.0F, 8.0F)
                      .cuboid(-6, -11, -2, 12, 22, 1, 0, 0, 0.0F)
                      .cuboid(-1, -3, -1, 2, 6, 6, 26, 0, 0.0F)
                      .build()),
          new Spec(
              "trident",
              "entity/trident/trident",
              1.3F,
              () ->
                  new Models("entity/trident/trident", 32, 32)
                      .shift(8.0F, 19.5F, 8.0F)
                      .cuboid(-0.5F, 2, -0.5F, 1, 25, 1, 0, 6, 0.0F)
                      .cuboid(-1.5F, 0, -0.5F, 3, 2, 1, 4, 0, 0.0F)
                      .cuboid(-2.5F, -3, -0.5F, 1, 4, 1, 4, 3, 0.0F)
                      .cuboid(-0.5F, -4, -0.5F, 1, 4, 1, 0, 0, 0.0F)
                      .cuboid(1.5F, -3, -0.5F, 1, 4, 1, 4, 3, 0.0F, true)
                      .build()),
          new Spec(
              "elytra",
              "entity/equipment/wings/elytra",
              1.25F,
              () ->
                  new Models("entity/equipment/wings/elytra", 64, 32)
                      .shift(8.0F, 18.0F, 8.0F)
                      .cuboid(-11, 0, 0, 10, 20, 2, 22, 0, 0.0F)
                      .cuboid(1, 0, 0, 10, 20, 2, 22, 0, 0.0F, true)
                      .build()));

  private Entities() {}

  public static List<Entry> build(Assets vanilla) {
    List<Entry> out = new ArrayList<>(SPECS.size());
    for (Spec spec : SPECS) {
      if (vanilla.has("textures/" + spec.texture() + ".png")) {
        out.add(
            new Entry(
                spec.id(),
                Entry.Kind.ENTITY,
                List.of(new Entry.State("", spec.shape().get())),
                spec.zoom()));
      }
    }
    return List.copyOf(out);
  }
}
