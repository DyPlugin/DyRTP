package dev.dyrtp.rtp;

import dev.dyrtp.DyRTP;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A few safe destinations found ahead of time per world, so /rtp can teleport without searching first.
 * Spots are found by the normal search and checked again before every teleport; a spot that turned unsafe
 * meanwhile falls back to a normal search. Only plain RTP uses the queue: biome choice and rank distances search live.
 */
public class LocationQueue {
    private final DyRTP plugin;
    private final LocationFinder finder;
    private final Map<UUID, Deque<Location>> spots = new ConcurrentHashMap<>();
    private final Set<UUID> filling = ConcurrentHashMap.newKeySet();
    private volatile long generation;

    public LocationQueue(DyRTP plugin, LocationFinder finder) {
        this.plugin = plugin;
        this.finder = finder;
    }

    public int size() {
        return Math.max(0, Math.min(20, plugin.getConfig().getInt("rtp.queue.size", 3)));
    }

    /** Fills the worlds listed in rtp.queue.worlds; other worlds are filled after their first RTP. */
    public void start() {
        if (size() == 0) return;
        for (String name : plugin.getConfig().getStringList("rtp.queue.worlds")) {
            World world = plugin.getServer().getWorld(name);
            if (world != null) refill(world);
        }
    }

    /**
     * A ready spot for a plain RTP from {@code origin}, or null. Spots closer to the player than the minimum
     * distance are skipped. Taking a spot starts finding a replacement.
     */
    public Location take(World world, Location origin) {
        if (size() == 0) return null;
        Deque<Location> queue = spots.get(world.getUID());
        Location found = null;
        if (queue != null) synchronized (queue) {
            int minimum = Math.max(0, plugin.getConfig().getInt("rtp.safety.min-distance-from-current", 250));
            for (var iterator = queue.iterator(); iterator.hasNext(); ) {
                Location spot = iterator.next();
                if (origin == null || !world.equals(origin.getWorld()) || spot.distanceSquared(origin) >= (double) minimum * minimum) {
                    iterator.remove();
                    found = spot.clone();
                    break;
                }
            }
        }
        refill(world);
        return found;
    }

    /** Searches for spots in the background until the world has rtp.queue.size of them. One search per world at a time. */
    public void refill(World world) {
        int target = size();
        if (target == 0 || !plugin.isEnabled() || plugin.getConfig().getStringList("rtp.disabled-worlds").contains(world.getName())) return;
        Deque<Location> queue = spots.computeIfAbsent(world.getUID(), id -> new ArrayDeque<>());
        synchronized (queue) {
            if (queue.size() >= target) return;
        }
        if (!filling.add(world.getUID())) return;
        long started = generation;
        finder.find(world, null).whenComplete((result, error) -> {
            filling.remove(world.getUID());
            if (started != generation || !plugin.isEnabled()) return;
            boolean added = false;
            if (error == null && result != null && result.location() != null) {
                synchronized (queue) {
                    if (queue.size() < target) {
                        queue.addLast(result.location().clone());
                        added = true;
                    }
                }
            }
            // A failed search is not retried at once; the next RTP in this world tries again.
            if (added) plugin.scheduler().runGlobal(() -> refill(world));
        });
    }

    /** Forgets every spot; settings may have changed. */
    public void clear() {
        generation++;
        spots.clear();
        filling.clear();
    }

    /** Spots ready in a world, for the status command and tests. */
    public int ready(World world) {
        Deque<Location> queue = spots.get(world.getUID());
        if (queue == null) return 0;
        synchronized (queue) {
            return queue.size();
        }
    }
}
