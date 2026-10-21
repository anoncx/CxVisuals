package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

final class FaceTextures {
  private static final int SLOTS = 32;
  private static final int EMPTY = Integer.MIN_VALUE;
  private static final int LABEL_FRAMES = 40;

  private final int[] ids = new int[SLOTS];
  private final GpuTextureView[] views = new GpuTextureView[SLOTS];
  private final TextureSetup[] setups = new TextureSetup[SLOTS];
  private final Component[] labels = new Component[SLOTS];
  private final int[] labelAge = new int[SLOTS];
  private int next;

  FaceTextures() {
    for (int i = 0; i < SLOTS; i++) {
      ids[i] = EMPTY;
    }
  }

  Component label(int entityId) {
    int slot = -1;
    for (int i = 0; i < SLOTS; i++) {
      if (ids[i] == entityId) {
        slot = i;
        break;
      }
    }
    if (slot >= 0 && labels[slot] != null && ++labelAge[slot] < LABEL_FRAMES) {
      return labels[slot];
    }
    Minecraft mc = Minecraft.getInstance();
    Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
    if (!(entity instanceof AbstractClientPlayer player)) {
      return slot < 0 ? null : labels[slot];
    }
    if (slot < 0) {
      slot = claim(entityId);
    }
    labels[slot] = player.getDisplayName();
    labelAge[slot] = 0;
    return labels[slot];
  }

  private int claim(int entityId) {
    int slot = next;
    next = (next + 1) % SLOTS;
    ids[slot] = entityId;
    views[slot] = null;
    setups[slot] = null;
    labels[slot] = null;
    return slot;
  }

  TextureSetup setup(int entityId) {
    int slot = -1;
    for (int i = 0; i < SLOTS; i++) {
      if (ids[i] == entityId) {
        slot = i;
        break;
      }
    }
    Minecraft mc = Minecraft.getInstance();
    Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
    if (!(entity instanceof AbstractClientPlayer player)) {
      return slot < 0 ? null : setups[slot];
    }
    GpuTextureView view;
    try {
      view =
          mc.getTextureManager().getTexture(player.getSkin().body().texturePath()).getTextureView();
    } catch (RuntimeException e) {
      return slot < 0 ? null : setups[slot];
    }
    if (slot < 0) {
      slot = claim(entityId);
    }
    if (views[slot] != view) {
      views[slot] = view;
      setups[slot] =
          TextureSetup.singleTexture(
              view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }
    return setups[slot];
  }
}
