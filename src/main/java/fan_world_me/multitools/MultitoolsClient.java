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
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.potion.Potions;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.component.type.PotionContentsComponent;

import java.util.ArrayDeque;
import java.util.Optional;
import fan_world_me.multitools.mixin.client.ForceAttack;

@Environment(EnvType.CLIENT)
public class MultitoolsClient implements ClientModInitializer {
   private static final int ELYTRA_BOUNCE_PRESERVE_TICKS = 14;
   private static final int PEARL_CATCH_FIRE_TICK = 0;
   private static final int PEARL_CATCH_SWAP_BACK_TICK = -1;
   private static final int PLAYER_CHEST_SLOT = 6;
   private static final float SUPPORT_POTION_PITCH = 88.0F;
   private static final float WIND_CHARGE_ELYTRA_DIVE_THRESHOLD = 28.0F;
   private static final float MIN_SELF_WIND_CHARGE_PITCH = 68.0F;
   private static final float MAX_SELF_WIND_CHARGE_PITCH = 88.0F;
   private static final double SMART_ATTACK_RANGE = 4.8D;
   private static final double SMART_ATTACK_VERTICAL_SPEED = -0.45D;
   private static final double SMART_ATTACK_VERTICAL_RANGE = 6.0D;
   private static final double SMART_ATTACK_TARGET_PADDING = 0.9D;
   private static final double SMART_ATTACK_FORWARD_RANGE = 1.5D;
   private static final double SMART_ATTACK_LOW_HEALTH_BONUS = 2000.0D;
   private static final double SMART_ATTACK_PLAYER_BONUS = 1000.0D;
   private static final int ELYTRA_THREAT_HOLD_TICKS = 6;
   private static final double CRYSTAL_GROUNDED_THREAT_RANGE = 3.25D;
   private static final double CRYSTAL_AIR_THREAT_RANGE = 5.0D;
   private static final double TNT_THREAT_RANGE = 7.0D;
   private static final int TNT_MAX_THREAT_FUSE = 16;
   private static final double MACE_THREAT_HORIZONTAL_RANGE = 3.4D;
   private static final double MACE_THREAT_VERTICAL_RANGE = 7.5D;
   private static final double MACE_THREAT_DESCENT_SPEED = -0.55D;

   private final DiveComboState diveComboState = new DiveComboState(this);
   private final ArrayDeque<Integer> pendingSupportPotionSlots = new ArrayDeque<>();

   private int spearBindDelay = 0;
   private int spearSwapBackDelay = -1;
   private int previousSpearSlot = 0;

   private int pearlCatchDelay = -1;
   private int previousPearlSlot = 0;
   private float pearlPitchStep = 0.0F;

   private int supportPotionDelay = 0;
   private int elytraBouncePreserveTicks = 0;
   private int elytraLandingSwapDelay = -1;
   private boolean lastJumpPressed = false;
   private boolean lastOnGround = true;
   private int pendingCommandDelay = -1;
   private String pendingCommand = null;
   private int elytraThreatHoldTicks = 0;
   private boolean restoreElytraAfterThreat = false;
   private boolean restoreGlideAfterThreat = false;
   private boolean suppressManualWindChargeIntercept = false;

