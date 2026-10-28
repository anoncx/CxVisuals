package me.cxiy.cxvisuals.modules.map.share;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import me.cxiy.cxvisuals.modules.friends.Sealer;

public final class RoomCrypto {
  private static final String MAC = "HmacSHA256";
  private static final int REGION_BYTES = 16;

  private RoomCrypto() {}

  public static String regionId(byte[] key, String dimension, int regionX, int regionZ) {
    try {
      Mac mac = Mac.getInstance(MAC);
      mac.init(new SecretKeySpec(key, MAC));
      mac.update(dimension.getBytes(StandardCharsets.UTF_8));
      mac.update(ByteBuffer.allocate(8).putInt(regionX).putInt(regionZ).array());
      return HexFormat.of().formatHex(mac.doFinal(), 0, REGION_BYTES);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public static byte[] sealDelta(byte[] key, String roomId, byte[] plain) {
    return Sealer.lock(key, plain, roomId.getBytes(StandardCharsets.UTF_8));
  }

  public static byte[] openDelta(byte[] key, String roomId, byte[] sealed) {
    return Sealer.unlock(key, sealed, roomId.getBytes(StandardCharsets.UTF_8));
  }
}
