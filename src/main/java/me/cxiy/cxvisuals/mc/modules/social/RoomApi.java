package me.cxiy.cxvisuals.mc.modules.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import me.cxiy.cxvisuals.modules.map.share.RoomFrames;
import me.cxiy.cxvisuals.modules.map.share.RoomSync;
import me.cxiy.cxvisuals.modules.social.SocialProtocol;

final class RoomApi implements RoomSync.Wire {
  record Member(String room, String owner, int epoch, List<String> members) {}

  record Spot(String from, byte[] body) {}

  private static final int PAGE_LIMIT = 6 << 20;
  private static final String STALE = "stale_epoch";

  private final String room;

  RoomApi(String room) {
    this.room = room;
  }

  private static String url(String path) {
    return Social.module().baseUrl() + "/v2/rooms" + path;
  }

  static void create(String id) throws IOException {
    JsonObject body = new JsonObject();
    body.addProperty("id", id);
    Social.cloud().post(url(""), Social.token(), body.toString());
  }

  static void addMember(String room, String uuid) throws IOException {
    Social.cloud().put(url("/" + room + "/members/" + uuid), Social.token(), "{}");
  }

  static void removeMember(String room, String uuid) throws IOException {
    Social.cloud().delete(url("/" + room + "/members/" + uuid), Social.token());
  }

  static int rotate(String room) throws IOException {
    JsonObject reply = Social.cloud().post(url("/" + room + "/rotate"), Social.token(), "{}");
    return (int) SocialProtocol.number(reply, "epoch", 0L);
  }

  static List<Member> list() throws IOException {
    JsonObject reply = Social.cloud().get(url(""), Social.token());
    List<Member> out = new ArrayList<>();
    JsonElement rows = reply.get("rooms");
    if (rows == null || !rows.isJsonArray()) {
      return out;
    }
    for (JsonElement row : rows.getAsJsonArray()) {
      if (!row.isJsonObject()) {
        continue;
      }
      JsonObject json = row.getAsJsonObject();
      List<String> members = new ArrayList<>();
      JsonElement listed = json.get("members");
      if (listed != null && listed.isJsonArray()) {
        for (JsonElement member : listed.getAsJsonArray()) {
          members.add(member.getAsString());
        }
      }
      out.add(
          new Member(
              SocialProtocol.text(json, "id", ""),
              SocialProtocol.text(json, "owner", ""),
              (int) SocialProtocol.number(json, "epoch", 0L),
              members));
    }
    return out;
  }

  @Override
  public void upload(String regionId, int epoch, byte[] sealed) throws IOException {
    try {
      Social.cloud()
          .send(
              url("/" + room + "/deltas?region=" + regionId + "&epoch=" + epoch),
              Social.token(),
              sealed);
    } catch (Cloud.Refused e) {
      throw STALE.equals(e.code) ? new RoomSync.StaleEpoch() : e;
    }
  }

  void snapshot(String regionId, long through, int epoch, byte[] sealed) throws IOException {
    try {
      Social.cloud()
          .send(
              url(
                  "/"
                      + room
                      + "/snapshot?region="
                      + regionId
                      + "&through="
                      + through
                      + "&epoch="
                      + epoch),
              Social.token(),
              sealed);
    } catch (Cloud.Refused e) {
      throw STALE.equals(e.code) ? new RoomSync.StaleEpoch() : e;
    }
  }

  @Override
  public RoomSync.Page download(long after) throws IOException {
    Cloud.Bytes reply =
        Social.cloud()
            .fetch(url("/" + room + "/deltas?after=" + after), Social.token(), PAGE_LIMIT);
    List<RoomSync.Frame> frames = RoomFrames.parse(reply.body());
    return new RoomSync.Page(frames, reply.more());
  }

  void sendSpot(String to, byte[] sealed) throws IOException {
    JsonObject body = new JsonObject();
    body.addProperty("to", to);
    body.addProperty("body", Base64.getEncoder().encodeToString(sealed));
    Social.cloud().post(url("/" + room + "/spots"), Social.token(), body.toString());
  }

  List<Spot> spots() throws IOException {
    JsonObject reply = Social.cloud().get(url("/" + room + "/spots"), Social.token());
    List<Spot> out = new ArrayList<>();
    JsonElement rows = reply.get("spots");
    if (rows == null || !rows.isJsonArray()) {
      return out;
    }
    JsonArray list = rows.getAsJsonArray();
    for (JsonElement row : list) {
      if (!row.isJsonObject()) {
        continue;
      }
      JsonObject json = row.getAsJsonObject();
      try {
        out.add(
            new Spot(
                SocialProtocol.lower(SocialProtocol.text(json, "from", "")),
                Base64.getDecoder().decode(SocialProtocol.text(json, "body", ""))));
      } catch (IllegalArgumentException e) {
        continue;
      }
    }
    return out;
  }

  void clearSpots() throws IOException {
    Social.cloud().delete(url("/" + room + "/spots"), Social.token());
  }
}