   @Override
   public void onInitializeClient() {
      MultitoolsSettings.load();
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
      ModKeyBindings.getSelfBuffs();
      ModKeyBindings.getSaturation();
      ModKeyBindings.getKillNonPlayers();
      MultitoolsSettings.applyKeybindings();

      UseItemCallback.EVENT.register((player, world, hand) -> {
         if (world.isClient() && player instanceof ClientPlayerEntity clientPlayer) {
            ActionResult manualWindChargeResult = this.handleManualWindChargeUse(clientPlayer, hand);
            if (manualWindChargeResult != ActionResult.PASS) {
               return manualWindChargeResult;
            }

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
            if (MultitoolsSettings.autoTotemEnabled) {
               this.swapTotemToOffhand(client);
            }
         }

         while (ModKeyBindings.getFireworkBoost().wasPressed()) {
            if (MultitoolsSettings.fireworkBoostEnabled && client.player.isGliding()) {
               this.useFirework(client);
            }
         }

         while (ModKeyBindings.getPearlCatch().wasPressed()) {
            if (MultitoolsSettings.pearlCatchEnabled) {
               this.throwPearl(client);
            }
         }

         while (ModKeyBindings.getLoadMain().wasPressed()) {
            if (MultitoolsSettings.loadMainEnabled) {
               this.runLoadMain(client);
            }
         }

         while (ModKeyBindings.getSettings().wasPressed()) {
            if (MultitoolsSettings.railgunEnabled) {
               this.queueCommand(client, "givef railgun straight 1");
            }
         }

         while (ModKeyBindings.getGiveNuke().wasPressed()) {
            if (MultitoolsSettings.nukeEnabled) {
               this.queueCommand(client, "givef nuke 1 11");
            }
         }

         while (ModKeyBindings.getGiveStab().wasPressed()) {
            if (MultitoolsSettings.stabEnabled) {
               this.queueCommand(client, "givef stab 1");
            }
         }

         while (ModKeyBindings.getSelfBuffs().wasPressed()) {
            if (MultitoolsSettings.selfBuffsEnabled) {
               this.queueSelfBuffPotions(client);
            }
         }

         while (ModKeyBindings.getSaturation().wasPressed()) {
            if (MultitoolsSettings.saturationEnabled) {
               client.player.networkHandler.sendChatCommand("effect give @p minecraft:saturation infinite 255 false");
            }
         }

         while (ModKeyBindings.getKillNonPlayers().wasPressed()) {
            if (MultitoolsSettings.killNonPlayersEnabled) {
               client.player.networkHandler.sendChatCommand("kill @e[type=!minecraft:player]");
            }
         }

         while (ModKeyBindings.getToggleElytraSwap().wasPressed()) {
            if (MultitoolsSettings.elytraSwapFeatureEnabled) {
               MultitoolsSettings.elytraSwapEnabled = !MultitoolsSettings.elytraSwapEnabled;
               MultitoolsSettings.save();
               client.player.sendMessage(Text.literal("Elytra Swap: " + (MultitoolsSettings.elytraSwapEnabled ? "ON" : "OFF")), true);
            }
         }

         this.tickQueuedCommand(client);
         this.tickSupportPotions(client);
         this.tickElytraThreatSwap(client);
         this.tickElytraSwap(client);
         this.tickSpearDash(client);
         this.tickPearlCatch(client);
         while (ModKeyBindings.getDiveCombo().wasPressed()) {
            if (MultitoolsSettings.diveComboEnabled) {
               this.diveComboState.trigger(client);
            }
         }
         this.diveComboState.tick(client);
         this.tickSmartAttack(client);
      });
   }

   private void tickElytraSwap(MinecraftClient client) {
      if (!MultitoolsSettings.elytraSwapFeatureEnabled || !MultitoolsSettings.elytraSwapEnabled) {
         this.elytraBouncePreserveTicks = 0;
         this.elytraLandingSwapDelay = -1;
         this.elytraThreatHoldTicks = 0;
         this.restoreElytraAfterThreat = false;
         this.restoreGlideAfterThreat = false;
         this.lastJumpPressed = client.options.jumpKey.isPressed();
         this.lastOnGround = client.player.isOnGround();
         return;
      }

      ClientPlayerEntity player = client.player;
      boolean onGround = player.isOnGround();
      boolean jumpPressed = client.options.jumpKey.isPressed();
      boolean gliding = player.isGliding();

      if (this.elytraBouncePreserveTicks > 0) {
         --this.elytraBouncePreserveTicks;
      }

      if (this.elytraLandingSwapDelay >= 0) {
         --this.elytraLandingSwapDelay;
         if (this.elytraLandingSwapDelay <= 0 && onGround && !gliding && this.elytraBouncePreserveTicks <= 0) {
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
         if (!gliding && this.elytraBouncePreserveTicks <= 0) {
            this.equipChestplateOnLanding(client);
            this.elytraLandingSwapDelay = -1;
         } else {
            this.elytraLandingSwapDelay = MultitoolsSettings.elytraLandingSwapDelayTicks;
         }
      } else if (!onGround && this.lastOnGround) {
         this.elytraLandingSwapDelay = -1;
         this.tryResumeBounceGlide(client);
      }

      this.lastJumpPressed = jumpPressed;
      this.lastOnGround = onGround;
   }

   private void tickElytraThreatSwap(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null || client.interactionManager == null) {
         this.elytraThreatHoldTicks = 0;
         this.restoreElytraAfterThreat = false;
         this.restoreGlideAfterThreat = false;
         return;
      }

      boolean threatDetected = this.isImmediateChestThreat(client, player);
      if (threatDetected) {
         this.elytraThreatHoldTicks = ELYTRA_THREAT_HOLD_TICKS;
         if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            boolean wasGrounded = player.isOnGround();
            boolean wasGliding = player.isGliding();
            this.equipChestplateFromInventory(client);
            this.restoreElytraAfterThreat = !wasGrounded;
            this.restoreGlideAfterThreat = wasGliding || (!wasGrounded && player.getVelocity().y < 0.0D);
         }
         return;
      }

