package me.cxiy.cxvisuals.gui.map;

import java.util.List;
import java.util.function.Consumer;

public interface ExploreHost {
  ExploreHost NONE =
      new ExploreHost() {
        @Override
        public boolean available() {
          return false;
        }

        @Override
        public boolean canPublish() {
          return false;
        }

        @Override
        public String server() {
          return "";
        }

        @Override
        public boolean hasSelection() {
          return false;
        }

        @Override
        public String suggestedTitle() {
          return "";
        }

        @Override
        public void list(
            ExploreModel.Request request, Consumer<ExploreModel.Page> done, Runnable failed) {
          failed.run();
        }

        @Override
        public void servers(Consumer<List<String>> done) {}

        @Override
        public byte[] preview(String id) {
          return new byte[0];
        }

        @Override
        public void add(String id, Consumer<Boolean> done) {
          done.accept(false);
        }

        @Override
        public void publish(PublishModel model, Consumer<Boolean> done) {
          done.accept(false);
        }

        @Override
        public void delete(String id, Runnable done) {}

        @Override
        public void report(String id) {}
      };

  boolean available();

  boolean canPublish();

  String server();

  boolean hasSelection();

  String suggestedTitle();

  void list(ExploreModel.Request request, Consumer<ExploreModel.Page> done, Runnable failed);

  void servers(Consumer<List<String>> done);

  byte[] preview(String id);

  void add(String id, Consumer<Boolean> done);

  void publish(PublishModel model, Consumer<Boolean> done);

  void delete(String id, Runnable done);

  void report(String id);
}
