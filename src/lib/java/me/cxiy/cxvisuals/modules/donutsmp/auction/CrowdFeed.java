package me.cxiy.cxvisuals.modules.donutsmp.auction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public final class CrowdFeed {
  public record Report(String id, String key, Variant variant, double unit, int seen) {
    public Report {
      key = key == null || key.isEmpty() ? id : key;
      variant = variant == null ? Variant.EMPTY : variant;
    }

    public Report(String id, double unit, int seen) {
      this(id, id, Variant.EMPTY, unit, seen);
    }
  }

  public record Price(String key, String id, double price, int reporters, long at, String basis) {
    public Price {
      key = key == null || key.isEmpty() ? id : key;
      basis = basis == null || basis.isEmpty() ? "market" : basis;
    }

    public Price(String id, double price, int reporters, long at) {
      this(id, id, price, reporters, at, "market");
    }
  }

  public record Answer(long now, List<Price> prices) {}

  public static final int MOST_REPORTS = 400;

  private CrowdFeed() {}

  public static String upload(List<Report> reports) {
    JsonArray items = new JsonArray();
    for (Report report : reports) {
      if (items.size() >= MOST_REPORTS) {
        break;
      }
      JsonObject row = new JsonObject();
      row.addProperty("id", report.id());
      row.addProperty("key", report.key());
      row.addProperty("unit", report.unit());
      row.addProperty("n", Math.max(1, report.seen()));
      if (!report.variant().plain()) {
        row.add("variant", report.variant().json());
      }
      items.add(row);
    }
    JsonObject body = new JsonObject();
    body.addProperty("source", Host.NAME);
    body.add("items", items);
    return body.toString();
  }

  public static Answer download(JsonObject reply) {
    List<Price> prices = new ArrayList<>();
    long now = number(reply, "now");
    if (reply.has("prices") && reply.get("prices").isJsonArray()) {
      for (JsonElement element : reply.getAsJsonArray("prices")) {
        if (!element.isJsonObject()) {
          continue;
        }
        JsonObject row = element.getAsJsonObject();
        String id =
            row.has("id") && row.get("id").isJsonPrimitive() ? row.get("id").getAsString() : "";
        String key =
            row.has("key") && row.get("key").isJsonPrimitive() ? row.get("key").getAsString() : id;
        String basis =
            row.has("basis") && row.get("basis").isJsonPrimitive()
                ? row.get("basis").getAsString()
                : "market";
        double price = number(row, "price");
        if (!id.isEmpty() && !key.isEmpty() && price > 0.0) {
          prices.add(
              new Price(key, id, price, (int) number(row, "reporters"), number(row, "at"), basis));
        }
      }
    }
    return new Answer(now, prices);
  }

  private static long number(JsonObject row, String key) {
    JsonElement value = row.get(key);
    try {
      return value == null || !value.isJsonPrimitive() ? 0L : (long) value.getAsDouble();
    } catch (RuntimeException e) {
      return 0L;
    }
  }
}
