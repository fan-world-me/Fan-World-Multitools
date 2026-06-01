package fan_world_me.multitools;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ModKeyBindings {
   private static final net.minecraft.client.option.KeyBinding.Category CATEGORY = net.minecraft.client.option.KeyBinding.Category.create(Identifier.of("multitools", "keybinds"));

   public static final net.minecraft.client.option.KeyBinding SPEAR_DASH = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.spear_dash", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding PEARL_CATCH = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.pearl_catch", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding WATER_UTILITY = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.water_utility", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding WEB_TRAP = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.web_trap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding SMART_ATTACK = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.smart_attack", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding DIVE_COMBO = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.dive_combo", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding FIREWORK_BOOST = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.firework_boost", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding TOGGLE_ELYTRA_SWAP = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.toggle_elytra_swap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding AUTO_TOTEM = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.auto_totem", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding LOAD_MAIN = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.load_main", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding SETTINGS = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding GIVE_NUKE = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.give_nuke", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding GIVE_STAB = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.give_stab", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding SELF_BUFFS = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.self_buffs", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding SATURATION = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.saturation", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F10, CATEGORY)
   );

   public static final net.minecraft.client.option.KeyBinding KILL_NON_PLAYERS = KeyBindingHelper.registerKeyBinding(
      new net.minecraft.client.option.KeyBinding("key.multitools.kill_non_players", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F12, CATEGORY)
   );

   public static net.minecraft.client.option.KeyBinding getSpearDash() {
      return SPEAR_DASH;
   }

   public static net.minecraft.client.option.KeyBinding getPearlCatch() {
      return PEARL_CATCH;
   }

   public static net.minecraft.client.option.KeyBinding getSmartAttack() {
      return SMART_ATTACK;
   }

   public static net.minecraft.client.option.KeyBinding getWaterUtility() {
      return WATER_UTILITY;
   }

   public static net.minecraft.client.option.KeyBinding getWebTrap() {
      return WEB_TRAP;
   }

   public static net.minecraft.client.option.KeyBinding getDiveCombo() {
      return DIVE_COMBO;
   }

   public static net.minecraft.client.option.KeyBinding getFireworkBoost() {
      return FIREWORK_BOOST;
   }

   public static net.minecraft.client.option.KeyBinding getToggleElytraSwap() {
      return TOGGLE_ELYTRA_SWAP;
   }

   public static net.minecraft.client.option.KeyBinding getAutoTotem() {
      return AUTO_TOTEM;
   }

   public static net.minecraft.client.option.KeyBinding getLoadMain() {
      return LOAD_MAIN;
   }

   public static net.minecraft.client.option.KeyBinding getSettings() {
      return SETTINGS;
   }

   public static net.minecraft.client.option.KeyBinding getGiveNuke() {
      return GIVE_NUKE;
   }

   public static net.minecraft.client.option.KeyBinding getGiveStab() {
      return GIVE_STAB;
   }

   public static net.minecraft.client.option.KeyBinding getSelfBuffs() {
      return SELF_BUFFS;
   }

   public static net.minecraft.client.option.KeyBinding getSaturation() {
      return SATURATION;
   }

   public static net.minecraft.client.option.KeyBinding getKillNonPlayers() {
      return KILL_NON_PLAYERS;
   }
}

