package fan_world_me.multitools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

final class MultitoolsSettings {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("multitools.json");

   static boolean spearDashEnabled = true;
   static boolean pearlCatchEnabled = true;
   static boolean smartAttackEnabled = true;
   static boolean diveComboEnabled = true;
   static boolean fireworkBoostEnabled = true;
   static boolean elytraSwapFeatureEnabled = true;
   static boolean elytraSwapEnabled = true;
   static boolean autoTotemEnabled = true;
   static boolean loadMainEnabled = true;
   static boolean railgunEnabled = true;
   static boolean nukeEnabled = true;
   static boolean stabEnabled = true;
   static boolean selfBuffsEnabled = true;
   static boolean saturationEnabled = true;
   static boolean killNonPlayersEnabled = true;
   static boolean diveComboAvoidObstacles = true;
   static boolean dropHeldItemBeforeGiveCommand = true;

   static int elytraLandingSwapDelayTicks = 5;
   static int spearKeyCooldownTicks = 8;
   static int spearSwapBackDelayTicks = 2;
   static int pearlCatchAimTicks = 2;
   static int supportPotionReuseTicks = 1;
   static int diveTargetRefreshTicks = 10;
   static int diveVectorRefreshTicks = 3;
   static int diveFireworkReuseTicks = 4;
   static int diveLaunchRecoveryTicks = 6;
   static int divePostStrikeRecoveryTicks = 8;
   static int diveGroundRelaunchDelayTicks = 2;

   static String spearDashKey = "key.keyboard.c";
   static String pearlCatchKey = "key.keyboard.v";
   static String waterUtilityKey = "key.keyboard.x";
   static String webTrapKey = "key.keyboard.z";
   static String smartAttackKey = "key.mouse.5";
   static String diveComboKey = "key.mouse.4";
   static String fireworkBoostKey = "key.keyboard.left.shift";
   static String toggleElytraSwapKey = "key.keyboard.grave.accent";
   static String autoTotemKey = "key.keyboard.r";
   static String loadMainKey = "key.keyboard.l";
   static String railgunKey = "key.keyboard.f6";
   static String nukeKey = "key.keyboard.f7";
   static String stabKey = "key.keyboard.f8";
   static String selfBuffsKey = "key.keyboard.f9";
   static String saturationKey = "key.keyboard.f10";
   static String killNonPlayersKey = "key.keyboard.f12";

   private MultitoolsSettings() {
   }

