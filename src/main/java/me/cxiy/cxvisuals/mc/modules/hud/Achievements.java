package me.cxiy.cxvisuals.mc.modules.hud;

import java.util.Optional;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.mc.mixin.AdvancementToastAccessor;
import me.cxiy.cxvisuals.mc.modules.inventory.Stacks;
import me.cxiy.cxvisuals.modules.hud.Achievement;
import me.cxiy.cxvisuals.modules.hud.HudModule;
import me.cxiy.cxvisuals.modules.hud.elements.AchievementsElement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

public final class Achievements {
  private static final String RECIPE_DETAIL = Lang.key("Check your recipe book");

  private Achievements() {}

  private static AchievementsElement element() {
    HudModule module = Hud.module();
    if (module == null || !module.enabled.get() || !module.achievements.shown.get()) {
      return null;
    }
    return module.achievements;
  }

  public static boolean intercept(Toast toast) {
    AchievementsElement element = element();
    if (element == null) {
      return false;
    }
    if (toast instanceof RecipeToast) {
      return element.recipes.get();
    }
    if (!(toast instanceof AdvancementToast advancement)) {
      return false;
    }
    Achievement card = advancement(advancement);
    if (card == null) {
      return false;
    }
    element.offer(card);
    SoundEvent sound = toast.getSoundEvent();
    if (element.sound.get() && sound != null) {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 1.0F));
    }
    return true;
  }

  public static void recipe(ItemStack unlocked) {
    AchievementsElement element = element();
    if (element == null || !element.recipes.get() || unlocked.isEmpty()) {
      return;
    }
    element.offer(
        new Achievement(
            Achievement.Kind.RECIPE,
            unlocked.getHoverName().getString(),
            Lang.tr(RECIPE_DETAIL),
            icon(unlocked)));
  }

  private static Achievement advancement(AdvancementToast toast) {
    AdvancementToastAccessor access = (AdvancementToastAccessor) toast;
    AdvancementHolder holder = access.cxvisuals$advancement();
    Optional<DisplayInfo> display = holder.value().display();
    if (display.isEmpty()) {
      return null;
    }
    DisplayInfo info = display.get();
    Achievement.Kind kind =
        switch (info.type()) {
          case CHALLENGE -> Achievement.Kind.CHALLENGE;
          case GOAL -> Achievement.Kind.GOAL;
          case TASK -> Achievement.Kind.TASK;
        };
    return new Achievement(
        kind,
        info.title().getString(),
        info.description().getString(),
        icon(access.cxvisuals$icon()));
  }

  private static int icon(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
      return Achievement.NO_ICON;
    }
    return Stacks.register(Stacks.keyOf(stack), stack, "", "");
  }
}
