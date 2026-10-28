package me.cxiy.cxvisuals.gui.map;

import java.util.Collection;
import java.util.List;
import me.cxiy.cxvisuals.modules.map.share.LocationMode;

public interface ShareHost {
  ShareHost NONE =
      new ShareHost() {
        @Override
        public boolean available() {
          return false;
        }

        @Override
        public boolean live() {
          return false;
        }

        @Override
        public String server() {
          return "";
        }

        @Override
        public String roomName() {
          return "";
        }

        @Override
        public List<ShareModel.Friend> friends() {
          return List.of();
        }

        @Override
        public List<LocationModel.Row> locationRows() {
          return List.of();
        }

        @Override
        public void share(Collection<String> uuids, boolean backfill) {}

        @Override
        public void locate(String uuid, LocationMode mode) {}

        @Override
        public void leave() {}

        @Override
        public boolean owner() {
          return false;
        }

        @Override
        public void remove(String uuid) {}
      };

  boolean available();

  boolean live();

  String server();

  String roomName();

  List<ShareModel.Friend> friends();

  List<LocationModel.Row> locationRows();

  void share(Collection<String> uuids, boolean backfill);

  void locate(String uuid, LocationMode mode);

  void leave();

  boolean owner();

  void remove(String uuid);
}
