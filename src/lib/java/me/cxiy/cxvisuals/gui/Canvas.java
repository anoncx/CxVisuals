package me.cxiy.cxvisuals.gui;

public interface Canvas {
  int CIRCLES = 0;
  int TRAILS = 1;

  void roundRect(float x, float y, float w, float h, float radius, int argb);

  void roundRectGradient(
      float x, float y, float w, float h, float radius, int topArgb, int bottomArgb);

  void spectrum(float x, float y, float w, float h, float radius, float hue);

  void rim(float x, float y, float w, float h, float radius, float width, float fade, int argb);

  void glass(float x, float y, float w, float h, float radius, int tintArgb);

  void roundRectBorder(
      float x, float y, float w, float h, float radius, float borderWidth, int argb);

  void shadow(
      float x, float y, float w, float h, float radius, float blur, float offsetY, int argb);

  void text(FontStyle font, String text, float x, float y, int argb);

  float textWidth(FontStyle font, String text);

  void styled(FontStyle font, StyledText text, float x, float y, int argb);

  float styledWidth(FontStyle font, StyledText text);

  float lineHeight(FontStyle font);

  void pushClip(float x, float y, float w, float h);

  void popClip();

  void pushTransform(float scale, float pivotX, float pivotY);

  void popTransform();

  void pushAffine(float scale, float shiftX, float shiftY, float pivotX, float pivotY);

  void perspective(float yaw, float pitch, float distance);

  default void perspective(float yaw, float pitch, float distance, float pivotX, float pivotY) {
    perspective(yaw, pitch, distance);
  }

  void pushLayer(float height);

  void popLayer();

  void pushAlpha(float alpha);

  void popAlpha();

  void beginDissolve(float x, float y, float w, float h, float erosion);

  void endDissolve();

  void pushContrast(int plateArgb);

  void popContrast();

  default String fit(FontStyle font, String text, float max) {
    if (textWidth(font, text) <= max) {
      return text;
    }
    int end = text.length();
    while (end > 1 && textWidth(font, text.substring(0, end) + "\u2026") > max) {
      end--;
    }
    return text.substring(0, end) + "\u2026";
  }

  default boolean curves() {
    return false;
  }

  default void stroke(float[] xs, float[] ys, int count, float width, float softness, int argb) {}

  default void area(float[] xs, float[] ys, int count, float base, int topArgb, int baseArgb) {}

  default float pixel() {
    return 0.5F;
  }

  int AUDITION_NONE = -1;
  int AUDITION_CLIP = 16;

  boolean face(int entityId, float x, float y, float size, float radius);

  boolean icon(byte[] png, float x, float y, float size, float radius);

  int styleKey();

  int lines(int messageId, float width);

  boolean line(int messageId, int index, float x, float y, float height, int argb);

  float nameWidth(int entityId);

  boolean name(int entityId, float x, float y, float height);

  String clipboardText();

  void clipboardText(String text);

  void item(int slot, float x, float y, float size);

  void stack(int handle, float x, float y, float size);

  void held(int index, float x, float y, float size);

  void map(int index, float x, float y, float size);

  void player(float x, float y, float w, float h, boolean follow);

  void model(int handle, float x, float y, float w, float h, float yaw, boolean live, int audition);

  default boolean stage(
      int scene,
      float x,
      float y,
      float w,
      float h,
      float yaw,
      float pitch,
      float clock,
      float radius) {
    return false;
  }
}
