package me.cxiy.cxvisuals.modules.donutsmp.auction;

import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Market {
  private static final int MAGIC = 0x43584D4B;
  private static final int VERSION = 4;
  private static final int MAX_PER_SELLER = 2;
  private static final double BAND = 4.0;
  private static final int PASSES = 3;
  private static final int CAPACITY = 48;
  private static final int MAX_VARIANTS = 8192;
  private static final long MAX_AGE = 48L * 3600L;

  private final Map<String, Book> books = new HashMap<>(512);
  private final Map<String, Variant> variants = new HashMap<>(64);

  private final double[] scratch = new double[CAPACITY];
  private boolean dirty;
  private int revision;
  private long latest;

  private static final class Book {
    final double[] unit = new double[CAPACITY];
    final long[] at = new long[CAPACITY];
    final int[] key = new int[CAPACITY];
    final int[] who = new int[CAPACITY];
    int size;

    void add(double price, int listing, int seller, long now) {
      for (int i = 0; i < size; i++) {
        if (key[i] == listing) {
          at[i] = now;
          return;
        }
      }
      if (seller != 0) {
        int theirs = 0;
        int oldestOwn = -1;
        for (int i = 0; i < size; i++) {
          if (who[i] == seller) {
            theirs++;
            if (oldestOwn < 0 || at[i] < at[oldestOwn]) {
              oldestOwn = i;
            }
          }
        }
        if (theirs >= MAX_PER_SELLER) {
          remove(oldestOwn);
        }
      }
      if (size == CAPACITY) {
        int oldest = 0;
        for (int i = 1; i < size; i++) {
          if (at[i] < at[oldest]) {
            oldest = i;
          }
        }
        remove(oldest);
      }
      unit[size] = price;
      at[size] = now;
      key[size] = listing;
      who[size] = seller;
      size++;
    }

    void remove(int index) {
      size--;
      unit[index] = unit[size];
      at[index] = at[size];
      key[index] = key[size];
      who[index] = who[size];
    }

    void expire(long now) {
      for (int i = size - 1; i >= 0; i--) {
        if (now - at[i] > MAX_AGE) {
          remove(i);
        }
      }
    }
  }

  public void observe(String id, double unit, int listing, long now) {
    observe(id, unit, listing, 0, now);
  }

  public synchronized void observe(String id, double unit, int listing, int seller, long now) {
    Book book = books.computeIfAbsent(id, key -> new Book());
    int before = book.size;
    book.expire(now);
    book.add(unit, listing, seller, now);
    latest = Math.max(latest, now);
    if (book.size != before) {
      revision++;
    }
    dirty = true;
  }

  public synchronized void describe(String key, Variant variant) {
    if (key == null
        || key.isEmpty()
        || variant == null
        || variant.plain()
        || variants.containsKey(key)
        || variants.size() >= MAX_VARIANTS) {
      return;
    }
    variants.put(key, variant);
    dirty = true;
  }

  public synchronized Variant variant(String key) {
    return variants.getOrDefault(key, Variant.EMPTY);
  }

  public synchronized int count(String id, long now) {
    Book book = books.get(id);
    if (book == null) {
      return 0;
    }
    book.expire(now);
    return book.size;
  }

  public synchronized double median(String id, long now) {
    int n = collect(id, now);
    if (n == 0) {
      return AuctionText.NONE;
    }
    return (n & 1) == 1 ? scratch[n / 2] : (scratch[n / 2 - 1] + scratch[n / 2]) * 0.5;
  }

  public synchronized long latest() {
    return latest;
  }

  public synchronized long newest(String id, long now) {
    Book book = books.get(id);
    long newest = Long.MIN_VALUE;
    if (book != null) {
      book.expire(now);
      for (int i = 0; i < book.size; i++) {
        newest = Math.max(newest, book.at[i]);
      }
    }
    return newest;
  }

  public synchronized List<String> seenSince(long cutoff, int most) {
    List<String> ids = new ArrayList<>();
    if (most <= 0) {
      return ids;
    }
    for (Map.Entry<String, Book> entry : books.entrySet()) {
      Book book = entry.getValue();
      for (int i = 0; i < book.size; i++) {
        if (book.at[i] >= cutoff) {
          ids.add(entry.getKey());
          break;
        }
      }
      if (ids.size() >= most) {
        break;
      }
    }
    return ids;
  }

  public synchronized double low(String id, long now) {
    return collect(id, now) == 0 ? AuctionText.NONE : scratch[0];
  }

  private int collect(String id, long now) {
    Book book = books.get(id);
    if (book == null) {
      return 0;
    }
    book.expire(now);
    System.arraycopy(book.unit, 0, scratch, 0, book.size);
    Arrays.sort(scratch, 0, book.size);
    return trim(book.size);
  }

  private int trim(int size) {
    int n = size;
    for (int pass = 0; pass < PASSES && n >= 3; pass++) {
      double middle = (n & 1) == 1 ? scratch[n / 2] : (scratch[n / 2 - 1] + scratch[n / 2]) * 0.5;
      int from = 0;
      while (from < n && scratch[from] < middle / BAND) {
        from++;
      }
      int to = n;
      while (to > from && scratch[to - 1] > middle * BAND) {
        to--;
      }
      if (from == 0 && to == n) {
        break;
      }
      System.arraycopy(scratch, from, scratch, 0, to - from);
      n = to - from;
    }
    return n;
  }

  public synchronized int revision() {
    return revision;
  }

  public synchronized boolean dirty() {
    return dirty;
  }

  public synchronized void reset() {
    books.clear();
    variants.clear();
    dirty = true;
    revision++;
  }

  public void save(Path file) throws IOException {
    Path temp = file.resolveSibling(file.getFileName() + ".tmp");
    synchronized (this) {
      Files.createDirectories(file.getParent());
      try (DataOutputStream out =
          new DataOutputStream(
              new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(books.size());
        for (Map.Entry<String, Book> entry : books.entrySet()) {
          Book book = entry.getValue();
          out.writeUTF(entry.getKey());
          out.writeInt(book.size);
          for (int i = 0; i < book.size; i++) {
            out.writeDouble(book.unit[i]);
            out.writeLong(book.at[i]);
            out.writeInt(book.key[i]);
            out.writeInt(book.who[i]);
          }
        }
        out.writeInt(variants.size());
        for (Map.Entry<String, Variant> entry : variants.entrySet()) {
          out.writeUTF(entry.getKey());
          out.writeUTF(entry.getValue().json().toString());
        }
      }
      dirty = false;
    }
    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
  }

  public void load(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return;
    }
    try (DataInputStream in =
        new DataInputStream(
            new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
      if (in.readInt() != MAGIC) {
        return;
      }
      int version = in.readInt();
      if (version != VERSION && version != 3 && version != 2) {
        return;
      }
      int count = in.readInt();
      Map<String, Book> loaded = new HashMap<>(Math.max(16, count));
      for (int n = 0; n < count; n++) {
        String id = in.readUTF();
        int size = in.readInt();
        if (size < 0 || size > CAPACITY) {
          throw new IOException("corrupt market");
        }
        Book book = new Book();
        for (int i = 0; i < size; i++) {
          book.unit[i] = in.readDouble();
          book.at[i] = in.readLong();
          book.key[i] = in.readInt();
          book.who[i] = version >= 3 ? in.readInt() : 0;
        }
        book.size = size;
        loaded.put(id, book);
      }
      Map<String, Variant> loadedVariants = new HashMap<>();
      if (version >= 4) {
        int variantsCount = in.readInt();
        if (variantsCount < 0 || variantsCount > 8192) {
          throw new IOException("corrupt market");
        }
        for (int n = 0; n < variantsCount; n++) {
          String key = in.readUTF();
          String json = in.readUTF();
          try {
            loadedVariants.put(
                key, Variant.fromJson(JsonParser.parseString(json).getAsJsonObject()));
          } catch (RuntimeException e) {
            throw new IOException("corrupt variant", e);
          }
        }
      }
      synchronized (this) {
        books.clear();
        books.putAll(loaded);
        variants.clear();
        variants.putAll(loadedVariants);
        latest = 0L;
        for (Book book : loaded.values()) {
          for (int i = 0; i < book.size; i++) {
            latest = Math.max(latest, book.at[i]);
          }
        }
        dirty = false;
        revision++;
      }
    }
  }
}
