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
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.potion.Potions;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

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
   private static final double WATER_DROP_TRIGGER_DISTANCE = 4.25D;
   private static final double WATER_DROP_TRIGGER_SPEED = -0.55D;
   private static final int TEMP_FLUID_EXTINGUISH_TICKS = 2;
   private static final int TEMP_FLUID_LAVA_TICKS = 2;
   private static final float STRAIGHT_DOWN_PITCH = 90.0F;

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
   private boolean suppressAutoElytraForSmartAttack = false;
   private TemporaryFluidMode temporaryFluidMode = TemporaryFluidMode.NONE;
   private BlockPos temporaryFluidPos = null;
   private Item temporaryFluidExpectedBucketItem = Items.BUCKET;
   private int temporaryFluidTicks = 0;
   private int temporaryFluidPickupDelayTicks = -1;
   private boolean temporaryFluidReturnRequested = false;
   private int temporaryFluidReturnTimeoutTicks = 0;
   private boolean lastWaterUtilityPressed = false;
   private boolean lastWebTrapPressed = false;

   @Override
   public void onInitializeClient() {
      MultitoolsSettings.load();
      ModKeyBindings.getSpearDash();
      ModKeyBindings.getPearlCatch();
      ModKeyBindings.getWaterUtility();
      ModKeyBindings.getWebTrap();
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

         this.tickTemporaryFluid(client);

         while (ModKeyBindings.getAutoTotem().wasPressed()) {
            MultitoolsSettings.autoTotemEnabled = !MultitoolsSettings.autoTotemEnabled;
            MultitoolsSettings.save();
            client.player.sendMessage(Text.literal("Auto Totem: " + (MultitoolsSettings.autoTotemEnabled ? "ON" : "OFF")), true);
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

         while (ModKeyBindings.getWaterUtility().wasPressed()) {
            this.handleWaterUtilityTap(client);
         }

         while (ModKeyBindings.getWebTrap().wasPressed()) {
            this.handleWebTrapTap(client);
         }

         while (ModKeyBindings.getLoadMain().wasPressed()) {
            if (MultitoolsSettings.loadMainEnabled) {
               this.runLoadMain(client);
            }
         }

      while (ModKeyBindings.getSettings().wasPressed()) {
         if (MultitoolsSettings.railgunEnabled) {
               this.queueGiveCommand(client, "givef railgun straight 1");
            }
         }

         while (ModKeyBindings.getGiveNuke().wasPressed()) {
            if (MultitoolsSettings.nukeEnabled) {
               this.queueGiveCommand(client, "givef nuke 1 11");
            }
         }

         while (ModKeyBindings.getGiveStab().wasPressed()) {
            if (MultitoolsSettings.stabEnabled) {
               this.queueGiveCommand(client, "givef stab 1");
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
         this.tickAutoTotem(client);
         this.tickElytraThreatSwap(client);
         this.tickElytraSwap(client);
         this.tickSpearDash(client);
         this.tickPearlCatch(client);
         this.tickWaterUtilityHold(client);
         this.tickWebTrapHold(client);
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
         } else if (!onGround && this.canAutoEquipElytra(player) && !this.suppressAutoElytraForSmartAttack) {
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
            MaceSwapState.setSelectedHotbarSlot(client, client.player, this.previousSpearSlot);
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

         MaceSwapState.setAutomationSuppressed(true);
          try {
             this.simulateHit(client);
          } finally {
             MaceSwapState.setAutomationSuppressed(false);
          }
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
            this.suppressManualWindChargeIntercept = true;
            try {
               client.interactionManager.interactItem(player, Hand.MAIN_HAND);
            } finally {
               this.suppressManualWindChargeIntercept = false;
            }
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
         this.suppressAutoElytraForSmartAttack = false;
         return;
      }

      ClientPlayerEntity player = client.player;
      if (player == null) {
         return;
      }

      if (player.getVelocity().y > SMART_ATTACK_VERTICAL_SPEED) {
          return;
       }

       Entity target = this.findSmartAttackTarget(client);
      if (!(target instanceof LivingEntity)) {
         return;
      }

      if (player.isGliding() && player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         if (this.equipChestplateFromInventory(client)) {
            this.suppressAutoElytraForSmartAttack = true;
         }
      }

      client.interactionManager.attackEntity(player, target);
      player.swingHand(Hand.MAIN_HAND);
   }

   private void tickWaterUtilityHold(MinecraftClient client) {
      boolean pressed = ModKeyBindings.getWaterUtility().isPressed();
      this.lastWaterUtilityPressed = pressed;

      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null) {
         return;
      }

      // Auto water-drop while X is held and falling (not gliding), no fluid already placed
      if (pressed
            && this.temporaryFluidMode == TemporaryFluidMode.NONE
            && !player.isOnGround()
            && !player.isGliding()
            && this.shouldAttemptWaterDrop(player)) {

         // Check if there's a flower/grass/plant blocking water placement
         BlockPos supportPos = this.findSupportBlockBelowPlayer(player, 6.0D);
         if (supportPos != null) {
            BlockState blockAbove = client.world.getBlockState(supportPos.up());
            boolean blocked = !blockAbove.isAir() && blockAbove.getFluidState().isEmpty();
            if (blocked) {
               player.sendMessage(Text.literal("§e⚠ Move — plant/block below!"), true);
               return;
            }
         }

         this.tryPlaceWaterBelowSelf(client, TemporaryFluidMode.WATER_DROP);
      }
   }

   private void tickWebTrapHold(MinecraftClient client) {
      this.lastWebTrapPressed = ModKeyBindings.getWebTrap().isPressed();
   }

   private void handleWaterUtilityTap(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null) {
         return;
      }

      boolean inFire = player.isOnFire()
         || client.world.getBlockState(player.getBlockPos()).isOf(Blocks.FIRE)
         || client.world.getBlockState(player.getBlockPos()).isOf(Blocks.SOUL_FIRE)
         || client.world.getBlockState(player.getBlockPos().down()).isOf(Blocks.FIRE)
         || client.world.getBlockState(player.getBlockPos().down()).isOf(Blocks.SOUL_FIRE);

      boolean falling = !player.isOnGround() && player.getVelocity().y < -0.3D;

      if (inFire) {
         this.tryPlaceWaterBelowSelf(client, TemporaryFluidMode.GROUND_EXTINGUISH);
         return;
      }

      if (falling) {
         this.tryPlaceWaterBelowSelf(client, TemporaryFluidMode.WATER_DROP);
         return;
      }

      LivingEntity target = this.findBestAttackTarget(client, 6.0D, 4.0D) instanceof LivingEntity living ? living : null;
      if (target != null && this.findInventorySlot(player, Items.LAVA_BUCKET) >= 0) {
         this.tryPlaceLavaUnderTarget(client);
         return;
      }

      this.tryPlaceWaterBelowSelf(client, TemporaryFluidMode.WATER_DROP);
   }

   private void handleWebTrapTap(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || this.isInsideCobweb(client, player)) {
         return;
      }

      this.trySwordWebTrap(client);
   }

   private void tickTemporaryFluid(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null) {
         this.clearTemporaryFluid();
         return;
      }

      if (this.temporaryFluidMode == TemporaryFluidMode.NONE || this.temporaryFluidPos == null) {
         return;
      }

      ++this.temporaryFluidTicks;
      if (this.temporaryFluidReturnRequested && this.temporaryFluidReturnTimeoutTicks > 0) {
          --this.temporaryFluidReturnTimeoutTicks;
       }

       if (this.temporaryFluidReturnRequested && this.temporaryFluidReturnTimeoutTicks <= 0) {
          this.clearTemporaryFluid();
          return;
       }

      if (this.temporaryFluidPickupDelayTicks > 0) {
         --this.temporaryFluidPickupDelayTicks;
      }

      boolean fluidPresent = this.isTemporaryFluidStillPresent(client);
      if (!fluidPresent && !this.temporaryFluidReturnRequested) {
         this.clearTemporaryFluid();
         return;
      }

      if (this.temporaryFluidPickupDelayTicks > 0) {
         return;
      }

      if (this.temporaryFluidReturnRequested) {
         this.tryPickupTemporaryFluid(client);
         return;
      }

      if (this.shouldPickupTemporaryFluidNow(client)) {
         this.tryPickupTemporaryFluid(client);
      }
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

      if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
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

   private void tickAutoTotem(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (!MultitoolsSettings.autoTotemEnabled || player == null || client.currentScreen != null) {
         return;
      }

      if (!player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
         this.swapTotemToOffhand(client);
      }
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

      this.suppressManualWindChargeIntercept = true;
      try {
         player.setPitch(pitch);
         client.interactionManager.interactItem(player, Hand.MAIN_HAND);
      } finally {
         player.setPitch(previousPitch);
         this.suppressManualWindChargeIntercept = false;
      }
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

   boolean equipChestplateFromInventory(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (client.interactionManager == null || player == null) {
         return false;
      }

      if (player.playerScreenHandler.getCursorStack().isEmpty() && player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
         int chestplateSlot = this.findChestplateInventorySlot(player);
         if (chestplateSlot >= 0) {
            this.swapInventoryItemIntoArmorSlot(client, player, chestplateSlot, PLAYER_CHEST_SLOT);
            return true;
         }
      }

      return false;
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

   private void queueGiveCommand(MinecraftClient client, String command) {
      ClientPlayerEntity player = client.player;
      if (player == null || player.networkHandler == null) {
         return;
      }

      int emptyHotbarSlot = this.findFirstEmptyHotbarSlot(player);
      if (emptyHotbarSlot >= 0) {
         if (player.getInventory().getSelectedSlot() != emptyHotbarSlot) {
            MaceSwapState.setSelectedHotbarSlot(client, player, emptyHotbarSlot);
         }

         player.networkHandler.sendChatCommand(command);
         return;
      }

      this.queueCommand(client, command);
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

   private boolean tryPlaceWaterBelowSelf(MinecraftClient client, TemporaryFluidMode mode) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null || this.temporaryFluidMode != TemporaryFluidMode.NONE) {
         return false;
      }

      int waterBucketSlot = this.findInventorySlot(player, Items.WATER_BUCKET);
      if (waterBucketSlot < 0) {
         return false;
      }

      BlockPos supportPos = this.findSupportBlockBelowPlayer(player, mode == TemporaryFluidMode.COBWEB_WATER ? 3.5D : 6.0D);
      if (supportPos == null) {
         return false;
      }

      BlockPos sourcePos = supportPos.up();
      if (!this.canUseTemporaryFluidAt(client, sourcePos, false)) {
         return false;
      }

      Vec3d hitVec = Vec3d.ofCenter(supportPos).add(0.0D, 0.5D, 0.0D);
      BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, supportPos, false);
      this.placeBucketViaInteractBlock(client, player, waterBucketSlot, hit);

      boolean placed = client.world.getBlockState(sourcePos).isOf(Blocks.WATER)
            || !client.world.getBlockState(sourcePos).getFluidState().isEmpty();
      if (!placed) {
         return false;
      }

      this.temporaryFluidMode = mode;
      this.temporaryFluidPos = sourcePos;
      this.temporaryFluidExpectedBucketItem = Items.WATER_BUCKET;
      this.temporaryFluidTicks = 0;
      this.temporaryFluidPickupDelayTicks = 1;
      this.temporaryFluidReturnRequested = false;
      this.temporaryFluidReturnTimeoutTicks = 0;
      return true;
   }

   private boolean tryPlaceLavaUnderTarget(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null || this.temporaryFluidMode != TemporaryFluidMode.NONE) {
         return false;
      }

      LivingEntity target = this.findBestAttackTarget(client, 12.0D, 6.0D) instanceof LivingEntity living ? living : null;
      if (target == null && client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
         net.minecraft.util.hit.EntityHitResult entityHit = (net.minecraft.util.hit.EntityHitResult) client.crosshairTarget;
         if (entityHit.getEntity() instanceof LivingEntity livingEntity) {
            target = livingEntity;
         }
      }

      int lavaBucketSlot = this.findInventorySlot(player, Items.LAVA_BUCKET);
      if (lavaBucketSlot < 0) {
         return false;
      }

      BlockPos placementPos;
      if (target != null) {
         placementPos = this.findBlockUnderTarget(client, target, 14.0D);
      } else if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
         placementPos = ((BlockHitResult) client.crosshairTarget).getBlockPos().up();
      } else {
         return false;
      }
      if (placementPos == null || !this.canUseTemporaryFluidAt(client, placementPos, false)) {
         return false;
      }

      BlockPos supportPos = placementPos.down();
      Vec3d hitVec = Vec3d.ofCenter(supportPos).add(0.0D, 0.5D, 0.0D);
      BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, supportPos, false);
      this.placeBucketViaInteractBlock(client, player, lavaBucketSlot, hit);

      boolean placed = client.world.getBlockState(placementPos).isOf(Blocks.LAVA)
            || !client.world.getBlockState(placementPos).getFluidState().isEmpty();
      if (!placed) {
         return false;
      }

      this.temporaryFluidMode = TemporaryFluidMode.LAVA_TRAP;
      this.temporaryFluidPos = placementPos;
      this.temporaryFluidExpectedBucketItem = Items.LAVA_BUCKET;
      this.temporaryFluidTicks = 0;
      this.temporaryFluidPickupDelayTicks = 1;
      this.temporaryFluidReturnRequested = false;
      this.temporaryFluidReturnTimeoutTicks = 0;
      return true;
   }

   /**
    * Places or picks up a fluid by briefly rotating the player's view to face the target block,
    * calling interactItem (which reads crosshairTarget from the rotated view), then restoring look.
    */
   private void placeBucketViaInteractBlock(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, BlockHitResult hit) {
      if (client.interactionManager == null || client.getNetworkHandler() == null) {
         return;
      }

      Vec3d eye = player.getEyePos();
      Vec3d target = hit.getPos();
      Vec3d delta = target.subtract(eye);
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float aimYaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
      float aimPitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));

      float prevYaw = player.getYaw();
      float prevPitch = player.getPitch();
      HitResult prevCrosshair = client.crosshairTarget;

      player.setYaw(aimYaw);
      player.setPitch(aimPitch);
      client.crosshairTarget = hit;

      // Send look packet so server knows where we're facing
      client.getNetworkHandler().sendPacket(
         new PlayerMoveC2SPacket.LookAndOnGround(aimYaw, aimPitch, player.isOnGround(), player.horizontalCollision)
      );

      this.runWithInventorySlotSelected(client, player, inventorySlot, () -> {
         client.interactionManager.interactItem(player, Hand.MAIN_HAND);
         player.swingHand(Hand.MAIN_HAND);
      });

      player.setYaw(prevYaw);
      player.setPitch(prevPitch);
      client.crosshairTarget = prevCrosshair;
   }

   private boolean trySwordWebTrap(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null) {
         return false;
      }

      LivingEntity target = this.findBestAttackTarget(client, 8.0D, 5.0D) instanceof LivingEntity living ? living : null;
      if (target == null) {
         return false;
      }

      int swordSlot = this.findInventorySlotByTag(player, ItemTags.SWORDS);
      int cobwebSlot = this.findInventorySlot(player, Items.COBWEB);
      if (swordSlot < 0 || cobwebSlot < 0) {
         return false;
      }

      boolean attacked = this.attackEntityFromInventorySlot(client, player, swordSlot, target);
      if (!attacked) {
         return false;
      }

      BlockPos placementPos = this.findEntityLandingPlacement(client, player, target, 16.0D, 2.5D);
      if (placementPos == null || !this.canPlaceCobwebAt(client, placementPos)) {
         return true;
      }

      if (!this.useInventoryItemOnBlock(client, player, cobwebSlot, placementPos.down(), Direction.UP, STRAIGHT_DOWN_PITCH)) {
         this.useInventoryItemOnBlockFlexible(client, player, cobwebSlot, placementPos);
      }
      return true;
   }

   private boolean shouldAttemptWaterDrop(ClientPlayerEntity player) {
      return !player.isOnGround() && player.getVelocity().y <= WATER_DROP_TRIGGER_SPEED && this.estimateGroundDistance(player, 12.0D) <= WATER_DROP_TRIGGER_DISTANCE;
   }

   private boolean isInsideCobweb(MinecraftClient client, ClientPlayerEntity player) {
      BlockPos feetPos = BlockPos.ofFloored(player.getX(), player.getBoundingBox().minY + 0.05D, player.getZ());
      BlockPos bodyPos = player.getBlockPos();
      return client.world.getBlockState(feetPos).isOf(Blocks.COBWEB) || client.world.getBlockState(bodyPos).isOf(Blocks.COBWEB);
   }

   private LivingEntity findPriorityTrapTarget(MinecraftClient client) {
      Entity entity = this.findBestAttackTarget(client, SMART_ATTACK_RANGE, 3.5D);
      return entity instanceof LivingEntity living ? living : null;
   }

   private BlockPos findSupportBlockBelowPlayer(ClientPlayerEntity player, double maxDistance) {
      Vec3d start = new Vec3d(player.getX(), player.getY() + 0.2D, player.getZ());
      Vec3d end = start.subtract(0.0D, maxDistance, 0.0D);
      BlockHitResult hit = player.getEntityWorld().raycast(new RaycastContext(
         start,
         end,
         RaycastContext.ShapeType.COLLIDER,
         RaycastContext.FluidHandling.NONE,
         player
      ));
      return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
   }

   private BlockPos findEntityLandingPlacement(MinecraftClient client, ClientPlayerEntity player, LivingEntity target, double maxDistance, double forwardOffset) {
      Vec3d predictedBase = this.computeForwardTrapBase(player, target, forwardOffset);
      BlockPos currentPos = BlockPos.ofFloored(predictedBase.x, target.getY(), predictedBase.z);
      if (client.world.getBlockState(currentPos).isAir() && this.hasSolidSupport(client, currentPos.down())) {
         return currentPos;
      }

      Vec3d start = new Vec3d(predictedBase.x, target.getY() + 0.2D, predictedBase.z);
      Vec3d end = start.subtract(0.0D, maxDistance, 0.0D);
      BlockHitResult hit = client.world.raycast(new RaycastContext(
         start,
         end,
         RaycastContext.ShapeType.COLLIDER,
         RaycastContext.FluidHandling.NONE,
         target
      ));
      if (hit.getType() != HitResult.Type.BLOCK) {
         return null;
      }

      BlockPos placementPos = hit.getBlockPos().up();
      return client.world.getBlockState(placementPos).isAir() ? placementPos : null;
   }

   private BlockPos findBlockUnderTarget(MinecraftClient client, LivingEntity target, double maxDistance) {
      Vec3d targetCenter = new Vec3d(target.getX(), target.getY(), target.getZ());
      BlockPos base = BlockPos.ofFloored(targetCenter.x, target.getBoundingBox().minY - 0.05D, targetCenter.z);
      if (client.world.getBlockState(base).isAir() && this.hasSolidSupport(client, base.down())) {
         return base;
      }

      Vec3d start = new Vec3d(targetCenter.x, target.getBoundingBox().minY + 0.2D, targetCenter.z);
      Vec3d end = start.subtract(0.0D, maxDistance, 0.0D);
      BlockHitResult hit = client.world.raycast(new RaycastContext(
         start,
         end,
         RaycastContext.ShapeType.COLLIDER,
         RaycastContext.FluidHandling.NONE,
         target
      ));
      if (hit.getType() != HitResult.Type.BLOCK) {
         return null;
      }

      BlockPos placementPos = hit.getBlockPos().up();
      return client.world.getBlockState(placementPos).isAir() ? placementPos : null;
   }

   private Vec3d computeForwardTrapBase(ClientPlayerEntity player, LivingEntity target, double forwardOffset) {
      Vec3d away = new Vec3d(target.getX() - player.getX(), 0.0D, target.getZ() - player.getZ());
      if (away.lengthSquared() < 1.0E-6D) {
         away = player.getRotationVec(1.0F);
         away = new Vec3d(away.x, 0.0D, away.z);
      }

      if (away.lengthSquared() < 1.0E-6D) {
         away = new Vec3d(0.0D, 0.0D, 1.0D);
      }

      Vec3d horizontalAway = away.normalize().multiply(forwardOffset);
      Vec3d targetVelocity = new Vec3d(target.getVelocity().x, 0.0D, target.getVelocity().z).multiply(3.0D);
      return new Vec3d(target.getX(), target.getY(), target.getZ()).add(horizontalAway).add(targetVelocity);
   }

   private boolean canUseTemporaryFluidAt(MinecraftClient client, BlockPos sourcePos, boolean requireCobweb) {
      BlockState state = client.world.getBlockState(sourcePos);
      if (requireCobweb) {
         return state.isOf(Blocks.COBWEB);
      }

      return state.isAir() || state.isOf(Blocks.COBWEB) || !state.getFluidState().isEmpty();
   }

   private boolean canPlaceCobwebAt(MinecraftClient client, BlockPos placementPos) {
      return client.world.getBlockState(placementPos).isAir() && this.hasSolidSupport(client, placementPos.down());
   }

   private boolean hasSolidSupport(MinecraftClient client, BlockPos supportPos) {
      return !client.world.getBlockState(supportPos).isAir();
   }

   private boolean tryPickupTemporaryFluid(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || this.temporaryFluidPos == null) {
         this.clearTemporaryFluid();
         return false;
      }

      int emptyBucketSlot = this.findInventorySlot(player, Items.BUCKET);
      if (emptyBucketSlot < 0) {
         return false;
      }

      Item expectedFilledBucket = this.temporaryFluidExpectedBucketItem;

      // Pick up fluid: aim at the fluid block and use interactItem (same as placement)
      Vec3d hitVec = Vec3d.ofCenter(this.temporaryFluidPos);
      BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, this.temporaryFluidPos, false);
      this.placeBucketViaInteractBlock(client, player, emptyBucketSlot, hit);

      boolean fluidPresent = this.isTemporaryFluidStillPresent(client);
      boolean bucketReturned = player.getInventory().getStack(emptyBucketSlot).isOf(expectedFilledBucket);
      if (fluidPresent || !bucketReturned) {
         return false;
      }

      this.clearTemporaryFluid();
      return true;
   }

   private void requestTemporaryFluidReturn(MinecraftClient client) {
      if (this.temporaryFluidMode == TemporaryFluidMode.NONE) {
         return;
      }

      this.temporaryFluidReturnRequested = true;
      this.temporaryFluidReturnTimeoutTicks = 20;
      this.temporaryFluidPickupDelayTicks = 0;
      this.tryPickupTemporaryFluid(client);
   }

   private boolean isHoldFluidMode(TemporaryFluidMode mode) {
      return mode == TemporaryFluidMode.WATER_DROP
         || mode == TemporaryFluidMode.GROUND_EXTINGUISH
         || mode == TemporaryFluidMode.LAVA_TRAP
         || mode == TemporaryFluidMode.COBWEB_WATER;
   }

   private boolean shouldPickupTemporaryFluidNow(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null) {
         return false;
      }

      return switch (this.temporaryFluidMode) {
         case WATER_DROP -> this.temporaryFluidReturnRequested || player.isOnGround() || player.isTouchingWater();
         case GROUND_EXTINGUISH -> this.temporaryFluidReturnRequested || !player.isOnFire();
         case LAVA_TRAP -> this.temporaryFluidReturnRequested || this.temporaryFluidTicks >= TEMP_FLUID_LAVA_TICKS;
         case COBWEB_WATER -> this.temporaryFluidReturnRequested || !ModKeyBindings.getWebTrap().isPressed() || !this.isInsideCobweb(client, player);
         case NONE -> false;
      };
   }

   private boolean isTemporaryFluidStillPresent(MinecraftClient client) {
      if (this.temporaryFluidPos == null) {
         return false;
      }

      BlockState state = client.world.getBlockState(this.temporaryFluidPos);
      return switch (this.temporaryFluidMode) {
         case WATER_DROP, GROUND_EXTINGUISH, COBWEB_WATER -> state.isOf(Blocks.WATER) || state.isOf(Blocks.KELP) || !state.getFluidState().isEmpty() && state.getFluidState().isStill();
         case LAVA_TRAP -> state.isOf(Blocks.LAVA) || !state.getFluidState().isEmpty() && state.getFluidState().isStill();
         case NONE -> false;
      };
   }

   private void clearTemporaryFluid() {
      this.temporaryFluidMode = TemporaryFluidMode.NONE;
      this.temporaryFluidPos = null;
      this.temporaryFluidExpectedBucketItem = Items.BUCKET;
      this.temporaryFluidTicks = 0;
      this.temporaryFluidPickupDelayTicks = -1;
      this.temporaryFluidReturnRequested = false;
      this.temporaryFluidReturnTimeoutTicks = 0;
   }

   private boolean attackEntityFromInventorySlot(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, LivingEntity target) {
      if (client.interactionManager == null || target == null) {
         return false;
      }

      final boolean[] attacked = {false};
      if (!this.runWithInventorySlotSelected(client, player, inventorySlot, () -> {
         client.interactionManager.attackEntity(player, target);
         player.swingHand(Hand.MAIN_HAND);
         attacked[0] = true;
      })) {
         return false;
      }

      return attacked[0];
   }

   private boolean useInventoryItemOnBlock(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, BlockPos blockPos, Direction side, float pitch) {
      if (client.interactionManager == null) {
         return false;
      }

      final boolean[] used = {false};
      if (!this.runWithInventorySlotSelected(client, player, inventorySlot, () -> {
         float previousPitch = player.getPitch();
         float previousYaw = player.getYaw();
         Vec3d hitPos = Vec3d.ofCenter(blockPos).add(0.0D, side == Direction.UP ? 0.5D : 0.0D, 0.0D);
         this.facePosition(player, hitPos, pitch);
         ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, new BlockHitResult(hitPos, side, blockPos, false));
         if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
            used[0] = true;
         }
         player.setPitch(previousPitch);
         player.setYaw(previousYaw);
      })) {
         return false;
      }

      return used[0];
   }

   private boolean useInventoryItemOnBlockFlexible(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, BlockPos blockPos) {
      if (client.interactionManager == null) {
         return false;
      }

      Direction[] directions = new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
      for (Direction side : directions) {
         if (this.useInventoryItemOnBlock(client, player, inventorySlot, blockPos, side, STRAIGHT_DOWN_PITCH)) {
            return true;
         }
      }

      return false;
   }

   private boolean useInventoryItemOnBlockFlexibleConfirmed(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, BlockPos blockPos, Item expectedAfterItem) {
      if (client.interactionManager == null) {
         return false;
      }

      Direction[] directions = new Direction[] {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
      for (Direction side : directions) {
         this.useInventoryItemOnBlock(client, player, inventorySlot, blockPos, side, STRAIGHT_DOWN_PITCH);
         if (player.getInventory().getStack(inventorySlot).isOf(expectedAfterItem)) {
            return true;
         }
      }

      return player.getInventory().getStack(inventorySlot).isOf(expectedAfterItem);
   }

   private boolean useInventoryItemAtAngles(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, float pitch, float yaw) {
      if (client.interactionManager == null) {
         return false;
      }

      final boolean[] used = {false};
      if (!this.runWithInventorySlotSelected(client, player, inventorySlot, () -> {
         float previousPitch = player.getPitch();
         float previousYaw = player.getYaw();
         player.setYaw(yaw);
         float clampedPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
         player.setPitch(clampedPitch);
         if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, clampedPitch, player.isOnGround(), player.horizontalCollision));
         }
         ActionResult result = client.interactionManager.interactItem(player, Hand.MAIN_HAND);
         if (result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND);
            used[0] = true;
         }
         player.setPitch(previousPitch);
         player.setYaw(previousYaw);
      })) {
         return false;
      }

      return used[0];
   }

   private boolean useInventoryItemAtAnglesConfirmed(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, float pitch, float yaw, Item expectedAfterItem) {
      if (client.interactionManager == null) {
         return false;
      }

      this.useInventoryItemAtAngles(client, player, inventorySlot, pitch, yaw);
      return player.getInventory().getStack(inventorySlot).isOf(expectedAfterItem);
   }

   private boolean runWithInventorySlotSelected(MinecraftClient client, ClientPlayerEntity player, int inventorySlot, Runnable action) {
      if (inventorySlot < 0 || inventorySlot >= 36 || client.interactionManager == null) {
         return false;
      }

      int selectedSlot = player.getInventory().getSelectedSlot();
      if (inventorySlot < 9) {
         MaceSwapState.setSelectedHotbarSlot(client, player, inventorySlot);
         try {
            action.run();
         } finally {
            MaceSwapState.setSelectedHotbarSlot(client, player, selectedSlot);
         }
         return true;
      }

      if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
         return false;
      }

      int syncId = player.playerScreenHandler.syncId;
      int sourceSlot = this.toScreenHandlerSlot(inventorySlot);
      client.interactionManager.clickSlot(syncId, sourceSlot, selectedSlot, SlotActionType.SWAP, player);
      try {
         action.run();
      } finally {
         client.interactionManager.clickSlot(syncId, sourceSlot, selectedSlot, SlotActionType.SWAP, player);
      }
      return true;
   }

   private int findInventorySlotByTag(ClientPlayerEntity player, net.minecraft.registry.tag.TagKey<Item> tag) {
      for (int slot = 0; slot < 36; ++slot) {
         ItemStack stack = player.getInventory().getStack(slot);
         if (!stack.isEmpty() && stack.isIn(tag)) {
            return slot;
         }
      }

      return -1;
   }

   private void facePosition(ClientPlayerEntity player, Vec3d targetPos, float pitch) {
      Vec3d delta = targetPos.subtract(player.getEyePos());
      float yaw = this.computeYawTo(player.getEyePos(), targetPos);
      player.setYaw(yaw);
      player.setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
   }

   private float computeYawTo(Vec3d source, Vec3d target) {
      Vec3d delta = target.subtract(source);
      return (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
   }

   private float computePitchTo(Vec3d source, Vec3d target) {
      Vec3d delta = target.subtract(source);
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      return (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
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

   private enum TemporaryFluidMode {
      NONE,
      WATER_DROP,
      GROUND_EXTINGUISH,
      LAVA_TRAP,
      COBWEB_WATER
   }
}

