package me.cxiy.cxvisuals.menu.model;

public interface TextStore {
  String read();

  void write(String value);

  static TextStore memory() {
    return new TextStore() {
      private String saved = "";

      @Override
      public String read() {
        return saved;
      }

      @Override
      public void write(String value) {
        saved = value;
      }
    };
  }
}
