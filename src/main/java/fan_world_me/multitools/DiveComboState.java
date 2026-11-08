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
   // С какого расстояния (между сущностями) снимаем элитры и падаем на цель. Меньше = меньше времени, чтобы цель ушла.
   private static final double BOOST_TO_DIVE_RANGE = 11.0D;
   private static final double MIN_DIVE_HEIGHT_ABOVE_TARGET = 6.0D;
   // Бьём, как только расстояние от глаз до хитбокса цели <= этого значения. Подгони под reach своего сервера.
   private static final double STRIKE_REACH = 3.8D;
   private static final int MAX_DIVE_TICKS = 80;
   private static final double OBSTACLE_LOOKAHEAD = 5.0D;
   private static final double SAFETY_ASCENT_HEIGHT = 7.0D;
   private static final double SELF_WIND_BURST_DELTA_Y = 0.42D;
   private static final double TARGET_FALLING_THRESHOLD = -0.08D;
   private static final double TARGET_RISING_THRESHOLD = 0.18D;
   private static final int RETARGET_AFTER_FAILED_DIVES = 2;
   // Бонус текущей цели в оценке: не даёт прицелу прыгать между целями каждые N тиков.
   private static final double TARGET_STICKY_BONUS = 400.0D;
   private static final float SELF_BOOST_PITCH = 88.0F;
   private static final float REBOOST_PITCH = -80.0F;
   private static final float ASCENT_PITCH = -68.0F;
   // Максимальный поворот камеры за тик (плавный прицел вместо резкого setYaw/setPitch).
   private static final float MAX_YAW_STEP = 28.0F;
   private static final float MAX_PITCH_STEP = 22.0F;
   // Лимит ракет на один заход; сбрасывается после успешного удара и при новом старте с земли.
   private static final int MAX_FIREWORKS_PER_APPROACH = 5;
   // Выше этой скорости (блоков/тик, в квадрате) ракету не используем.
   private static final double MAX_FIREWORK_SPEED_SQ = 2.8D;
   // Ракету подаём, только если смотрим на цель в этом конусе (cos угла).
   private static final double FIREWORK_MIN_ALIGNMENT = 0.8D;
   // При наборе высоты ракета имеет смысл, только если смотрим достаточно вверх.
   private static final float ASCENT_FIREWORK_MAX_PITCH = -35.0F;

   private final MultitoolsClient clientHooks;

   private Phase phase = Phase.IDLE;
   private int previousSelectedSlot = -1;
   private int targetEntityId = -1;
   private int targetRefreshTicks = 0;
   private int fireworkCooldown = 0;
   private int fireworksThisApproach = 0;
   private int phaseTicks = 0;
   private int diveTicks = 0;
   private int failedDiveAttempts = 0;
   private double ascentTargetY = Double.NaN;
   private double lastVelocityY = 0.0D;
   private boolean holdsSuppression = false;
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
      this.fireworkCooldown = 0;
      this.fireworksThisApproach = 0;
      this.phaseTicks = 0;
      this.diveTicks = 0;
      this.failedDiveAttempts = 0;
      this.ascentTargetY = Double.NaN;
      this.lastVelocityY = player.getVelocity().y;
      if (!this.holdsSuppression) {
         MaceSwapState.acquireSuppression();
         this.holdsSuppression = true;
      }
   }

   private void stop(MinecraftClient client, boolean restoreSlot) {
      if (this.holdsSuppression) {
         MaceSwapState.releaseSuppression();
         this.holdsSuppression = false;
      }

      if (restoreSlot && client.player != null && this.previousSelectedSlot >= 0) {
         MaceSwapState.setSelectedHotbarSlot(client, client.player, this.previousSelectedSlot);
      }

      this.phase = Phase.IDLE;
      this.active = false;
      this.targetEntityId = -1;
      this.targetRefreshTicks = 0;
      this.fireworkCooldown = 0;
      this.fireworksThisApproach = 0;
      this.phaseTicks = 0;
      this.diveTicks = 0;
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

      boolean close = player.squaredDistanceTo(target) <= BOOST_TO_DIVE_RANGE * BOOST_TO_DIVE_RANGE;
      if (close && player.getY() <= target.getY() + MIN_DIVE_HEIGHT_ABOVE_TARGET) {
         // Рядом, но ниже цели: сначала набираем высоту.
         this.facePosition(player, new Vec3d(target.getX(), target.getY() + MIN_DIVE_HEIGHT_ABOVE_TARGET + 2.0D, target.getZ()), ASCENT_PITCH);
         this.tryUseAscentFirework(client, player);
         return;
      }

      if (close) {
         this.clientHooks.equipChestplateOnLanding(client);
         this.phase = Phase.PREPARE_DIVE;
         this.phaseTicks = 1;
         this.diveTicks = 0;
         return;
      }

      this.faceForApproach(player, target);
      this.tryUseFirework(client, player, target);
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
      this.diveTicks = 0;
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

      ++this.diveTicks;
      if (this.diveTicks > MAX_DIVE_TICKS) {
         this.recoverToBoosting(client, player, true);
         return;
      }

      this.faceForStrike(player, target);

      // Бьём, как только цель в пределах reach — на любой скорости. Раньше окно было 4.8–6.2 блока,
      // и на скорости пикирования его перепрыгивали за один тик (или улетали мимо со счётом "промах").
      double reach = this.distanceToHitbox(player, target);
      if (this.canStrikeNow(player, target, reach)) {
         this.phase = Phase.STRIKE;
         this.tickStrike(client, player, target);
         return;
      }

      if (this.hasMissedTarget(player, target, reach)) {
         this.recoverToBoosting(client, player, true);
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
         return;
      }

      this.faceForStrike(player, target);
      if (!MaceSwapState.performDiveAttack(client, player, target, this.previousSelectedSlot)) {
         this.recoverToBoosting(client, player, true);
         return;
      }

      this.failedDiveAttempts = 0;
      this.fireworksThisApproach = 0;
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
         return;
      }

      if (this.isTargetAirborne(target) && target.getVelocity().y > TARGET_RISING_THRESHOLD) {
         this.phase = Phase.WAITING_FOR_TARGET_FALL;
         return;
      }

      if (this.phaseTicks <= 0) {
         this.phase = Phase.BOOSTING;
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
      }
   }

   private void prepareForFlight(MinecraftClient client, ClientPlayerEntity player) {
      if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && this.clientHooks.canAutoEquipElytra(player)) {
         this.clientHooks.equipElytraFromHotbar(client);
      }

      this.clientHooks.startFallFlyingIfPossible(player);
   }

   private boolean canUseFirework(ClientPlayerEntity player) {
      return player.isGliding()
         && this.fireworkCooldown <= 0
         && this.fireworksThisApproach < MAX_FIREWORKS_PER_APPROACH;
   }

   private void fireRocket(MinecraftClient client) {
      this.clientHooks.useFirework(client);
      this.fireworkCooldown = Math.max(1, MultitoolsSettings.diveFireworkReuseTicks);
      ++this.fireworksThisApproach;
   }

   /** Ракета при подлёте к цели: только если уже не слишком быстро и мы смотрим на цель. */
   private void tryUseFirework(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
      if (!this.canUseFirework(player)) {
         return;
      }

      Vec3d velocity = player.getVelocity();
      if (velocity.lengthSquared() > MAX_FIREWORK_SPEED_SQ) {
         return;
      }

      double distanceSq = player.squaredDistanceTo(target);
      if (velocity.horizontalLengthSquared() > 1.8D && distanceSq < 256.0D && velocity.y > -0.22D) {
         return;
      }

      Vec3d toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePos());
      double length = toTarget.length();
      if (length > 1.0E-6D && player.getRotationVec(1.0F).dotProduct(toTarget.multiply(1.0D / length)) < FIREWORK_MIN_ALIGNMENT) {
         return;
      }

      this.fireRocket(client);
   }

   /** Ракета при наборе высоты над целью: цель не по курсу, поэтому смотрим только на то, что мы летим вверх. */
   private void tryUseAscentFirework(MinecraftClient client, ClientPlayerEntity player) {
      if (!this.canUseFirework(player)) {
         return;
      }

      if (player.getVelocity().lengthSquared() > MAX_FIREWORK_SPEED_SQ || player.getPitch() > ASCENT_FIREWORK_MAX_PITCH) {
         return;
      }

      this.fireRocket(client);
   }

   private void tryImmediateReboost(MinecraftClient client, ClientPlayerEntity player) {
      if (player.isOnGround()) {
         return;
      }

      // Здесь разворот мгновенный: ракета летит по текущему взгляду, а после удара он направлен вниз.
      this.snapFacePosition(player, player.getEyePos().add(0.0D, 12.0D, 0.0D), REBOOST_PITCH);
      if (this.canUseFirework(player)) {
         this.fireRocket(client);
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

      this.diveTicks = 0;
      this.clientHooks.equipElytraFromHotbar(client);
      this.clientHooks.startFallFlyingIfPossible(player);
      this.phase = player.isOnGround() ? Phase.SEARCHING : Phase.BOOSTING;
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

   /** Плавный поворот к точке: не больше MAX_YAW_STEP / MAX_PITCH_STEP градусов за тик. */
   private void facePosition(ClientPlayerEntity player, Vec3d targetPos, Float fixedPitch) {
      this.aimAt(player, targetPos, fixedPitch, MAX_YAW_STEP, MAX_PITCH_STEP);
   }

   /** Мгновенный поворот (нужен только там, где сразу после поворота используется предмет). */
   private void snapFacePosition(ClientPlayerEntity player, Vec3d targetPos, Float fixedPitch) {
      this.aimAt(player, targetPos, fixedPitch, 360.0F, 180.0F);
   }

   private void aimAt(ClientPlayerEntity player, Vec3d targetPos, Float fixedPitch, float maxYawStep, float maxPitchStep) {
      Vec3d delta = targetPos.subtract(player.getEyePos());
      double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
      float desiredYaw = horizontal > 1.0E-4D
         ? (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D)
         : player.getYaw();
      float desiredPitch = fixedPitch != null
         ? fixedPitch
         : (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
      desiredPitch = MathHelper.clamp(desiredPitch, -90.0F, 90.0F);

      float yawDelta = MathHelper.wrapDegrees(desiredYaw - player.getYaw());
      float pitchDelta = desiredPitch - player.getPitch();
      player.setYaw(player.getYaw() + MathHelper.clamp(yawDelta, -maxYawStep, maxYawStep));
      player.setPitch(MathHelper.clamp(player.getPitch() + MathHelper.clamp(pitchDelta, -maxPitchStep, maxPitchStep), -90.0F, 90.0F));
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

      this.fireworksThisApproach = 0;
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
      int previousTargetId = this.targetEntityId;

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
      // Счётчик промахов сбрасываем только при реальной смене цели: раньше он обнулялся на каждом
      // переоценивании, и "смена цели после 2 промахов" не срабатывала.
      if (this.targetEntityId != previousTargetId) {
         this.failedDiveAttempts = 0;
      }
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

      if (target.getId() == this.targetEntityId) {
         score -= TARGET_STICKY_BONUS;
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

   /** Расстояние от глаз игрока до ближайшей точки хитбокса цели (то, что проверяет сервер при ударе). */
   private double distanceToHitbox(ClientPlayerEntity player, LivingEntity target) {
      Box box = target.getBoundingBox();
      Vec3d eye = player.getEyePos();
      double dx = eye.x - MathHelper.clamp(eye.x, box.minX, box.maxX);
      double dy = eye.y - MathHelper.clamp(eye.y, box.minY, box.maxY);
      double dz = eye.z - MathHelper.clamp(eye.z, box.minZ, box.maxZ);
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private boolean canStrikeNow(ClientPlayerEntity player, LivingEntity target, double reach) {
      if (reach > STRIKE_REACH) {
         return false;
      }

      return player.getVelocity().y < -0.08D && player.fallDistance > 3.0F && player.getY() > target.getY() + 1.0D;
   }

   /** Цель упущена: мы уже ниже неё или явно удаляемся от неё, при этом достать не можем. */
   private boolean hasMissedTarget(ClientPlayerEntity player, LivingEntity target, double reach) {
      if (reach <= STRIKE_REACH) {
         return false;
      }

      if (player.getY() < target.getY() - 0.5D) {
         return true;
      }

      Vec3d toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePos());
      boolean movingAway = player.getVelocity().dotProduct(toTarget) <= 0.0D;
      return movingAway && reach > STRIKE_REACH * 2.5D;
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
