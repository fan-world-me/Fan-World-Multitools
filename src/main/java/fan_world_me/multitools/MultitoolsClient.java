package fan_world_me.multitools;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import fan_world_me.multitools.mixin.client.ForceAttack;

@Environment(EnvType.CLIENT)
public class MultitoolsClient implements ClientModInitializer {
   private static final int ELYTRA_LANDING_SWAP_DELAY_TICKS = 5;
   private static final int SPEAR_KEY_COOLDOWN_TICKS = 8;
   private static final int SPEAR_SWAP_BACK_DELAY_TICKS = 2;
   private static final int PEARL_CATCH_AIM_TICKS = 2;
   private static final int PEARL_CATCH_FIRE_TICK = 0;
   private static final int PEARL_CATCH_SWAP_BACK_TICK = -1;
   private static final int PLAYER_CHEST_SLOT = 6;
   private static final double SMART_ATTACK_RANGE = 4.8D;
   private static final double SMART_ATTACK_VERTICAL_SPEED = -0.45D;
   private static final double SMART_ATTACK_VERTICAL_RANGE = 6.0D;
   private static final double SMART_ATTACK_TARGET_PADDING = 0.9D;
   private static final double SMART_ATTACK_FORWARD_RANGE = 1.5D;
   private static final double SMART_ATTACK_LOW_HEALTH_BONUS = 2000.0D;
   private static final double SMART_ATTACK_PLAYER_BONUS = 1000.0D;

   private final DiveComboState diveComboState = new DiveComboState(this);

   private int spearBindDelay = 0;
   private int spearSwapBackDelay = -1;
   private int previousSpearSlot = 0;

   private int pearlCatchDelay = -1;
   private int previousPearlSlot = 0;
   private float pearlPitchStep = 0.0F;

   private int elytraLandingSwapDelay = -1;
   private boolean lastJumpPressed = false;
   private boolean lastOnGround = true;
   private int pendingCommandDelay = -1;
   private String pendingCommand = null;

   @Override
   public void onInitializeClient() {
      ModKeyBindings.getSpearDash();
      ModKeyBindings.getPearlCatch();
      ModKeyBindings.getSmartAttack();
      ModKeyBindings.getDiveCombo();
      ModKeyBindings.getFireworkBoost();
      ModKeyBindings.getToggleElytraSwap();
      ModKeyBindings.getAutoTotem();
      ModKeyBindings.getLoadMain();
      ModKeyBindings.getSettings();
      ModKeyBindings.getGiveNuke();
      ModKeyBindings.getGiveStab();

      UseItemCallback.EVENT.register((player, world, hand) -> {
         if (world.isClient() && player instanceof ClientPlayerEntity clientPlayer) {
            this.handlePearlUse(clientPlayer, hand);
         }

         return ActionResult.PASS;
      });

      ClientTickEvents.START_CLIENT_TICK.register(client -> {
         if (client.player == null || client.world == null) {
            this.resetPearlCatchState();
            return;
         }

         if (client.currentScreen != null) {
            this.diveComboState.cancel(client);
         }

         while (ModKeyBindings.getAutoTotem().wasPressed()) {
            this.swapTotemToOffhand(client);
         }

         while (ModKeyBindings.getFireworkBoost().wasPressed()) {
            if (client.player.isGliding()) {
               this.useFirework(client);
            }
         }

         while (ModKeyBindings.getPearlCatch().wasPressed()) {
            this.throwPearl(client);
         }

         while (ModKeyBindings.getLoadMain().wasPressed()) {
            this.runLoadMain(client);
         }

         while (ModKeyBindings.getSettings().wasPressed()) {
            client.setScreen(new MultitoolsSettingsScreen(client.currentScreen));
         }

         while (ModKeyBindings.getGiveNuke().wasPressed()) {
            this.queueCommand(client, "givef nuke 1 16");
         }

         while (ModKeyBindings.getGiveStab().wasPressed()) {
            this.queueCommand(client, "givef stab 1");
         }

         while (ModKeyBindings.getToggleElytraSwap().wasPressed()) {
            MultitoolsSettings.elytraSwapEnabled = !MultitoolsSettings.elytraSwapEnabled;
            client.player.sendMessage(Text.literal("Elytra Swap: " + (MultitoolsSettings.elytraSwapEnabled ? "ON" : "OFF")), true);
         }

         this.tickQueuedCommand(client);
         this.tickElytraSwap(client);
         this.tickSpearDash(client);
         this.tickPearlCatch(client);
         while (ModKeyBindings.getDiveCombo().wasPressed()) {
            this.diveComboState.trigger(client);
         }
         this.diveComboState.tick(client);
         this.tickSmartAttack(client);
      });
   }

   private void tickElytraSwap(MinecraftClient client) {
      if (!MultitoolsSettings.elytraSwapEnabled) {
         this.elytraLandingSwapDelay = -1;
         this.lastJumpPressed = client.options.jumpKey.isPressed();
         this.lastOnGround = client.player.isOnGround();
         return;
      }

      ClientPlayerEntity player = client.player;
      boolean onGround = player.isOnGround();
      boolean jumpPressed = client.options.jumpKey.isPressed();
      boolean gliding = player.isGliding();

      if (this.elytraLandingSwapDelay >= 0) {
         --this.elytraLandingSwapDelay;
         if (this.elytraLandingSwapDelay <= 0 && onGround && !gliding) {
            this.equipChestplateOnLanding(client);
            this.elytraLandingSwapDelay = -1;
         }
      }

      if (jumpPressed && !this.lastJumpPressed) {
         if (gliding) {
            this.equipChestplateFromInventory(client);
         } else if (!onGround && this.canAutoEquipElytra(player)) {
            this.equipElytraFromHotbar(client);
         }
      }

      if (onGround && !this.lastOnGround) {
         this.elytraLandingSwapDelay = ELYTRA_LANDING_SWAP_DELAY_TICKS;
      } else if (!onGround && this.lastOnGround) {
         this.elytraLandingSwapDelay = -1;
      }

      this.lastJumpPressed = jumpPressed;
      this.lastOnGround = onGround;
   }

