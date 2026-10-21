package me.cxiy.cxvisuals.mc.menu;

import me.cxiy.cxvisuals.mc.CxVisuals;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.menu.views.Sfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

final class McSfx implements Sfx {
  private static final SoundEvent TICK = event("menu.tick");
  private static final SoundEvent THOCK = event("menu.thock");
  private static final SoundEvent LAUNCH = event("menu.launch");
  private static final SoundEvent BACK = event("menu.back");
  private static final SoundEvent TURN = event("menu.turn");
  private static final float HOVER_PITCH = 0.96F;
  private static final float HOVER_STEP = 0.03F;
  private static final float HOVER_SHARE = 0.55F;
  private static final float FULL = 100.0F;
  private static final float STEADY = 1.0F;

  private static SoundEvent event(String path) {
    return SoundEvent.createVariableRangeEvent(
        Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, path));
  }

  private static void play(SoundEvent sound, float pitch, float share) {
    float volume = CxVisuals.registry().client().menuVolume.get().floatValue() / FULL * share;
    if (volume > 0.0F) {
      Minecraft.getInstance()
          .getSoundManager()
          .play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
  }

  @Override
  public void hover(int column) {
    play(TICK, HOVER_PITCH + HOVER_STEP * column, HOVER_SHARE);
  }

  @Override
  public void press() {
    play(THOCK, STEADY, STEADY);
  }

  @Override
  public void launch() {
    play(LAUNCH, STEADY, STEADY);
  }

  @Override
  public void back() {
    play(BACK, STEADY, STEADY);
  }

  @Override
  public void turn() {
    play(TURN, STEADY, STEADY);
  }
}
