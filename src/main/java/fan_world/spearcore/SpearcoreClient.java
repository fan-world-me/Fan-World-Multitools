package fan_world.spearcore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import fan_world.spearcore.mixin.client.forceAttack;

@Environment(EnvType.CLIENT)
public class SpearcoreClient implements ClientModInitializer {
   private static final int ELYTRA_DOUBLE_JUMP_WINDOW_TICKS = 7;
   private static final int ELYTRA_BOUNCE_PRESERVE_TICKS = 14;
   private static final int ELYTRA_LANDING_SWAP_DELAY_TICKS = 5;
   private static final int SPEAR_KEY_COOLDOWN_TICKS = 8;
   private static final int SPEAR_SWAP_BACK_DELAY_TICKS = 2;
   private static final int PEARL_CATCH_AIM_TICKS = 2;
   private static final int PEARL_CATCH_FIRE_TICK = 0;
   private static final int PEARL_CATCH_SWAP_BACK_TICK = -1;
   private static final int WIND_CHARGE_USE_DELAY_TICKS = 1;
   private static final int PLAYER_CHEST_SLOT = 6;
   private static final float WIND_CHARGE_SELF_BOOST_PITCH = 88.0F;
   private static final float WIND_CHARGE_AIR_BOOST_PITCH = 84.0F;
   private static final float WIND_CHARGE_ELYTRA_BOOST_PITCH = 72.0F;
   private static final float WIND_CHARGE_ELYTRA_DIVE_THRESHOLD = 28.0F;
   private static final float ELYTRA_DIVE_PITCH_THRESHOLD = 35.0F;
   private static final double ELYTRA_DIVE_SPEED_THRESHOLD = -0.20D;
   private static final double SMART_ATTACK_RANGE = 4.8D;
   private static final double SMART_ATTACK_VERTICAL_SPEED = -0.45D;
   private static final double SMART_ATTACK_VERTICAL_RANGE = 6.0D;
   private static final double SMART_ATTACK_TARGET_PADDING = 0.9D;
   private static final double SMART_ATTACK_FORWARD_RANGE = 1.5D;

   private int spearBindDelay = 0;
   private int spearSwapBackDelay = -1;
   private int previousSpearSlot = 0;

   private int pearlCatchDelay = -1;
   private int previousPearlSlot = 0;
   private float pearlPitchStep = 0.0F;
   private int windChargeUseDelay = -1;
   private int previousWindChargeSlot = -1;
   private float previousWindChargePitch = 0.0F;
   private int elytraJumpWindow = 0;
   private int elytraBouncePreserveTicks = 0;
   private int elytraLandingSwapDelay = -1;
   private boolean lastJumpPressed = false;
   private boolean lastOnGround = true;
   private boolean elytraSwapEnabled = true;

   @Override
   public void onInitializeClient() {
      keyBinding.getSpearDash();
      keyBinding.getPearlCatch();
      keyBinding.getSmartAttack();
      keyBinding.getWindChargeJump();
      keyBinding.getFireworkBoost();
      keyBinding.getToggleElytraSwap();
      keyBinding.getAutoTotem();
      keyBinding.getLoadMain();

      UseItemCallback.EVENT.register((player, world, hand) -> {
         if (world.isClient() && player instanceof ClientPlayerEntity clientPlayer) {
            this.handlePearlUse(clientPlayer, hand);
         }

         return ActionResult.PASS;
      });

      ClientTickEvents.START_CLIENT_TICK.register((client) -> {
         if (client.player == null || client.world == null) {
            return;
         }

         while (keyBinding.getAutoTotem().wasPressed()) {
            this.swapTotemToOffhand(client);
         }

         while (keyBinding.getFireworkBoost().wasPressed()) {
            if (client.player.isGliding()) {
               this.useFirework(client);
            }
         }

         while (keyBinding.getWindChargeJump().wasPressed()) {
            this.startWindChargeJump(client);
         }

         while (keyBinding.getLoadMain().wasPressed()) {
            this.runLoadMain(client);
         }

         while (keyBinding.getToggleElytraSwap().wasPressed()) {
            this.elytraSwapEnabled = !this.elytraSwapEnabled;
            client.player.sendMessage(Text.literal("Elytra Swap: " + (this.elytraSwapEnabled ? "ON" : "OFF")), true);
         }

         this.tickElytraSwap(client);
         this.tickSpearDash(client);
         this.tickPearlCatch(client);
         this.tickWindChargeJump(client);
         this.tickSmartAttack(client);
      });
   }

