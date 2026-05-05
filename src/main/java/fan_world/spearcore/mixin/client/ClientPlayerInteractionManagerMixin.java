package fan_world.spearcore.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fan_world.spearcore.MaceSwapState;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
   @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
   private void spearcore$interceptAttackEntity(net.minecraft.entity.player.PlayerEntity player, Entity target, CallbackInfo ci) {
      if (player instanceof ClientPlayerEntity clientPlayer && MaceSwapState.onInterceptAttack(MinecraftClient.getInstance(), clientPlayer, target)) {
         ci.cancel();
      }
   }

   @Inject(method = "attackEntity", at = @At("TAIL"))
   private void spearcore$postAttackEntity(net.minecraft.entity.player.PlayerEntity player, Entity target, CallbackInfo ci) {
      if (player instanceof ClientPlayerEntity clientPlayer) {
         MaceSwapState.onPostVanillaAttack(MinecraftClient.getInstance(), clientPlayer, target);
      }
   }
}
