package fan_world_me.multitools;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public final class MaceSwapState {
   private static final float DENSITY_PRIORITY_FALL_DISTANCE = 1.0F;

   private static boolean performingFollowUp = false;
   private static boolean queuedSwordSwap = false;
   private static int queuedTargetId = -1;
   // Счётчик, а не boolean: DiveCombo, SpearDash и WebTrap могут подавлять авто-булаву одновременно,
   // и один из них не должен сбрасывать подавление за остальных.
   private static int suppressionDepth = 0;

   private MaceSwapState() {
   }

   public static void toggle(MinecraftClient client) {
      clearQueuedSwordSwap();
   }

   public static boolean onInterceptAttack(MinecraftClient client, ClientPlayerEntity attacker, Entity target) {
      if (suppressionDepth > 0 || performingFollowUp || client == null || attacker == null || target == null) {
         return false;
      }

      if (!(target instanceof LivingEntity livingTarget)) {
         return false;
      }

      ItemStack mainHand = attacker.getMainHandStack();
      int originalSlot = attacker.getInventory().getSelectedSlot();
      boolean preferDensity = hasPoweredMaceEffect(attacker);
      if (isBlockingWithShield(livingTarget)) {
         if (isFallingForStunSlam(attacker)) {
            int preferredMaceSlot = findPreferredMaceInHotbar(attacker, preferDensity);
            if (preferredMaceSlot < 0) {
               return false;
            }

            if (isShieldFacingAttacker(attacker, livingTarget)) {
               int axeSlot = findFirstHotbarSlot(attacker, ItemTags.AXES);
               if (axeSlot < 0) {
                  return false;
               }

               performShieldBreakMaceCombo(client, target.getId(), axeSlot, preferredMaceSlot, originalSlot);
            } else {
               performTemporaryAttack(client, target.getId(), preferredMaceSlot, -1);
            }

            return true;
         }

         if (mainHand.isIn(ItemTags.AXES)) {
            queuedSwordSwap = true;
            queuedTargetId = target.getId();
            return false;
         }

         int axeSlot = findFirstHotbarSlot(attacker, ItemTags.AXES);
         int maceSlot = findPreferredMaceInHotbar(attacker, preferDensity);
         if (axeSlot < 0 || maceSlot < 0) {
            return false;
         }

         performShieldBreakMaceCombo(client, target.getId(), axeSlot, maceSlot, originalSlot);
         return true;
      }

      if (mainHand.isOf(Items.MACE)) {
         return false;
      }

      int breachMaceSlot = findPreferredMaceInHotbar(attacker, preferDensity);
      if (breachMaceSlot < 0) {
         return false;
      }

      performTemporaryAttack(client, target.getId(), breachMaceSlot, -1);
      return true;
   }

   public static void onPostVanillaAttack(MinecraftClient client, ClientPlayerEntity attacker, Entity target) {
      if (performingFollowUp || client == null || attacker == null) {
         clearQueuedSwordSwap();
         return;
      }

      if (!queuedSwordSwap) {
         return;
      }

      if (target == null || target.getId() != queuedTargetId) {
         clearQueuedSwordSwap();
         return;
      }

      clearQueuedSwordSwap();
      int swordSlot = findFirstHotbarSlot(attacker, ItemTags.SWORDS);
      if (swordSlot >= 0) {
         setSelectedHotbarSlot(client, attacker, swordSlot);
      }
   }

   static boolean performDiveAttack(MinecraftClient client, ClientPlayerEntity attacker, LivingEntity target, int restoreSlot) {
      if (client == null || attacker == null || target == null || client.world == null || client.interactionManager == null) {
         return false;
      }

      boolean preferDensity = hasPoweredMaceEffect(attacker);
      if (isBlockingWithShield(target) && isShieldFacingAttacker(attacker, target)) {
         int axeSlot = findFirstHotbarSlot(attacker, ItemTags.AXES);
         int maceSlot = findPreferredMaceInHotbar(attacker, preferDensity);
         if (axeSlot < 0 || maceSlot < 0) {
            return false;
         }

         performShieldBreakMaceCombo(client, target.getId(), axeSlot, maceSlot, restoreSlot);
         return true;
      }

      int maceSlot = findPreferredMaceInHotbar(attacker, preferDensity);
      if (maceSlot < 0) {
         return false;
      }

      performTemporaryAttack(client, target.getId(), maceSlot, restoreSlot);
      return true;
   }

   private static void performTemporaryAttack(MinecraftClient client, int targetEntityId, int weaponSlot, int followUpSlot) {
      if (performingFollowUp || client.world == null || client.interactionManager == null || client.player == null) {
         return;
      }

      Entity target = client.world.getEntityById(targetEntityId);
      if (!(target instanceof LivingEntity)) {
         return;
      }

      int originalSlot = client.player.getInventory().getSelectedSlot();

      try {
         performingFollowUp = true;
         setSelectedHotbarSlot(client, client.player, weaponSlot);
         client.interactionManager.attackEntity(client.player, target);
         client.player.swingHand(Hand.MAIN_HAND);
      } finally {
         if (followUpSlot >= 0) {
            setSelectedHotbarSlot(client, client.player, followUpSlot);
         } else {
            setSelectedHotbarSlot(client, client.player, originalSlot);
         }

         performingFollowUp = false;
      }
   }

   private static void performShieldBreakMaceCombo(MinecraftClient client, int targetEntityId, int axeSlot, int maceSlot, int restoreSlot) {
      if (performingFollowUp || client.world == null || client.interactionManager == null || client.player == null) {
         return;
      }

      Entity target = client.world.getEntityById(targetEntityId);
      if (!(target instanceof LivingEntity)) {
         return;
      }

      try {
         performingFollowUp = true;
         setSelectedHotbarSlot(client, client.player, axeSlot);
         client.interactionManager.attackEntity(client.player, target);
         client.player.swingHand(Hand.MAIN_HAND);

         setSelectedHotbarSlot(client, client.player, maceSlot);
         client.interactionManager.attackEntity(client.player, target);
         client.player.swingHand(Hand.MAIN_HAND);
      } finally {
         setSelectedHotbarSlot(client, client.player, restoreSlot);
         performingFollowUp = false;
      }
   }

   /** Включает подавление авто-булавы. Каждый acquireSuppression() обязан быть закрыт ровно одним releaseSuppression(). */
   static void acquireSuppression() {
      ++suppressionDepth;
      clearQueuedSwordSwap();
   }

   static void releaseSuppression() {
      if (suppressionDepth > 0) {
         --suppressionDepth;
      }
   }

   static void setSelectedHotbarSlot(MinecraftClient client, ClientPlayerEntity player, int slot) {
      if (slot < 0 || slot > 8 || player.getInventory().getSelectedSlot() == slot) {
         return;
      }

      player.getInventory().setSelectedSlot(slot);
      if (client.getNetworkHandler() != null) {
         client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
      }
   }

   static int findPreferredMaceInHotbar(ClientPlayerEntity player, boolean preferDensity) {
      RegistryEntry<Enchantment> density;
      RegistryEntry<Enchantment> breach;
      try {
         density = player.getRegistryManager()
            .getOrThrow(RegistryKeys.ENCHANTMENT)
            .getOrThrow(Enchantments.DENSITY);
         breach = player.getRegistryManager()
            .getOrThrow(RegistryKeys.ENCHANTMENT)
            .getOrThrow(Enchantments.BREACH);
      } catch (RuntimeException exception) {
         // На серверах, где этих чар нет в реестре, берём первую булаву в хотбаре.
         return findFirstMaceInHotbar(player);
      }

      int fallbackSlot = -1;
      int bestPrimarySlot = -1;
      int bestPrimaryLevel = 0;
      int bestSecondarySlot = -1;
      int bestSecondaryLevel = 0;

      for (int slot = 0; slot < 9; ++slot) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (!stack.isOf(Items.MACE)) {
            continue;
         }

         if (fallbackSlot < 0) {
            fallbackSlot = slot;
         }

         ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
         int primaryLevel = preferDensity ? enchantments.getLevel(density) : enchantments.getLevel(breach);
         int secondaryLevel = preferDensity ? enchantments.getLevel(breach) : enchantments.getLevel(density);
         if (primaryLevel > bestPrimaryLevel) {
            bestPrimaryLevel = primaryLevel;
            bestPrimarySlot = slot;
         }

         if (secondaryLevel > bestSecondaryLevel) {
            bestSecondaryLevel = secondaryLevel;
            bestSecondarySlot = slot;
         }
      }

      if (bestPrimarySlot >= 0) {
         return bestPrimarySlot;
      }

      if (bestSecondarySlot >= 0) {
         return bestSecondarySlot;
      }

      return fallbackSlot;
   }

   private static int findFirstMaceInHotbar(ClientPlayerEntity player) {
      for (int slot = 0; slot < 9; ++slot) {
         if (player.getInventory().getStack(slot).isOf(Items.MACE)) {
            return slot;
         }
      }

      return -1;
   }

   static int findFirstHotbarSlot(ClientPlayerEntity player, net.minecraft.registry.tag.TagKey<net.minecraft.item.Item> tag) {
      for (int slot = 0; slot < 9; ++slot) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (!stack.isEmpty() && stack.isIn(tag)) {
            return slot;
         }
      }

      return -1;
   }

   static boolean isBlockingWithShield(LivingEntity target) {
      if (!target.isUsingItem()) {
         return false;
      }

      ItemStack active = target.getActiveItem();
      return !active.isEmpty() && active.isOf(Items.SHIELD);
   }

   static boolean isShieldFacingAttacker(ClientPlayerEntity attacker, LivingEntity target) {
      Vec3d toAttacker = new Vec3d(
         attacker.getX() - target.getX(),
         attacker.getY() - target.getY(),
         attacker.getZ() - target.getZ()
      );
      Vec3d horizontalToAttacker = new Vec3d(toAttacker.x, 0.0D, toAttacker.z);
      if (horizontalToAttacker.lengthSquared() < 1.0E-6D) {
         return true;
      }

      Vec3d facing = target.getRotationVec(1.0F);
      Vec3d horizontalFacing = new Vec3d(facing.x, 0.0D, facing.z);
      if (horizontalFacing.lengthSquared() < 1.0E-6D) {
         return true;
      }

      return horizontalFacing.normalize().dotProduct(horizontalToAttacker.normalize()) > 0.0D;
   }

   private static boolean isFallingForStunSlam(ClientPlayerEntity player) {
      return !player.isOnGround() && player.getVelocity().y < 0.0D && player.fallDistance > 0.0F;
   }

   private static boolean hasPoweredMaceEffect(ClientPlayerEntity player) {
      return !player.isOnGround() && player.getVelocity().y < 0.0D && player.fallDistance > DENSITY_PRIORITY_FALL_DISTANCE;
   }

   private static void clearQueuedSwordSwap() {
      queuedSwordSwap = false;
      queuedTargetId = -1;
   }
}
