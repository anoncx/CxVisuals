package me.cxiy.cxvisuals.mc.modules.map;

import com.mojang.blaze3d.platform.Window;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.map.ExploreHost;
import me.cxiy.cxvisuals.gui.map.MapHost;
import me.cxiy.cxvisuals.gui.map.MapPlayer;
import me.cxiy.cxvisuals.gui.map.MapScreenView;
import me.cxiy.cxvisuals.gui.map.ShareHost;
import me.cxiy.cxvisuals.mc.compat.Keys;
import me.cxiy.cxvisuals.mc.gui.GlassPipeline;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.gui.ShapePipeline;
import me.cxiy.cxvisuals.mc.modules.download.Downloads;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.social.ExploreLink;
import me.cxiy.cxvisuals.mc.modules.social.FriendsLink;
import me.cxiy.cxvisuals.mc.modules.social.RoomShare;
import me.cxiy.cxvisuals.mc.modules.waypoints.Markers;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.map.BlockSearch;
import me.cxiy.cxvisuals.modules.map.MapSource;
import me.cxiy.cxvisuals.modules.map.regions.DonutRegions;
import me.cxiy.cxvisuals.modules.waypoints.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import me.cxiy.cxvisuals.mc.compat.Mouse;

public final class MapScreen extends Screen {
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final MapHost HOST =
      new MapHost() {
        @Override
        public MapSource source() {
          return Maps.source();
        }

        @Override
        public HudData data() {
          return Hud.module().data;
        }

        @Override
        public Waypoints waypoints() {
          return Markers.module().waypoints;
        }

        @Override
        public String title() {
          Minecraft mc = Minecraft.getInstance();
          if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
          }
          ServerData server = mc.getCurrentServer();
          if (server == null) {
            return Lang.tr("Map");
          }
          return server.name == null || server.name.isBlank() ? server.ip : server.name;
        }

        @Override
        public String dimension() {
          return Markers.dimension(Minecraft.getInstance());
        }

        @Override
        public int height(int x, int z) {
          return Maps.height(x, z);
        }

        @Override
        public List<Waypoints.Target> friends() {
          return FriendsLink.targets();
        }

        @Override
        public void shareArea(
            int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, Waypoints.Target to) {
          Maps.shareArea(minChunkX, minChunkZ, maxChunkX, maxChunkZ, to);
        }

        @Override
        public boolean downloadArea(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
          return Downloads.area(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        }

        @Override
        public void findBlocks(
            String query, double nearX, double nearZ, Consumer<BlockSearch.Result> done) {
          Maps.world().search(query, nearX, nearZ, done);
        }

        @Override
        public MapPlayer player(int entityId) {
          return Maps.player(entityId);
        }

        @Override
        public String notice() {
          return Maps.notice();
        }

        @Override
        public boolean regionsAvailable() {
          Minecraft mc = Minecraft.getInstance();
          return (FORCE_REGIONS || DonutRegions.matches(Markers.scope(mc)))
              && "minecraft:overworld".equals(Markers.dimension(mc));
        }

        @Override
        public boolean regionsOn() {
          return Maps.module().regions.get();
        }

        @Override
        public void regionsOn(boolean on) {
          Maps.module().regions.set(on);
        }

        @Override
        public int regionAt(double x, double z) {
          return DonutRegions.cellAt(x, z);
        }

        @Override
        public ShareHost share() {
          return RoomShare.INSTANCE;
        }

        @Override
        public ExploreHost explore(Supplier<int[]> selection) {
          return new ExploreLink(selection);
        }
      };
  private static final boolean FORCE_REGIONS = Boolean.getBoolean("cxvisuals.regions");
  private static MapScreenView view;

  private final Screen parent;
  private final Supplier<Theme> theme;
  private final McCanvas canvas = new McCanvas();
  private long lastNanos;
  private float pointerScale = 1.0F;

  private MapScreen(Screen parent, Supplier<Theme> theme) {
    super(Component.literal("Map"));
    ShapePipeline.recheck();
    GlassPipeline.recheck();
    this.parent = parent;
    this.theme = theme;
    if (view == null) {
      view = new MapScreenView(HOST);
    }
    view.open();
  }

  static void open(Screen parent, Supplier<Theme> theme) {
    Minecraft.getInstance().gui.setScreen(new MapScreen(parent, theme));
  }

  public static MapScreenView view() {
    return view;
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {}

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;
    Window window = this.minecraft.getWindow();
    int guiScale = Math.max(1, window.getGuiScale());
    float interfaceScale = McCanvas.interfaceScale(window.getHeight());
    pointerScale = guiScale / interfaceScale;
    view.resize(window.getWidth() / interfaceScale, window.getHeight() / interfaceScale);
    canvas.begin(graphics, interfaceScale, guiScale);
    view.draw(canvas, theme.get(), mouseX * pointerScale, mouseY * pointerScale, dt);
    canvas.end();
    String copied = view.takeCopy();
    if (copied != null) {
      this.minecraft.keyboardHandler.setClipboard(copied);
    }
  }

  @Override
  public void tick() {
    if (view.closed() || !Maps.live()) {
      this.minecraft.gui.setScreen(parent);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
    float x = (float) event.x() * pointerScale;
    float y = (float) event.y() * pointerScale;
    if (Mouse.button(event) == 0) {
      view.mouseDown(x, y, event.hasShiftDown());
    } else if (Mouse.button(event) == 1) {
      view.secondaryDown(x, y);
    }
    return true;
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    if (Mouse.button(event) == 0) {
      view.mouseUp((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    } else if (Mouse.button(event) == 1) {
      view.secondaryUp((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
    if (Mouse.button(event) == 0) {
      view.mouseDrag((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    } else if (Mouse.button(event) == 1) {
      view.secondaryDrag((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
    view.scroll((float) x * pointerScale, (float) y * pointerScale, (float) scrollY);
    return true;
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (!view.typing() && Maps.closes(event)) {
      view.close();
      return true;
    }
    return view.keyDown(Keys.code(event), event.hasControlDown(), event.hasShiftDown())
        || super.keyPressed(event);
  }

  @Override
  public boolean charTyped(CharacterEvent event) {
    return view.charTyped(event.codepoint()) || super.charTyped(event);
  }

  @Override
  public boolean shouldCloseOnEsc() {
    return false;
  }

  @Override
  public void onClose() {
    view.close();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
