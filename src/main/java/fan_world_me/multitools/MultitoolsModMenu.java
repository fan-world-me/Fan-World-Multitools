package fan_world_me.multitools;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;

public final class MultitoolsModMenu implements ModMenuApi {
   @Override
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return MultitoolsSettingsScreen::new;
   }

   public static Screen createConfigScreen(Screen parent) {
      return new MultitoolsSettingsScreen(parent);
   }
}
