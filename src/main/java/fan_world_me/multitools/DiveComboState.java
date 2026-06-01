package fan_world_me.multitools;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

final class DiveComboState {
   private static final double TARGET_RANGE = 30.0D;
   private static final double BOOST_TO_DIVE_RANGE = 15.0D;
   private static final double MIN_DIVE_HEIGHT_ABOVE_TARGET = 6.0D;
   private static final double STRIKE_RANGE_MIN = 4.8D;
   private static final double STRIKE_RANGE_MAX = 6.2D;
   private static final double STRIKE_ALIGNMENT_MIN = 0.93D;
   private static final double STRIKE_MAX_LATERAL_OFFSET = 1.75D;
   private static final double STRIKE_MIN_CLOSING_SPEED = 0.12D;
   private static final double OBSTACLE_LOOKAHEAD = 5.0D;
   private static final double SAFETY_ASCENT_HEIGHT = 7.0D;
   private static final double SELF_WIND_BURST_DELTA_Y = 0.42D;
   private static final double TARGET_FALLING_THRESHOLD = -0.08D;
   private static final double TARGET_RISING_THRESHOLD = 0.18D;
   private static final int RETARGET_AFTER_FAILED_DIVES = 2;
   private static final float SELF_BOOST_PITCH = 88.0F;
   private static final float REBOOST_PITCH = -80.0F;
   private static final float ASCENT_PITCH = -68.0F;

   private final MultitoolsClient clientHooks;

   private Phase phase = Phase.IDLE;
   private int previousSelectedSlot = -1;
   private int targetEntityId = -1;
   private int targetRefreshTicks = 0;
   private int vectorRefreshTicks = 0;
   private int fireworkCooldown = 0;
   private int phaseTicks = 0;
    private int failedDiveAttempts = 0;
   private double ascentTargetY = Double.NaN;
   private double lastVelocityY = 0.0D;
   private boolean active = false;

   DiveComboState(MultitoolsClient clientHooks) {
      this.clientHooks = clientHooks;
   }

   void trigger(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null) {
         return;
      }

      if (this.active) {
         this.stop(client, true);
         return;
      }

