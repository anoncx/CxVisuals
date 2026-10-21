package me.cxiy.cxvisuals.modules.friends;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class Sealer {
  public static final int KEY_BYTES = 32;
  public static final int OVERHEAD = 28;
  private static final int NONCE = 12;
  private static final int TAG_BITS = 128;
  private static final String CURVE = "X25519";
  private static final String INFO = "cxvisuals/dm/1|";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final PrivateKey secret;
  private final String stored;
  private final String publicKey;
  private final ConcurrentHashMap<String, byte[]> shared = new ConcurrentHashMap<>();

  private Sealer(PrivateKey secret, String stored, String publicKey) {
    this.secret = secret;
    this.stored = stored;
    this.publicKey = publicKey;
  }

  public static Sealer create() {
    try {
      KeyPair pair = KeyPairGenerator.getInstance(CURVE).generateKeyPair();
      String pub = Base64.getEncoder().encodeToString(raw(pair.getPublic()));
      String priv = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
      return new Sealer(pair.getPrivate(), priv + "\n" + pub, pub);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Sealer restore(String stored) {
    if (stored == null) {
      return null;
    }
    String[] lines = stored.strip().split("\n");
    if (lines.length != 2) {
      return null;
    }
    try {
      String pub = lines[1].strip();
      if (Base64.getDecoder().decode(pub).length != KEY_BYTES) {
        return null;
      }
      PrivateKey secret =
          KeyFactory.getInstance(CURVE)
              .generatePrivate(
                  new PKCS8EncodedKeySpec(Base64.getDecoder().decode(lines[0].strip())));
      return new Sealer(secret, lines[0].strip() + "\n" + pub, pub);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      return null;
    }
  }

  public String stored() {
    return stored;
  }

  public String publicKey() {
    return publicKey;
  }

  public byte[] seal(String peerKey, String me, String peer, byte[] plain) {
    byte[] key = key(peerKey, me, peer);
    return key == null ? null : lock(key, plain, me.getBytes(StandardCharsets.UTF_8));
  }

  public byte[] open(String peerKey, String me, String peer, String sender, byte[] sealed) {
    byte[] key = key(peerKey, me, peer);
    return key == null ? null : unlock(key, sealed, sender.getBytes(StandardCharsets.UTF_8));
  }

  private byte[] key(String peerKey, String me, String peer) {
    if (peerKey == null || peerKey.isEmpty()) {
      return null;
    }
    return shared.computeIfAbsent(
        peerKey + "|" + peer,
        ignored -> {
          try {
            byte[] point = Base64.getDecoder().decode(peerKey);
            if (point.length != KEY_BYTES) {
              return null;
            }
            KeyAgreement agreement = KeyAgreement.getInstance(CURVE);
            agreement.init(secret);
            agreement.doPhase(publicKey(point), true);
            String low = me.compareTo(peer) < 0 ? me : peer;
            String high = me.compareTo(peer) < 0 ? peer : me;
            return derive(agreement.generateSecret(), INFO + low + "|" + high);
          } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
          }
        });
  }

  public static byte[] newKey() {
    byte[] key = new byte[KEY_BYTES];
    RANDOM.nextBytes(key);
    return key;
  }

  public static byte[] lock(byte[] key, byte[] plain, byte[] bound) {
    try {
      byte[] nonce = new byte[NONCE];
      RANDOM.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(bound);
      byte[] body = cipher.doFinal(plain);
      byte[] out = Arrays.copyOf(nonce, NONCE + body.length);
      System.arraycopy(body, 0, out, NONCE, body.length);
      return out;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public static byte[] unlock(byte[] key, byte[] sealed, byte[] bound) {
    if (key == null || key.length != KEY_BYTES || sealed == null || sealed.length < OVERHEAD) {
      return null;
    }
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(TAG_BITS, sealed, 0, NONCE));
      cipher.updateAAD(bound);
      return cipher.doFinal(sealed, NONCE, sealed.length - NONCE);
    } catch (GeneralSecurityException e) {
      return null;
    }
  }

  private static byte[] derive(byte[] secret, String info) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(new byte[KEY_BYTES], "HmacSHA256"));
    byte[] extracted = mac.doFinal(secret);
    mac.init(new SecretKeySpec(extracted, "HmacSHA256"));
    mac.update(info.getBytes(StandardCharsets.UTF_8));
    mac.update((byte) 1);
    return mac.doFinal();
  }

  private static byte[] raw(PublicKey key) {
    byte[] big = ((XECPublicKey) key).getU().toByteArray();
    byte[] out = new byte[KEY_BYTES];
    for (int i = 0; i < big.length && i < KEY_BYTES; i++) {
      out[i] = big[big.length - 1 - i];
    }
    return out;
  }

  private static PublicKey publicKey(byte[] point) throws GeneralSecurityException {
    byte[] big = new byte[KEY_BYTES];
    for (int i = 0; i < KEY_BYTES; i++) {
      big[i] = point[KEY_BYTES - 1 - i];
    }
    big[0] &= 0x7F;
    return KeyFactory.getInstance(CURVE)
        .generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, new BigInteger(1, big)));
  }
}
