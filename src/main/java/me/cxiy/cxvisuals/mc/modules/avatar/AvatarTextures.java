package me.cxiy.cxvisuals.mc.modules.avatar;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.bake.Texture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

final class AvatarTextures {
  static final Identifier WHITE =
      Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "avatar/white");

  private static boolean whiteReady;

  private final List<Identifier> owned = new ArrayList<>();
  private Identifier[] ids = new Identifier[0];

  Identifier[] bind(String key, BakedAvatar avatar) {
    release();
    TextureManager manager = Minecraft.getInstance().getTextureManager();
    ensureWhite(manager);
    ids = new Identifier[avatar.textures.length];
    for (int i = 0; i < avatar.textures.length; i++) {
      Texture texture = avatar.textures[i];
      Identifier id =
          Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "avatar/" + key + "/" + i);
      manager.register(id, upload(id.getPath(), texture));
      owned.add(id);
      ids[i] = id;
    }
    return ids;
  }

  Identifier texture(int index) {
    return index >= 0 && index < ids.length ? ids[index] : WHITE;
  }

  void release() {
    if (owned.isEmpty()) {
      return;
    }
    TextureManager manager = Minecraft.getInstance().getTextureManager();
    for (Identifier id : owned) {
      manager.release(id);
    }
    owned.clear();
    ids = new Identifier[0];
  }

  private static DynamicTexture upload(String label, Texture texture) {
    NativeImage image =
        new NativeImage(NativeImage.Format.RGBA, texture.width(), texture.height(), false);
    int[] argb = texture.argb();
    int width = texture.width();
    for (int y = 0; y < texture.height(); y++) {
      int row = y * width;
      for (int x = 0; x < width; x++) {
        image.setPixel(x, y, argb[row + x]);
      }
    }
    return new DynamicTexture(() -> "cxvisuals_" + label, image);
  }

  private static void ensureWhite(TextureManager manager) {
    if (whiteReady) {
      return;
    }
    whiteReady = true;
    manager.register(WHITE, upload("avatar_white", new Texture(1, 1, new int[] {0xFFFFFFFF})));
  }
}
