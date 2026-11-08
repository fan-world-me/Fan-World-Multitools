package fan_world_me.multitools;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

final class MultitoolsSettingsScreen extends Screen {
   private static final int ROW_HEIGHT = 26;
   private static final int PANEL_WIDTH = 920;
   private static final int PANEL_TOP = 44;
   private static final int PANEL_BOTTOM = 42;

   private final Screen parent;
   private final List<FeatureEntry> featureEntries = new ArrayList<>();
   private final List<DelayEntry> delayEntries = new ArrayList<>();
   private final List<TextFieldWidget> delayFields = new ArrayList<>();

   private Tab tab = Tab.FEATURES;
   private int scrollOffset = 0;
   private ButtonWidget featuresTabButton;
   private ButtonWidget delaysTabButton;
   private ButtonWidget resetButton;
   private ButtonWidget doneButton;
   private ButtonWidget scrollUpButton;
   private ButtonWidget scrollDownButton;
   private BindingTarget listeningTarget;

   MultitoolsSettingsScreen(Screen parent) {
      super(Text.literal("Multitools"));
      this.parent = parent;
      this.buildEntries();
   }

   @Override
   protected void init() {
      this.clearChildren();
      this.delayFields.clear();

      int panelX = this.panelX();
      int panelY = PANEL_TOP;
      int panelWidth = this.panelWidth();
      int panelHeight = this.panelHeight();

      this.featuresTabButton = this.addDrawableChild(ButtonWidget.builder(this.tabLabel(Tab.FEATURES), button -> this.switchTab(Tab.FEATURES))
         .dimensions(panelX + 16, panelY + 14, 150, 20)
         .build());
      this.delaysTabButton = this.addDrawableChild(ButtonWidget.builder(this.tabLabel(Tab.DELAYS), button -> this.switchTab(Tab.DELAYS))
         .dimensions(panelX + 172, panelY + 14, 150, 20)
         .build());

      this.scrollUpButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("^"), button -> this.adjustScroll(-1))
         .dimensions(panelX + panelWidth - 38, panelY + 48, 22, 20)
         .build());
      this.scrollDownButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("v"), button -> this.adjustScroll(1))
         .dimensions(panelX + panelWidth - 38, panelY + panelHeight - 78, 22, 20)
         .build());

      this.resetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset Defaults"), button -> this.resetDefaults())
         .dimensions(panelX + 16, panelY + panelHeight - 30, 150, 20)
         .build());
      this.doneButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
         .dimensions(panelX + panelWidth - 106, panelY + panelHeight - 30, 90, 20)
         .build());

      this.rebuildVisibleWidgets();
   }

   @Override
   public void close() {
      this.persistSettings();
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }

   @Override
   public void removed() {
      this.persistSettings();
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.renderPlainBackground(context);
      int panelX = this.panelX();
      int panelY = PANEL_TOP;
      int panelWidth = this.panelWidth();
      int panelHeight = this.panelHeight();
      int contentTop = panelY + 48;
      int contentBottom = panelY + panelHeight - 42;

      context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0101016);
      context.drawStrokedRectangle(panelX, panelY, panelWidth, panelHeight, 0xFF4E4E58);
      context.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 40, 0xFF1B1C24);
      context.fill(panelX + 1, contentTop - 2, panelX + panelWidth - 1, contentBottom, 0xCC14161D);

      super.render(context, mouseX, mouseY, delta);

      context.drawText(this.textRenderer, this.title, panelX + 16, panelY + 14, 0xFFFFFF, false);
      context.drawText(this.textRenderer, Text.literal("Feature"), panelX + 18, contentTop + 4, 0xBFC7D5, false);

      if (this.tab == Tab.FEATURES) {
         context.drawText(this.textRenderer, Text.literal("Bind"), panelX + panelWidth - 250, contentTop + 4, 0xBFC7D5, false);
         context.drawText(this.textRenderer, Text.literal("State"), panelX + panelWidth - 122, contentTop + 4, 0xBFC7D5, false);
         this.renderFeatureDescriptions(context, mouseX, mouseY, contentTop);
      } else {
         context.drawText(this.textRenderer, Text.literal("Ticks"), panelX + panelWidth - 194, contentTop + 4, 0xBFC7D5, false);
         this.renderDelayDescriptions(context, mouseX, mouseY, contentTop);
      }

      if (this.listeningTarget != null) {
         String waiting = this.listeningTarget.label() + ": press key or mouse button";
         context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(waiting), this.width / 2, panelY + panelHeight - 52, 0xFFE082);
      }

      int totalRows = this.activeRows().size();
      int visibleRows = this.visibleRowCount();
      int pageInfoY = panelY + panelHeight - 52;
      if (totalRows > visibleRows) {
         int start = Math.min(totalRows, this.scrollOffset + 1);
         int end = Math.min(totalRows, this.scrollOffset + visibleRows);
         context.drawText(this.textRenderer, Text.literal(start + "-" + end + " / " + totalRows), panelX + panelWidth - 94, pageInfoY, 0x9FA7B5, false);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (verticalAmount == 0.0D) {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }

      this.adjustScroll(verticalAmount > 0.0D ? -1 : 1);
      return true;
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubleClick) {
      if (this.listeningTarget != null && click.button() >= GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
         this.applyBinding(InputUtil.Type.MOUSE.createFromCode(click.button()));
         return true;
      }

      return super.mouseClicked(click, doubleClick);
   }

   @Override
   public boolean keyPressed(KeyInput input) {
      if (this.listeningTarget != null) {
         if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.listeningTarget = null;
            this.rebuildVisibleWidgets();
            return true;
         }

         this.applyBinding(InputUtil.fromKeyCode(input));
         return true;
      }

      return super.keyPressed(input);
   }

   @Override
   public boolean charTyped(CharInput input) {
      return super.charTyped(input);
   }

   private void renderPlainBackground(DrawContext context) {
      context.fillGradient(0, 0, this.width, this.height, 0xFF0A0B10, 0xFF11141C);
   }

   private void renderFeatureDescriptions(DrawContext context, int mouseX, int mouseY, int contentTop) {
      List<?> visible = this.visibleRows();
      for (int index = 0; index < visible.size(); ++index) {
         FeatureEntry entry = (FeatureEntry) visible.get(index);
         int rowY = contentTop + 22 + index * ROW_HEIGHT;
         context.drawText(this.textRenderer, Text.literal(entry.label), this.panelX() + 18, rowY + 6, 0xF0F2F5, false);
         context.drawText(this.textRenderer, Text.literal(entry.description), this.panelX() + 240, rowY + 6, 0x8791A1, false);
      }
   }

   private void renderDelayDescriptions(DrawContext context, int mouseX, int mouseY, int contentTop) {
      List<?> visible = this.visibleRows();
      for (int index = 0; index < visible.size(); ++index) {
         DelayEntry entry = (DelayEntry) visible.get(index);
         int rowY = contentTop + 22 + index * ROW_HEIGHT;
         context.drawText(this.textRenderer, Text.literal(entry.label), this.panelX() + 18, rowY + 6, 0xF0F2F5, false);
         context.drawText(this.textRenderer, Text.literal(entry.description), this.panelX() + 240, rowY + 6, 0x8791A1, false);
      }
   }

   private void switchTab(Tab newTab) {
      if (this.tab == newTab) {
         return;
      }

      this.tab = newTab;
      this.scrollOffset = 0;
      this.listeningTarget = null;
      this.rebuildVisibleWidgets();
   }

   private void adjustScroll(int amount) {
      int max = this.maxScroll();
      this.scrollOffset = Math.max(0, Math.min(max, this.scrollOffset + amount));
      this.rebuildVisibleWidgets();
   }

   private void resetDefaults() {
      MultitoolsSettings.restoreDefaults();
      MultitoolsSettings.applyKeybindings();
      if (this.client != null) {
         KeyBinding.updateKeysByCode();
         this.client.options.write();
      }
      this.scrollOffset = 0;
      this.listeningTarget = null;
      this.rebuildVisibleWidgets();
   }

   private void persistSettings() {
      MultitoolsSettings.captureKeybindings();
      MultitoolsSettings.save();
      if (this.client != null) {
         this.client.options.write();
      }
   }

   private void applyBinding(InputUtil.Key key) {
      this.listeningTarget.keyBinding().setBoundKey(key);
      KeyBinding.updateKeysByCode();
      if (this.client != null) {
         this.client.options.write();
      }
      MultitoolsSettings.captureKeybindings();
      MultitoolsSettings.save();
      this.listeningTarget = null;
      this.rebuildVisibleWidgets();
   }

   private void rebuildVisibleWidgets() {
      this.clearDynamicChildren();
      int panelX = this.panelX();
      int contentTop = PANEL_TOP + 70;
      int visibleRows = this.visibleRowCount();

      if (this.tab == Tab.FEATURES) {
         List<?> visible = this.visibleRows();
         for (int index = 0; index < visible.size(); ++index) {
            FeatureEntry entry = (FeatureEntry) visible.get(index);
            int rowY = contentTop + index * ROW_HEIGHT;

            if (entry.keyBinding != null) {
               Text bindText = this.listeningTarget != null && this.listeningTarget.keyBinding == entry.keyBinding
                  ? Text.literal("Press...")
                  : Text.literal(entry.keyBinding.getBoundKeyLocalizedText().getString());
               this.addDrawableChild(ButtonWidget.builder(bindText, button -> {
                     this.listeningTarget = new BindingTarget(entry.label, entry.keyBinding);
                     this.rebuildVisibleWidgets();
                  })
                  .dimensions(panelX + this.panelWidth() - 274, rowY, 140, 20)
                  .build());
            }

            this.addDrawableChild(ButtonWidget.builder(this.toggleText(entry.enabled.getAsBoolean()), button -> {
                  entry.setter.accept(!entry.enabled.getAsBoolean());
                  MultitoolsSettings.save();
                  this.rebuildVisibleWidgets();
               })
               .dimensions(panelX + this.panelWidth() - 118, rowY, 92, 20)
               .build());
         }
      } else {
         List<?> visible = this.visibleRows();
         for (int index = 0; index < visible.size(); ++index) {
            DelayEntry entry = (DelayEntry) visible.get(index);
            int rowY = contentTop + index * ROW_HEIGHT;
            int fieldX = panelX + this.panelWidth() - 190;

            this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> {
                  entry.setter.accept(Math.max(entry.minValue, entry.getter.getAsInt() - 1));
                  MultitoolsSettings.save();
                  this.rebuildVisibleWidgets();
               })
               .dimensions(fieldX - 26, rowY, 20, 20)
               .build());

            TextFieldWidget field = this.addDrawableChild(new TextFieldWidget(this.textRenderer, fieldX, rowY, 70, 20, Text.literal(entry.label)));
            field.setText(Integer.toString(entry.getter.getAsInt()));
            field.setChangedListener(text -> this.applyDelayField(entry, field));
            this.delayFields.add(field);

            this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> {
                  entry.setter.accept(Math.min(entry.maxValue, entry.getter.getAsInt() + 1));
                  MultitoolsSettings.save();
                  this.rebuildVisibleWidgets();
               })
               .dimensions(fieldX + 76, rowY, 20, 20)
               .build());
         }
      }

      this.featuresTabButton.active = this.tab != Tab.FEATURES;
      this.delaysTabButton.active = this.tab != Tab.DELAYS;
      this.scrollUpButton.active = this.scrollOffset > 0;
      this.scrollDownButton.active = this.scrollOffset < this.maxScroll();
      this.resetButton.active = true;
      this.doneButton.active = true;
   }

   private void applyDelayField(DelayEntry entry, TextFieldWidget field) {
      String text = field.getText().trim();
      if (text.isEmpty() || text.equals("-")) {
         return;
      }

      try {
         int value = Integer.parseInt(text);
         value = Math.max(entry.minValue, Math.min(entry.maxValue, value));
         entry.setter.accept(value);
         MultitoolsSettings.save();
      } catch (NumberFormatException ignored) {
      }
   }

   private void clearDynamicChildren() {
      if (this.featuresTabButton == null) {
         return;
      }

      this.clearChildren();
      this.addDrawableChild(this.featuresTabButton);
      this.addDrawableChild(this.delaysTabButton);
      this.addDrawableChild(this.scrollUpButton);
      this.addDrawableChild(this.scrollDownButton);
      this.addDrawableChild(this.resetButton);
      this.addDrawableChild(this.doneButton);
      this.delayFields.clear();
   }

   private void buildEntries() {
      if (!this.featureEntries.isEmpty() || !this.delayEntries.isEmpty()) {
         return;
      }

      this.featureEntries.add(new FeatureEntry("Spear Dash", "Lunge spear dash automation", () -> MultitoolsSettings.spearDashEnabled, value -> MultitoolsSettings.spearDashEnabled = value, ModKeyBindings.SPEAR_DASH));
      this.featureEntries.add(new FeatureEntry("Pearl Catch", "Throw pearl and auto follow with wind charge", () -> MultitoolsSettings.pearlCatchEnabled, value -> MultitoolsSettings.pearlCatchEnabled = value, ModKeyBindings.PEARL_CATCH));
      this.featureEntries.add(new FeatureEntry("Smart Attack", "Late-hit target selection while diving", () -> MultitoolsSettings.smartAttackEnabled, value -> MultitoolsSettings.smartAttackEnabled = value, ModKeyBindings.SMART_ATTACK));
      this.featureEntries.add(new FeatureEntry("Dive Combo", "Air loop with late mace strike", () -> MultitoolsSettings.diveComboEnabled, value -> MultitoolsSettings.diveComboEnabled = value, ModKeyBindings.DIVE_COMBO));
      this.featureEntries.add(new FeatureEntry("Dive Avoid Obstacles", "Prefer vertical gain instead of tree impact", () -> MultitoolsSettings.diveComboAvoidObstacles, value -> MultitoolsSettings.diveComboAvoidObstacles = value, null));
      this.featureEntries.add(new FeatureEntry("Firework Boost", "Manual firework trigger while gliding", () -> MultitoolsSettings.fireworkBoostEnabled, value -> MultitoolsSettings.fireworkBoostEnabled = value, ModKeyBindings.FIREWORK_BOOST));
      this.featureEntries.add(new FeatureEntry("Elytra Swap Feature", "Master switch for auto chest swap logic", () -> MultitoolsSettings.elytraSwapFeatureEnabled, value -> MultitoolsSettings.elytraSwapFeatureEnabled = value, null));
      this.featureEntries.add(new FeatureEntry("Elytra Swap State", "Current runtime state of auto elytra swap", () -> MultitoolsSettings.elytraSwapEnabled, value -> MultitoolsSettings.elytraSwapEnabled = value, ModKeyBindings.TOGGLE_ELYTRA_SWAP));
      this.featureEntries.add(new FeatureEntry("Auto Totem", "Move totem into offhand", () -> MultitoolsSettings.autoTotemEnabled, value -> MultitoolsSettings.autoTotemEnabled = value, ModKeyBindings.AUTO_TOTEM));
      this.featureEntries.add(new FeatureEntry("Load Main", "Run ck load main", () -> MultitoolsSettings.loadMainEnabled, value -> MultitoolsSettings.loadMainEnabled = value, ModKeyBindings.LOAD_MAIN));
      this.featureEntries.add(new FeatureEntry("Railgun", "Run givef railgun straight 1", () -> MultitoolsSettings.railgunEnabled, value -> MultitoolsSettings.railgunEnabled = value, ModKeyBindings.GIVE_RAILGUN));
      this.featureEntries.add(new FeatureEntry("Give Nuke", "Run givef nuke 1 11", () -> MultitoolsSettings.nukeEnabled, value -> MultitoolsSettings.nukeEnabled = value, ModKeyBindings.GIVE_NUKE));
      this.featureEntries.add(new FeatureEntry("Give Stab", "Run givef stab 1", () -> MultitoolsSettings.stabEnabled, value -> MultitoolsSettings.stabEnabled = value, ModKeyBindings.GIVE_STAB));
      this.featureEntries.add(new FeatureEntry("Self Buffs", "Use splash potions under player", () -> MultitoolsSettings.selfBuffsEnabled, value -> MultitoolsSettings.selfBuffsEnabled = value, ModKeyBindings.SELF_BUFFS));
      this.featureEntries.add(new FeatureEntry("Saturation", "Run infinite saturation command", () -> MultitoolsSettings.saturationEnabled, value -> MultitoolsSettings.saturationEnabled = value, ModKeyBindings.SATURATION));
      this.featureEntries.add(new FeatureEntry("Kill Non Players", "Run kill command for non-player entities", () -> MultitoolsSettings.killNonPlayersEnabled, value -> MultitoolsSettings.killNonPlayersEnabled = value, ModKeyBindings.KILL_NON_PLAYERS));
      this.featureEntries.add(new FeatureEntry("Drop Before Give", "Free the hand before give commands", () -> MultitoolsSettings.dropHeldItemBeforeGiveCommand, value -> MultitoolsSettings.dropHeldItemBeforeGiveCommand = value, null));

      this.delayEntries.add(new DelayEntry("Elytra Landing Swap", "Ticks before chestplate restore after landing", () -> MultitoolsSettings.elytraLandingSwapDelayTicks, value -> MultitoolsSettings.elytraLandingSwapDelayTicks = value, 0, 40));
      this.delayEntries.add(new DelayEntry("Spear Key Cooldown", "Cooldown between spear dash presses", () -> MultitoolsSettings.spearKeyCooldownTicks, value -> MultitoolsSettings.spearKeyCooldownTicks = value, 0, 40));
      this.delayEntries.add(new DelayEntry("Spear Swap Back", "Delay before returning to previous spear slot", () -> MultitoolsSettings.spearSwapBackDelayTicks, value -> MultitoolsSettings.spearSwapBackDelayTicks = value, 0, 20));
      this.delayEntries.add(new DelayEntry("Pearl Catch Aim", "Ticks spent stepping pitch after pearl use", () -> MultitoolsSettings.pearlCatchAimTicks, value -> MultitoolsSettings.pearlCatchAimTicks = value, 1, 20));
      this.delayEntries.add(new DelayEntry("Potion Reuse", "Ticks between support potion uses", () -> MultitoolsSettings.supportPotionReuseTicks, value -> MultitoolsSettings.supportPotionReuseTicks = value, 0, 20));
      this.delayEntries.add(new DelayEntry("Dive Target Refresh", "Ticks between target reacquire passes", () -> MultitoolsSettings.diveTargetRefreshTicks, value -> MultitoolsSettings.diveTargetRefreshTicks = value, 1, 40));
      this.delayEntries.add(new DelayEntry("Dive Vector Refresh", "Ticks between look-vector corrections", () -> MultitoolsSettings.diveVectorRefreshTicks, value -> MultitoolsSettings.diveVectorRefreshTicks = value, 1, 20));
      this.delayEntries.add(new DelayEntry("Dive Firework Reuse", "Ticks between dive firework uses", () -> MultitoolsSettings.diveFireworkReuseTicks, value -> MultitoolsSettings.diveFireworkReuseTicks = value, 0, 20));
      this.delayEntries.add(new DelayEntry("Dive Launch Recovery", "Ticks after self wind charge before boost", () -> MultitoolsSettings.diveLaunchRecoveryTicks, value -> MultitoolsSettings.diveLaunchRecoveryTicks = value, 0, 20));
      this.delayEntries.add(new DelayEntry("Dive Post Strike Recovery", "Ticks to hold post-hit re-engage", () -> MultitoolsSettings.divePostStrikeRecoveryTicks, value -> MultitoolsSettings.divePostStrikeRecoveryTicks = value, 0, 30));
      this.delayEntries.add(new DelayEntry("Dive Ground Relaunch", "Ticks before reattempting from ground", () -> MultitoolsSettings.diveGroundRelaunchDelayTicks, value -> MultitoolsSettings.diveGroundRelaunchDelayTicks = value, 0, 20));
   }

   private Text tabLabel(Tab tab) {
      return Text.literal(tab == this.tab ? "> " + tab.title : tab.title);
   }

   private Text toggleText(boolean enabled) {
      return Text.literal(enabled ? "ON" : "OFF");
   }

   private List<?> activeRows() {
      return this.tab == Tab.FEATURES ? this.featureEntries : this.delayEntries;
   }

   private List<?> visibleRows() {
      List<?> rows = this.activeRows();
      int fromIndex = Math.min(this.scrollOffset, rows.size());
      int toIndex = Math.min(rows.size(), fromIndex + this.visibleRowCount());
      return rows.subList(fromIndex, toIndex);
   }

   private int visibleRowCount() {
      return Math.max(1, (this.panelHeight() - 112) / ROW_HEIGHT);
   }

   private int maxScroll() {
      return Math.max(0, this.activeRows().size() - this.visibleRowCount());
   }

   private int panelWidth() {
      return Math.min(PANEL_WIDTH, this.width - 32);
   }

   private int panelHeight() {
      return this.height - PANEL_TOP - PANEL_BOTTOM;
   }

   private int panelX() {
      return (this.width - this.panelWidth()) / 2;
   }

   private record FeatureEntry(String label, String description, BooleanSupplier enabled, Consumer<Boolean> setter, KeyBinding keyBinding) {
   }

   private record DelayEntry(String label, String description, IntSupplier getter, IntConsumer setter, int minValue, int maxValue) {
   }

   private record BindingTarget(String label, KeyBinding keyBinding) {
   }

   private enum Tab {
      FEATURES("Features"),
      DELAYS("Delays");

      private final String title;

      Tab(String title) {
         this.title = title;
      }
   }
}
