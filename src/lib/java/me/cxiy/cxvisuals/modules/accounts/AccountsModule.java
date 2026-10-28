package me.cxiy.cxvisuals.modules.accounts;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.InfoSetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;

public final class AccountsModule extends Module {
  public enum Rejoin {
    ASK,
    ALWAYS,
    NEVER
  }

  public static final String ID = "accounts";
  public static final String OPEN_KEY = "accounts.open";

  public final BoolSetting lookup;
  public final BoolSetting autoSwitch;
  public final EnumSetting<Rejoin> rejoin;
  public final NumberSetting settle;
  public final BoolSetting closeOnUse;
  public final BoolSetting confirmRemove;
  public final BoolSetting openBrowser;
  public final BoolSetting copyCode;
  public final InfoSetting vaultStatus;

  public AccountsModule(KeyBinder keys, Runnable open, Runnable lock) {
    super(ID, "Accounts", true, keys);
    hostedBy("social", 1);
    Page page = section("manager", "Accounts");

    Group window = page.group("Window");
    window
        .key("open_key", "Open key", keys.bind(OPEN_KEY, "Open accounts", KeyBinder.UNBOUND))
        .describe("Opens the account manager from anywhere in game.");
    closeOnUse = window.bool("close_on_use", "Close after switching", true);
    confirmRemove =
        window
            .bool("confirm_remove", "Confirm removing", true)
            .describe("Asks for a second click before an account is deleted.");
    action("open", "Manage accounts", ActionSetting.Kind.PRIMARY, open);

    Group servers = page.group("Servers");
    autoSwitch =
        servers
            .bool("auto_switch", "Switch to the linked account", true)
            .describe("Joining a server that has a linked account switches to it first.");
    rejoin =
        servers
            .enumOf("rejoin", "Rejoin after switching", Rejoin.ASK, "Ask", "Always", "Never")
            .describe("What happens to the server you are on when you switch account in game.");
    settle =
        servers
            .number("settle", "Reconnect delay", 1.5, 0.5, 10.0, 0.5, "s")
            .describe(
                "How long to wait after leaving a server before joining again, so it does not see two logins.");

    Group microsoft = page.group("Microsoft");
    openBrowser =
        microsoft
            .bool("open_browser", "Open the login page", true)
            .describe("Opens Microsoft's sign-in page in your browser when you add an account.");
    copyCode =
        microsoft
            .bool("copy_code", "Copy the login code", true)
            .describe("Puts the code on your clipboard so you can paste it straight in.");

    Page security = section("security", "Security");
    Group safe = security.group("Protection");
    vaultStatus = safe.info("vault_status", "Vault", "Not set up");
    lookup =
        safe.bool("lookup", "Look up skins and names online", true)
            .describe(
                "Asks Ashcon for the skin and name history of your accounts. Turn it off to keep every name off the network.");
    safe.action("lock", "Lock the vault", "Lock", ActionSetting.Kind.QUIET, lock);
  }

  public float settleSeconds() {
    return settle.get().floatValue();
  }

  @Override
  public String group() {
    return "Player";
  }
}
