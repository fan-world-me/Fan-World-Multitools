package fan_world_me.multitoolshelp;

import net.minecraft.entity.TntEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayDeque;

/**
 * Оптимизированный Nuke.
 *
 * Оригинал спавнил 700+ TNT сразу → сервер умирал.
 *
 * Оптимизации:
 * 1. Все TNT добавляются в очередь и спавнятся батчами по 40 штук/тик
 *    через StrikeScheduler — никакого new Timer(), никаких лагов.
 * 2. Равномерное распределение по кольцу (2π/N*i вместо Math.random())
 *    — нет кластеризации, взрыв симметричный.
 * 3. fuse = 80 как в оригинале (TNT должен долететь до земли по дуге).
 * 4. Центральный TNT + кольца — логика оригинала сохранена, просто без лагов.
 */
public class NukeLogic {

    private static final int[] BASE_RADII = {12, 22, 32, 42, 52, 62, 72, 82, 92, 102};
    private static final double GRAVITY = -0.03;   // как в оригинале
    /** Как в оригинале: TNT летит по дуге и падает на землю ~80 тиков. fuse=1 взрывал всё в воздухе. */
    private static final int FUSE = 80;

    /** Сколько TNT спавнится за тик. Меньше = плавнее (взрывы растягиваются во времени), но дольше "волна". */
    private static final int NUKE_BATCH_PER_TICK = 20;

    /**
     * Желаемое расстояние между соседними TNT на кольце (блоков).
     * Взрыв TNT ~5 блоков радиусом, так что 6 почти не оставляет дыр, а внутренние кольца
     * в оригинале были забиты TNT на одно и то же место (84 штуки на кольцо длиной 75 блоков).
     * Больше число = меньше TNT = меньше лагов, но между воронками могут остаться перемычки.
     */
    private static final double TNT_SPACING = 6.0;
    private static final double VELOCITY_MULTIPLIER = 1.4;

    public static void summon(ServerWorld world, int centerX, int centerY, int centerZ, int numRings) {
        // Собираем ВСЕ TNT в очередь — спавним батчами через планировщик
        ArrayDeque<Runnable> queue = new ArrayDeque<>();

        // Центральная TNT
        queue.add(() -> spawnTnt(world, centerX + 0.5, centerY, centerZ + 0.5, 0, GRAVITY, 0));

        // Строим массив радиусов
        int[] radii = buildRadii(numRings);

        for (int ringIndex = 0; ringIndex < radii.length; ringIndex++) {
            int r = radii[ringIndex];
            int count = tntsPerRing(ringIndex, r);

            for (int i = 0; i < count; i++) {
                // Равномерное распределение по кольцу — никакого random()
                double angle = 2.0 * Math.PI * i / count + ringIndex * 0.37; // сдвиг кольца, чтобы лучи не совпадали
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                double vx = dx * ((double) r / FUSE) * VELOCITY_MULTIPLIER;
                double vz = dz * ((double) r / FUSE) * VELOCITY_MULTIPLIER;

                final double fvx = vx;
                final double fvz = vz;
                queue.add(() -> spawnTnt(world, centerX + 0.5, centerY, centerZ + 0.5, fvx, GRAVITY, fvz));
            }
        }

        // Батчами по 40 штук/тик, стартуем сразу
        StrikeScheduler.scheduleBatch(0, queue, NUKE_BATCH_PER_TICK);
    }

    private static void spawnTnt(ServerWorld world, double x, double y, double z,
                                  double vx, double vy, double vz) {
        TntEntity tnt = new TntEntity(world, x, y, z, null);
        tnt.setFuse(FUSE);
        tnt.setVelocity(vx, vy, vz);
        world.spawnEntity(tnt);
    }

    private static int[] buildRadii(int numRings) {
        if (numRings <= BASE_RADII.length) {
            int[] result = new int[numRings];
            System.arraycopy(BASE_RADII, 0, result, 0, numRings);
            return result;
        }
        int[] result = new int[numRings];
        System.arraycopy(BASE_RADII, 0, result, 0, BASE_RADII.length);
        for (int i = BASE_RADII.length; i < numRings; i++) {
            result[i] = 98 + (i - BASE_RADII.length + 1) * 10;
        }
        return result;
    }

    private static int tntsPerRing(int ringIndex, int r) {
        int original;
        if (ringIndex == 0) original = 84;
        else if (ringIndex == 1) original = 50;
        else if (ringIndex <= 5) original = 70;
        else if (ringIndex == 6) original = 90;
        else original = Math.min(100, Math.round(r * 2 - ringIndex * 1.2f));

        // не больше, чем реально нужно для покрытия кольца взрывами
        int needed = (int) Math.ceil(2.0 * Math.PI * r / TNT_SPACING);
        return Math.max(8, Math.min(original, needed));
    }
}
