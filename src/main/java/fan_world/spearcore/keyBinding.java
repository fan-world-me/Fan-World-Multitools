package fan_world.spearcore;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class keyBinding {
   private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("multitools", "keybinds"));

   public static final KeyBinding SPEAR_DASH = KeyBindingHelper.registerKeyBinding(
      new KeyBinding("key.multitools.spear_dash", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY)
   );

   public static final KeyBinding PEARL_CATCH = KeyBindingHelper.registerKeyBinding(
      new KeyBinding("key.multitools.pearl_catch", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY)
   );

   public static final KeyBinding SMART_ATTACK = KeyBindingHelper.registerKeyBinding(
      new KeyBinding("key.multitools.smart_attack", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, CATEGORY)
   );

   public static final KeyBinding WIND_CHARGE_JUMP = KeyBindingHelper.registerKeyBinding(
      new KeyBinding("key.multitools.wind_charge_jump", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, CATEGORY)
   );

   public static final KeyBinding FIREWORK_BOOST = KeyBindingHelper.registerKeyBinding(
      new KeyBinding("key.multitools.firework_boost", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY)
   );

   public static final KeyBinding TOGGLE_ELYTRA_SWAP = KeyBindingHelper.registerKeyBinding(
      new KeyBinding("key.multitools.toggle_elytra_swap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY)
   );

   public static final KeyBinding AUTO_TOTEM = KeyBindingHelper.registerKeyBinding(
      new KeyBinding("key.multitools.auto_totem", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY)
   );

   public static final KeyBinding LOAD_MAIN = KeyBindingHelper.registerKeyBinding(
      new KeyBinding("key.multitools.load_main", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY)
   );

   public static KeyBinding getSpearDash() {
      return SPEAR_DASH;
   }

   public static KeyBinding getPearlCatch() {
      return PEARL_CATCH;
   }

   public static KeyBinding getSmartAttack() {
      return SMART_ATTACK;
   }

   public static KeyBinding getWindChargeJump() {
      return WIND_CHARGE_JUMP;
   }

   public static KeyBinding getFireworkBoost() {
      return FIREWORK_BOOST;
   }

   public static KeyBinding getToggleElytraSwap() {
      return TOGGLE_ELYTRA_SWAP;
   }

   public static KeyBinding getAutoTotem() {
      return AUTO_TOTEM;
   }

   public static KeyBinding getLoadMain() {
      return LOAD_MAIN;
   }
}
