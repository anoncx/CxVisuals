package me.cxiy.cxvisuals.mc.menu.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.StyledText;
import me.cxiy.cxvisuals.menu.model.Item;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

final class WorldItems extends PlainItems<WorldSelectionList.Entry> {
  private static final long LARGEST_ICON = 1L << 20;

  WorldItems(WorldSelectionList list) {
    super(list);
  }

  @Override
  protected void fill(WorldSelectionList.Entry entry, Item item) {
    if (!(entry instanceof WorldSelectionList.WorldListEntry world)) {
      item.header = true;
      item.title =
          entry instanceof WorldSelectionList.LoadingHeader
              ? Texts.plain(Component.translatable("selectWorld.loading_list"))
              : named(entry.getNarration());
      return;
    }
    LevelSummary summary = world.getLevelSummary();
    item.title = summary.getLevelName();
    String played = "";
    if (summary.getLastPlayed() != -1L) {
      ZonedDateTime time =
          ZonedDateTime.ofInstant(
              Instant.ofEpochMilli(summary.getLastPlayed()), ZoneId.systemDefault());
      played = WorldSelectionList.DATE_FORMAT.format(time);
    }
    item.first =
        StyledText.of(
            played.isEmpty() ? summary.getLevelId() : summary.getLevelId() + "  ·  " + played);
    item.second = Texts.styled(summary.getInfo());
    item.flagged = summary.isDisabled() || !summary.isCompatible();
    item.icon = icon(summary.getIcon());
  }

  private static byte[] icon(Path file) {
    try {
      if (file != null && Files.isRegularFile(file) && Files.size(file) <= LARGEST_ICON) {
        return Files.readAllBytes(file);
      }
    } catch (IOException | RuntimeException e) {
      return null;
    }
    return null;
  }

  @Override
  public String empty() {
    return Lang.tr("No worlds yet. Create one to start playing.");
  }

  @Override
  public void open(int index) {
    if (list.children().get(index) instanceof WorldSelectionList.WorldListEntry world) {
      world.joinWorld();
    }
  }
}
