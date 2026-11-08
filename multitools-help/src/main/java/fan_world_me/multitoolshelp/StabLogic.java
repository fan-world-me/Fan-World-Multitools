package fan_world_me.multitoolshelp;

import net.minecraft.entity.TntEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;

/**
 * Оптимизированный Stab.
 *
 * Оригинал итерировал весь вертикальный столб (до 384 блоков) и спавнил
 * 2 TNT на каждый непустой блок прямо в событии — сервер фризил.
 *
 * Оптимизации:
 * 1. Список позиций собирается заранее в очередь.
 * 2. Спавн батчами по 40 TNT/тик через StrikeScheduler.
 * 3. fuse = 1 тик → мгновенный взрыв без ожидания.
 * 4. 2 TNT на блок сохранено как в оригинале.
 */
public class StabLogic {

    public static void summon(ServerWorld world, int centerX, int centerZ) {
        int minY = world.getBottomY();
        int maxY = minY + world.getHeight() - 1;

        ArrayDeque<Runnable> queue = new ArrayDeque<>();

        // Сканируем столб сверху вниз, собираем позиции непустых блоков
        for (int y = maxY; y >= minY; y--) {
            BlockPos pos = new BlockPos(centerX, y, centerZ);
            if (!world.getBlockState(pos).isAir()) {
                final double spawnY = y;
                // 2 TNT на блок как в оригинале
                queue.add(() -> spawnTnt(world, centerX + 0.5, spawnY, centerZ + 0.5));
                queue.add(() -> spawnTnt(world, centerX + 0.5, spawnY, centerZ + 0.5));
            }
        }

        StrikeScheduler.scheduleBatch(0, queue, StrikeScheduler.TNT_BATCH_PER_TICK);
    }

    private static void spawnTnt(ServerWorld world, double x, double y, double z) {
        TntEntity tnt = new TntEntity(world, x, y, z, null);
        tnt.setFuse(1);
        world.spawnEntity(tnt);
    }
}
