package me.cxiy.cxvisuals.mc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.core.update.Release;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.social.Social;
import me.cxiy.cxvisuals.modules.friends.Friends;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class Updates {
  private static final String PATH = "/v2/client?mc=";
  private static final long FIRST_CHECK = 15_000_000_000L;
  private static final long EVERY = 1_800_000_000_000L;
  private static final int BUFFER = 65536;
  private static final float PITCH_HIGH = 1.3F;
  private static final String TITLE = Lang.key("CxVisuals update");
  private static final String BODY = Lang.key("Relaunch CxLauncher or get the jar");
  private static final String TAG = Lang.key("NEW");

  private static ClientSettings settings;
  private static final String MINECRAFT = SharedConstants.getCurrentVersion().name();
  private static volatile String installed = "";
  private static volatile Release latest = Release.NONE;
  private static String announced = "";
  private static long nextCheck;
  private static volatile boolean checking;

  private Updates() {}

  public static void start(ClientSettings client) {
    settings = client;
    client.noticeSound.onChange(() -> ping(Minecraft.getInstance()));
    nextCheck = System.nanoTime() + FIRST_CHECK;
    Thread hasher = new Thread(Updates::hash, "CxVisuals-Update-Hash");
    hasher.setDaemon(true);
    hasher.start();
  }

  public static void tick(Minecraft mc) {
    if (settings == null || !settings.updateNotice.get() || installed.isEmpty()) {
      return;
    }
    long now = System.nanoTime();
    if (!checking && now - nextCheck >= 0L) {
      checking = true;
      nextCheck = now + EVERY;
      Thread worker = new Thread(Updates::fetch, "CxVisuals-Update-Check");
      worker.setDaemon(true);
      worker.start();
    }
    Release release = latest;
    if (mc.player != null
        && Hud.module() != null
        && release.newerThan(installed)
        && !release.sha1().equals(announced)) {
      announced = release.sha1();
      announce(mc, release);
    }
  }

  public static void announce(Minecraft mc, Release release) {
    Hud.module()
        .toasts
        .stack
        .push(
            new Friends.Toast("", Lang.tr(TITLE), Lang.tr(BODY), Friends.NO_ENTITY, Lang.tr(TAG)));
    ping(mc);
  }

  public static void ping(Minecraft mc) {
    ClientSettings.NoticeSound sound = settings.noticeSound.get();
    float volume = settings.noticeVolume.get().floatValue() / 100.0F;
    if (sound == ClientSettings.NoticeSound.OFF || volume <= 0.0F) {
      return;
    }
    SoundEvent event =
        switch (sound) {
          case BELL -> SoundEvents.NOTE_BLOCK_BELL.value();
          case CHIME -> SoundEvents.AMETHYST_BLOCK_CHIME;
          case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
          case HARP -> SoundEvents.NOTE_BLOCK_HARP.value();
          case ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
          default -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
        };
    float pitch =
        sound == ClientSettings.NoticeSound.CHIME || sound == ClientSettings.NoticeSound.PLING
            ? PITCH_HIGH
            : 1.0F;
    mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
  }

  private static void hash() {
    try {
      Path origin =
          FabricLoader.getInstance()
              .getModContainer(CxVisualsClient.MOD_ID)
              .map(container -> container.getOrigin().getPaths().get(0))
              .orElse(null);
      if (origin == null || !Files.isRegularFile(origin)) {
        return;
      }
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      try (InputStream in = Files.newInputStream(origin)) {
        byte[] chunk = new byte[BUFFER];
        for (int read = in.read(chunk); read >= 0; read = in.read(chunk)) {
          digest.update(chunk, 0, read);
        }
      }
      installed = HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
      CxVisualsClient.LOG.warn(
          "Update check disabled, the installed jar could not be read: {}", e.toString());
    }
  }

  private static void fetch() {
    try {
      latest = Release.parse(Social.read(PATH + MINECRAFT), MINECRAFT);
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.debug("Update check failed: {}", e.toString());
    } finally {
      checking = false;
    }
  }
}
