package fan_world_me.multitools;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

final class MultitoolsSettingsScreen extends Screen {
   private static final int PANEL_WIDTH = 860;
   private static final int ROW_HEIGHT = 64;
   private static final int TOGGLE_WIDTH = 190;
   private static final int HELP_WIDTH = 54;
   private static final int FOOTER_HEIGHT = 44;

   private final Screen parent;
   private final List<SettingRow> rows = new ArrayList<>();

   private int panelLeft;
   private int panelTop;
   private int panelWidth;
   private int contentTop;
   private int footerTop;

   MultitoolsSettingsScreen(Screen parent) {
      super(Text.literal("Multitools Settings"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      this.clearChildren();
      this.rows.clear();

      this.panelWidth = Math.min(PANEL_WIDTH, this.width - 40);
      this.panelLeft = (this.width - this.panelWidth) / 2;
      this.panelTop = 24;
      this.contentTop = this.panelTop + 74;
      this.footerTop = this.height - 74;

      this.rows.add(new SettingRow(
         "Elytra Swap",
         "Keeps automatic elytra equip and landing chestplate restore active.",
         new BooleanState(() -> MultitoolsSettings.elytraSwapEnabled, value -> MultitoolsSettings.elytraSwapEnabled = value)
      ));
      this.rows.add(new SettingRow(
         "Dive Combo",
         "Enables the Mouse 4 elytra-mace combo state machine.",
         new BooleanState(() -> MultitoolsSettings.diveComboEnabled, value -> MultitoolsSettings.diveComboEnabled = value)
      ));
      this.rows.add(new SettingRow(
         "Dive Obstacle Avoid",
         "Makes dive setup climb before trees or solid blocks on the approach path.",
         new BooleanState(() -> MultitoolsSettings.diveComboAvoidObstacles, value -> MultitoolsSettings.diveComboAvoidObstacles = value)
      ));
      this.rows.add(new SettingRow(
         "Drop Before F7/F8",
         "Moves the held item away before give-commands, and drops only as the last fallback.",
         new BooleanState(() -> MultitoolsSettings.dropHeldItemBeforeGiveCommand, value -> MultitoolsSettings.dropHeldItemBeforeGiveCommand = value)
      ));

      for (int index = 0; index < this.rows.size(); ++index) {
         this.addRowWidgets(index, this.rows.get(index));
      }

      int buttonWidth = (this.panelWidth - 24) / 2;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset Defaults"), button -> {
         MultitoolsSettings.elytraSwapEnabled = true;
         MultitoolsSettings.diveComboEnabled = true;
         MultitoolsSettings.diveComboAvoidObstacles = true;
         MultitoolsSettings.dropHeldItemBeforeGiveCommand = true;
         this.init();
      }).dimensions(this.panelLeft, this.footerTop, buttonWidth, FOOTER_HEIGHT).build());

      this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
         .dimensions(this.panelLeft + this.panelWidth - buttonWidth, this.footerTop, buttonWidth, FOOTER_HEIGHT)
         .build());
   }

   private void addRowWidgets(int index, SettingRow row) {
      int rowTop = this.contentTop + index * ROW_HEIGHT;
      int toggleLeft = this.panelLeft + this.panelWidth - TOGGLE_WIDTH - HELP_WIDTH;
      int helpLeft = this.panelLeft + this.panelWidth - HELP_WIDTH;

      this.addDrawableChild(ButtonWidget.builder(this.getToggleText(row.state().getAsBoolean()), button -> {
         row.state().set(!row.state().getAsBoolean());
         button.setMessage(this.getToggleText(row.state().getAsBoolean()));
      }).dimensions(toggleLeft, rowTop + 10, TOGGLE_WIDTH, ROW_HEIGHT - 14).build());

      this.addDrawableChild(ButtonWidget.builder(Text.literal("?"), button -> {
      }).dimensions(helpLeft, rowTop + 10, HELP_WIDTH, ROW_HEIGHT - 14).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(row.description()))).build());
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context, mouseX, mouseY, delta);
      context.fill(this.panelLeft - 20, this.panelTop - 16, this.panelLeft + this.panelWidth + 20, this.height - 20, 0x9A000000);
      context.fill(this.panelLeft - 20, this.panelTop - 18, this.panelLeft + this.panelWidth + 20, this.panelTop - 16, 0xBFD6D6D6);

      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Multitools Config"), this.width / 2, this.panelTop - 6, 0xF0F0F0);
      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Combat and Utility"), this.width / 2, this.panelTop + 22, 0xD8D8D8);

      for (int index = 0; index < this.rows.size(); ++index) {
         int rowTop = this.contentTop + index * ROW_HEIGHT;
         context.drawTextWithShadow(this.textRenderer, this.rows.get(index).title(), this.panelLeft + 14, rowTop + 20, 0xFFFFFF);
         context.fill(this.panelLeft + this.panelWidth - TOGGLE_WIDTH - HELP_WIDTH, rowTop + 10, this.panelLeft + this.panelWidth, rowTop + ROW_HEIGHT - 4, 0xD0000000);
      }

      super.render(context, mouseX, mouseY, delta);
   }

   @Override
   public void close() {
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }

   private Text getToggleText(boolean enabled) {
      return Text.literal(enabled ? "ON" : "OFF");
   }

   private record SettingRow(String title, String description, BooleanState state) {
   }

   @FunctionalInterface
   private interface BooleanSetter {
      void set(boolean value);
   }

   private record BooleanState(java.util.function.BooleanSupplier getter, BooleanSetter setter) {
      boolean getAsBoolean() {
         return this.getter.getAsBoolean();
      }

      void set(boolean value) {
         this.setter.set(value);
      }
   }
}
