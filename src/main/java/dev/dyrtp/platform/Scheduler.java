package dev.dyrtp.platform;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * One place for scheduling and teleporting. Folia runs work on the region or entity that owns it;
 * other servers use the main thread. Paper and Folia teleport asynchronously, Spigot synchronously.
 */
public class Scheduler {
    public static final boolean FOLIA = present("io.papermc.paper.threadedregions.RegionizedServer");
    private static final boolean ASYNC_TELEPORT = hasMethod(Entity.class, "teleportAsync", Location.class);
    private static final boolean ASYNC_CHUNKS = hasMethod(org.bukkit.World.class, "getChunkAtAsync", Location.class);

    /** A scheduled task that can be cancelled whichever scheduler started it. */
    public interface Task {
        void cancel();
    }

    private final Plugin plugin;

    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Runs on the thread that owns the entity; on Folia nothing runs if the entity is gone. */
    public Task runLater(Entity entity, Runnable task, long ticks) {
        if (FOLIA) return FoliaBridge.entity(plugin, entity, task, Math.max(1L, ticks));
        BukkitTask handle = plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(0L, ticks));
        return () -> { if (handle != null) handle.cancel(); };
    }

    public Task run(Entity entity, Runnable task) {
        return runLater(entity, task, 1L);
    }

    /** Runs now when already on the owning thread, otherwise as soon as possible on it: the entity's thread on Folia. */
    public void runFor(Entity entity, Runnable task) {
        if (FOLIA) FoliaBridge.entityNow(plugin, entity, task);
        else runMain(task);
    }

    /** Runs now when on the server thread, otherwise on the next tick; on Folia on the global region. */
    public void runMain(Runnable task) {
        if (FOLIA) FoliaBridge.global(plugin, task);
        else if (org.bukkit.Bukkit.isPrimaryThread()) task.run();
        else plugin.getServer().getScheduler().runTask(plugin, task);
    }

    /** Runs on the thread that owns the location; on other servers this is the server thread. */
    public void runAt(Location location, Runnable task) {
        if (FOLIA) FoliaBridge.region(plugin, location, task);
        else runMain(task);
    }

    /** Runs on the thread that owns the location; used to read blocks at a destination. */
    public <T> CompletableFuture<T> supplyAt(Location location, Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable body = () -> {
            try {
                result.complete(task.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        };
        if (FOLIA) FoliaBridge.region(plugin, location, body);
        else if (plugin.getServer().isPrimaryThread()) body.run();
        else plugin.getServer().getScheduler().runTask(plugin, body);
        return result;
    }

    public Task runGlobalTimer(Runnable task, long delay, long period) {
        if (FOLIA) return FoliaBridge.globalTimer(plugin, task, Math.max(1L, delay), Math.max(1L, period));
        BukkitTask handle = plugin.getServer().getScheduler().runTaskTimer(plugin, task, delay, period);
        return () -> { if (handle != null) handle.cancel(); };
    }

    public Task runGlobal(Runnable task) {
        if (FOLIA) return FoliaBridge.globalTimer(plugin, task, 1L, -1L);
        BukkitTask handle = plugin.getServer().getScheduler().runTask(plugin, task);
        return () -> { if (handle != null) handle.cancel(); };
    }

    public void runAsync(Runnable task) {
        if (FOLIA) FoliaBridge.async(plugin, task);
        else plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    /** Loads the destination chunk without blocking where the server can (Paper, Purpur, Folia). */
    public CompletableFuture<?> loadChunk(Location location) {
        if (ASYNC_CHUNKS && location.getWorld() != null) return PaperBridge.loadChunk(location);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> teleport(Player player, Location destination) {
        if (ASYNC_TELEPORT) return PaperBridge.teleport(player, destination);
        return CompletableFuture.completedFuture(player.teleport(destination, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN));
    }

    public void cancelAll() {
        if (FOLIA) FoliaBridge.cancelAll(plugin);
        else plugin.getServer().getScheduler().cancelTasks(plugin);
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, Scheduler.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    private static boolean hasMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            type.getMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException | LinkageError missing) {
            return false;
        }
    }

    /** Only loaded on Folia, so Spigot never links these classes. */
    private static final class FoliaBridge {
        static Task entity(Plugin plugin, Entity entity, Runnable task, long ticks) {
            var handle = entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, ticks);
            return () -> { if (handle != null) handle.cancel(); };
        }

        static void entityNow(Plugin plugin, Entity entity, Runnable task) {
            entity.getScheduler().run(plugin, scheduled -> task.run(), null);
        }

        static void global(Plugin plugin, Runnable task) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
        }

        static void region(Plugin plugin, Location location, Runnable task) {
            plugin.getServer().getRegionScheduler().execute(plugin, location, task);
        }

        static Task globalTimer(Plugin plugin, Runnable task, long delay, long period) {
            var scheduler = plugin.getServer().getGlobalRegionScheduler();
            var handle = period < 0 ? scheduler.runDelayed(plugin, scheduled -> task.run(), delay)
                    : scheduler.runAtFixedRate(plugin, scheduled -> task.run(), delay, period);
            return handle::cancel;
        }

        static void async(Plugin plugin, Runnable task) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> task.run());
        }

        static void cancelAll(Plugin plugin) {
            plugin.getServer().getGlobalRegionScheduler().cancelTasks(plugin);
            plugin.getServer().getAsyncScheduler().cancelTasks(plugin);
        }
    }

    private static final class PaperBridge {
        static CompletableFuture<?> loadChunk(Location location) {
            return location.getWorld().getChunkAtAsync(location);
        }

        static CompletableFuture<Boolean> teleport(Player player, Location destination) {
            return player.teleportAsync(destination, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }
}