      if (this.elytraThreatHoldTicks > 0) {
         --this.elytraThreatHoldTicks;
         return;
      }

      if (!this.restoreElytraAfterThreat) {
         return;
      }

      if (player.isOnGround()) {
         this.restoreElytraAfterThreat = false;
         this.restoreGlideAfterThreat = false;
         return;
      }

      if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         this.equipElytraFromHotbar(client);
      }

      if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && this.restoreGlideAfterThreat) {
         this.startFallFlyingIfPossible(player);
      }

      this.restoreElytraAfterThreat = false;
      this.restoreGlideAfterThreat = false;
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

   private void tryResumeBounceGlide(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || this.elytraBouncePreserveTicks <= 0) {
         return;
      }

      this.startFallFlyingIfPossible(player);
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
         if (!MultitoolsSettings.spearDashEnabled) {
            continue;
         }

         if (this.spearBindDelay > 0) {
            continue;
         }

         this.previousSpearSlot = client.player.getInventory().getSelectedSlot();
         if (!this.swapToLungeSpear(client.player)) {
            continue;
         }

         this.simulateHit(client);
         this.spearSwapBackDelay = MultitoolsSettings.spearSwapBackDelayTicks;
         this.spearBindDelay = MultitoolsSettings.spearKeyCooldownTicks;
      }
   }

   private void tickPearlCatch(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null || client.interactionManager == null) {
         this.resetPearlCatchState();
         this.pendingCommand = null;
         this.pendingCommandDelay = -1;
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
      if (!MultitoolsSettings.smartAttackEnabled || !ModKeyBindings.getSmartAttack().isPressed()) {
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

      int aimTicks = Math.max(1, MultitoolsSettings.pearlCatchAimTicks);
      this.pearlPitchStep = this.computePearlPitchStep(pitch) / aimTicks;
      this.pearlCatchDelay = aimTicks;
   }

   private ActionResult handleManualWindChargeUse(ClientPlayerEntity player, Hand hand) {
      if (this.suppressManualWindChargeIntercept) {
         return ActionResult.PASS;
      }

      ItemStack usedStack = player.getStackInHand(hand);
      if (!usedStack.isOf(Items.WIND_CHARGE)) {
         return ActionResult.PASS;
      }

      if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         return ActionResult.PASS;
      }

      if (player.isOnGround()) {
         return ActionResult.PASS;
      }

      if (player.getPitch() < WIND_CHARGE_ELYTRA_DIVE_THRESHOLD && player.getVelocity().y > -0.08D) {
         return ActionResult.PASS;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client.interactionManager == null || client.player != player) {
         return ActionResult.PASS;
      }

      float previousPitch = player.getPitch();
      float adjustedPitch = this.computeManualWindChargePitch(player);
      this.suppressManualWindChargeIntercept = true;
      try {
         player.setPitch(adjustedPitch);
         client.interactionManager.interactItem(player, hand);
      } finally {
         player.setPitch(previousPitch);
         this.suppressManualWindChargeIntercept = false;
      }
      this.elytraBouncePreserveTicks = ELYTRA_BOUNCE_PRESERVE_TICKS;
      this.elytraLandingSwapDelay = -1;
      this.restoreElytraAfterThreat = false;
      this.restoreGlideAfterThreat = false;
      return ActionResult.SUCCESS;
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

   private void queueSelfBuffPotions(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.interactionManager == null) {
         return;
      }

      this.pendingSupportPotionSlots.clear();
      this.supportPotionDelay = 0;
      this.enqueueBestSplashPotion(player, Potions.FIRE_RESISTANCE, Potions.LONG_FIRE_RESISTANCE);
      this.enqueueBestSplashPotion(player, Potions.STRONG_STRENGTH, Potions.STRENGTH, Potions.LONG_STRENGTH);
      this.enqueueBestSplashPotion(player, Potions.STRONG_SWIFTNESS, Potions.SWIFTNESS, Potions.LONG_SWIFTNESS);
      this.enqueueBestSplashPotion(player, Potions.STRONG_REGENERATION, Potions.REGENERATION, Potions.LONG_REGENERATION);
   }

   private void enqueueBestSplashPotion(ClientPlayerEntity player, RegistryEntry<net.minecraft.potion.Potion>... preferredPotions) {
      int bestSlot = -1;
      int bestRank = Integer.MAX_VALUE;

      for (int slot = 0; slot < 36; ++slot) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (!stack.isOf(Items.SPLASH_POTION)) {
            continue;
         }

         PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
         if (potionContents == null) {
            continue;
         }

         Optional<RegistryEntry<net.minecraft.potion.Potion>> potion = potionContents.potion();
         if (potion.isEmpty()) {
            continue;
         }

         for (int rank = 0; rank < preferredPotions.length; ++rank) {
            if (potion.get().matchesKey(preferredPotions[rank].getKey().orElseThrow())) {
               if (rank < bestRank) {
                  bestRank = rank;
                  bestSlot = slot;
               }
               break;
            }
         }
      }

      if (bestSlot >= 0) {
         this.pendingSupportPotionSlots.add(bestSlot);
      }
   }

   private void tickSupportPotions(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.interactionManager == null) {
         this.pendingSupportPotionSlots.clear();
         this.supportPotionDelay = 0;
         return;
      }

      if (this.supportPotionDelay > 0) {
         --this.supportPotionDelay;
         return;
      }

      Integer slot = this.pendingSupportPotionSlots.poll();
      if (slot == null) {
         return;
      }

      if (slot < 0 || slot >= 36) {
         return;
      }

      ItemStack stack = player.getInventory().getStack(slot);
      if (!stack.isOf(Items.SPLASH_POTION)) {
         return;
      }

      this.useInventoryItemAtPitch(client, player, slot, SUPPORT_POTION_PITCH);
      this.supportPotionDelay = Math.max(0, MultitoolsSettings.supportPotionReuseTicks);
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
         if (this.findFirstEmptyHotbarSlot(player) >= 0) {
            player.networkHandler.sendChatCommand(command);
            return;
         }

         int emptyInventorySlot = this.findFirstEmptyInventorySlot(player);
         if (emptyInventorySlot >= 9 && this.swapSelectedHotbarStackIntoInventory(client, player, emptyInventorySlot)) {
            this.pendingCommand = command;
            this.pendingCommandDelay = 1;
            return;
         }

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

   private int findFirstEmptyHotbarSlot(ClientPlayerEntity player) {
      for (int slot = 0; slot < 9; ++slot) {
         if (player.getInventory().getStack(slot).isEmpty()) {
            return slot;
         }
      }

      return -1;
   }

   private int findFirstEmptyInventorySlot(ClientPlayerEntity player) {
      for (int slot = 9; slot < 36; ++slot) {
         if (player.getInventory().getStack(slot).isEmpty()) {
            return slot;
         }
      }

      return -1;
   }

   private boolean swapSelectedHotbarStackIntoInventory(MinecraftClient client, ClientPlayerEntity player, int inventorySlot) {
      if (client.interactionManager == null || !player.playerScreenHandler.getCursorStack().isEmpty()) {
         return false;
      }

      int selectedSlot = player.getInventory().getSelectedSlot();
      int targetSlot = this.toScreenHandlerSlot(inventorySlot);
      int syncId = player.playerScreenHandler.syncId;
      client.interactionManager.clickSlot(syncId, targetSlot, selectedSlot, SlotActionType.SWAP, player);
      return true;
   }

   private void useInventoryItemAtPitch(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, float pitch) {
      int selectedSlot = player.getInventory().getSelectedSlot();
      float previousPitch = player.getPitch();

      if (inventorySlot < 9) {
         MaceSwapState.setSelectedHotbarSlot(client, player, inventorySlot);
         player.setPitch(pitch);
         client.interactionManager.interactItem(player, Hand.MAIN_HAND);
         player.setPitch(previousPitch);
         MaceSwapState.setSelectedHotbarSlot(client, player, selectedSlot);
         return;
      }

      int syncId = player.playerScreenHandler.syncId;
      int sourceSlot = this.toScreenHandlerSlot(inventorySlot);
      client.interactionManager.clickSlot(syncId, sourceSlot, selectedSlot, SlotActionType.SWAP, player);
      player.setPitch(pitch);
      client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      player.setPitch(previousPitch);
      client.interactionManager.clickSlot(syncId, sourceSlot, selectedSlot, SlotActionType.SWAP, player);
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

   private boolean isImmediateChestThreat(MinecraftClient client, ClientPlayerEntity player) {
      if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         return false;
      }

      Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
      Box searchBox = player.getBoundingBox().expand(8.0D, 8.0D, 8.0D);
      double crystalThreatRange = player.isOnGround() ? CRYSTAL_GROUNDED_THREAT_RANGE : CRYSTAL_AIR_THREAT_RANGE;
      double crystalThreatRangeSq = crystalThreatRange * crystalThreatRange;
      for (Entity entity : client.world.getOtherEntities(player, searchBox)) {
         if (entity instanceof EndCrystalEntity && entity.squaredDistanceTo(playerPos) <= crystalThreatRangeSq) {
            return true;
         }

         if (entity instanceof TntEntity tnt && tnt.squaredDistanceTo(playerPos) <= TNT_THREAT_RANGE * TNT_THREAT_RANGE && tnt.getFuse() <= TNT_MAX_THREAT_FUSE) {
            return true;
         }

         if (entity instanceof PlayerEntity otherPlayer && this.isIncomingMaceThreat(player, otherPlayer)) {
            return true;
         }
      }

      return false;
   }

   private boolean isIncomingMaceThreat(ClientPlayerEntity player, PlayerEntity otherPlayer) {
      if (otherPlayer == player || !otherPlayer.isAlive()) {
         return false;
      }

      if (!otherPlayer.getMainHandStack().isOf(Items.MACE) && !otherPlayer.getOffHandStack().isOf(Items.MACE)) {
         return false;
      }

      if (otherPlayer.isOnGround() || otherPlayer.getVelocity().y > MACE_THREAT_DESCENT_SPEED || otherPlayer.fallDistance < 3.0F) {
         return false;
      }

      double horizontalDistanceSq = MathHelper.squaredHypot(otherPlayer.getX() - player.getX(), otherPlayer.getZ() - player.getZ());
      if (horizontalDistanceSq > MACE_THREAT_HORIZONTAL_RANGE * MACE_THREAT_HORIZONTAL_RANGE) {
         return false;
      }

      double verticalDelta = otherPlayer.getY() - player.getY();
      return verticalDelta >= -1.0D && verticalDelta <= MACE_THREAT_VERTICAL_RANGE;
   }

   private float computeManualWindChargePitch(ClientPlayerEntity player) {
      Vec3d velocity = player.getVelocity();
      double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
      double downwardSpeed = Math.max(0.0D, -velocity.y);
      double groundDistance = this.estimateGroundDistance(player, 20.0D);
      float pitch = 72.0F;
      pitch += (float)(horizontalSpeed * 5.5D);
      pitch += (float)(downwardSpeed * 14.0D);

      if (groundDistance < 6.0D) {
         pitch += 6.0F;
      }
      if (groundDistance < 3.5D) {
         pitch += 6.0F;
      }

      return MathHelper.clamp(pitch, MIN_SELF_WIND_CHARGE_PITCH, MAX_SELF_WIND_CHARGE_PITCH);
   }

   private double estimateGroundDistance(ClientPlayerEntity player, double maxDistance) {
      Vec3d start = player.getEyePos();
      Vec3d end = start.subtract(0.0D, maxDistance, 0.0D);
      var hit = player.getEntityWorld().raycast(new RaycastContext(
         start,
         end,
         RaycastContext.ShapeType.COLLIDER,
         RaycastContext.FluidHandling.NONE,
         player
      ));
      if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
         return Math.max(0.0D, start.y - hit.getPos().y);
      }
      return maxDistance;
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

