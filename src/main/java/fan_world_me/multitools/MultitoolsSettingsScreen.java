package fan_world_me.multitools;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class MultitoolsSettingsScreen extends Screen {
   private final Screen parent;

   MultitoolsSettingsScreen(Screen parent) {
      super(Text.literal("Multitools Settings"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      int centerX = this.width / 2;
      int y = this.height / 4;
      int buttonWidth = 220;
      int buttonHeight = 20;
      int gap = 24;

      this.addDrawableChild(ButtonWidget.builder(this.getElytraSwapText(), button -> {
         MultitoolsSettings.elytraSwapEnabled = !MultitoolsSettings.elytraSwapEnabled;
         button.setMessage(this.getElytraSwapText());
      }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());

      y += gap;
      this.addDrawableChild(ButtonWidget.builder(this.getDiveComboText(), button -> {
         MultitoolsSettings.diveComboEnabled = !MultitoolsSettings.diveComboEnabled;
         button.setMessage(this.getDiveComboText());
      }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());

      y += gap;
      this.addDrawableChild(ButtonWidget.builder(this.getObstacleText(), button -> {
         MultitoolsSettings.diveComboAvoidObstacles = !MultitoolsSettings.diveComboAvoidObstacles;
         button.setMessage(this.getObstacleText());
      }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());

      y += gap;
      this.addDrawableChild(ButtonWidget.builder(this.getDropCommandText(), button -> {
         MultitoolsSettings.dropHeldItemBeforeGiveCommand = !MultitoolsSettings.dropHeldItemBeforeGiveCommand;
         button.setMessage(this.getDropCommandText());
      }).dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());

      y += gap + 8;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
         .dimensions(centerX - 60, y, 120, buttonHeight)
         .build());
   }

   @Override
   public void close() {
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }

   private Text getElytraSwapText() {
      return Text.literal("Elytra Swap: " + this.toggleValue(MultitoolsSettings.elytraSwapEnabled));
   }

   private Text getDiveComboText() {
      return Text.literal("Dive Combo: " + this.toggleValue(MultitoolsSettings.diveComboEnabled));
   }

   private Text getObstacleText() {
      return Text.literal("Dive Obstacle Avoid: " + this.toggleValue(MultitoolsSettings.diveComboAvoidObstacles));
   }

   private Text getDropCommandText() {
      return Text.literal("Drop Before F7/F8: " + this.toggleValue(MultitoolsSettings.dropHeldItemBeforeGiveCommand));
   }

   private String toggleValue(boolean enabled) {
      return enabled ? "ON" : "OFF";
   }
}
