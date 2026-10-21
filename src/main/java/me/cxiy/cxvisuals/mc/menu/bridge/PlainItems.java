package me.cxiy.cxvisuals.mc.menu.bridge;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.menu.model.Item;
import me.cxiy.cxvisuals.menu.model.ItemList;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

class PlainItems<E extends ObjectSelectionList.Entry<E>> implements ItemList {
  private static final String SELECT = "narrator.select";

  protected final ObjectSelectionList<E> list;
  private final Map<Object, Item> items = new IdentityHashMap<>();

  PlainItems(ObjectSelectionList<E> list) {
    this.list = list;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static ItemList of(AbstractSelectionList<?> list) {
    return new PlainItems((ObjectSelectionList) list);
  }

  static String named(Component narration) {
    if (narration.getContents() instanceof TranslatableContents contents
        && SELECT.equals(contents.getKey())
        && contents.getArgs().length > 0) {
      Object subject = contents.getArgs()[0];
      return subject instanceof Component component
          ? Texts.plain(component)
          : String.valueOf(subject);
    }
    return Texts.plain(narration);
  }

  protected void fill(E entry, Item item) {
    item.title = named(entry.getNarration());
  }

  protected boolean live(E entry) {
    return false;
  }

  @Override
  public int size() {
    List<E> entries = list.children();
    if (items.size() > entries.size() * 2 + 8) {
      items.clear();
    }
    return entries.size();
  }

  @Override
  public Item get(int index) {
    E entry = list.children().get(index);
    Item item = items.get(entry);
    if (item == null) {
      item = new Item();
      items.put(entry, item);
      fill(entry, item);
    } else if (live(entry)) {
      fill(entry, item);
    }
    return item;
  }

  @Override
  public int selected() {
    E entry = list.getSelected();
    return entry == null ? NONE : list.children().indexOf(entry);
  }

  @Override
  public void select(int index) {
    list.setSelected(list.children().get(index));
  }

  @Override
  public void open(int index) {
    list.children().get(index).mouseClicked(WidgetControl.CLICK, true);
  }
}
