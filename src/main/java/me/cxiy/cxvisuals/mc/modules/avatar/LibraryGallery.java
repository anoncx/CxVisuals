package me.cxiy.cxvisuals.mc.modules.avatar;

import java.util.List;
import me.cxiy.cxvisuals.gui.Gallery;
import me.cxiy.cxvisuals.modules.avatar.AvatarModule;
import me.cxiy.cxvisuals.modules.avatar.library.AvatarEntry;
import me.cxiy.cxvisuals.modules.avatar.library.AvatarLibrary;

public final class LibraryGallery implements Gallery {
  private final AvatarModule module;
  private final AvatarLibrary library;

  public LibraryGallery(AvatarModule module, AvatarLibrary library) {
    this.module = module;
    this.library = library;
  }

  private AvatarEntry entry(int index) {
    return library.entries().get(index);
  }

  @Override
  public int count() {
    return library.entries().size();
  }

  @Override
  public String name(int index) {
    return entry(index).name();
  }

  @Override
  public String meta(int index) {
    return entry(index).meta();
  }

  @Override
  public int state(int index) {
    AvatarEntry entry = entry(index);
    return switch (entry.state()) {
      case READY -> READY;
      case BROKEN -> BROKEN;
      case LOADING -> {
        Avatars.load(entry);
        yield BUSY;
      }
      case IMPORTING -> BUSY;
    };
  }

  @Override
  public int handle(int index) {
    return entry(index).handle();
  }

  @Override
  public boolean animated(int index) {
    return entry(index).animated();
  }

  @Override
  public int selected() {
    AvatarEntry entry = Avatars.selectedEntry();
    return entry == null ? -1 : library.entries().indexOf(entry);
  }

  @Override
  public void select(int index) {
    List<AvatarEntry> entries = library.entries();
    if (index < 0 || index >= entries.size()) {
      module.selected.set("");
      return;
    }
    AvatarEntry entry = entries.get(index);
    if (entry.state() == AvatarEntry.State.IMPORTING || entry.state() == AvatarEntry.State.BROKEN) {
      return;
    }
    module.selected.set(entry.id());
    module.enabled.set(true);
  }

  @Override
  public void requestImport() {
    Importer.open();
  }

  @Override
  public void delete(int index) {
    AvatarEntry entry = entry(index);
    if (entry == Avatars.selectedEntry()) {
      module.selected.set("");
    }
    library.delete(entry);
  }

  @Override
  public String notice() {
    return library.notice();
  }

  @Override
  public int noticeId() {
    return library.noticeId();
  }
}
