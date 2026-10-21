package me.cxiy.cxvisuals.modules.friends;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;
import me.cxiy.cxvisuals.core.settings.TextSetting;
import me.cxiy.cxvisuals.modules.social.Account;

public final class FriendsModule extends Module {
  public enum Mode {
    ONLINE("online"),
    BUSY("busy"),
    HIDDEN("hidden");

    public final String wire;

    Mode(String wire) {
      this.wire = wire;
    }
  }

  public static final String ID = "friends";
  public static final String PEOPLE = "people";
  public static final String CHAT = "chat";
  public static final int STATUS_LIMIT = 48;

  public final Friends friends = new Friends();
  public final Account account;
  public final PanelSetting people;
  public final PanelSetting chat;
  public final EnumSetting<Mode> mode;
  public final TextSetting status;
  public final BoolSetting shareServer;
  public final BoolSetting shareLocation;
  public final BoolSetting toasts;
  public final BoolSetting sound;
  public final TextSetting favorites;
  public final TextSetting read;

  public FriendsModule(KeyBinder keys, Account account) {
    super(ID, "Friends", true, keys);
    this.account = account;
    favorites = settings.text("favorites", "");
    read = settings.text("read", "");
    Page list = section(PEOPLE, "Friends");
    list.group("Locked")
        .showWhen(() -> !account.signedIn())
        .info("locked", "Friends", "Connect on the Account page first");
    people = list.group("People").showWhen(account::signedIn).panel(PEOPLE);
    Page talk = section(CHAT, "Chat");
    chat = talk.group("Conversation").panel(CHAT);
    Page you = section("you", "You");
    Group presence = you.group("Presence");
    mode =
        presence
            .enumOf("mode", "Show me as", Mode.ONLINE, "Online", "Busy", "Invisible")
            .describe("Busy mutes notifications. Invisible shows you as offline.");
    status =
        presence
            .text("status", "Status", "")
            .describe("A short line your friends see under your name.");
    shareServer =
        presence
            .bool("share_server", "Share my server", false)
            .describe(
                "Friends see the server you play on and can join. Never shared for singleplayer or LAN.");
    shareLocation =
        presence
            .bool("share_location", "Share my location", false)
            .describe(
                "Friends on the same server who also share theirs see you on their compass. Never shared for singleplayer or LAN.");
    Group notify = you.group("Notifications");
    toasts =
        notify
            .bool("toasts", "Notifications", true)
            .describe("A short banner for messages, waves and friends coming online.");
    sound =
        notify.bool("sound", "Notification sound", true).describe("A soft chime with each banner.");
  }

  @Override
  public boolean pinned() {
    return true;
  }

  @Override
  public boolean live() {
    return enabled.get() && account.signedIn() && friends.unread() > 0;
  }

  public boolean active() {
    return enabled.get() && account.online();
  }

  public String statusLine() {
    String line = status.get().strip();
    return line.length() > STATUS_LIMIT ? line.substring(0, STATUS_LIMIT) : line;
  }
}
