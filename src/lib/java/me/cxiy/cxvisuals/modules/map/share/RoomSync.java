package me.cxiy.cxvisuals.modules.map.share;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.modules.map.BlockPalette;
import me.cxiy.cxvisuals.modules.map.MapShare;

public final class RoomSync {
  public static final int PULL_LIMIT = 4096;
  public static final int MAX_DELTA = 1 << 20;

  public record Frame(long seq, byte[] blob) {}

  public record Page(List<Frame> frames, boolean more) {}

  public interface Wire {
    void upload(String regionId, int epoch, byte[] sealed) throws IOException;

    Page download(long after) throws IOException;
  }

  public interface Source {
    List<MapShare.Chunk> read(String dimension, int regionX, int regionZ, List<long[]> coords);
  }

  public static final class StaleEpoch extends IOException {
    public StaleEpoch() {
      super("room key is out of date");
    }
  }

  private final Supplier<SharedRoom> room;
  private final Consumer<SharedRoom> persist;
  private final Wire wire;
  private final Source source;
  private final BlockPalette palette;
  private final DeltaBatcher batcher;
  private final RemoteQueue queue;
  private final TokenBucket bucket;

  public RoomSync(
      Supplier<SharedRoom> room,
      Consumer<SharedRoom> persist,
      Wire wire,
      Source source,
      BlockPalette palette,
      DeltaBatcher batcher,
      RemoteQueue queue,
      TokenBucket bucket) {
    this.room = room;
    this.persist = persist;
    this.wire = wire;
    this.source = source;
    this.palette = palette;
    this.batcher = batcher;
    this.queue = queue;
    this.bucket = bucket;
  }

  public int push(int playerChunkX, int playerChunkZ) throws IOException {
    List<DeltaBatcher.Batch> batches = batcher.take(playerChunkX, playerChunkZ);
    int sent = 0;
    for (int i = 0; i < batches.size(); i++) {
      DeltaBatcher.Batch batch = batches.get(i);
      try {
        List<MapShare.Chunk> chunks =
            source.read(batch.dimension(), batch.regionX(), batch.regionZ(), batch.chunks());
        if (!chunks.isEmpty() && !send(batch, chunks)) {
          batcher.requeue(batches.subList(i, batches.size()));
          return sent;
        }
        sent += chunks.size();
      } catch (IOException e) {
        batcher.requeue(batches.subList(i, batches.size()));
        throw e;
      }
    }
    return sent;
  }

  public boolean pull() throws IOException {
    if (queue.size() >= PULL_LIMIT) {
      return false;
    }
    SharedRoom now = room.get();
    Page page = wire.download(now.cursor());
    long top = now.cursor();
    for (Frame frame : page.frames()) {
      top = Math.max(top, frame.seq());
      byte[] plain = RoomCrypto.openDelta(now.key(), now.id(), frame.blob());
      MapShare.Area area = plain == null ? null : MapShare.decode(plain, palette);
      if (area != null && area.scope().equals(now.scope())) {
        queue.offer(area.dimension(), area.chunks());
      }
    }
    if (top > now.cursor()) {
      persist.accept(room.get().withCursor(top));
    }
    return page.more();
  }

  private boolean send(DeltaBatcher.Batch batch, List<MapShare.Chunk> chunks) throws IOException {
    SharedRoom now = room.get();
    byte[] sealed =
        RoomCrypto.sealDelta(
            now.key(), now.id(), MapShare.encode(now.scope(), batch.dimension(), chunks, palette));
    if (sealed.length > MAX_DELTA && chunks.size() > 1) {
      int half = chunks.size() / 2;
      return send(batch, chunks.subList(0, half))
          && send(batch, chunks.subList(half, chunks.size()));
    }
    if (!bucket.tryTake(sealed.length)) {
      return false;
    }
    wire.upload(
        RoomCrypto.regionId(now.key(), batch.dimension(), batch.regionX(), batch.regionZ()),
        now.epoch(),
        sealed);
    return true;
  }
}
