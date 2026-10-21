package me.cxiy.cxvisuals.modules.social;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;
import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class SocialModule extends Module {
  public static final String ID = "social";
  public static final String DEFAULT_SERVER = "https://visuals.cxiy.dev";

  public final Account account = new Account();
  public final TextSetting server;
  public final BoolSetting connected;
  public final PanelSetting profile;
  public final PanelSetting nearby;
  public final PanelSetting blocked;
  public final PanelSetting erase;
  public final BoolSetting shareAvatar;
  public final BoolSetting shareMusic;
  public final BoolSetting seeAvatars;
  public final BoolSetting seeMusic;

  public SocialModule(KeyBinder keys) {
    super(ID, "Account", true, keys);
    server = settings.text("server", DEFAULT_SERVER);
    connected = settings.toggle("connected", "Connected", false);
    Page page = section("account", "Account");
    profile = page.group("Profile").panel("profile");
    Group share = page.group("Share").showWhen(account::signedIn);
    shareAvatar =
        share
            .bool("share_avatar", "Avatar", true)
            .describe("Players nearby see the avatar you wear.");
    shareMusic =
        share
            .bool("share_music", "Music", true)
            .describe("Players nearby see the song you play. Title and artist only, never audio.");
    Group see = page.group("See").showWhen(account::signedIn);
    seeAvatars =
        see.bool("see_avatars", "Avatars", true).describe("Show the avatars of players nearby.");
    seeMusic =
        see.bool("see_music", "Music", true).describe("Show what players nearby are listening to.");
    nearby = page.group("Nearby").showWhen(account::signedIn).panel("nearby");
    blocked =
        page.group("Blocked")
            .showWhen(() -> account.signedIn() && !account.blocked().isEmpty())
            .panel("blocked");
    erase = page.group("Cloud data").showWhen(account::signedIn).panel("erase");
  }

  @Override
  public boolean pinned() {
    return true;
  }

  @Override
  public boolean live() {
    return enabled.get() && account.online();
  }

  public String baseUrl() {
    return SocialProtocol.normalizeBase(server.get());
  }
}
