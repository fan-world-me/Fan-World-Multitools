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

   public static net.minecraft.client.option.KeyBinding getSpearDash() {
      return SPEAR_DASH;
   }

   public static net.minecraft.client.option.KeyBinding getPearlCatch() {
      return PEARL_CATCH;
   }

   public static net.minecraft.client.option.KeyBinding getSmartAttack() {
      return SMART_ATTACK;
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
}

