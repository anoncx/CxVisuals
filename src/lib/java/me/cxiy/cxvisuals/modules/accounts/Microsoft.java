package me.cxiy.cxvisuals.modules.accounts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Microsoft {
  private static final String CLIENT = "00000000402b5328";
  private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
  private static final String DEVICE = "https://login.live.com/oauth20_connect.srf";
  private static final String TOKEN = "https://login.live.com/oauth20_token.srf";
  private static final String XBOX = "https://user.auth.xboxlive.com/user/authenticate";
  private static final String XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize";
  private static final String MINECRAFT =
      "https://api.minecraftservices.com/authentication/login_with_xbox";
  private static final long SHORTEST_POLL = 1_000L;
  private static final long DEFAULT_POLL = 5_000L;
  private static final long DEFAULT_LIFE = 900_000L;

  public record Code(String user, String link) {}

  public record Login(String token, String refresh, String uuid, String username) {}

  public interface Listener {
    void code(Code code);

    void done(Login login);

    void failed(String reason);
  }

  public static final class Failure extends Exception {
    public Failure(String message) {
      super(message);
    }
  }

  public final class Flow {
    private volatile boolean cancelled;
    private Thread thread;

    public void cancel() {
      cancelled = true;
      Thread running = thread;
      if (running != null) {
        running.interrupt();
      }
    }

    public boolean running() {
      Thread running = thread;
      return running != null && running.isAlive();
    }

    private void run(Listener listener) {
      try {
        walk(listener);
      } catch (Failure e) {
        if (!cancelled) {
          listener.failed(e.getMessage());
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (IOException | RuntimeException e) {
        if (!cancelled) {
          listener.failed(
              Lang.tr("Could not reach Microsoft, check your connection and try again."));
        }
      }
    }

    private void walk(Listener listener) throws Failure, IOException, InterruptedException {
      JsonObject start =
          object(
              http.postForm(
                  DEVICE,
                  Map.of("client_id", CLIENT, "scope", SCOPE, "response_type", "device_code")),
              Lang.tr("Requesting the login code"));
      String device = start.get("device_code").getAsString();
      String user = start.get("user_code").getAsString();
      String uri = start.get("verification_uri").getAsString();
      long life =
          start.has("expires_in") ? start.get("expires_in").getAsLong() * 1000L : DEFAULT_LIFE;
      long interval =
          start.has("interval")
              ? Math.max(SHORTEST_POLL, start.get("interval").getAsLong() * 1000L)
              : DEFAULT_POLL;
      if (cancelled) {
        return;
      }
      listener.code(new Code(user, uri + "?otc=" + user));
      JsonObject tokens = poll(device, interval, System.currentTimeMillis() + life);
      if (tokens == null || cancelled) {
        return;
      }
      Login login =
          chain(
              tokens.get("access_token").getAsString(), tokens.get("refresh_token").getAsString());
      if (!cancelled) {
        listener.done(login);
      }
    }

    private JsonObject poll(String device, long start, long deadline)
        throws Failure, IOException, InterruptedException {
      Map<String, String> form = new LinkedHashMap<>();
      form.put("client_id", CLIENT);
      form.put("device_code", device);
      form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
      long interval = start;
      while (!cancelled) {
        if (System.currentTimeMillis() > deadline) {
          throw new Failure(Lang.tr("The login code expired, try again."));
        }
        Thread.sleep(interval);
        if (cancelled) {
          return null;
        }
        Http.Reply reply = http.postForm(TOKEN, form);
        if (reply.ok()) {
          return JsonParser.parseString(reply.body()).getAsJsonObject();
        }
        String error = error(reply.body());
        if ("authorization_pending".equals(error)) {
          continue;
        }
        if ("slow_down".equals(error)) {
          interval += SHORTEST_POLL;
          continue;
        }
        throw new Failure(
            switch (error == null ? "" : error) {
              case "authorization_declined" -> Lang.tr("The login was declined.");
              case "expired_token" -> Lang.tr("The login code expired, try again.");
              default -> Lang.tr("Microsoft refused the login, try again.");
            });
      }
      return null;
    }
  }

  private final Http http;

  public Microsoft(Http http) {
    this.http = http;
  }

  public Flow begin(Listener listener) {
    Flow flow = new Flow();
    Thread thread = new Thread(() -> flow.run(listener), "CxVisuals-Microsoft-Login");
    thread.setDaemon(true);
    flow.thread = thread;
    thread.start();
    return flow;
  }

  public Login refresh(String refreshToken) throws Failure, IOException {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new Failure(Lang.tr("This account has no saved sign-in, add it again."));
    }
    Http.Reply reply =
        http.postForm(
            TOKEN,
            Map.of(
                "client_id",
                CLIENT,
                "scope",
                SCOPE,
                "refresh_token",
                refreshToken,
                "grant_type",
                "refresh_token"));
    if (!reply.ok()) {
      throw new Failure(
          Lang.tr("Microsoft no longer accepts this sign-in, add the account again."));
    }
    JsonObject tokens = JsonParser.parseString(reply.body()).getAsJsonObject();
    String next =
        tokens.has("refresh_token") ? tokens.get("refresh_token").getAsString() : refreshToken;
    return chain(tokens.get("access_token").getAsString(), next);
  }

  private Login chain(String microsoftToken, String refreshToken) throws Failure, IOException {
    JsonObject xbox =
        object(
            http.postJson(
                XBOX,
                "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\""
                    + escape(microsoftToken)
                    + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}",
                null),
            Lang.tr("Signing in to Xbox Live"));
    String xboxToken = xbox.get("Token").getAsString();
    Http.Reply xstsReply =
        http.postJson(
            XSTS,
            "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
                + escape(xboxToken)
                + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}",
            null);
    if (!xstsReply.ok()) {
      throw new Failure(xstsError(xstsReply.body()));
    }
    JsonObject xsts = JsonParser.parseString(xstsReply.body()).getAsJsonObject();
    String xstsToken = xsts.get("Token").getAsString();
    String hash =
        xsts.getAsJsonObject("DisplayClaims")
            .getAsJsonArray("xui")
            .get(0)
            .getAsJsonObject()
            .get("uhs")
            .getAsString();
    JsonObject minecraft =
        object(
            http.postJson(
                MINECRAFT,
                "{\"identityToken\":\"XBL3.0 x=" + escape(hash) + ";" + escape(xstsToken) + "\"}",
                null),
            Lang.tr("Signing in to Minecraft"));
    String token = minecraft.get("access_token").getAsString();
    Http.Reply profile = http.get(Profiles.SERVICES, token);
    Profiles.Owned owned = profile.ok() ? Profiles.parseOwned(profile.body()) : null;
    if (owned == null) {
      throw new Failure(Lang.tr("This Microsoft account does not own Minecraft: Java Edition."));
    }
    return new Login(token, refreshToken, owned.uuid(), owned.username());
  }

  private static JsonObject object(Http.Reply reply, String step) throws Failure {
    if (!reply.ok()) {
      throw new Failure(Lang.format("{0} failed (HTTP {1}).", step, reply.code()));
    }
    return JsonParser.parseString(reply.body()).getAsJsonObject();
  }

  private static String error(String body) {
    try {
      JsonObject json = JsonParser.parseString(body).getAsJsonObject();
      return json.has("error") ? json.get("error").getAsString() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String xstsError(String body) {
    try {
      long code = JsonParser.parseString(body).getAsJsonObject().get("XErr").getAsLong();
      if (code == 2148916233L) {
        return Lang.tr("This account has no Xbox profile.");
      }
      if (code == 2148916235L) {
        return Lang.tr("Xbox Live is not available in this account's region.");
      }
      if (code == 2148916238L) {
        return Lang.tr("Child accounts have to be added to a family first.");
      }
    } catch (RuntimeException e) {
      return Lang.tr("Xbox did not allow this sign-in.");
    }
    return Lang.tr("Xbox did not allow this sign-in.");
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
