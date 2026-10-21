package me.cxiy.cxvisuals.mc;

import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.i18n.Language;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.LanguagePicker;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.mc.gui.LanguageScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.LanguageManager;

public final class Languages {
  private static ClientSettings client;
  private static Supplier<Theme> theme;

  private Languages() {}

  static void init(ClientSettings settings, Supplier<Theme> themes) {
    client = settings;
    theme = themes;
    Lang.use(settings.language.get());
    settings.language.onChange(Languages::changed);
  }

  static void tick(Minecraft mc) {
    if (client.languagePicked.get()
        || mc.gui.overlay() != null
        || !(mc.gui.screen() instanceof TitleScreen title)) {
      return;
    }
    Language suggested = Language.ofMinecraft(mc.getLanguageManager().getSelected());
    mc.gui.setScreen(
        new LanguageScreen(title, new LanguagePicker(suggested, Languages::pick), theme));
  }

  private static void pick(Language language) {
    client.language.set(language);
    client.languagePicked.set(true);
    follow(language);
  }

  private static void changed() {
    client.languagePicked.set(true);
    follow(client.language.get());
  }

  private static void follow(Language language) {
    Minecraft mc = Minecraft.getInstance();
    LanguageManager manager = mc.getLanguageManager();
    if (Language.ofMinecraft(manager.getSelected()) == language
        || manager.getLanguage(language.minecraft) == null) {
      return;
    }
    manager.setSelected(language.minecraft);
    mc.options.languageCode = language.minecraft;
    manager.onResourceManagerReload(mc.getResourceManager());
    mc.options.save();
    Screen screen = mc.gui.screen();
    if (screen != null && !(screen instanceof LanguageScreen)) {
      screen.resize(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
  }
}
