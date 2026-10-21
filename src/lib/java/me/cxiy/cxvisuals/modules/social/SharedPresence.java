package me.cxiy.cxvisuals.modules.social;

import com.google.gson.JsonObject;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipMap;

public record SharedPresence(
    String uuid,
    String name,
    String avatarHash,
    String tuneUp,
    int tuneTurn,
    double tuneSize,
    double tuneLift,
    boolean tuneClips,
    ClipMap clipMap,
    String clipKey,
    String trackTitle,
    String trackArtist,
    boolean playing) {
  private static final JsonObject EMPTY = new JsonObject();

  public boolean hasAvatar() {
    return !avatarHash.isEmpty();
  }

  public boolean hasTrack() {
    return !trackTitle.isEmpty() || !trackArtist.isEmpty();
  }

  public String trackLine(int limit) {
    return SocialProtocol.trackLine(trackTitle, trackArtist, limit);
  }

  public static SharedPresence parse(JsonObject json) {
    if (json == null) {
      return null;
    }
    String uuid = SocialProtocol.lower(SocialProtocol.text(json, "uuid", ""));
    String hash = SocialProtocol.lower(SocialProtocol.text(json, "avatar", ""));
    if (!SocialProtocol.validUuid(uuid) || !SocialProtocol.validHash(hash)) {
      return null;
    }
    JsonObject tune = SocialProtocol.object(json, "tune", EMPTY);
    JsonObject track = SocialProtocol.object(json, "track", EMPTY);
    JsonObject mapping = SocialProtocol.object(tune, "map", null);
    return new SharedPresence(
        uuid,
        SocialProtocol.clip(SocialProtocol.text(json, "name", ""), SocialProtocol.NAME_LIMIT),
        hash,
        SocialProtocol.tuneUp(SocialProtocol.text(tune, "up", "Y")),
        SocialProtocol.clampTurn(SocialProtocol.number(tune, "turn", 0)),
        SocialProtocol.clampSize(SocialProtocol.decimal(tune, "size", 100.0)),
        SocialProtocol.clampLift(SocialProtocol.decimal(tune, "lift", 0.0)),
        SocialProtocol.flag(tune, "clips", true),
        SocialProtocol.clipMap(mapping),
        mapping == null ? "" : mapping.toString(),
        SocialProtocol.clip(SocialProtocol.text(track, "title", ""), SocialProtocol.TRACK_LIMIT),
        SocialProtocol.clip(SocialProtocol.text(track, "artist", ""), SocialProtocol.TRACK_LIMIT),
        SocialProtocol.flag(track, "playing", false));
  }
}