      this.stop(client, true);
      this.start(player);
      this.active = true;
   }

   void cancel(MinecraftClient client) {
      this.stop(client, true);
   }

   void tick(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      if (player == null || client.world == null || client.interactionManager == null) {
         this.stop(client, false);
         return;
      }

      if (!this.active || !MultitoolsSettings.diveComboEnabled) {
         return;
      }
      this.tickCounters();

      LivingEntity target = this.getTrackedTarget(client);
      if (target == null || this.targetRefreshTicks <= 0) {
         target = this.acquireTarget(client);
      }

      if (target == null) {
         this.phase = Phase.SEARCHING;
      }

      switch (this.phase) {
         case SEARCHING -> this.tickSearching(client, player, target);
         case LAUNCHING -> this.tickLaunching(client, player, target);
         case BOOSTING -> this.tickBoosting(client, player, target);
         case PREPARE_DIVE -> this.tickPrepareDive(client, player, target);
         case DIVING -> this.tickDiving(client, player, target);
         case STRIKE -> this.tickStrike(client, player, target);
         case POST_STRIKE -> this.tickPostStrike(client, player, target);
         case WAITING_FOR_TARGET_FALL -> this.tickWaitingForTargetFall(client, player, target);
         case IDLE -> {
         }
      }

      this.lastVelocityY = player.getVelocity().y;
   }

   private void tickCounters() {
      if (this.targetRefreshTicks > 0) {
         --this.targetRefreshTicks;
      }

      if (this.vectorRefreshTicks > 0) {
         --this.vectorRefreshTicks;
      }

      if (this.fireworkCooldown > 0) {
         --this.fireworkCooldown;
      }

      if (this.phaseTicks > 0) {
         --this.phaseTicks;
      }
   }

   private void start(ClientPlayerEntity player) {
      this.phase = Phase.SEARCHING;
      this.previousSelectedSlot = player.getInventory().getSelectedSlot();
      this.targetEntityId = -1;
      this.targetRefreshTicks = 0;
      this.vectorRefreshTicks = 0;
      this.fireworkCooldown = 0;
      this.phaseTicks = 0;
      this.failedDiveAttempts = 0;
      this.ascentTargetY = Double.NaN;
      this.lastVelocityY = player.getVelocity().y;
      MaceSwapState.setAutomationSuppressed(true);
   }

   private void stop(MinecraftClient client, boolean restoreSlot) {
      MaceSwapState.setAutomationSuppressed(false);
      if (restoreSlot && client.player != null && this.previousSelectedSlot >= 0) {
         MaceSwapState.setSelectedHotbarSlot(client, client.player, this.previousSelectedSlot);
      }

      this.phase = Phase.IDLE;
      this.active = false;
      this.targetEntityId = -1;
      this.targetRefreshTicks = 0;
      this.vectorRefreshTicks = 0;
      this.fireworkCooldown = 0;
      this.phaseTicks = 0;
      this.failedDiveAttempts = 0;
      this.ascentTargetY = Double.NaN;
      this.previousSelectedSlot = -1;
   }

   private void tickSearching(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (target == null) {
         this.stop(client, true);
         return;
      }

      if (player.isOnGround()) {
         if (this.phaseTicks > 0) {
            return;
         }

         this.tryLaunchFromGround(client, player, target);
         return;
      }

      this.phase = Phase.BOOSTING;
      this.vectorRefreshTicks = 0;
   }

   private void tickLaunching(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (target == null) {
         this.phase = Phase.SEARCHING;
         return;
      }

      this.prepareForFlight(client, player);
      this.facePosition(player, new Vec3d(player.getX(), player.getY() + SAFETY_ASCENT_HEIGHT, player.getZ()), ASCENT_PITCH);
      if (this.phaseTicks > 0) {
         return;
      }

      if (player.isOnGround()) {
         this.phase = Phase.SEARCHING;
         this.phaseTicks = MultitoolsSettings.diveGroundRelaunchDelayTicks;
         return;
      }

      this.phase = Phase.BOOSTING;
      this.vectorRefreshTicks = 0;
   }

   private void tickBoosting(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (target == null) {
         this.phase = Phase.SEARCHING;
         return;
      }

      if (player.isOnGround()) {
         if (this.phaseTicks > 0) {
            return;
         }

         this.tryLaunchFromGround(client, player, target);
         return;
      }

      this.prepareForFlight(client, player);
      if (this.vectorRefreshTicks <= 0) {
         this.vectorRefreshTicks = MultitoolsSettings.diveVectorRefreshTicks;
         this.faceForApproach(player, target);
      }

      this.tryUseFirework(client, player, target);
      if (player.squaredDistanceTo(target) <= BOOST_TO_DIVE_RANGE * BOOST_TO_DIVE_RANGE) {
         if (player.getY() <= target.getY() + MIN_DIVE_HEIGHT_ABOVE_TARGET) {
            this.facePosition(player, new Vec3d(target.getX(), target.getY() + MIN_DIVE_HEIGHT_ABOVE_TARGET + 2.0D, target.getZ()), ASCENT_PITCH);
            this.tryUseFirework(client, player, target);
            return;
         }

         this.clientHooks.equipChestplateOnLanding(client);
         this.phase = Phase.PREPARE_DIVE;
         this.phaseTicks = 1;
      }
   }

   private void tickPrepareDive(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (target == null) {
         this.phase = Phase.SEARCHING;
         return;
      }

      if (player.isOnGround()) {
         this.phase = Phase.SEARCHING;
         this.phaseTicks = MultitoolsSettings.diveGroundRelaunchDelayTicks;
         return;
      }

      this.faceForStrike(player, target);
      if (this.phaseTicks > 0) {
         return;
      }

      this.phase = Phase.DIVING;
      this.vectorRefreshTicks = 0;
   }

   private void tickDiving(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (target == null) {
         this.recoverToBoosting(client, player, true);
         return;
      }

      if (player.isOnGround()) {
         this.phase = Phase.SEARCHING;
         this.phaseTicks = MultitoolsSettings.diveGroundRelaunchDelayTicks;
         return;
      }

      if (this.vectorRefreshTicks <= 0) {
         this.vectorRefreshTicks = MultitoolsSettings.diveVectorRefreshTicks;
         this.faceForStrike(player, target);
      }

      double distance = Math.sqrt(player.squaredDistanceTo(target));
      if (distance < STRIKE_RANGE_MIN) {
         this.recoverToBoosting(client, player, true);
         return;
      }

      if (this.canStrikeNow(player, target, distance)) {
         this.phase = Phase.STRIKE;
      }
   }

   private void tickStrike(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (target == null) {
         this.recoverToBoosting(client, player, true);
         return;
      }

      if (player.isOnGround()) {
         this.phase = Phase.SEARCHING;
         this.phaseTicks = MultitoolsSettings.diveGroundRelaunchDelayTicks;
         return;
      }

      if (target.hurtTime > 0 || player.getAttackCooldownProgress(0.0F) < 0.92F || player.getVelocity().y >= -0.05D || player.fallDistance <= 3.0F) {
         this.phase = Phase.DIVING;
         this.vectorRefreshTicks = 0;
         return;
      }

      this.faceForStrike(player, target);
      if (!MaceSwapState.performDiveAttack(client, player, target, this.previousSelectedSlot)) {
         this.recoverToBoosting(client, player, true);
         return;
      }

      this.failedDiveAttempts = 0;
      this.phase = Phase.POST_STRIKE;
      this.phaseTicks = MultitoolsSettings.divePostStrikeRecoveryTicks;
      this.clientHooks.equipElytraFromHotbar(client);
      this.clientHooks.startFallFlyingIfPossible(player);
      this.tryImmediateReboost(client, player);
   }

   private void tickPostStrike(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (target == null) {
         this.phase = Phase.SEARCHING;
         this.targetRefreshTicks = 0;
         return;
      }

      if (player.isOnGround()) {
         if (this.phaseTicks > 0) {
            return;
         }

         this.phase = Phase.SEARCHING;
         this.phaseTicks = MultitoolsSettings.diveGroundRelaunchDelayTicks;
         return;
      }

      this.prepareForFlight(client, player);
      if (player.getVelocity().y - this.lastVelocityY >= SELF_WIND_BURST_DELTA_Y) {
         this.phase = Phase.BOOSTING;
         this.vectorRefreshTicks = 0;
         return;
      }

      if (this.isTargetAirborne(target) && target.getVelocity().y > TARGET_RISING_THRESHOLD) {
         this.phase = Phase.WAITING_FOR_TARGET_FALL;
         return;
      }

      if (this.phaseTicks <= 0) {
         this.phase = Phase.BOOSTING;
         this.vectorRefreshTicks = 0;
      }
   }

   private void tickWaitingForTargetFall(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (target == null) {
         this.phase = Phase.SEARCHING;
         this.targetRefreshTicks = 0;
         return;
      }

      if (player.isOnGround()) {
         if (this.phaseTicks > 0) {
            return;
         }

         this.phase = Phase.SEARCHING;
         this.phaseTicks = MultitoolsSettings.diveGroundRelaunchDelayTicks;
         return;
      }

      this.prepareForFlight(client, player);
      if (target.isOnGround() || target.getVelocity().y <= TARGET_FALLING_THRESHOLD) {
         this.phase = Phase.BOOSTING;
         this.vectorRefreshTicks = 0;
      }
   }

   private void prepareForFlight(MinecraftClient client, ClientPlayerEntity player) {
      if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && this.clientHooks.canAutoEquipElytra(player)) {
         this.clientHooks.equipElytraFromHotbar(client);
      }

      this.clientHooks.startFallFlyingIfPossible(player);
   }

   private void tryUseFirework(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (!player.isGliding() || this.fireworkCooldown > 0) {
         return;
      }

      double distanceSq = player.squaredDistanceTo(target);
      double horizontalSpeedSq = player.getVelocity().horizontalLengthSquared();
      if (horizontalSpeedSq > 1.8D && distanceSq < 256.0D && player.getVelocity().y > -0.22D) {
         return;
      }

      this.clientHooks.useFirework(client);
      this.fireworkCooldown = MultitoolsSettings.diveFireworkReuseTicks;
   }

   private void tryImmediateReboost(MinecraftClient client, ClientPlayerEntity player) {
      if (player.isOnGround()) {
         return;
      }

      this.facePosition(player, player.getEyePos().add(0.0D, 12.0D, 0.0D), REBOOST_PITCH);
      if (player.isGliding() && this.fireworkCooldown <= 0) {
         this.clientHooks.useFirework(client);
         this.fireworkCooldown = MultitoolsSettings.diveFireworkReuseTicks;
      }
   }

   private void recoverToBoosting(MinecraftClient client, ClientPlayerEntity player, boolean countFailure) {
      if (countFailure) {
         ++this.failedDiveAttempts;
         if (this.failedDiveAttempts >= RETARGET_AFTER_FAILED_DIVES) {
            this.targetEntityId = -1;
            this.targetRefreshTicks = 0;
            this.failedDiveAttempts = 0;
         }
      }

      this.clientHooks.equipElytraFromHotbar(client);
      this.clientHooks.startFallFlyingIfPossible(player);
      this.phase = player.isOnGround() ? Phase.SEARCHING : Phase.BOOSTING;
      this.vectorRefreshTicks = 0;
   }

   private void faceForApproach(ClientPlayerEntity player, LivingEntity target) {
      if (MultitoolsSettings.diveComboAvoidObstacles && this.hasObstacleAhead(player, target)) {
         this.ascentTargetY = Math.max(target.getY() + 1.5D, player.getY() + SAFETY_ASCENT_HEIGHT);
      } else if (Double.isFinite(this.ascentTargetY) && player.getY() + 0.75D >= this.ascentTargetY) {
         this.ascentTargetY = Double.NaN;
      }

      if (Double.isFinite(this.ascentTargetY) && player.getY() + 0.75D < this.ascentTargetY) {
         this.facePosition(player, new Vec3d(target.getX(), this.ascentTargetY, target.getZ()), ASCENT_PITCH);
         return;
      }

      this.facePosition(player, this.getPredictedApproachPos(target), null);
   }

   private void faceForStrike(ClientPlayerEntity player, LivingEntity target) {
      this.facePosition(player, this.getPredictedStrikePos(target), null);
   }

   private void facePosition(ClientPlayerEntity player, Vec3d targetPos, Float fixedPitch) {
      Vec3d delta = targetPos.subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float yaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
      float pitch = fixedPitch != null
         ? fixedPitch
         : (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      player.setYaw(yaw);
      player.setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
   }

   private boolean hasObstacleAhead(ClientPlayerEntity player, LivingEntity target) {
      Vec3d start = player.getEyePos();
      Vec3d toTarget = target.getBoundingBox().getCenter().subtract(start);
      double length = toTarget.length();
      if (length < 1.0E-6D) {
         return false;
      }

      Vec3d end = start.add(toTarget.normalize().multiply(Math.min(OBSTACLE_LOOKAHEAD, length)));
      BlockHitResult hit = player.getEntityWorld().raycast(new RaycastContext(
         start,
         end,
         RaycastContext.ShapeType.COLLIDER,
         RaycastContext.FluidHandling.NONE,
         player
      ));
      return hit.getType() == HitResult.Type.BLOCK;
   }

   private Vec3d getPredictedApproachPos(LivingEntity target) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      Vec3d center = target.getBoundingBox().getCenter();
      if (player == null) {
         return center.add(0.0D, this.isTargetAirborne(target) ? 0.4D : 5.0D, 0.0D);
      }

      double predictionTicks = this.computePredictionTicks(player, target, 2.0D, 7.0D);
      Vec3d velocity = target.getVelocity();
      double heightOffset = this.isTargetAirborne(target) ? 0.5D : 4.5D;
      return center.add(velocity.multiply(predictionTicks)).add(0.0D, heightOffset, 0.0D);
   }

   private Vec3d getPredictedStrikePos(LivingEntity target) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      Vec3d center = target.getBoundingBox().getCenter();
      if (player == null) {
         return center;
      }

      double predictionTicks = this.computePredictionTicks(player, target, 0.35D, 2.25D);
      return center.add(target.getVelocity().multiply(predictionTicks));
   }

   private boolean tryLaunchFromGround(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (this.phaseTicks > 0) {
         return false;
      }

      this.ascentTargetY = Math.max(target.getY() + 1.5D, player.getY() + SAFETY_ASCENT_HEIGHT);
      if (!this.clientHooks.useWindChargeAtPitch(client, player, SELF_BOOST_PITCH)) {
         this.phase = Phase.SEARCHING;
         return false;
      }

      this.phase = Phase.LAUNCHING;
      this.phaseTicks = MultitoolsSettings.diveLaunchRecoveryTicks;
      return true;
   }

   private LivingEntity getTrackedTarget(MinecraftClient client) {
      Entity entity = this.targetEntityId < 0 ? null : client.world.getEntityById(this.targetEntityId);
      return entity instanceof LivingEntity living && living.isAlive() && canTrackTarget(client.player, living) ? living : null;
   }

   private LivingEntity acquireTarget(MinecraftClient client) {
      ClientPlayerEntity player = client.player;
      Box searchBox = player.getBoundingBox().expand(TARGET_RANGE, TARGET_RANGE, TARGET_RANGE);
      LivingEntity bestTarget = null;
      double bestScore = Double.MAX_VALUE;

      for (Entity entity : client.world.getOtherEntities(player, searchBox, candidate -> canTrackTarget(player, candidate))) {
         if (!(entity instanceof LivingEntity living)) {
            continue;
         }

         double score = this.computeTargetScore(player, living);
         if (score < bestScore) {
            bestScore = score;
            bestTarget = living;
         }
      }

      this.targetEntityId = bestTarget == null ? -1 : bestTarget.getId();
      this.targetRefreshTicks = MultitoolsSettings.diveTargetRefreshTicks;
      this.failedDiveAttempts = 0;
      return bestTarget;
   }

   private double computeTargetScore(ClientPlayerEntity player, LivingEntity target) {
      double score = player.squaredDistanceTo(target);
      if (target instanceof PlayerEntity targetPlayer) {
         score -= targetPlayer.getHealth() < 6.0F ? 5000.0D : 2500.0D;
      } else if (!(target instanceof MobEntity)) {
         score += 500.0D;
      }

      if (this.isTargetAirborne(target)) {
         score -= 50.0D;
      }

      return score;
   }

   private boolean isTargetAirborne(LivingEntity target) {
      if (target instanceof PlayerEntity player) {
         ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
         if (player.isGliding() || (!player.isOnGround() && chest.isOf(Items.ELYTRA))) {
            return true;
         }
      }

      return !target.isOnGround() || Math.abs(target.getVelocity().y) > 0.08D;
   }

   private static boolean canTrackTarget(ClientPlayerEntity player, Entity entity) {
      if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity == player) {
         return false;
      }

      double deltaX = Math.abs(entity.getX() - player.getX());
      double deltaY = Math.abs(entity.getY() - player.getY());
      double deltaZ = Math.abs(entity.getZ() - player.getZ());
      return deltaX <= TARGET_RANGE && deltaY <= TARGET_RANGE && deltaZ <= TARGET_RANGE;
   }

   private boolean canStrikeNow(ClientPlayerEntity player, LivingEntity target, double distance) {
      if (distance > STRIKE_RANGE_MAX || distance < STRIKE_RANGE_MIN) {
         return false;
      }

      if (player.getVelocity().y >= -0.08D || player.fallDistance <= 3.0F || player.getY() <= target.getY() + 2.5D) {
         return false;
      }

      Vec3d eyePos = player.getEyePos();
      Vec3d predictedTarget = this.getPredictedStrikePos(target);
      Vec3d toTarget = predictedTarget.subtract(eyePos);
      double targetDistance = toTarget.length();
      if (targetDistance < 1.0E-6D) {
         return false;
      }

      Vec3d look = player.getRotationVec(1.0F).normalize();
      Vec3d direction = toTarget.multiply(1.0D / targetDistance);
      double alignment = look.dotProduct(direction);
      if (alignment < STRIKE_ALIGNMENT_MIN) {
         return false;
      }

      double forward = toTarget.dotProduct(look);
      double lateralSq = Math.max(0.0D, toTarget.lengthSquared() - forward * forward);
      if (lateralSq > STRIKE_MAX_LATERAL_OFFSET * STRIKE_MAX_LATERAL_OFFSET) {
         return false;
      }

      Vec3d relativeVelocity = player.getVelocity().subtract(target.getVelocity());
      double closingSpeed = relativeVelocity.dotProduct(direction);
      return closingSpeed >= STRIKE_MIN_CLOSING_SPEED;
   }

   private double computePredictionTicks(ClientPlayerEntity player, LivingEntity target, double minTicks, double maxTicks) {
      Vec3d toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePos());
      double distance = toTarget.length();
      if (distance < 1.0E-6D) {
         return minTicks;
      }

      Vec3d relativeVelocity = player.getVelocity().subtract(target.getVelocity());
      double closingSpeed = Math.max(0.35D, relativeVelocity.length());
      return MathHelper.clamp(distance / closingSpeed, minTicks, maxTicks);
   }

   private enum Phase {
      IDLE,
      SEARCHING,
      LAUNCHING,
      BOOSTING,
      PREPARE_DIVE,
      DIVING,
      STRIKE,
      POST_STRIKE,
      WAITING_FOR_TARGET_FALL
   }
}
