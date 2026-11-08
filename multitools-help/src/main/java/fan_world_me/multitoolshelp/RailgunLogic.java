package fan_world_me.multitoolshelp;

import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Random;

public class RailgunLogic {

    private static final int    ARROW_COUNT = 45;
    private static final double VELOCITY    = 1500.0;
    private static final Random RANDOM      = new Random();

    public static void fire(ServerPlayerEntity player, Vec3d target, double spreadRadius) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d eye       = player.getEyePos();
        Vec3d direction = target.subtract(eye).normalize();
        ItemStack arrowStack = new ItemStack(Items.ARROW);

        ArrayDeque<Runnable> queue = new ArrayDeque<>();

        for (int i = 0; i < ARROW_COUNT; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * spreadRadius * 2.0;
            double oy = (RANDOM.nextDouble() - 0.5) * spreadRadius * 2.0;
            double oz = (RANDOM.nextDouble() - 0.5) * spreadRadius * 2.0;
            Vec3d spawn = eye.add(direction.multiply(3.0)).add(ox, oy, oz);

            queue.add(() -> {
                ArrowEntity arrow = new ArrowEntity(
                    world,
                    spawn.x, spawn.y, spawn.z,
                    arrowStack,
                    arrowStack
                );
                arrow.setOwner(player);
                arrow.setVelocity(direction.multiply(VELOCITY));
                arrow.setCritical(true);
                arrow.setNoGravity(true);
                arrow.setDamage(Float.MAX_VALUE / 2.0);
                // setPierceLevel private — обходим через reflection
                try {
                    java.lang.reflect.Method m = net.minecraft.entity.projectile.PersistentProjectileEntity.class
                        .getDeclaredMethod("setPierceLevel", byte.class);
                    m.setAccessible(true);
                    m.invoke(arrow, (byte) 127);
                } catch (Exception ignored) {}
                world.spawnEntity(arrow);
            });
        }

        StrikeScheduler.scheduleBatch(0, queue, StrikeScheduler.ARROW_BATCH_PER_TICK);
    }
}