   static void load() {
      if (!Files.exists(CONFIG_PATH)) {
         return;
      }

      try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
         Data data = GSON.fromJson(reader, Data.class);
         if (data == null) {
            return;
         }

         spearDashEnabled = data.spearDashEnabled;
         pearlCatchEnabled = data.pearlCatchEnabled;
         smartAttackEnabled = data.smartAttackEnabled;
         diveComboEnabled = data.diveComboEnabled;
         fireworkBoostEnabled = data.fireworkBoostEnabled;
         elytraSwapFeatureEnabled = data.elytraSwapFeatureEnabled;
         elytraSwapEnabled = data.elytraSwapEnabled;
         autoTotemEnabled = data.autoTotemEnabled;
         loadMainEnabled = data.loadMainEnabled;
         railgunEnabled = data.railgunEnabled;
         nukeEnabled = data.nukeEnabled;
         stabEnabled = data.stabEnabled;
         selfBuffsEnabled = data.selfBuffsEnabled;
         saturationEnabled = data.saturationEnabled;
         killNonPlayersEnabled = data.killNonPlayersEnabled;
         diveComboAvoidObstacles = data.diveComboAvoidObstacles;
         dropHeldItemBeforeGiveCommand = data.dropHeldItemBeforeGiveCommand;

         elytraLandingSwapDelayTicks = data.elytraLandingSwapDelayTicks;
         spearKeyCooldownTicks = data.spearKeyCooldownTicks;
         spearSwapBackDelayTicks = data.spearSwapBackDelayTicks;
         pearlCatchAimTicks = data.pearlCatchAimTicks;
         supportPotionReuseTicks = data.supportPotionReuseTicks;
         diveTargetRefreshTicks = data.diveTargetRefreshTicks;
         diveVectorRefreshTicks = data.diveVectorRefreshTicks;
         diveFireworkReuseTicks = data.diveFireworkReuseTicks;
         diveLaunchRecoveryTicks = data.diveLaunchRecoveryTicks;
         divePostStrikeRecoveryTicks = data.divePostStrikeRecoveryTicks;
         diveGroundRelaunchDelayTicks = data.diveGroundRelaunchDelayTicks;

         spearDashKey = valueOrDefault(data.spearDashKey, spearDashKey);
         pearlCatchKey = valueOrDefault(data.pearlCatchKey, pearlCatchKey);
         waterUtilityKey = valueOrDefault(data.waterUtilityKey, waterUtilityKey);
         webTrapKey = valueOrDefault(data.webTrapKey, webTrapKey);
         smartAttackKey = valueOrDefault(data.smartAttackKey, smartAttackKey);
         diveComboKey = valueOrDefault(data.diveComboKey, diveComboKey);
         fireworkBoostKey = valueOrDefault(data.fireworkBoostKey, fireworkBoostKey);
         toggleElytraSwapKey = valueOrDefault(data.toggleElytraSwapKey, toggleElytraSwapKey);
         autoTotemKey = valueOrDefault(data.autoTotemKey, autoTotemKey);
         loadMainKey = valueOrDefault(data.loadMainKey, loadMainKey);
         railgunKey = valueOrDefault(data.railgunKey, railgunKey);
         nukeKey = valueOrDefault(data.nukeKey, nukeKey);
         stabKey = valueOrDefault(data.stabKey, stabKey);
         selfBuffsKey = valueOrDefault(data.selfBuffsKey, selfBuffsKey);
         saturationKey = valueOrDefault(data.saturationKey, saturationKey);
         killNonPlayersKey = valueOrDefault(data.killNonPlayersKey, killNonPlayersKey);
      } catch (Exception ignored) {
      }
   }

   static void save() {
      Data data = new Data();
      data.spearDashEnabled = spearDashEnabled;
      data.pearlCatchEnabled = pearlCatchEnabled;
      data.smartAttackEnabled = smartAttackEnabled;
      data.diveComboEnabled = diveComboEnabled;
      data.fireworkBoostEnabled = fireworkBoostEnabled;
      data.elytraSwapFeatureEnabled = elytraSwapFeatureEnabled;
      data.elytraSwapEnabled = elytraSwapEnabled;
      data.autoTotemEnabled = autoTotemEnabled;
      data.loadMainEnabled = loadMainEnabled;
      data.railgunEnabled = railgunEnabled;
      data.nukeEnabled = nukeEnabled;
      data.stabEnabled = stabEnabled;
      data.selfBuffsEnabled = selfBuffsEnabled;
      data.saturationEnabled = saturationEnabled;
      data.killNonPlayersEnabled = killNonPlayersEnabled;
      data.diveComboAvoidObstacles = diveComboAvoidObstacles;
      data.dropHeldItemBeforeGiveCommand = dropHeldItemBeforeGiveCommand;

      data.elytraLandingSwapDelayTicks = elytraLandingSwapDelayTicks;
      data.spearKeyCooldownTicks = spearKeyCooldownTicks;
      data.spearSwapBackDelayTicks = spearSwapBackDelayTicks;
      data.pearlCatchAimTicks = pearlCatchAimTicks;
      data.supportPotionReuseTicks = supportPotionReuseTicks;
      data.diveTargetRefreshTicks = diveTargetRefreshTicks;
      data.diveVectorRefreshTicks = diveVectorRefreshTicks;
      data.diveFireworkReuseTicks = diveFireworkReuseTicks;
      data.diveLaunchRecoveryTicks = diveLaunchRecoveryTicks;
      data.divePostStrikeRecoveryTicks = divePostStrikeRecoveryTicks;
      data.diveGroundRelaunchDelayTicks = diveGroundRelaunchDelayTicks;

      data.spearDashKey = spearDashKey;
      data.pearlCatchKey = pearlCatchKey;
      data.waterUtilityKey = waterUtilityKey;
      data.webTrapKey = webTrapKey;
      data.smartAttackKey = smartAttackKey;
      data.diveComboKey = diveComboKey;
      data.fireworkBoostKey = fireworkBoostKey;
      data.toggleElytraSwapKey = toggleElytraSwapKey;
      data.autoTotemKey = autoTotemKey;
      data.loadMainKey = loadMainKey;
      data.railgunKey = railgunKey;
      data.nukeKey = nukeKey;
      data.stabKey = stabKey;
      data.selfBuffsKey = selfBuffsKey;
      data.saturationKey = saturationKey;
      data.killNonPlayersKey = killNonPlayersKey;

      try {
         Files.createDirectories(CONFIG_PATH.getParent());
         try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(data, writer);
         }
      } catch (Exception ignored) {
      }
   }

   static void applyKeybindings() {
      apply(ModKeyBindings.SPEAR_DASH, spearDashKey);
      apply(ModKeyBindings.PEARL_CATCH, pearlCatchKey);
      apply(ModKeyBindings.WATER_UTILITY, waterUtilityKey);
      apply(ModKeyBindings.WEB_TRAP, webTrapKey);
      apply(ModKeyBindings.SMART_ATTACK, smartAttackKey);
      apply(ModKeyBindings.DIVE_COMBO, diveComboKey);
      apply(ModKeyBindings.FIREWORK_BOOST, fireworkBoostKey);
      apply(ModKeyBindings.TOGGLE_ELYTRA_SWAP, toggleElytraSwapKey);
      apply(ModKeyBindings.AUTO_TOTEM, autoTotemKey);
      apply(ModKeyBindings.LOAD_MAIN, loadMainKey);
      apply(ModKeyBindings.SETTINGS, railgunKey);
      apply(ModKeyBindings.GIVE_NUKE, nukeKey);
      apply(ModKeyBindings.GIVE_STAB, stabKey);
      apply(ModKeyBindings.SELF_BUFFS, selfBuffsKey);
      apply(ModKeyBindings.SATURATION, saturationKey);
      apply(ModKeyBindings.KILL_NON_PLAYERS, killNonPlayersKey);
      KeyBinding.updateKeysByCode();
   }

   static void captureKeybindings() {
      spearDashKey = ModKeyBindings.SPEAR_DASH.getBoundKeyTranslationKey();
      pearlCatchKey = ModKeyBindings.PEARL_CATCH.getBoundKeyTranslationKey();
      waterUtilityKey = ModKeyBindings.WATER_UTILITY.getBoundKeyTranslationKey();
      webTrapKey = ModKeyBindings.WEB_TRAP.getBoundKeyTranslationKey();
      smartAttackKey = ModKeyBindings.SMART_ATTACK.getBoundKeyTranslationKey();
      diveComboKey = ModKeyBindings.DIVE_COMBO.getBoundKeyTranslationKey();
      fireworkBoostKey = ModKeyBindings.FIREWORK_BOOST.getBoundKeyTranslationKey();
      toggleElytraSwapKey = ModKeyBindings.TOGGLE_ELYTRA_SWAP.getBoundKeyTranslationKey();
      autoTotemKey = ModKeyBindings.AUTO_TOTEM.getBoundKeyTranslationKey();
      loadMainKey = ModKeyBindings.LOAD_MAIN.getBoundKeyTranslationKey();
      railgunKey = ModKeyBindings.SETTINGS.getBoundKeyTranslationKey();
      nukeKey = ModKeyBindings.GIVE_NUKE.getBoundKeyTranslationKey();
      stabKey = ModKeyBindings.GIVE_STAB.getBoundKeyTranslationKey();
      selfBuffsKey = ModKeyBindings.SELF_BUFFS.getBoundKeyTranslationKey();
      saturationKey = ModKeyBindings.SATURATION.getBoundKeyTranslationKey();
      killNonPlayersKey = ModKeyBindings.KILL_NON_PLAYERS.getBoundKeyTranslationKey();
   }

   static void restoreDefaults() {
      spearDashEnabled = true;
      pearlCatchEnabled = true;
      smartAttackEnabled = true;
      diveComboEnabled = true;
      fireworkBoostEnabled = true;
      elytraSwapFeatureEnabled = true;
      elytraSwapEnabled = true;
      autoTotemEnabled = true;
      loadMainEnabled = true;
      railgunEnabled = true;
      nukeEnabled = true;
      stabEnabled = true;
      selfBuffsEnabled = true;
      saturationEnabled = true;
      killNonPlayersEnabled = true;
      diveComboAvoidObstacles = true;
      dropHeldItemBeforeGiveCommand = true;

      elytraLandingSwapDelayTicks = 5;
      spearKeyCooldownTicks = 8;
      spearSwapBackDelayTicks = 2;
      pearlCatchAimTicks = 2;
      supportPotionReuseTicks = 1;
      diveTargetRefreshTicks = 10;
      diveVectorRefreshTicks = 3;
      diveFireworkReuseTicks = 4;
      diveLaunchRecoveryTicks = 6;
      divePostStrikeRecoveryTicks = 8;
      diveGroundRelaunchDelayTicks = 2;

      spearDashKey = "key.keyboard.c";
      pearlCatchKey = "key.keyboard.v";
      waterUtilityKey = "key.keyboard.x";
      webTrapKey = "key.keyboard.z";
      smartAttackKey = "key.mouse.5";
      diveComboKey = "key.mouse.4";
      fireworkBoostKey = "key.keyboard.left.shift";
      toggleElytraSwapKey = "key.keyboard.grave.accent";
      autoTotemKey = "key.keyboard.r";
      loadMainKey = "key.keyboard.l";
      railgunKey = "key.keyboard.f6";
      nukeKey = "key.keyboard.f7";
      stabKey = "key.keyboard.f8";
      selfBuffsKey = "key.keyboard.f9";
      saturationKey = "key.keyboard.f10";
      killNonPlayersKey = "key.keyboard.f12";
   }

   private static void apply(KeyBinding keyBinding, String translationKey) {
      keyBinding.setBoundKey(InputUtil.fromTranslationKey(translationKey));
   }

   private static String valueOrDefault(String value, String fallback) {
      return value == null || value.isBlank() ? fallback : value;
   }

   private static final class Data {
      boolean spearDashEnabled = true;
      boolean pearlCatchEnabled = true;
      boolean smartAttackEnabled = true;
      boolean diveComboEnabled = true;
      boolean fireworkBoostEnabled = true;
      boolean elytraSwapFeatureEnabled = true;
      boolean elytraSwapEnabled = true;
      boolean autoTotemEnabled = true;
      boolean loadMainEnabled = true;
      boolean railgunEnabled = true;
      boolean nukeEnabled = true;
      boolean stabEnabled = true;
      boolean selfBuffsEnabled = true;
      boolean saturationEnabled = true;
      boolean killNonPlayersEnabled = true;
      boolean diveComboAvoidObstacles = true;
      boolean dropHeldItemBeforeGiveCommand = true;

      int elytraLandingSwapDelayTicks = 5;
      int spearKeyCooldownTicks = 8;
      int spearSwapBackDelayTicks = 2;
      int pearlCatchAimTicks = 2;
      int supportPotionReuseTicks = 1;
      int diveTargetRefreshTicks = 10;
      int diveVectorRefreshTicks = 3;
      int diveFireworkReuseTicks = 4;
      int diveLaunchRecoveryTicks = 6;
      int divePostStrikeRecoveryTicks = 8;
      int diveGroundRelaunchDelayTicks = 2;

      String spearDashKey = "key.keyboard.c";
      String pearlCatchKey = "key.keyboard.v";
      String waterUtilityKey = "key.keyboard.x";
      String webTrapKey = "key.keyboard.z";
      String smartAttackKey = "key.mouse.5";
      String diveComboKey = "key.mouse.4";
      String fireworkBoostKey = "key.keyboard.left.shift";
      String toggleElytraSwapKey = "key.keyboard.grave.accent";
      String autoTotemKey = "key.keyboard.r";
      String loadMainKey = "key.keyboard.l";
      String railgunKey = "key.keyboard.f6";
      String nukeKey = "key.keyboard.f7";
      String stabKey = "key.keyboard.f8";
      String selfBuffsKey = "key.keyboard.f9";
      String saturationKey = "key.keyboard.f10";
      String killNonPlayersKey = "key.keyboard.f12";
   }
}
