package me.cxiy.cxvisuals.mc.gui;

import java.util.EnumMap;
import me.cxiy.cxvisuals.gui.Sound;
import me.cxiy.cxvisuals.mc.CxVisuals;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class UiSounds {
  private static final float FULL = 100.0F;
  private static final float STEP_SHARE = 0.55F;
  private static final long STEP_GAP_NANOS = 40_000_000L;
  private static final float[] STEP_PITCH = {1.0F, 0.94F, 1.06F, 0.97F, 1.03F, 0.91F};

  private static final EnumMap<Sound.Cue, SoundEvent> EVENTS = new EnumMap<>(Sound.Cue.class);

  private static long lastStep;
  private static int stepIndex;

  private UiSounds() {}

  public static void init() {
    for (Sound.Cue cue : Sound.Cue.values()) {
      String path = "ui." + cue.name().toLowerCase();
      EVENTS.put(
          cue,
          SoundEvent.createVariableRangeEvent(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, path)));
    }
    Sound.sink(UiSounds::play);
  }

  private static void play(Sound.Cue cue) {
    float volume = CxVisuals.registry().client().menuVolume.get().floatValue() / FULL;
    if (volume <= 0.0F) {
      return;
    }
    if (cue == Sound.Cue.STEP) {
      long now = System.nanoTime();
      if (now - lastStep < STEP_GAP_NANOS) {
        return;
      }
      lastStep = now;
      volume *= STEP_SHARE;
    }
    float pitch = cue == Sound.Cue.STEP ? STEP_PITCH[stepIndex++ % STEP_PITCH.length] : 1.0F;
    Minecraft.getInstance()
        .getSoundManager()
        .play(SimpleSoundInstance.forUI(EVENTS.get(cue), pitch, volume));
  }
}
