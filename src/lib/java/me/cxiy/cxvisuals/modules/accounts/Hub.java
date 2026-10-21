package me.cxiy.cxvisuals.modules.accounts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Hub {
  public enum Tone {
    INFO,
    GOOD,
    BAD
  }

  public record Status(String text, Tone tone, long at) {}

  public record Ready(String id, String name, String uuid, String token) {}

  public interface Outcome {
    void done(Ready ready, String error);
  }

  private record Fresh(String token, long until) {}

  private static final long TOKEN_LIFE_MILLIS = 20L * 60L * 60L * 1000L;
  private static final long NOTICE_MILLIS = 4_500L;

  private final AccountsModule module;
  private final Platform platform;
  private final Vault vault;
  private final AccountBook book;
  private final Links links;
  private final Faces faces;
  private final Microsoft microsoft;
  private final Profiles profiles;
  private final ExecutorService io =
      Executors.newCachedThreadPool(
          task -> {
            Thread thread = new Thread(task, "CxVisuals-Accounts");
            thread.setDaemon(true);
            return thread;
          });
  private final Map<String, Fresh> fresh = new ConcurrentHashMap<>();

  private volatile Status status = new Status("", Tone.INFO, 0L);
  private final AtomicInteger working = new AtomicInteger();
  private volatile Microsoft.Flow flow;
  private volatile Microsoft.Code code;
  private volatile boolean signing;
  private volatile int serial;

  public Hub(AccountsModule module, Path directory, Http http, Platform platform) {
    this.module = module;
    this.platform = platform;
    this.vault = new Vault(directory, Machine::fingerprint);
    this.book = new AccountBook(vault);
    this.links = new Links(vault);
    this.microsoft = new Microsoft(http);
    this.profiles = new Profiles(http);
    this.faces = new Faces(directory.resolve("heads"), profiles, io, module.lookup::get);
  }

  public AccountsModule module() {
    return module;
  }

  public Vault vault() {
    return vault;
  }

  public AccountBook book() {
    return book;
  }

  public Links links() {
    return links;
  }

  public Faces faces() {
    return faces;
  }

  public Platform platform() {
    return platform;
  }

  public Status status() {
    Status now = status;
    return System.currentTimeMillis() - now.at > NOTICE_MILLIS && !busy() ? null : now;
  }

  public boolean busy() {
    return working.get() > 0;
  }

  public Microsoft.Code code() {
    return code;
  }

  public boolean signing() {
    return signing;
  }

  public void say(String text, Tone tone) {
    status = new Status(text, tone, System.currentTimeMillis());
  }

  public void start() {
    if (vault.open()) {
      return;
    }
    if (vault.autoUnlock()) {
      load();
    }
  }

  public void startLater() {
    work(this::start);
  }

  public Vault.State vaultState() {
    return vault.state();
  }

  public void describeVault() {
    module.vaultStatus.show(
        switch (vault.state()) {
          case NONE -> Lang.tr("Not set up");
          case LOCKED -> Lang.tr("Locked");
          case OPEN ->
              vault.method() == Vault.Method.PASSWORD
                  ? Lang.tr("Open, protected by a password")
                  : Lang.tr("Open, tied to this device");
        });
  }

  public void setup(Vault.Method method, char[] password) {
    work(
        () -> {
          boolean made = vault.create(method, password);
          wipe(password);
          if (made) {
            load();
            platform.main(() -> say(Lang.tr("Your accounts are protected."), Tone.GOOD));
          } else {
            platform.main(
                () ->
                    say(Lang.tr("The vault could not be created, check the password."), Tone.BAD));
          }
        });
  }

  public void unlock(char[] password) {
    work(
        () -> {
          boolean opened = vault.unlock(password);
          wipe(password);
          if (opened) {
            load();
            platform.main(() -> say(Lang.tr("Unlocked."), Tone.GOOD));
          } else {
            platform.main(() -> say(Lang.tr("That is not the right password."), Tone.BAD));
          }
        });
  }

  public void protect(Vault.Method method, char[] current, char[] next) {
    work(
        () -> {
          boolean changed = vault.convert(method, current, next);
          wipe(current);
          wipe(next);
          platform.main(
              () ->
                  say(
                      changed
                          ? Lang.tr("Protection updated.")
                          : Lang.tr("The current password is wrong, or the new one is too short."),
                      changed ? Tone.GOOD : Tone.BAD));
        });
  }

  public void lock() {
    cancelMicrosoft();
    vault.lock();
    book.clear();
    links.clear();
    fresh.clear();
    describeVault();
  }

  public void reset() {
    lock();
    vault.destroy();
    describeVault();
    say(Lang.tr("The vault was erased."), Tone.INFO);
  }

  private void load() {
    boolean clean = book.load();
    links.load();
    describeVault();
    if (!clean) {
      platform.main(() -> say(Lang.tr("Some saved accounts could not be read."), Tone.BAD));
    }
  }

  public Entry active() {
    Entry match = book.byUuid(platform.activeUuid());
    if (match != null) {
      return match;
    }
    String name = platform.activeName();
    for (Entry entry : book.all()) {
      if (entry.kind() == Kind.OFFLINE && entry.username.equalsIgnoreCase(name)) {
        return entry;
      }
    }
    return null;
  }

  public boolean isActive(Entry entry) {
    Entry active = active();
    return active != null && active.id.equals(entry.id);
  }

  public Entry linked(String address) {
    if (!module.live() || !module.autoSwitch.get() || !vault.open()) {
      return null;
    }
    String id = links.get(address);
    return id == null ? null : book.find(id);
  }

  public void beginMicrosoft() {
    cancelMicrosoft();
    int turn = ++serial;
    signing = true;
    code = null;
    flow =
        microsoft.begin(
            new Microsoft.Listener() {
              @Override
              public void code(Microsoft.Code next) {
                if (turn == serial) {
                  code = next;
                }
              }

              @Override
              public void done(Microsoft.Login login) {
                if (turn != serial) {
                  return;
                }
                Entry entry =
                    new Entry(
                        Kind.MICROSOFT,
                        login.username(),
                        login.uuid(),
                        login.refresh(),
                        System.currentTimeMillis());
                AccountBook.Added added = book.add(entry);
                fresh.put(
                    added.entry().id,
                    new Fresh(login.token(), System.currentTimeMillis() + TOKEN_LIFE_MILLIS));
                skin(added.entry(), login.token());
                signing = false;
                code = null;
                platform.main(
                    () ->
                        say(
                            added.fresh()
                                ? Lang.format("Added {0}.", login.username())
                                : Lang.format(
                                    "{0} was already saved and has been updated.",
                                    login.username()),
                            Tone.GOOD));
              }

              @Override
              public void failed(String reason) {
                if (turn != serial) {
                  return;
                }
                signing = false;
                code = null;
                platform.main(() -> say(reason, Tone.BAD));
              }
            });
  }

  public void cancelMicrosoft() {
    serial++;
    Microsoft.Flow running = flow;
    flow = null;
    if (running != null) {
      running.cancel();
    }
    signing = false;
    code = null;
  }

  public String addOffline(String name) {
    String clean = name == null ? "" : name.strip();
    if (!Ids.validName(clean)) {
      return Lang.format(
          "Use {0} to {1} letters, digits or underscores.", Ids.minName(), Ids.maxName());
    }
    Entry entry =
        new Entry(Kind.OFFLINE, clean, Ids.offline(clean), "", System.currentTimeMillis());
    AccountBook.Added added = book.add(entry);
    if (!added.fresh()) {
      return Lang.tr("That account is already saved.");
    }
    say(Lang.format("Added {0}.", clean), Tone.GOOD);
    return null;
  }

  public void addToken(String token) {
    String clean = token == null ? "" : token.strip();
    if (clean.isEmpty()) {
      say(Lang.tr("Paste a Minecraft access token first."), Tone.BAD);
      return;
    }
    work(
        () -> {
          try {
            Profiles.Owned owned = profiles.owned(clean);
            if (owned == null) {
              platform.main(
                  () -> say(Lang.tr("That token is not valid or has expired."), Tone.BAD));
              return;
            }
            Entry entry =
                new Entry(
                    Kind.TOKEN, owned.username(), owned.uuid(), clean, System.currentTimeMillis());
            AccountBook.Added added = book.add(entry);
            fresh.put(
                added.entry().id, new Fresh(clean, System.currentTimeMillis() + TOKEN_LIFE_MILLIS));
            skin(added.entry(), clean);
            platform.main(
                () ->
                    say(
                        added.fresh()
                            ? Lang.format("Added {0}.", owned.username())
                            : Lang.tr("That account is already saved."),
                        added.fresh() ? Tone.GOOD : Tone.INFO));
          } catch (IOException e) {
            platform.main(
                () ->
                    say(
                        Lang.tr("Could not reach Minecraft, check your connection and try again."),
                        Tone.BAD));
          }
        });
  }

  public void refresh(Entry entry) {
    resolve(
        entry,
        (ready, error) ->
            say(
                error != null ? error : Lang.format("{0} is signed in and ready.", ready.name()),
                error != null ? Tone.BAD : Tone.GOOD));
  }

  public void use(Entry entry, Platform.Rejoin rejoin, Consumer<Boolean> finished) {
    resolve(
        entry,
        (ready, error) -> {
          if (error != null) {
            say(error, Tone.BAD);
            finished.accept(false);
            return;
          }
          platform.switchTo(
              ready,
              rejoin,
              problem -> {
                if (problem != null) {
                  say(problem, Tone.BAD);
                  finished.accept(false);
                  return;
                }
                book.used(entry.id, System.currentTimeMillis());
                say(Lang.format("Switched to {0}.", ready.name()), Tone.GOOD);
                finished.accept(true);
              });
        });
  }

  public void resolve(Entry entry, Outcome outcome) {
    work(
        () -> {
          Ready ready = null;
          String error = null;
          try {
            switch (entry.kind()) {
              case OFFLINE ->
                  ready = new Ready(entry.id, entry.username, Ids.offline(entry.username), "");
              case MICROSOFT -> ready = microsoftReady(entry);
              case TOKEN -> ready = tokenReady(entry);
            }
          } catch (Microsoft.Failure e) {
            error = e.getMessage();
          } catch (IOException e) {
            error = Lang.tr("Could not reach Minecraft, check your connection and try again.");
          }
          Ready result = ready;
          String problem = error;
          platform.main(() -> outcome.done(result, problem));
        });
  }

  private Ready microsoftReady(Entry entry) throws Microsoft.Failure, IOException {
    Fresh cached = fresh.get(entry.id);
    if (cached != null && cached.until > System.currentTimeMillis() && entry.verified()) {
      return new Ready(entry.id, entry.username, entry.uuid, cached.token);
    }
    Microsoft.Login login = microsoft.refresh(entry.secret);
    book.refreshed(entry.id, login.username(), login.uuid(), login.refresh());
    fresh.put(entry.id, new Fresh(login.token(), System.currentTimeMillis() + TOKEN_LIFE_MILLIS));
    skin(entry, login.token());
    return new Ready(entry.id, login.username(), login.uuid(), login.token());
  }

  private Ready tokenReady(Entry entry) throws Microsoft.Failure, IOException {
    Fresh cached = fresh.get(entry.id);
    if (cached != null && cached.until > System.currentTimeMillis() && entry.verified()) {
      return new Ready(entry.id, entry.username, entry.uuid, cached.token);
    }
    Profiles.Owned owned = profiles.owned(entry.secret);
    if (owned == null) {
      throw new Microsoft.Failure(Lang.tr("This token has expired, add the account again."));
    }
    book.refreshed(entry.id, owned.username(), owned.uuid(), null);
    fresh.put(entry.id, new Fresh(entry.secret, System.currentTimeMillis() + TOKEN_LIFE_MILLIS));
    skin(entry, entry.secret);
    return new Ready(entry.id, owned.username(), owned.uuid(), entry.secret);
  }

  private void skin(Entry entry, String token) {
    try {
      Profiles.Owned owned = profiles.owned(token);
      if (owned != null && owned.skin() != null) {
        faces.supply(entry, owned.skin().url(), owned.skin().slim());
      }
    } catch (IOException ignored) {
      return;
    }
  }

  public void remove(Entry entry) {
    links.forget(entry.id);
    faces.forget(entry);
    fresh.remove(entry.id);
    book.remove(entry.id);
    say(Lang.format("Removed {0}.", entry.username), Tone.INFO);
  }

  public void link(Entry entry, String address) {
    String key = Links.normalize(address);
    if (key.isEmpty()) {
      say(Lang.tr("Join a server first, then link the account to it."), Tone.INFO);
      return;
    }
    links.set(key, entry.id);
    say(Lang.format("{0} will be used on {1}.", entry.username, key), Tone.GOOD);
  }

  public void unlink(String address) {
    links.remove(address);
    say(Lang.tr("Link removed."), Tone.INFO);
  }

  public void open(String url) {
    platform.open(url);
  }

  private void work(Runnable task) {
    working.incrementAndGet();
    io.execute(
        () -> {
          try {
            task.run();
          } finally {
            working.decrementAndGet();
          }
        });
  }

  private static void wipe(char[] secret) {
    if (secret != null) {
      Arrays.fill(secret, '\0');
    }
  }
}