   private void tickElytraSwap(MinecraftClient client) {
      if (!this.elytraSwapEnabled) {
         this.elytraJumpWindow = 0;
         this.elytraBouncePreserveTicks = 0;
         this.elytraLandingSwapDelay = -1;
         this.lastJumpPressed = client.options.jumpKey.isPressed();
         this.lastOnGround = client.player.isOnGround();
         return;
      }

      ClientPlayerEntity player = client.player;
      boolean onGround = player.isOnGround();
      boolean jumpPressed = client.options.jumpKey.isPressed();
      boolean gliding = player.isGliding();

      if (this.elytraJumpWindow > 0) {
         --this.elytraJumpWindow;
      }

      if (this.elytraBouncePreserveTicks > 0) {
         --this.elytraBouncePreserveTicks;
      }

      if (this.elytraLandingSwapDelay >= 0) {
         --this.elytraLandingSwapDelay;
         if (this.elytraLandingSwapDelay <= 0 && onGround && !gliding && this.elytraBouncePreserveTicks <= 0) {
            this.equipChestplateOnLanding(client);
            this.elytraLandingSwapDelay = -1;
         } else if (this.elytraLandingSwapDelay < 0 && onGround && !gliding) {
            this.elytraLandingSwapDelay = 0;
         }
      }

      if (gliding && this.shouldPreserveElytraForBounce(player)) {
         this.elytraBouncePreserveTicks = ELYTRA_BOUNCE_PRESERVE_TICKS;
      }

      if (jumpPressed && !this.lastJumpPressed) {
         if (gliding) {
            this.equipChestplateFromInventory(client);
         } else if (!onGround && this.canAutoEquipElytra(player)) {
            this.equipElytraFromHotbar(client);
         } else if (this.elytraJumpWindow > 0 && this.canAutoEquipElytra(player)) {
            this.equipElytraFromHotbar(client);
            this.elytraJumpWindow = 0;
         } else {
            this.elytraJumpWindow = ELYTRA_DOUBLE_JUMP_WINDOW_TICKS;
         }
      }

      if (onGround && !this.lastOnGround) {
         this.elytraLandingSwapDelay = ELYTRA_LANDING_SWAP_DELAY_TICKS;
         this.elytraJumpWindow = 0;
      } else if (!onGround && this.lastOnGround) {
         this.elytraLandingSwapDelay = -1;
         this.tryResumeBounceGlide(client);
      }

      this.lastJumpPressed = jumpPressed;
      this.lastOnGround = onGround;
   }

   private boolean shouldPreserveElytraForBounce(ClientPlayerEntity player) {
      return player.getPitch() >= ELYTRA_DIVE_PITCH_THRESHOLD || player.getVelocity().y <= ELYTRA_DIVE_SPEED_THRESHOLD;
   }

   private boolean canAutoEquipElytra(ClientPlayerEntity player) {
      if (player.isTouchingWater() || player.isSubmergedInWater() || player.isClimbing()) {
         return false;
      }

      if (player.isHoldingOntoLadder()) {
         return false;
      }

      return !player.isSneaking();
   }

   private void tryResumeBounceGlide(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || player.networkHandler == null || this.elytraBouncePreserveTicks <= 0) {
         return;
      }

