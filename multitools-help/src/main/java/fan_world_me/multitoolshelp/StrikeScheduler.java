package fan_world_me.multitoolshelp;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Центральный тик-планировщик.
 *
 * Вместо new Timer() / new Thread() все отложенные задачи регистрируются здесь
 * и выполняются строго в серверном тике — безопасно, без race condition,
 * без накладных расходов на создание потоков.
 *
 * Батчинг: задачи типа BATCH выполняются по N штук за тик, чтобы не спавнить
 * сотни сущностей одновременно.
 */
public class StrikeScheduler {

    /** Сколько TNT-сущностей спавним за один тик (батч для Nuke и Stab). */
    public static final int TNT_BATCH_PER_TICK = 40;

    /** Сколько стрел спавним за один тик (батч для Railgun). */
    public static final int ARROW_BATCH_PER_TICK = 45;

    private static final List<ScheduledTask> tasks = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(StrikeScheduler::onTick);
    }

    /**
     * Запланировать одиночный Runnable с задержкой в тиках.
     * @param delayTicks 0 = выполнить в следующем тике
     */
    public static void schedule(int delayTicks, Runnable action) {
        tasks.add(new SimpleTask(delayTicks, action));
    }

    /**
     * Запланировать батч-задачу: очередь Runnable выполняется по batchSize за тик
     * начиная с delayTicks.
     */
    public static void scheduleBatch(int delayTicks, ArrayDeque<Runnable> actions, int batchSize) {
        tasks.add(new BatchTask(delayTicks, actions, batchSize));
    }

    private static void onTick(MinecraftServer server) {
        if (tasks.isEmpty()) return;

        Iterator<ScheduledTask> it = tasks.iterator();
        while (it.hasNext()) {
            ScheduledTask task = it.next();
            task.tick();
            if (task.isDone()) {
                it.remove();
            }
        }
    }

    // ── internal types ────────────────────────────────────────────────────────

    private interface ScheduledTask {
        void tick();
        boolean isDone();
    }

    private static final class SimpleTask implements ScheduledTask {
        private int delay;
        private final Runnable action;
        private boolean done = false;

        SimpleTask(int delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }

        @Override
        public void tick() {
            if (done) return;
            if (delay > 0) { --delay; return; }
            try { action.run(); } catch (Exception e) { MultitoolsHelp.LOGGER.error("StrikeScheduler task error", e); }
            done = true;
        }

        @Override public boolean isDone() { return done; }
    }

    private static final class BatchTask implements ScheduledTask {
        private int delay;
        private final ArrayDeque<Runnable> queue;
        private final int batchSize;

        BatchTask(int delay, ArrayDeque<Runnable> queue, int batchSize) {
            this.delay = delay;
            this.queue = queue;
            this.batchSize = batchSize;
        }

        @Override
        public void tick() {
            if (delay > 0) { --delay; return; }
            int count = 0;
            while (!queue.isEmpty() && count < batchSize) {
                try { queue.poll().run(); } catch (Exception e) { MultitoolsHelp.LOGGER.error("StrikeScheduler batch error", e); }
                ++count;
            }
        }

        @Override public boolean isDone() { return delay <= 0 && queue.isEmpty(); }
    }
}
