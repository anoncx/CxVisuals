package me.cxiy.cxvisuals.modules.hud;

import me.cxiy.cxvisuals.modules.hud.media.Lyrics;
import me.cxiy.cxvisuals.modules.hud.media.MediaSnapshot;

public final class HudData {
  public static final int GEAR_SLOTS = 6;
  public static final int FIRST_HAND = 4;
  public static final int TARGET_FIRST = GEAR_SLOTS;
  public static final int SLOTS = GEAR_SLOTS * 2;
  public static final int NONE = -1;
  public static final int PLAYER_SLOTS = 12;
  public static final float UNKNOWN = -1.0F;
  public static final int MARKS = 48;

  public boolean still;
  public boolean editing;

  public float fps;
  public float ping;
  public float cpu = UNKNOWN;
  public float gpu = UNKNOWN;
  public float ram;
  public float tps = UNKNOWN;
  public float speed;
  public float clicksLeft;
  public float clicksRight;

  public double x;
  public double y;
  public double z;
  public float yaw;
  public float pitch;
  public String biome = "";

  public long dayTime;
  public int clockMinutes;
  public long sessionSeconds;

  public boolean forward;
  public boolean back;
  public boolean left;
  public boolean right;
  public boolean jump;
  public boolean attack;
  public boolean use;

  public int markCount;
  public final double[] markX = new double[MARKS];
  public final double[] markY = new double[MARKS];
  public final double[] markZ = new double[MARKS];
  public final int[] markEntity = new int[MARKS];
  public final boolean[] pinOn = new boolean[MARKS];
  public final float[] pinFocus = new float[MARKS];
  public final float[] pinX = new float[MARKS];
  public final float[] pinY = new float[MARKS];
  public final float[] pinDistance = new float[MARKS];
  public final int[] pinMetres = new int[MARKS];
  public final String[] pinRange = new String[MARKS];
  public final String[] markNote = new String[MARKS];
  public final String[] pinNote = new String[MARKS];
  public final String[] markLabel = new String[MARKS];
  public final int[] markColor = new int[MARKS];
  public final boolean[] markFriend = new boolean[MARKS];
  public final String[] markIcon = new String[MARKS];

  public boolean mark(
      String label, double x, double y, double z, int color, boolean friend, int entity) {
    return mark(label, "", x, y, z, color, friend, entity);
  }

  public boolean mark(
      String label,
      String note,
      double x,
      double y,
      double z,
      int color,
      boolean friend,
      int entity) {
    return mark(label, note, x, y, z, color, friend, entity, "");
  }

  public boolean mark(
      String label,
      String note,
      double x,
      double y,
      double z,
      int color,
      boolean friend,
      int entity,
      String icon) {
    if (markCount == MARKS) {
      return false;
    }
    markX[markCount] = x;
    markY[markCount] = y;
    markZ[markCount] = z;
    markEntity[markCount] = entity;
    markNote[markCount] = note;
    markLabel[markCount] = label;
    markColor[markCount] = color;
    markFriend[markCount] = friend;
    markIcon[markCount] = icon;
    markCount++;
    return true;
  }

  public int playerCount;
  public final int[] playerIds = new int[PLAYER_SLOTS];
  public final String[] playerNames = new String[PLAYER_SLOTS];
  public final String[] playerTracks = new String[PLAYER_SLOTS];
  public final float[] playerDistance = new float[PLAYER_SLOTS];

  public final boolean[] gear = new boolean[SLOTS];
  public final float[] durability = new float[SLOTS];
  public final int[] count = new int[SLOTS];

  public int targetId = NONE;
  public boolean targetPlayer;
  public String targetName = "";
  public float targetDistance;
  public float targetHealth = UNKNOWN;
  public float targetMax;
  public float targetAbsorb;
  public int targetArmor;

  public String serverName = "";
  public String serverAddress = "";
  public byte[] serverIcon;
  public String serverBrand = "";
  public String dimension = "";
  public int onlinePlayers;
  public int maxPlayers;
  public float packetsIn;
  public float packetsOut;

  public final ChatLog chat = new ChatLog();
  public boolean chatOpen;

  public MediaSnapshot media = MediaSnapshot.EMPTY;
  public long mediaNanos;
  public byte[] cover;
  public Lyrics lyrics = Lyrics.MISSING;
  public boolean pointer;
}
