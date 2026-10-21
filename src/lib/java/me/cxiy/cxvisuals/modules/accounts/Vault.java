package me.cxiy.cxvisuals.modules.accounts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.Supplier;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class Vault {
  public enum State {
    NONE,
    LOCKED,
    OPEN
  }

  public enum Method {
    DEVICE,
    PASSWORD
  }

  public static final int MIN_PASSWORD = 8;

  private static final byte[] MAGIC = {'C', 'X', 'V', '1'};
  private static final int SALT = 32;
  private static final int NONCE = 12;
  private static final int KEY = 32;
  private static final int TAG_BITS = 128;
  private static final int KEY_BITS = 256;
  private static final int ROUNDS = 310_000;
  private static final String META = "vault.meta";
  private static final String SUFFIX = ".dat";

  private static final SecureRandom RANDOM = new SecureRandom();

  private final Path directory;
  private final Supplier<char[]> device;
  private volatile byte[] master;

  public Vault(Path directory, Supplier<char[]> device) {
    this.directory = directory;
    this.device = device;
  }

  public State state() {
    if (master != null) {
      return State.OPEN;
    }
    return method() == null ? State.NONE : State.LOCKED;
  }

  public boolean open() {
    return master != null;
  }

  public Method method() {
    try {
      byte[] meta = Files.readAllBytes(directory.resolve(META));
      if (meta.length < MAGIC.length + 1 + SALT
          || !Arrays.equals(Arrays.copyOf(meta, MAGIC.length), MAGIC)) {
        return null;
      }
      int ordinal = meta[MAGIC.length];
      return ordinal >= 0 && ordinal < Method.values().length ? Method.values()[ordinal] : null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  public boolean create(Method method, char[] password) {
    if (method() != null || !acceptable(method, password)) {
      return false;
    }
    byte[] fresh = random(KEY);
    if (!wrap(method, password, fresh)) {
      wipe(fresh);
      return false;
    }
    install(fresh);
    return true;
  }

  public boolean unlock(char[] password) {
    byte[] opened = unwrap(password);
    if (opened == null) {
      return false;
    }
    install(opened);
    return true;
  }

  public boolean autoUnlock() {
    return method() == Method.DEVICE && unlock(null);
  }

  public boolean convert(Method method, char[] current, char[] next) {
    if (!acceptable(method, next)) {
      return false;
    }
    byte[] opened = unwrap(current);
    if (opened == null) {
      return false;
    }
    if (!wrap(method, next, opened)) {
      wipe(opened);
      return false;
    }
    install(opened);
    return true;
  }

  public void lock() {
    wipe(master);
    master = null;
  }

  public boolean destroy() {
    lock();
    try {
      if (Files.isDirectory(directory)) {
        try (var files = Files.list(directory)) {
          for (Path file :
              (Iterable<Path>)
                  files.filter(
                          path ->
                              path.getFileName().toString().endsWith(SUFFIX)
                                  || path.getFileName().toString().equals(META))
                      ::iterator) {
            Files.deleteIfExists(file);
          }
        }
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public String read(String name) {
    byte[] key = master;
    Path file = directory.resolve(name + SUFFIX);
    if (key == null || !Files.isRegularFile(file)) {
      return null;
    }
    try {
      byte[] plain = decrypt(key, Files.readAllBytes(file));
      return plain == null ? null : new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    }
  }

  public boolean write(String name, String text) {
    byte[] key = master;
    if (key == null) {
      return false;
    }
    try {
      store(
          directory.resolve(name + SUFFIX),
          encrypt(key, text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      return true;
    } catch (IOException | GeneralSecurityException e) {
      return false;
    }
  }

  public boolean erase(String name) {
    try {
      return Files.deleteIfExists(directory.resolve(name + SUFFIX));
    } catch (IOException e) {
      return false;
    }
  }

  private void install(byte[] next) {
    wipe(master);
    master = next;
  }

  private static boolean acceptable(Method method, char[] password) {
    return method != null
        && (method == Method.DEVICE || password != null && password.length >= MIN_PASSWORD);
  }

  private boolean wrap(Method method, char[] password, byte[] key) {
    byte[] salt = random(SALT);
    byte[] wrapping = derive(method, password, salt);
    if (wrapping == null) {
      return false;
    }
    try {
      byte[] wrapped = encrypt(wrapping, key);
      store(
          directory.resolve(META),
          ByteBuffer.allocate(MAGIC.length + 1 + salt.length + wrapped.length)
              .put(MAGIC)
              .put((byte) method.ordinal())
              .put(salt)
              .put(wrapped)
              .array());
      return true;
    } catch (IOException | GeneralSecurityException e) {
      return false;
    } finally {
      wipe(wrapping);
    }
  }

  private byte[] unwrap(char[] password) {
    byte[] wrapping = null;
    try {
      ByteBuffer meta = ByteBuffer.wrap(Files.readAllBytes(directory.resolve(META)));
      byte[] magic = new byte[MAGIC.length];
      meta.get(magic);
      if (!Arrays.equals(magic, MAGIC)) {
        return null;
      }
      Method method = Method.values()[meta.get()];
      byte[] salt = new byte[SALT];
      meta.get(salt);
      byte[] wrapped = new byte[meta.remaining()];
      meta.get(wrapped);
      wrapping = derive(method, password, salt);
      return wrapping == null ? null : decrypt(wrapping, wrapped);
    } catch (IOException | RuntimeException e) {
      return null;
    } finally {
      wipe(wrapping);
    }
  }

  private byte[] derive(Method method, char[] password, byte[] salt) {
    char[] secret = method == Method.DEVICE ? device.get() : password;
    if (secret == null || secret.length == 0) {
      return null;
    }
    PBEKeySpec spec = new PBEKeySpec(secret, salt, ROUNDS, KEY_BITS);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable", e);
    } finally {
      spec.clearPassword();
      if (method == Method.DEVICE) {
        Arrays.fill(secret, '\0');
      }
    }
  }

  private static byte[] encrypt(byte[] key, byte[] plain) throws GeneralSecurityException {
    byte[] nonce = random(NONCE);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
    byte[] sealed = cipher.doFinal(plain);
    return ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
  }

  private static byte[] decrypt(byte[] key, byte[] sealed) {
    if (sealed == null || sealed.length <= NONCE) {
      return null;
    }
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(TAG_BITS, sealed, 0, NONCE));
      return cipher.doFinal(sealed, NONCE, sealed.length - NONCE);
    } catch (GeneralSecurityException e) {
      return null;
    }
  }

  private static void store(Path target, byte[] bytes) throws IOException {
    Files.createDirectories(target.getParent());
    Path temp = target.resolveSibling(target.getFileName() + ".tmp");
    Files.write(temp, bytes);
    restrict(temp);
    try {
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void restrict(Path file) {
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException | IOException e) {
      return;
    }
  }

  private static byte[] random(int length) {
    byte[] bytes = new byte[length];
    RANDOM.nextBytes(bytes);
    return bytes;
  }

  private static void wipe(byte[] data) {
    if (data != null) {
      Arrays.fill(data, (byte) 0);
    }
  }
}