      if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) || player.isGliding()) {
         return;
      }

      if (player.isOnGround() || player.isTouchingWater() || player.hasVehicle() || player.isClimbing()) {
         return;
      }

      player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
   }

   private void tickSpearDash(MinecraftClient client) {
      if (this.spearBindDelay > 0) {
         --this.spearBindDelay;
      }

      if (this.spearSwapBackDelay >= 0) {
         --this.spearSwapBackDelay;
         if (this.spearSwapBackDelay == 0) {
            client.player.getInventory().setSelectedSlot(this.previousSpearSlot);
         }
      }

      while (keyBinding.getSpearDash().wasPressed()) {
         if (this.spearBindDelay > 0) {
            continue;
         }

         this.previousSpearSlot = client.player.getInventory().getSelectedSlot();
         if (!this.swapToLungeSpear(client.player)) {
            continue;
         }

         this.simulateHit(client);
         this.spearSwapBackDelay = SPEAR_SWAP_BACK_DELAY_TICKS;
         this.spearBindDelay = SPEAR_KEY_COOLDOWN_TICKS;
      }
   }

   private void tickPearlCatch(MinecraftClient client) {
      while (keyBinding.getPearlCatch().wasPressed()) {
         this.throwPearl(client);
      }

      if (this.pearlCatchDelay > 0) {
         client.player.setPitch(client.player.getPitch() + this.pearlPitchStep);
         --this.pearlCatchDelay;
         return;
      }

      if (this.pearlCatchDelay == PEARL_CATCH_FIRE_TICK) {
         this.swapToWindCharge(client.player);
         client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
         --this.pearlCatchDelay;
         return;
      }

      if (this.pearlCatchDelay == PEARL_CATCH_SWAP_BACK_TICK) {
         this.pearlCatchDelay = -2;
      }
   }

   private void tickSmartAttack(MinecraftClient client) {
      if (!keyBinding.getSmartAttack().isPressed()) {
         return;
      }

      ClientPlayerEntity player = client.player;
      if (player.getVelocity().y > SMART_ATTACK_VERTICAL_SPEED) {
         return;
      }

      Entity target = this.findSmartAttackTarget(client);
      if (!(target instanceof LivingEntity)) {
         return;
      }

      client.interactionManager.attackEntity(player, target);
      player.swingHand(Hand.MAIN_HAND);
   }

   private void startWindChargeJump(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player == null) {
         return;
      }

      if (this.windChargeUseDelay >= 0) {
         return;
      }

      int previousSlot = player.getInventory().getSelectedSlot();
      if (!this.swapToWindCharge(player)) {
         return;
      }

      this.previousWindChargeSlot = previousSlot;
      this.previousWindChargePitch = player.getPitch();
      this.windChargeUseDelay = WIND_CHARGE_USE_DELAY_TICKS;

      if (!player.isGliding() && player.isOnGround()) {
         player.jump();
      }
   }

   private void tickWindChargeJump(MinecraftClient client) {
      if (this.windChargeUseDelay < 0) {
         return;
      }

      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player == null) {
         this.clearWindChargeState();
         return;
      }

      --this.windChargeUseDelay;
      if (this.windChargeUseDelay > 0) {
         return;
      }

      player.setPitch(this.getWindChargePitch(player));
      client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      player.setPitch(this.previousWindChargePitch);

      if (this.previousWindChargeSlot >= 0) {
         player.getInventory().setSelectedSlot(this.previousWindChargeSlot);
      }

      this.clearWindChargeState();
   }

   private void throwPearl(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      this.previousPearlSlot = player.getInventory().getSelectedSlot();
      if (!this.swapToPearl(player)) {
         return;
      }

      client.interactionManager.interactItem(player, Hand.MAIN_HAND);
   }

   private void handlePearlUse(ClientPlayerEntity player, Hand hand) {
      ItemStack usedStack = player.getStackInHand(hand);
      if (!usedStack.isOf(Items.ENDER_PEARL)) {
         return;
      }

      float pitch = player.getPitch();
      if (pitch >= -25.0F) {
         return;
      }

      this.pearlPitchStep = this.computePearlPitchStep(pitch) / PEARL_CATCH_AIM_TICKS;
      this.pearlCatchDelay = PEARL_CATCH_AIM_TICKS;
   }

   private float computePearlPitchStep(float pitch) {
      if (pitch <= -89.0F) {
         return 0.0F;
      }

      if (pitch >= -89.0F && pitch <= -66.0F) {
         return 2.0F;
      }

      if (pitch > -66.0F && pitch <= -56.0F) {
         return 4.0F;
      }

      if (pitch > -56.0F && pitch <= -50.0F) {
         return 5.0F;
      }

      if (pitch > -50.0F && pitch <= -30.0F) {
         return 8.0F;
      }

      if (pitch > -30.0F && pitch <= -20.0F) {
         return 11.0F;
      }

      return 2.0F;
   }

   private float getWindChargePitch(ClientPlayerEntity player) {
      if (player.isGliding() && (player.getPitch() >= WIND_CHARGE_ELYTRA_DIVE_THRESHOLD || player.getVelocity().y < 0.0D)) {
         return WIND_CHARGE_ELYTRA_BOOST_PITCH;
      }

      if (!player.isOnGround()) {
         return WIND_CHARGE_AIR_BOOST_PITCH;
      }

      return WIND_CHARGE_SELF_BOOST_PITCH;
   }

   private void clearWindChargeState() {
      this.windChargeUseDelay = -1;
      this.previousWindChargeSlot = -1;
   }

   private boolean swapToLungeSpear(ClientPlayerEntity player) {
      RegistryEntry<Enchantment> lunge;
      try {
         lunge = player.getRegistryManager()
            .getOrThrow(RegistryKeys.ENCHANTMENT)
            .getOrThrow(Enchantments.LUNGE);
      } catch (IllegalStateException exception) {
         return false;
      }

      for (int slot = 0; slot < 9; ++slot) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (!stack.isEmpty() && stack.getItem().toString().contains("spear")) {
            ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
            if (enchantments.getLevel(lunge) > 0) {
               player.getInventory().setSelectedSlot(slot);
               return true;
            }
         }
      }

      return false;
   }

   private boolean swapToWindCharge(ClientPlayerEntity player) {
      for (int slot = 0; slot < 9; ++slot) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (stack.isOf(Items.WIND_CHARGE)) {
            player.getInventory().setSelectedSlot(slot);
            return true;
         }
      }

      return false;
   }

   private boolean swapToPearl(ClientPlayerEntity player) {
      for (int slot = 0; slot < 9; ++slot) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (stack.isOf(Items.ENDER_PEARL)) {
            player.getInventory().setSelectedSlot(slot);
            return true;
         }
      }

      return false;
   }

   private void swapTotemToOffhand(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player == null) {
         return;
      }

      if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         return;
      }

      if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
         return;
      }

      int inventorySlot = this.findTotemInventorySlot(player);
      if (inventorySlot < 0) {
         return;
      }

      int sourceSlot = this.toScreenHandlerSlot(inventorySlot);
      int offhandSlot = 45;
      int syncId = player.playerScreenHandler.syncId;

      client.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
      client.interactionManager.clickSlot(syncId, offhandSlot, 0, SlotActionType.PICKUP, player);

      if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
         client.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
      }
   }

   private int findTotemInventorySlot(ClientPlayerEntity player) {
      for (int slot = 0; slot < 36; ++slot) {
         if (player.getInventory().getStack(slot).isOf(Items.TOTEM_OF_UNDYING)) {
            return slot;
         }
      }

      return -1;
   }

   private int toScreenHandlerSlot(int inventorySlot) {
      return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
   }

   private void equipElytraFromHotbar(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player == null) {
         return;
      }

      if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
         return;
      }

      int elytraSlot = this.findHotbarSlot(player, Items.ELYTRA);
      if (elytraSlot < 0) {
         return;
      }

      this.swapInventoryItemIntoArmorSlot(client, player, elytraSlot, PLAYER_CHEST_SLOT);
   }

   private void equipChestplateOnLanding(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player == null) {
         return;
      }

      if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         return;
      }

      if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
         return;
      }

      int chestplateSlot = this.findChestplateInventorySlot(player);
      if (chestplateSlot < 0) {
         return;
      }

      this.swapInventoryItemIntoArmorSlot(client, player, chestplateSlot, PLAYER_CHEST_SLOT);
   }

   private void equipChestplateFromInventory(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player == null) {
         return;
      }

      if (player.playerScreenHandler.getCursorStack().isEmpty() && player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         int chestplateSlot = this.findChestplateInventorySlot(player);
         if (chestplateSlot >= 0) {
            this.swapInventoryItemIntoArmorSlot(client, player, chestplateSlot, PLAYER_CHEST_SLOT);
         }
      }
   }

   private void runLoadMain(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || player.networkHandler == null) {
         return;
      }

      player.networkHandler.sendChatCommand("ck load main");
   }

   private void useFirework(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player == null) {
         return;
      }

      if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
         client.interactionManager.interactItem(player, Hand.OFF_HAND);
         return;
      }

      int selectedSlot = player.getInventory().getSelectedSlot();
      int hotbarSlot = this.findHotbarSlot(player, Items.FIREWORK_ROCKET);
      if (hotbarSlot >= 0) {
         player.getInventory().setSelectedSlot(hotbarSlot);
         client.interactionManager.interactItem(player, Hand.MAIN_HAND);
         player.getInventory().setSelectedSlot(selectedSlot);
         return;
      }

      int inventorySlot = this.findInventorySlot(player, Items.FIREWORK_ROCKET);
      if (inventorySlot < 9) {
         return;
      }

      int syncId = player.playerScreenHandler.syncId;
      int sourceSlot = this.toScreenHandlerSlot(inventorySlot);
      int targetSlot = this.toScreenHandlerSlot(selectedSlot);
      client.interactionManager.clickSlot(syncId, sourceSlot, selectedSlot, SlotActionType.SWAP, player);
      client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      client.interactionManager.clickSlot(syncId, sourceSlot, selectedSlot, SlotActionType.SWAP, player);
   }

   private void swapInventoryItemIntoArmorSlot(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, int armorSlot) {
      int sourceSlot = this.toScreenHandlerSlot(inventorySlot);
      int syncId = player.playerScreenHandler.syncId;

      client.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
      client.interactionManager.clickSlot(syncId, armorSlot, 0, SlotActionType.PICKUP, player);

      if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
         client.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
      }
   }

   private int findHotbarSlot(ClientPlayerEntity player, net.minecraft.item.Item item) {
      for (int slot = 0; slot < 9; ++slot) {
         if (player.getInventory().getStack(slot).isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   private int findInventorySlot(ClientPlayerEntity player, net.minecraft.item.Item item) {
      for (int slot = 0; slot < 36; ++slot) {
         if (player.getInventory().getStack(slot).isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   private int findChestplateInventorySlot(ClientPlayerEntity player) {
      for (int slot = 0; slot < 36; ++slot) {
         ItemStack stack = player.getInventory().getStack(slot);
         EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
         if (equippable != null && equippable.slot() == EquipmentSlot.CHEST && !stack.isOf(Items.ELYTRA)) {
            return slot;
         }
      }

      return -1;
   }

   private Entity findSmartAttackTarget(MinecraftClient client) {
      return this.findBestAttackTarget(client, SMART_ATTACK_RANGE, SMART_ATTACK_VERTICAL_RANGE);
   }

   private Entity findBestAttackTarget(MinecraftClient client, double range, double verticalRange) {
      ClientPlayerEntity player = client.player;
      Vec3d eyePos = player.getEyePos();
      Box searchBox = player.getBoundingBox().expand(range, verticalRange, range);

      Entity bestTarget = null;
      double bestScore = Double.MAX_VALUE;

      for (Entity entity : client.world.getOtherEntities(player, searchBox, this::isValidSmartAttackTarget)) {
         double score = this.computeTargetScore(player, eyePos, entity, range);
         if (score < bestScore) {
            bestScore = score;
            bestTarget = entity;
         }
      }

      return bestTarget;
   }

   private boolean isValidSmartAttackTarget(Entity entity) {
      if (!(entity instanceof LivingEntity)) {
         return false;
      }

      if (!entity.isAlive()) {
         return false;
      }

      return !(entity instanceof EndCrystalEntity) && !(entity instanceof ProjectileEntity);
   }

   private double squaredDistanceToTarget(Vec3d source, Entity entity) {
      Box hitBox = entity.getBoundingBox().expand(SMART_ATTACK_TARGET_PADDING);
      double closestX = Math.max(hitBox.minX, Math.min(source.x, hitBox.maxX));
      double closestY = Math.max(hitBox.minY, Math.min(source.y, hitBox.maxY));
      double closestZ = Math.max(hitBox.minZ, Math.min(source.z, hitBox.maxZ));
      double deltaX = source.x - closestX;
      double deltaY = source.y - closestY;
      double deltaZ = source.z - closestZ;
      return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
   }

   private double computeTargetScore(ClientPlayerEntity player, Vec3d eyePos, Entity entity, double range) {
      double distanceScore = this.squaredDistanceToTarget(eyePos, entity);
      if (distanceScore > range * range) {
         return Double.MAX_VALUE;
      }

      Vec3d look = player.getRotationVec(1.0F).normalize();
      Vec3d toTarget = entity.getBoundingBox().getCenter().subtract(eyePos);
      double toTargetLength = toTarget.length();
      if (toTargetLength > 1.0E-6D) {
         toTarget = toTarget.multiply(1.0D / toTargetLength);
      }

      double alignmentPenalty = 1.0D - Math.max(0.0D, look.dotProduct(toTarget));
      double score = distanceScore + alignmentPenalty * SMART_ATTACK_FORWARD_RANGE;

      if (entity instanceof PlayerEntity targetPlayer) {
         if (targetPlayer.isGliding()) {
            score -= 1.25D;
         }

         if (targetPlayer.isUsingItem() && targetPlayer.getActiveItem().isOf(Items.SHIELD)) {
            score -= 0.6D;
         }
      }

      return score;
   }

   private void simulateHit(MinecraftClient client) {
      if (client instanceof forceAttack accessor) {
         accessor.callDoAttack();
      }
   }
}