   boolean canAutoEquipElytra(ClientPlayerEntity player) {
      if (player.isTouchingWater() || player.isSubmergedInWater() || player.isClimbing()) {
         return false;
      }

      if (player.isHoldingOntoLadder()) {
         return false;
      }

      return !player.isSneaking();
   }

   void startFallFlyingIfPossible(ClientPlayerEntity player) {
      if (player.networkHandler == null) {
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

      while (ModKeyBindings.getSpearDash().wasPressed()) {
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
      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null || client.interactionManager == null) {
         this.resetPearlCatchState();
         return;
      }

      if (this.pearlCatchDelay > 0) {
         player.setPitch(player.getPitch() + this.pearlPitchStep);
         --this.pearlCatchDelay;
         return;
      }

      if (this.pearlCatchDelay == PEARL_CATCH_FIRE_TICK) {
         if (this.swapToWindCharge(player)) {
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
         }
         --this.pearlCatchDelay;
         return;
      }

      if (this.pearlCatchDelay == PEARL_CATCH_SWAP_BACK_TICK) {
         if (this.previousPearlSlot >= 0) {
            MaceSwapState.setSelectedHotbarSlot(client, player, this.previousPearlSlot);
         }
         this.pearlCatchDelay = -2;
      }
   }

   private void tickSmartAttack(MinecraftClient client) {
      if (!ModKeyBindings.getSmartAttack().isPressed()) {
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

   private void resetPearlCatchState() {
      this.pearlCatchDelay = -1;
      this.previousPearlSlot = 0;
      this.pearlPitchStep = 0.0F;
   }

   private void throwPearl(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player == null) {
         return;
      }

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

      if (pitch <= -66.0F) {
         return 2.0F;
      }

      if (pitch <= -56.0F) {
         return 4.0F;
      }

      if (pitch <= -50.0F) {
         return 5.0F;
      }

      if (pitch <= -30.0F) {
         return 8.0F;
      }

      if (pitch <= -20.0F) {
         return 11.0F;
      }

      return 2.0F;
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

   boolean swapToWindCharge(ClientPlayerEntity player) {
      for (int slot = 0; slot < 9; ++slot) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (stack.isOf(Items.WIND_CHARGE)) {
            player.getInventory().setSelectedSlot(slot);
            return true;
         }
      }

      return false;
   }

   boolean useWindChargeAtPitch(MinecraftClient client, ClientPlayerEntity player, float pitch) {
      if (client.interactionManager == null) {
         return false;
      }

      int previousSlot = player.getInventory().getSelectedSlot();
      float previousPitch = player.getPitch();
      if (!this.swapToWindCharge(player)) {
         return false;
      }

      player.setPitch(pitch);
      client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      player.setPitch(previousPitch);
      MaceSwapState.setSelectedHotbarSlot(client, player, previousSlot);
      return true;
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

   void equipElytraFromHotbar(MinecraftClient client) {
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

   void equipChestplateOnLanding(MinecraftClient client) {
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

   void useFirework(MinecraftClient client) {
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
      client.interactionManager.clickSlot(syncId, sourceSlot, selectedSlot, SlotActionType.SWAP, player);
      client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      client.interactionManager.clickSlot(syncId, sourceSlot, selectedSlot, SlotActionType.SWAP, player);
   }

   private void queueCommand(MinecraftClient client, String command) {
      ClientPlayerEntity player = client.player;
      if (player == null || player.networkHandler == null) {
         return;
      }

      if (MultitoolsSettings.dropHeldItemBeforeGiveCommand && !player.getMainHandStack().isEmpty()) {
         player.dropSelectedItem(true);
         this.pendingCommand = command;
         this.pendingCommandDelay = 1;
         return;
      }

      player.networkHandler.sendChatCommand(command);
   }

   private void tickQueuedCommand(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || player.networkHandler == null || this.pendingCommand == null) {
         return;
      }

      if (this.pendingCommandDelay > 0) {
         --this.pendingCommandDelay;
         return;
      }

      player.networkHandler.sendChatCommand(this.pendingCommand);
      this.pendingCommand = null;
      this.pendingCommandDelay = -1;
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

   private int findHotbarSlot(ClientPlayerEntity player, Item item) {
      for (int slot = 0; slot < 9; ++slot) {
         if (player.getInventory().getStack(slot).isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   private int findInventorySlot(ClientPlayerEntity player, Item item) {
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
         score -= SMART_ATTACK_PLAYER_BONUS;
         if (targetPlayer.getHealth() < 6.0F) {
            score -= SMART_ATTACK_LOW_HEALTH_BONUS;
         }

         if (targetPlayer.isGliding()) {
            score -= 1.25D;
         }

         if (targetPlayer.isUsingItem() && targetPlayer.getActiveItem().isOf(Items.SHIELD)) {
            score -= 0.6D;
         }
      }

      return score;
   }

   void simulateHit(MinecraftClient client) {
      if (client instanceof ForceAttack accessor) {
         accessor.callDoAttack();
      }
   }

}

